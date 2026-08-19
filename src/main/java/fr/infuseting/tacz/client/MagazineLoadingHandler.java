package fr.infuseting.tacz.client;

import cn.sh1rocu.tacz.api.event.InputEvent;
import com.mojang.blaze3d.platform.InputConstants;
import fr.infuseting.tacz.TaCZMagazines;
import fr.infuseting.tacz.config.GunOverrideConfig;
import fr.infuseting.tacz.config.MechanicsConfig;
import fr.infuseting.tacz.crafting.GunsmithIntegration;
import fr.infuseting.tacz.item.MagazineAmmoSource;
import fr.infuseting.tacz.item.MagazineItem;
import fr.infuseting.tacz.item.SoundRegistrar;
import fr.infuseting.tacz.magazine.MagazineFamilySystem;
import fr.infuseting.tacz.network.PacketHandler;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.util.InputExtraCheck;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class MagazineLoadingHandler {

    private static boolean pendingDiscovery = false;

    public static void scheduleDeferredDiscovery() {
        pendingDiscovery = true;
    }

    // â”€â”€ Inventory session state â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private static boolean active    = false;
    private static boolean unloading = false;
    private static int containerSlot = -1;
    private static int tickCounter   = 0;
    private static int totalTicks    = 1;

    public static float progress = 0f;

    // In-hand session state
    private static boolean inHandActive    = false;
    private static boolean inHandUnloading = false;
    private static int     inHandTick      = 0;
    private static int     inHandTotal     = 1;
    private static boolean unloadActiveBeforeMousePress = false;

    public static float inHandProgress = 0f;

    // Inspect delay state
    private static int inspectTimer = -1;
    private static ItemStack inspectStack = ItemStack.EMPTY;

    public static void startInspect(LocalPlayer player, ItemStack stack) {
        inspectTimer = 30; // 1.5 seconds delay matching inspection animation
        inspectStack = stack.copy();
    }

    public static void register() {
        InputEvent.InteractionKeyMappingTriggered.EVENT.register(MagazineLoadingHandler::onInteractionKey);
        InputEvent.Key.EVENT.register(MagazineLoadingHandler::onKeyInput);

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenMouseEvents.allowMouseClick(screen).register((s, mouseX, mouseY, button) -> {
                unloadActiveBeforeMousePress = active && unloading;
                return true;
            });
            ScreenMouseEvents.afterMouseClick(screen).register((s, mouseX, mouseY, button) -> {
                if (unloadActiveBeforeMousePress && active && unloading) {
                    cancel();
                }
                unloadActiveBeforeMousePress = false;
            });
        });
    }

    // â”€â”€ Public API â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public static boolean isActive()        { return active; }
    public static boolean isUnloading()     { return unloading; }
    public static int    getContainerSlot() { return containerSlot; }

    public static boolean isInHandActive()    { return inHandActive; }
    public static boolean isInHandUnloading() { return inHandUnloading; }

    public static void startLoading(int slot) {
        active        = true;
        unloading     = false;
        containerSlot = slot;
        totalTicks    = MechanicsConfig.effectiveLoadTicks();
        tickCounter   = totalTicks;
        progress      = 0f;
    }

    public static void startUnloading(int slot) {
        active        = true;
        unloading     = true;
        containerSlot = slot;
        totalTicks    = MechanicsConfig.effectiveUnloadTicks();
        tickCounter   = totalTicks;
        progress      = 0f;
    }

    public static void cancel() {
        active   = false;
        progress = 0f;
    }

    public static void startInHandLoading() {
        inHandActive    = true;
        inHandUnloading = false;
        inHandTotal     = MechanicsConfig.effectiveLoadTicks();
        inHandTick      = inHandTotal;
        inHandProgress  = 0f;
    }

    public static void startInHandUnloading() {
        inHandActive    = true;
        inHandUnloading = true;
        inHandTotal     = MechanicsConfig.effectiveUnloadTicks();
        inHandTick      = inHandTotal;
        inHandProgress  = 0f;
    }

    public static void cancelInHand() {
        inHandActive   = false;
        inHandProgress = 0f;
    }

    // â”€â”€ Client tick â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public static void onClientTick(Minecraft client) {
        if (pendingDiscovery && MagazineFamilySystem.getAllFamilies().isEmpty()) {
            if (!CommonAssetsManager.get().getAllGuns().isEmpty()) {
                pendingDiscovery = false;
                MagazineFamilySystem.discoverMagazineFamilies();
                GunOverrideConfig.apply();
                GunsmithIntegration.injectTabClientSide();
                MagazineItemRenderer.invalidateCache();
                TaCZMagazines.LOGGER.info("[MagazineLoadingHandler] Deferred client discovery complete");
            }
            return;
        }
        pendingDiscovery = false;

        Minecraft mc       = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (inspectTimer > 0) {
            inspectTimer--;
            if (player == null || !ItemStack.isSameItemSameTags(player.getMainHandItem(), inspectStack)) {
                inspectTimer = -1;
                inspectStack = ItemStack.EMPTY;
            } else if (inspectTimer == 0) {
                inspectTimer = -1;
                inspectStack = ItemStack.EMPTY;
                PacketHandler.sendCheckMagazine();
            }
        }

        // â”€â”€ In-hand session tick â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        if (inHandActive) {
            if (player == null || !isInHandSessionStillValid(player)) {
                cancelInHand();
            } else {
                inHandTick--;
                inHandProgress = 1f - (float) inHandTick / (float) inHandTotal;

                if (inHandTick <= 0) {
                    if (player.getAbilities().instabuild) {
                        if (inHandUnloading) {
                            creativeUnloadOneFromHand(player);
                        } else {
                            creativeLoadOneInHand(player);
                        }
                    } else if (inHandUnloading) {
                        PacketHandler.sendUnloadOneFromHand();
                    } else {
                        PacketHandler.sendLoadOneFromHand();
                    }
                    inHandTotal    = inHandUnloading ? MechanicsConfig.effectiveUnloadTicks()
                                                     : MechanicsConfig.effectiveLoadTicks();
                    inHandTick     = inHandTotal;
                    inHandProgress = 1f;
                }
            }
        }

        if (!active) return;

        if (player == null || mc.screen == null) {
            cancel();
            return;
        }

        if (!isSessionStillValid(player)) {
            cancel();
            return;
        }

        tickCounter--;
        progress = 1f - (float) tickCounter / (float) totalTicks;

        if (tickCounter <= 0) {
            if (player.getAbilities().instabuild) {
                creativeTransferInventoryRound(player);
            } else {
                PacketHandler.sendBulletTransfer(containerSlot, unloading);
            }

            totalTicks  = unloading ? MechanicsConfig.effectiveUnloadTicks()
                                     : MechanicsConfig.effectiveLoadTicks();
            tickCounter = totalTicks;
            progress    = 1f;
        }
    }

    private static void creativeTransferInventoryRound(LocalPlayer player) {
        if (containerSlot < 0 || containerSlot >= player.getInventory().items.size()) return;
        ItemStack magazine = player.getInventory().getItem(containerSlot);
        if (!(magazine.getItem() instanceof MagazineItem magItem)
                || magazine.isEmpty()) return;

        ItemStack extras;
        if (unloading) {
            int current = magItem.getAmmoCount(magazine);
            ResourceLocation ammoId = magItem.getAmmoId(magazine);
            if (current <= 0 || DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) return;

            extras = splitMagazineStack(magazine);
            magItem.setAmmoCount(magazine, current - 1);
            if (current == 1) magItem.setAmmoId(magazine, DefaultAssets.EMPTY_AMMO_ID);
            returnCreativeInventoryRound(player, ammoId);
            SoundRegistrar.playMagazineUnload(player);
        } else {
            String familyId = MagazineItem.getMagazineFamilyId(magazine);
            ResourceLocation familyAmmo = familyId == null
                    ? null
                    : MagazineFamilySystem.getAmmoTypeForFamily(familyId);
            int current = magItem.getAmmoCount(magazine);
            if (familyAmmo == null || current >= MagazineItem.getMaxCapacity(magazine)) return;

            ResourceLocation loadedAmmo = magItem.getAmmoId(magazine);
            if (!DefaultAssets.EMPTY_AMMO_ID.equals(loadedAmmo)
                    && !loadedAmmo.equals(familyAmmo)) return;

            extras = splitMagazineStack(magazine);
            magItem.setAmmoId(magazine, familyAmmo);
            magItem.setAmmoCount(magazine, current + 1);
            SoundRegistrar.playMagazineLoad(player);
        }

        returnSplitMagazines(player, extras);
        syncCreativeInventory(player, containerSlot, !extras.isEmpty());
    }

    private static void creativeLoadOneInHand(LocalPlayer player) {
        ItemStack magazine = player.getMainHandItem();
        if (!(magazine.getItem() instanceof MagazineItem magItem)) return;

        String familyId = MagazineItem.getMagazineFamilyId(magazine);
        ResourceLocation familyAmmo = familyId == null
                ? null
                : MagazineFamilySystem.getAmmoTypeForFamily(familyId);
        int current = magItem.getAmmoCount(magazine);
        if (familyAmmo == null || current >= MagazineItem.getMaxCapacity(magazine)) return;

        ResourceLocation loadedAmmo = magItem.getAmmoId(magazine);
        if (!DefaultAssets.EMPTY_AMMO_ID.equals(loadedAmmo)
                    && !loadedAmmo.equals(familyAmmo)) return;

        ItemStack extras = splitMagazineStack(magazine);
        magItem.setAmmoId(magazine, familyAmmo);
        magItem.setAmmoCount(magazine, current + 1);
        returnSplitMagazines(player, extras);
        syncCreativeInventory(player, player.getInventory().selected, !extras.isEmpty());
        SoundRegistrar.playMagazineLoad(player);
    }

    private static void creativeUnloadOneFromHand(LocalPlayer player) {
        ItemStack magazine = player.getMainHandItem();
        if (!(magazine.getItem() instanceof MagazineItem magItem)) return;

        int current = magItem.getAmmoCount(magazine);
        ResourceLocation ammoId = magItem.getAmmoId(magazine);
        if (current <= 0 || DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) return;

        ItemStack extras = splitMagazineStack(magazine);
        magItem.setAmmoCount(magazine, current - 1);
        if (current == 1) magItem.setAmmoId(magazine, DefaultAssets.EMPTY_AMMO_ID);
        returnSplitMagazines(player, extras);

        ItemStack bullet = AmmoItemBuilder.create().setId(ammoId).setCount(1).build();
        if (!player.getInventory().add(bullet)) player.drop(bullet, false);
        syncCreativeInventory(player, player.getInventory().selected, true);
        SoundRegistrar.playMagazineUnload(player);
    }

    private static ItemStack splitMagazineStack(ItemStack magazine) {
        if (magazine.getCount() <= 1) return ItemStack.EMPTY;
        return magazine.split(magazine.getCount() - 1);
    }

    private static void returnSplitMagazines(LocalPlayer player, ItemStack extras) {
        if (!extras.isEmpty() && !player.getInventory().add(extras)) {
            player.drop(extras, false);
        }
    }

    private static void returnCreativeInventoryRound(LocalPlayer player, ResourceLocation ammoId) {
        AbstractContainerMenu menu = getVisibleMenu(player);
        if (menu == null) return;

        ItemStack bullet = AmmoItemBuilder.create().setId(ammoId).setCount(1).build();
        ItemStack cursor = menu.getCarried();
        if (cursor.isEmpty()) {
            menu.setCarried(bullet);
        } else if (ItemStack.isSameItemSameTags(cursor, bullet)
                && cursor.getCount() < cursor.getMaxStackSize()) {
            cursor.grow(1);
            menu.setCarried(cursor);
        } else if (!player.getInventory().add(bullet)) {
            player.drop(bullet, false);
        }
    }

    private static AbstractContainerMenu getVisibleMenu(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AbstractContainerScreen<?> screen) {
            return screen.getMenu();
        }
        return player.containerMenu;
    }

    private static void syncCreativeInventory(LocalPlayer player, int changedSlot, boolean syncAll) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null) return;

        if (syncAll) {
            for (int inventorySlot = 0; inventorySlot < player.getInventory().items.size(); inventorySlot++) {
                mc.gameMode.handleCreativeModeItemAdd(
                        player.getInventory().getItem(inventorySlot).copy(),
                        inventoryMenuSlot(inventorySlot));
            }
        } else {
            mc.gameMode.handleCreativeModeItemAdd(
                    player.getInventory().getItem(changedSlot).copy(),
                    inventoryMenuSlot(changedSlot));
        }
        player.getInventory().setChanged();
    }

    private static int inventoryMenuSlot(int inventorySlot) {
        return inventorySlot < 9 ? 36 + inventorySlot : inventorySlot;
    }

    private static boolean isSessionStillValid(LocalPlayer player) {
        if (containerSlot < 0 || containerSlot >= player.getInventory().items.size()) return false;
        ItemStack mag = player.getInventory().getItem(containerSlot);
        if (mag.isEmpty() || !(mag.getItem() instanceof MagazineItem magItem)) return false;

        if (unloading) {
            return magItem.getAmmoCount(mag) > 0;
        } else {
            String familyId = MagazineItem.getMagazineFamilyId(mag);
            if (familyId == null) return false;

            ResourceLocation familyAmmo = MagazineFamilySystem.getAmmoTypeForFamily(familyId);
            if (familyAmmo == null) return false;
            AbstractContainerMenu menu = getVisibleMenu(player);
            if (menu == null) return false;
            if (!player.getAbilities().instabuild
                    && MagazineAmmoSource.compatibleAmmoId(menu.getCarried(), familyAmmo) == null) return false;

            return magItem.getAmmoCount(mag) < MagazineItem.getMaxCapacity(mag);
        }
    }

    private static boolean isInHandSessionStillValid(LocalPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !(held.getItem() instanceof MagazineItem magItem)) return false;

        if (inHandUnloading) {
            return magItem.getAmmoCount(held) > 0;
        } else {
            int current = magItem.getAmmoCount(held);
            if (current >= MagazineItem.getMaxCapacity(held)) return false;
            return hasCompatibleAmmo((LocalPlayer) player, held, magItem);
        }
    }

    private static boolean hasCompatibleAmmo(LocalPlayer player, ItemStack mag, MagazineItem magItem) {
        if (player.getAbilities().instabuild) return true;
        String familyId = MagazineItem.getMagazineFamilyId(mag);
        if (familyId == null) return false;
        ResourceLocation familyAmmo = MagazineFamilySystem.getAmmoTypeForFamily(familyId);
        if (familyAmmo == null) return false;
        ResourceLocation magAmmoId = magItem.getAmmoId(mag);

        for (ItemStack source : player.getInventory().items) {
            ResourceLocation ammoId = MagazineAmmoSource.compatibleAmmoId(source, familyAmmo);
            if (ammoId == null) continue;
            if (!DefaultAssets.EMPTY_AMMO_ID.equals(magAmmoId) && !magAmmoId.equals(ammoId)) continue;
            if (MagazineAmmoSource.available(source) > 0) return true;
        }
        return false;
    }

    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !InputExtraCheck.isInGame()) return;

        ItemStack held = mc.player.getMainHandItem();
        if (!(held.getItem() instanceof MagazineItem magItem)) return;

        event.setCanceled(true);
        event.setSwingHand(false);

        if (MechanicsConfig.IN_HAND_TICK_BASED.get()) {
            if (inHandActive && !inHandUnloading) {
                cancelInHand();
                return;
            }
            cancelInHand();
            if (hasCompatibleAmmo((LocalPlayer) mc.player, held, magItem)
                    && magItem.getAmmoCount(held) < MagazineItem.getMaxCapacity(held)) {
                startInHandLoading();
            }
        } else if (MechanicsConfig.TICK_BASED.get()) {
            if (magItem.getAmmoCount(held) < MagazineItem.getMaxCapacity(held)
                    && hasCompatibleAmmo((LocalPlayer) mc.player, held, magItem)) {
                PacketHandler.sendLoadOneFromHand();
            }
        }
    }

    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        if (event.getKey() == GLFW.GLFW_KEY_ESCAPE) {
            boolean handled = active || inHandActive;
            if (active) cancel();
            if (inHandActive) cancelInHand();
            if (handled) return;
        }

        if (ModKeybinds.matchesUnloadKey(event.getKey(), event.getScanCode())) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null || player.isSpectator() || !InputExtraCheck.isInGame()) return;
            ItemStack held = player.getMainHandItem();
            if (held.getItem() instanceof IGun) {
                PacketHandler.sendUnloadGunMag();
            }
        }

        if (ModKeybinds.matchesCheckKey(event.getKey(), event.getScanCode())) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null || player.isSpectator() || !InputExtraCheck.isInGame()) return;
            ItemStack held = player.getMainHandItem();
            if (held.getItem() instanceof IGun) {
                com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(player).inspect();
                startInspect(player, held);
            } else if (held.getItem() instanceof MagazineItem) {
                PacketHandler.sendCheckMagazine();
            }
        }

        if (ModKeybinds.matchesFastReloadKey(event.getKey(), event.getScanCode())) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null || player.isSpectator() || !InputExtraCheck.isInGame()) return;
            ItemStack held = player.getMainHandItem();
            if (held.getItem() instanceof IGun) {
                held.getOrCreateTag().putBoolean("TaCZMag_FastReloadActive", true);
                held.getOrCreateTag().putBoolean("TaCZMag_FastReload", true);
                ClientReloadKeyHandler.setFastReloadActive(true);
                PacketHandler.sendSelectMagazine(-1, true);
                com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(player).reload();
            }
        }
    }
}

