package fr.infuseting.tacz.item;

import fr.infuseting.tacz.ammo.AmmoStack;
import fr.infuseting.tacz.config.MechanicsConfig;
import fr.infuseting.tacz.magazine.MagazineFamilySystem;
import fr.infuseting.tacz.tooltip.MagazineTooltipData;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class MagazineItem extends Item implements IAmmoBox {
    private static final String AMMO_ID_TAG = "AmmoId";
    private static final String AMMO_COUNT_TAG = "AmmoCount";
    private static final String FAMILY_ID_TAG = "MagazineFamily";
    private static final String MAX_CAPACITY_TAG = "MaxCapacity";
    private static final String IS_CHECKED_TAG = "IsChecked";

    public MagazineItem(Properties properties) {
        super(properties);
    }

    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    // ========== MAGAZINE FAMILY METHODS ==========

    public static boolean isChecked(ItemStack magazine) {
        if (magazine.isEmpty() || !(magazine.getItem() instanceof MagazineItem magItem)) return false;
        int ammo = magItem.getAmmoCount(magazine);
        int max = getMaxCapacity(magazine);
        if (ammo == 0 || ammo >= max) {
            return true;
        }
        CompoundTag tag = magazine.getTag();
        return tag != null && tag.contains(IS_CHECKED_TAG, Tag.TAG_BYTE) && tag.getBoolean(IS_CHECKED_TAG);
    }

    public static boolean isGunMagChecked(ItemStack magazine, int currentAmmo) {
        if (magazine.isEmpty() || !(magazine.getItem() instanceof MagazineItem magItem)) return false;
        CompoundTag tag = magazine.getTag();
        boolean hasExplicitCheckTag = tag != null && tag.contains(IS_CHECKED_TAG, Tag.TAG_BYTE) && tag.getBoolean(IS_CHECKED_TAG);
        if (!hasExplicitCheckTag) return false;
        return magItem.getAmmoCount(magazine) == currentAmmo;
    }

    public static void setChecked(ItemStack magazine, boolean checked) {
        if (magazine.isEmpty()) return;
        CompoundTag tag = magazine.getOrCreateTag();
        if (checked) {
            tag.putBoolean(IS_CHECKED_TAG, true);
        } else {
            tag.remove(IS_CHECKED_TAG);
        }
    }

    public static String getMagazineFamilyId(ItemStack magazine) {
        CompoundTag tag = magazine.getTag();
        if (tag != null && tag.contains(FAMILY_ID_TAG, Tag.TAG_STRING)) {
            return tag.getString(FAMILY_ID_TAG);
        }
        return null;
    }

    public static int getMaxCapacity(ItemStack magazine) {
        CompoundTag tag = magazine.getTag();
        if (tag != null && tag.contains(MAX_CAPACITY_TAG, Tag.TAG_INT)) {
            return tag.getInt(MAX_CAPACITY_TAG);
        }

        String familyId = getMagazineFamilyId(magazine);
        if (familyId != null) {
            return MagazineFamilySystem.getCapacityForFamily(familyId);
        }

        return 30;
    }

    @Override
    public ResourceLocation getAmmoId(ItemStack magazine) {
        return AmmoStack.fromItemStack(magazine).peek();
    }

    @Override
    public int getAmmoCount(ItemStack magazine) {
        return AmmoStack.fromItemStack(magazine).getTotalCount();
    }

    @Override
    public void setAmmoId(ItemStack magazine, ResourceLocation ammoId) {
        AmmoStack ammoStack = AmmoStack.fromItemStack(magazine);
        if (ammoId == null || ammoId.equals(DefaultAssets.EMPTY_AMMO_ID)) {
            ammoStack.clear();
        } else {
            if (ammoStack.isEmpty()) {
                ammoStack.push(ammoId, 1);
            }
        }
        ammoStack.saveToItemStack(magazine);
        setChecked(magazine, false);
    }

    @Override
    public void setAmmoCount(ItemStack magazine, int count) {
        AmmoStack ammoStack = AmmoStack.fromItemStack(magazine);
        int maxCapacity = getMaxCapacity(magazine);
        int targetCount = Math.max(0, Math.min(count, maxCapacity));
        int currentCount = ammoStack.getTotalCount();
        if (targetCount < currentCount) {
            ammoStack.pop(currentCount - targetCount);
        } else if (targetCount > currentCount) {
            ResourceLocation topId = ammoStack.peek();
            if (topId.equals(DefaultAssets.EMPTY_AMMO_ID)) {
                String familyId = getMagazineFamilyId(magazine);
                if (familyId != null) {
                    topId = MagazineFamilySystem.getAmmoTypeForFamily(familyId);
                }
            }
            if (topId != null && !topId.equals(DefaultAssets.EMPTY_AMMO_ID)) {
                ammoStack.push(topId, targetCount - currentCount);
            }
        }
        ammoStack.saveToItemStack(magazine);
        setChecked(magazine, false);
    }

    @Override
    public boolean isAmmoBoxOfGun(ItemStack gun, ItemStack magazine) {
        if (!(gun.getItem() instanceof IGun iGun)) return false;

        ResourceLocation gunId = iGun.getGunId(gun);
        String magazineFamilyId = getMagazineFamilyId(magazine);

        if (magazineFamilyId == null) return false;
        if (!MagazineFamilySystem.isMagazineCompatibleWithGun(magazineFamilyId, gunId)) return false;

        // Extended mags require the gun to have the corresponding attachment installed,
        // unless the config option to bypass that requirement is enabled.
        if (MagazineFamilySystem.isExtendedFamily(magazineFamilyId)) {
            if (!MechanicsConfig.ALLOW_EXTENDED_WITHOUT_ATTACHMENT.get()) {
                int required = MagazineFamilySystem.getExtLevelForFamily(magazineFamilyId);
                var gunIndexOpt = com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gunId);
                if (!gunIndexOpt.isPresent()) return false;
                int installed = com.tacz.guns.util.AttachmentDataUtils.getMagExtendLevel(gun, gunIndexOpt.get().getGunData());
                return installed >= required;
            }
        }

        return true;
    }

    @Override
    public ItemStack setAmmoLevel(ItemStack magazine, int ammoLevel) {
        return magazine;
    }

    @Override
    public int getAmmoLevel(ItemStack magazine) {
        return 0;
    }

    @Override
    public boolean isCreative(ItemStack magazine) {
        return false;
    }

    @Override
    public boolean isAllTypeCreative(ItemStack magazine) {
        return false;
    }

    @Override
    public ItemStack setCreative(ItemStack magazine, boolean isAllType) {
        return magazine;
    }

    // =========================================================================
    // Inventory click overrides â€” supports both drop-in and tick-based modes
    // =========================================================================

    @Override
    public boolean overrideStackedOnOther(ItemStack stack, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) return false;
        if (!isPlayerInventorySlot(slot, player)) return false;

        String familyId = getMagazineFamilyId(stack);
        if (familyId == null) return false;

        ResourceLocation familyAmmo = MagazineFamilySystem.getAmmoTypeForFamily(familyId);
        if (familyAmmo == null) return false;

        ItemStack other = slot.getItem();
        ResourceLocation heldAmmoId = player.getAbilities().instabuild
                ? familyAmmo
                : MagazineAmmoSource.compatibleAmmoId(other, familyAmmo);
        if (heldAmmoId == null || heldAmmoId.equals(DefaultAssets.EMPTY_AMMO_ID)) return false;

        int maxCapacity  = getMaxCapacity(stack);
        int magAmmoCount = this.getAmmoCount(stack);
        if (magAmmoCount >= maxCapacity) return false;

        if (player.level().isClientSide && !player.getAbilities().instabuild) return true;

        if (MechanicsConfig.TICK_BASED.get()) {
            if (!player.getAbilities().instabuild
                    && MagazineAmmoSource.available(other) <= 0) return false;

            if (stack.getCount() > 1) {
                ItemStack extras = stack.copy();
                extras.setCount(stack.getCount() - 1);
                stack.setCount(1);
                if (!player.getInventory().add(extras)) player.drop(extras, false);
            }

            AmmoStack ammoStack = AmmoStack.fromItemStack(stack);
            ammoStack.push(heldAmmoId, 1);
            ammoStack.saveToItemStack(stack);
            setChecked(stack, false);

            if (!player.getAbilities().instabuild) {
                MagazineAmmoSource.consume(other, 1);
            }
            slot.setChanged();
        } else {
            int space    = maxCapacity - magAmmoCount;
            int transfer = player.getAbilities().instabuild
                    ? space
                    : Math.min(MagazineAmmoSource.available(other), space);
            if (transfer <= 0) return false;

            if (stack.getCount() > 1) {
                ItemStack extras = stack.copy();
                extras.setCount(stack.getCount() - 1);
                stack.setCount(1);
                if (!player.getInventory().add(extras)) player.drop(extras, false);
            }

            AmmoStack ammoStack = AmmoStack.fromItemStack(stack);
            ammoStack.push(heldAmmoId, transfer);
            ammoStack.saveToItemStack(stack);
            setChecked(stack, false);

            if (!player.getAbilities().instabuild) {
                MagazineAmmoSource.consume(other, transfer);
            }
            slot.setChanged();
        }

        this.playInsertSound(player);
        return true;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack magazine, ItemStack heldStack,
                                            Slot slot, ClickAction action,
                                            Player player, SlotAccess heldAccess) {
        boolean tickBased = MechanicsConfig.TICK_BASED.get();
        int magazineInventorySlot = findPlayerInventoryIndex(slot, magazine, player);
        if (magazineInventorySlot < 0) return false;

        // â”€â”€ Empty cursor â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        if (heldStack.isEmpty()) {
            if (action != ClickAction.SECONDARY) return false;

            ResourceLocation ammoId = this.getAmmoId(magazine);
            if (ammoId.equals(DefaultAssets.EMPTY_AMMO_ID) || this.getAmmoCount(magazine) <= 0) return false;

            if (player.getAbilities().instabuild && !tickBased) {
                boolean unloaded = unloadAllCreative(magazine, player, heldAccess);
                if (unloaded) slot.setChanged();
                return unloaded;
            }

            if (tickBased) {
                if (player.level().isClientSide) {
                    final int idx = magazineInventorySlot;
                    triggerClientUnload(idx);
                    return true;
                }
                // SERVER: split immediately so the session operates on a singleton
                if (magazine.getCount() > 1) {
                    ItemStack extras = magazine.copy();
                    extras.setCount(magazine.getCount() - 1);
                    magazine.setCount(1);
                    slot.set(magazine);
                    if (!player.getInventory().add(extras)) player.drop(extras, false);
                    player.containerMenu.broadcastChanges();
                }
                return true;
            } else {
                if (player.level().isClientSide) return true;
                return unloadAll(magazine, player);
            }
        }

        // â”€â”€ Held ammo â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        String familyId = getMagazineFamilyId(magazine);
        if (familyId == null) return false;

        ResourceLocation familyAmmoType = MagazineFamilySystem.getAmmoTypeForFamily(familyId);
        if (familyAmmoType == null) return false;

        ResourceLocation heldAmmoId = player.getAbilities().instabuild
                ? familyAmmoType
                : MagazineAmmoSource.compatibleAmmoId(heldStack, familyAmmoType);
        if (heldAmmoId == null || heldAmmoId.equals(DefaultAssets.EMPTY_AMMO_ID)) return false;

        int maxCapacity  = getMaxCapacity(magazine);
        int magAmmoCount = this.getAmmoCount(magazine);
        if (magAmmoCount >= maxCapacity) return false;

        if (player.getAbilities().instabuild && !tickBased) {
            this.setAmmoId(magazine, familyAmmoType);
            this.setAmmoCount(magazine, maxCapacity);
            slot.setChanged();
            this.playInsertSound(player);
            return true;
        }

        if (tickBased) {
            if (action == ClickAction.PRIMARY) {
                if (player.level().isClientSide) {
                    final int idx = magazineInventorySlot;
                    triggerClientLoad(idx);
                    return true;
                }
                // SERVER: split immediately so the session operates on a singleton
                if (magazine.getCount() > 1) {
                    ItemStack extras = magazine.copy();
                    extras.setCount(magazine.getCount() - 1);
                    magazine.setCount(1);
                    slot.set(magazine);
                    if (!player.getInventory().add(extras)) player.drop(extras, false);
                    player.containerMenu.broadcastChanges();
                }
                return true;
            } else if (action == ClickAction.SECONDARY) {
                if (player.level().isClientSide) {
                    triggerClientCancel();
                }
                return true;
            }
            return false;
        } else {
            if (action != ClickAction.SECONDARY) return false;
            if (player.level().isClientSide) return true;
            boolean filled = fillAll(magazine, heldStack, heldAmmoId, magAmmoCount, maxCapacity, player, heldAccess);
            if (filled) slot.setChanged();
            return filled;
        }
    }

    @Environment(EnvType.CLIENT)
    private static void triggerClientUnload(int idx) {
        ClientMagazineActionHelper.startOrToggleUnloading(idx);
    }

    @Environment(EnvType.CLIENT)
    private static void triggerClientLoad(int idx) {
        ClientMagazineActionHelper.startOrToggleLoading(idx);
    }

    @Environment(EnvType.CLIENT)
    private static void triggerClientCancel() {
        ClientMagazineActionHelper.cancelLoading();
    }

    @Environment(EnvType.CLIENT)
    private static void triggerClientInHandUnload() {
        ClientMagazineActionHelper.startOrToggleInHandUnloading();
    }

    // â”€â”€ Transfer helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private boolean fillAll(ItemStack magazine, ItemStack heldStack,
                             ResourceLocation ammoId, int current, int max,
                             Player player, SlotAccess heldAccess) {
        int space    = max - current;
        int transfer = Math.min(MagazineAmmoSource.available(heldStack), space);
        if (transfer <= 0) return false;

        if (magazine.getCount() == 1) {
            AmmoStack ammoStack = AmmoStack.fromItemStack(magazine);
            ammoStack.push(ammoId, transfer);
            ammoStack.saveToItemStack(magazine);
            MagazineAmmoSource.consume(heldStack, transfer);
        } else {
            ItemStack filledMag = magazine.copyWithCount(1);
            AmmoStack ammoStack = AmmoStack.fromItemStack(filledMag);
            ammoStack.push(ammoId, transfer);
            ammoStack.saveToItemStack(filledMag);
            if (!player.getInventory().add(filledMag)) return false;
            magazine.shrink(1);
            MagazineAmmoSource.consume(heldStack, transfer);
        }

        this.playInsertSound(player);
        return true;
    }

    private boolean unloadAll(ItemStack magazine, Player player) {
        AmmoStack ammoStack = AmmoStack.fromItemStack(magazine);
        if (ammoStack.isEmpty()) return false;

        CompoundTag srcTag = magazine.getTag();
        String familyId = srcTag != null && srcTag.contains(FAMILY_ID_TAG) ? srcTag.getString(FAMILY_ID_TAG) : null;
        int maxCap      = srcTag != null && srcTag.contains(MAX_CAPACITY_TAG) ? srcTag.getInt(MAX_CAPACITY_TAG) : -1;

        magazine.shrink(1);

        ItemStack emptyMag = new ItemStack(this);
        CompoundTag emptyTag = emptyMag.getOrCreateTag();
        if (familyId != null) emptyTag.putString(FAMILY_ID_TAG, familyId);
        if (maxCap >= 0) emptyTag.putInt(MAX_CAPACITY_TAG, maxCap);
        if (!player.getInventory().add(emptyMag)) player.drop(emptyMag, false);

        while (!ammoStack.isEmpty()) {
            AmmoStack.AmmoEntry top = ammoStack.getEntries().get(ammoStack.getEntries().size() - 1);
            ResourceLocation ammoId = top.getId();
            int count = top.getCount();
            ammoStack.pop(count);

            int remaining = count;
            while (remaining > 0) {
                int give = Math.min(remaining, 64);
                ItemStack ammoStackItem = AmmoItemBuilder.create().setId(ammoId).setCount(give).build();
                if (!player.getInventory().add(ammoStackItem)) player.drop(ammoStackItem, false);
                remaining -= give;
            }
        }

        this.playRemoveOneSound(player);
        return true;
    }

    private boolean unloadAllCreative(ItemStack magazine, Player player, SlotAccess heldAccess) {
        ResourceLocation ammoId = this.getAmmoId(magazine);
        int ammoCount = this.getAmmoCount(magazine);
        if (ammoId.equals(DefaultAssets.EMPTY_AMMO_ID) || ammoCount <= 0) return false;

        CompoundTag srcTag = magazine.getTag();
        String familyId = srcTag != null && srcTag.contains(FAMILY_ID_TAG)
                ? srcTag.getString(FAMILY_ID_TAG)
                : null;
        int maxCap = srcTag != null && srcTag.contains(MAX_CAPACITY_TAG)
                ? srcTag.getInt(MAX_CAPACITY_TAG)
                : -1;

        ItemStack loadedExtras = ItemStack.EMPTY;
        if (magazine.getCount() > 1) {
            loadedExtras = magazine.copyWithCount(magazine.getCount() - 1);
            magazine.setCount(1);
        }

        this.setAmmoCount(magazine, 0);
        this.setAmmoId(magazine, DefaultAssets.EMPTY_AMMO_ID);
        CompoundTag emptyTag = magazine.getOrCreateTag();
        if (familyId != null) emptyTag.putString(FAMILY_ID_TAG, familyId);
        if (maxCap >= 0) emptyTag.putInt(MAX_CAPACITY_TAG, maxCap);

        if (!loadedExtras.isEmpty() && !player.getInventory().add(loadedExtras)) {
            player.drop(loadedExtras, false);
        }

        ItemStack cursorAmmo = AmmoItemBuilder.create().setId(ammoId).setCount(1).build();
        int cursorCount = Math.min(ammoCount, cursorAmmo.getMaxStackSize());
        cursorAmmo.setCount(cursorCount);
        heldAccess.set(cursorAmmo);

        int remaining = ammoCount - cursorCount;
        while (remaining > 0) {
            ItemStack ammoStack = AmmoItemBuilder.create().setId(ammoId).setCount(1).build();
            int give = Math.min(remaining, ammoStack.getMaxStackSize());
            ammoStack.setCount(give);
            if (!player.getInventory().add(ammoStack)) player.drop(ammoStack, false);
            remaining -= give;
        }

        this.playRemoveOneSound(player);
        return true;
    }

    private void playRemoveOneSound(Entity entity) {
        SoundRegistrar.playMagazineUnload(entity);
    }

    private void playInsertSound(Entity entity) {
        SoundRegistrar.playMagazineLoad(entity);
    }

    private static boolean isPlayerInventorySlot(Slot slot, Player player) {
        return slot != null
                && findPlayerInventoryIndex(slot, slot.getItem(), player) >= 0;
    }

    private static int findPlayerInventoryIndex(Slot slot, ItemStack expected, Player player) {
        if (slot == null) return -1;

        if (!expected.isEmpty()) {
            for (int i = 0; i < player.getInventory().items.size(); i++) {
                if (player.getInventory().getItem(i) == expected) return i;
            }
        }

        int inventoryIndex = slot.getContainerSlot();
        if (slot.container == player.getInventory()
                && inventoryIndex >= 0
                && inventoryIndex < player.getInventory().items.size()) {
            return inventoryIndex;
        }
        return -1;
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return true;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return isChecked(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int ammoCount = this.getAmmoCount(stack);
        int maxCapacity = getMaxCapacity(stack);
        if (maxCapacity <= 0) return 0;
        double widthPercent = (double) ammoCount / (double) maxCapacity;
        return (int) Math.round(13.0 * widthPercent);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        int ammoCount = this.getAmmoCount(stack);
        if (ammoCount == 0) return 0xFF555555;
        return Mth.hsvToRgb(0.1f, 0.8F, 1.0F);
    }

    @Override
    public Component getName(ItemStack stack) {
        String familyId = getMagazineFamilyId(stack);
        if (familyId == null) {
            return Component.literal("Magazine");
        }

        return Component.literal(MagazineFamilySystem.getFamilyDisplayName(familyId));
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String familyId = getMagazineFamilyId(stack);
        if (familyId != null) {
            Set<ResourceLocation> compatibleGuns = MagazineFamilySystem.getCompatibleGuns(familyId);

            if (Screen.hasShiftDown()) {
                if (!compatibleGuns.isEmpty()) {
                    tooltip.add(Component.literal("Compatible Guns:")
                            .withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE));

                    List<String> gunNames = compatibleGuns.stream()
                            .map(ResourceLocation::getPath)
                            .sorted()
                            .collect(Collectors.toList());

                    for (String gunName : gunNames) {
                        tooltip.add(Component.literal("  • " + gunName)
                                .withStyle(ChatFormatting.GRAY));
                    }
                }
            } else {
                if (!compatibleGuns.isEmpty()) {
                    tooltip.add(Component.literal("Compatible: " + compatibleGuns.size() + " Gun(s)")
                            .withStyle(ChatFormatting.AQUA));
                    tooltip.add(Component.literal("[Hold SHIFT for list]")
                            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                }
            }
        }

        tooltip.add(Component.literal("Hold ammo + left-click magazine to fill  |  Right-click in hand to unload")
                .withStyle(ChatFormatting.GOLD));
    }

    // ========== RIGHT-CLICK TO UNLOAD ==========

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack heldStack = player.getItemInHand(hand);
        ResourceLocation ammoId = this.getAmmoId(heldStack);

        if (ammoId.equals(DefaultAssets.EMPTY_AMMO_ID) || this.getAmmoCount(heldStack) <= 0) {
            return InteractionResultHolder.pass(heldStack);
        }

        if (level.isClientSide) {
            if (MechanicsConfig.IN_HAND_TICK_BASED.get()) {
                triggerClientInHandUnload();
            }
            return InteractionResultHolder.consume(heldStack);
        }

        if (MechanicsConfig.IN_HAND_TICK_BASED.get()) {
            return InteractionResultHolder.consume(heldStack);
        }

        if (MechanicsConfig.TICK_BASED.get()) {
            int ammoCount = this.getAmmoCount(heldStack);
            if (ammoCount <= 0) return InteractionResultHolder.pass(heldStack);

            ItemStack singleMag;
            if (heldStack.getCount() > 1) {
                singleMag = heldStack.copyWithCount(1);
                heldStack.shrink(1);
                player.setItemInHand(hand, heldStack);
                int newCount = ammoCount - 1;
                this.setAmmoCount(singleMag, newCount);
                if (newCount == 0) this.setAmmoId(singleMag, DefaultAssets.EMPTY_AMMO_ID);
                if (!player.getInventory().add(singleMag)) player.drop(singleMag, false);
            } else {
                int newCount = ammoCount - 1;
                this.setAmmoCount(heldStack, newCount);
                if (newCount == 0) this.setAmmoId(heldStack, DefaultAssets.EMPTY_AMMO_ID);
            }

            ItemStack bullet = AmmoItemBuilder.create().setId(ammoId).setCount(1).build();
            if (!player.getInventory().add(bullet)) player.drop(bullet, false);
            playRemoveOneSound(player);
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }

        // Drop-in mode: unload all ammo at once
        int ammoCount = this.getAmmoCount(heldStack);
        ItemStack singleMag = heldStack.copyWithCount(1);
        heldStack.shrink(1);

        int remaining = ammoCount;
        while (remaining > 0) {
            int give = Math.min(remaining, 64);
            ItemStack ammoStack = AmmoItemBuilder.create().setId(ammoId).setCount(give).build();
            if (!player.getInventory().add(ammoStack)) {
                player.drop(ammoStack, false);
            }
            remaining -= give;
        }

        this.setAmmoCount(singleMag, 0);
        this.setAmmoId(singleMag, DefaultAssets.EMPTY_AMMO_ID);

        if (!player.getInventory().add(singleMag)) {
            player.drop(singleMag, false);
        }

        playRemoveOneSound(player);
        return InteractionResultHolder.success(heldStack);
    }

    // ========== FACTORY METHODS ==========

    public static ItemStack createMagazineByFamily(Item magazineItem, String familyId, int ammoCount) {
        ItemStack stack = new ItemStack(magazineItem);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(FAMILY_ID_TAG, familyId);

        int capacity = MagazineFamilySystem.getCapacityForFamily(familyId);
        tag.putInt(MAX_CAPACITY_TAG, capacity);
        return stack;
    }

    public static ItemStack createMagazineByFamily(Item magazineItem, String familyId, int ammoCount, ResourceLocation ammoId) {
        ItemStack stack = createMagazineByFamily(magazineItem, familyId, ammoCount);
        int capacity = MagazineFamilySystem.getCapacityForFamily(familyId);
        int count = Math.min(ammoCount, capacity);
        if (count > 0 && ammoId != null && !DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) {
            AmmoStack ammoStack = new AmmoStack();
            ammoStack.push(ammoId, count);
            ammoStack.saveToItemStack(stack);
        }
        return stack;
    }

    @Override
    public Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.of(new MagazineTooltipData(stack));
    }
}

