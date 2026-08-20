package com.tacz.guns.api.item.gun;

import cn.sh1rocu.tacz.api.extension.IItem;
import cn.sh1rocu.tacz.util.itemhandler.IItemHandler;
import cn.sh1rocu.tacz.util.itemhandler.ItemHandlerHelper;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.*;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.inventory.tooltip.GunTooltip;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import com.tacz.guns.util.AttachmentDataUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Supplier;

public abstract class AbstractGunItem extends Item implements IGun, IAnimationItem, IItem {
    protected AbstractGunItem(Properties pProperties) {
        super(pProperties);
    }

    private static Comparator<Map.Entry<ResourceLocation, CommonGunIndex>> idNameSort() {
        return Comparator.comparingInt(m -> m.getValue().getSort());
    }

    /**
     * 开始拉栓时调用，返回 bolt 状态
     *
     * @return bolt 状态。ture 代表开始 bolt，false 则代表不开始。
     */
    public abstract boolean startBolt(ShooterDataHolder dataHolder, ItemStack gunItem, LivingEntity shooter);

    /**
     * 拉栓 tick 时调用，返回是否仍在 bolt 状态
     *
     * @return 是否仍在 bolt 状态
     */
    public abstract boolean tickBolt(ShooterDataHolder dataHolder, ItemStack gunItem, LivingEntity shooter);

    /**
     * 射击时触发
     */
    public abstract void shoot(ShooterDataHolder dataHolder, ItemStack gunItem, Supplier<Float> pitch, Supplier<Float> yaw, LivingEntity shooter);

    /**
     * 开始换弹时调用
     */
    public abstract boolean startReload(ShooterDataHolder dataHolder, ItemStack gunItem, LivingEntity shooter);

    /**
     * 换弹时每个 tick 调用
     *
     * @return 如果返回的类型是 NOT_RELOADING 则下一个 tick 不再继续调用
     */
    public abstract ReloadState tickReload(ShooterDataHolder dataHolder, ItemStack gunItem, LivingEntity shooter);

    /**
     * 尝试打断换弹时调用
     */
    public abstract void interruptReload(ShooterDataHolder dataHolder, ItemStack gunItem, LivingEntity shooter);

    /**
     * 切换开火模式时调用
     */
    public abstract void fireSelect(ShooterDataHolder dataHolder, ItemStack gunItem);

    /**
     * 近战时调用
     */
    public abstract void melee(ShooterDataHolder dataHolder, LivingEntity user, ItemStack gunItem);

    /**
     * 过热 tick 处理<br/>
     * 默认不做任何事情
     */
    public void tickHeat(ShooterDataHolder dataHolder, ItemStack gunItem, LivingEntity shooter) {
    }

    ;

    /**
     * 初始化子弹角度和速度
     *
     * @param dataHolder     状态数据
     * @param gunItem        枪械物品
     * @param shooter        射击者
     * @param projectile     子弹
     * @param bulletCnt      多弹丸的子弹序数
     * @param processedSpeed 修正后的子弹初速
     * @param inaccuracy     修正后的子弹不准确度
     * @param pitch          射击方向
     * @param yaw            射击方向
     */
    public void doBulletSpread(ShooterDataHolder dataHolder, ItemStack gunItem, LivingEntity shooter, Projectile projectile,
                               int bulletCnt, float processedSpeed, float inaccuracy, float pitch, float yaw) {
        projectile.shootFromRotation(shooter, pitch, yaw, 0.0F, processedSpeed, inaccuracy);
    }

    private static final ThreadLocal<LivingEntity> CURRENT_SHOOTER = new ThreadLocal<>();

    public static void setThreadLocalShooter(LivingEntity shooter) {
        CURRENT_SHOOTER.set(shooter);
    }

    private static CommonGunIndex getManagedGunIndex(AbstractGunItem gunItem, ItemStack gun) {
        ResourceLocation gunId = gunItem.getGunId(gun);
        CommonGunIndex index = TimelessAPI.getCommonGunIndex(gunId).orElse(null);
        if (index == null) return null;
        if (index.getGunData().getReloadData().getType() != FeedType.MAGAZINE) return null;
        return fr.infuseting.tacz.magazine.MagazineFamilySystem.getFamilyForGun(gunId) == null ? null : index;
    }

    private static void writeGunAmmoToMagazine(ItemStack magazine, int ammo, ResourceLocation ammoId) {
        if (!(magazine.getItem() instanceof fr.infuseting.tacz.item.MagazineItem magItem)) return;
        if (ammo > 0 && !DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) {
            magItem.setAmmoId(magazine, ammoId);
            magItem.setAmmoCount(magazine, ammo);
        } else {
            magItem.setAmmoCount(magazine, 0);
            magItem.setAmmoId(magazine, DefaultAssets.EMPTY_AMMO_ID);
        }
    }

    private static void clearLegacyCreativeSourceMarker(ItemStack magazine) {
        if (magazine.hasTag()) {
            magazine.getTag().remove("TaCZMagazinesCreativeSource");
        }
    }

    /**
     * 换弹前的检查，完成如下检查：枪内弹药是否已经填满？玩家背包是否有可用弹药？是否为背包直读？
     *
     * @param shooter 准备换弹的实体
     * @param gunItem 枪械物品
     * @return 是否满足换弹条件
     */
    public boolean canReload(LivingEntity shooter, ItemStack gunItem) {
        CURRENT_SHOOTER.set(shooter);
        fr.infuseting.tacz.magazine.GunMagazineInitializer.ensureMagazineForLoadedGun(gunItem);
        CommonGunIndex managedIndex = getManagedGunIndex(this, gunItem);
        if (managedIndex != null) {
            if (shooter instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                    && fr.infuseting.tacz.network.OpenSelectorPacket.SELECTING_PLAYERS.contains(serverPlayer.getUUID())) {
                return false;
            }

            if (useInventoryAmmo(gunItem)) {
                return false;
            }

            if (shooter instanceof Player player && player.getAbilities().instabuild) {
                return true;
            }

            if (managedIndex.getGunData().getReloadData().isInfinite()) {
                return true;
            }

            if (useDummyAmmo(gunItem)) {
                return getDummyAmmoAmount(gunItem) > 0;
            }

            int current = getCurrentAmmoCount(gunItem);
            int maximum = AttachmentDataUtils.getAmmoCountWithAttachment(gunItem, managedIndex.getGunData());
            if (current >= maximum) {
                fr.infuseting.tacz.capability.GunMagazineCapability cap = fr.infuseting.tacz.capability.GunMagazineCapability.of(gunItem);
                if (cap.hasMagazine()) {
                    return true;
                }
            }

            fr.infuseting.tacz.capability.GunMagazineCapability cap = fr.infuseting.tacz.capability.GunMagazineCapability.of(gunItem);
            if (cap.hasMagazine()) {
                ItemStack stored = cap.getStoredMagazine();
                if (stored.getItem() instanceof fr.infuseting.tacz.item.MagazineItem magItem && magItem.getAmmoCount(stored) > 0) {
                    return true;
                }
            }

            return shooter.tacz$getItemHandler(null)
                    .map(handler -> fr.infuseting.tacz.item.MagazineReloadSource.hasUsableMagazine(handler, gunItem))
                    .orElse(false);
        }

        ResourceLocation gunId = this.getGunId(gunItem);
        CommonGunIndex gunIndex = TimelessAPI.getCommonGunIndex(gunId).orElse(null);
        if (gunIndex == null) {
            return false;
        }

        int currentAmmoCount = getCurrentAmmoCount(gunItem);
        int maxAmmoCount = AttachmentDataUtils.getAmmoCountWithAttachment(gunItem, gunIndex.getGunData());
        if (currentAmmoCount >= maxAmmoCount) {
            return false;
        }
        // 背包直读不进行换弹
        if (useInventoryAmmo(gunItem)) {
            return false;
        }
        // 无限备弹不需要消耗实际子弹
        if (gunIndex.getGunData().getReloadData().isInfinite()) {
            return true;
        }
        // 虚拟备弹处理
        if (useDummyAmmo(gunItem)) {
            return getDummyAmmoAmount(gunItem) > 0;
        }
        // 检查背包内的弹药数量
        return shooter.tacz$getItemHandler(null).map(cap -> {
            // 背包检查
            for (int i = 0; i < cap.getSlots(); i++) {
                ItemStack checkAmmoStack = cap.getStackInSlot(i);
                if (checkAmmoStack.getItem() instanceof IAmmo iAmmo && iAmmo.isAmmoOfGun(gunItem, checkAmmoStack)) {
                    return true;
                }
                if (checkAmmoStack.getItem() instanceof IAmmoBox iAmmoBox && iAmmoBox.isAmmoBoxOfGun(gunItem, checkAmmoStack)) {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    /**
     * 将枪内的弹药全部退至背包（如果背包满了会丢到地上）。不会退枪膛内的弹药。
     * 目前，仅更换弹匣配件时调用。
     *
     * @param player  玩家
     * @param gunItem 枪械物品
     */
    @Override
    public void dropAllAmmo(Player player, ItemStack gunItem) {
        fr.infuseting.tacz.magazine.GunMagazineInitializer.ensureMagazineForLoadedGun(gunItem);
        CommonGunIndex managedIndex = getManagedGunIndex(this, gunItem);
        if (managedIndex != null) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                    && fr.infuseting.tacz.network.OpenSelectorPacket.SELECTING_PLAYERS.contains(serverPlayer.getUUID())) {
                return;
            }

            int remaining = getCurrentAmmoCount(gunItem);
            ResourceLocation ammoId = managedIndex.getGunData().getAmmoId();
            boolean hadMagazine = false;

            fr.infuseting.tacz.capability.GunMagazineCapability cap = fr.infuseting.tacz.capability.GunMagazineCapability.of(gunItem);
            if (cap.hasMagazine()) {
                hadMagazine = true;
                ItemStack stored = cap.getStoredMagazine();

                setCurrentAmmoCount(gunItem, 0);
                cap.clearMagazine();

                writeGunAmmoToMagazine(stored, remaining, ammoId);
                clearLegacyCreativeSourceMarker(stored);
                IItemHandler itemHandler = player.tacz$getItemHandler(null).orElse(null);
                ItemStack leftover = ItemHandlerHelper.insertItemStackedFromEnd(itemHandler, stored, false);
                if (!leftover.isEmpty()) player.drop(leftover, false);
            }

            if (!hadMagazine) {
                boolean hasBulletInBarrel = hasBulletInBarrel(gunItem);
                int count = (hasBulletInBarrel ? 1 : 0) + remaining;
                if (count > 0 && ammoId != null && !DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) {
                    ItemStack bullet = AmmoItemBuilder.create().setId(ammoId).setCount(count).build();
                    if (!player.getInventory().add(bullet)) player.drop(bullet, false);
                    setBulletInBarrel(gunItem, false);
                    setCurrentAmmoCount(gunItem, 0);
                }
            }
            return;
        }

        // 背包直读时不调用退弹
        if (useInventoryAmmo(gunItem)) {
            return;
        }
        //TODO 这里操作的对象不应该是 Player 而是 LivingEntity。此外枪膛内的子弹也要退
        int ammoCount = getCurrentAmmoCount(gunItem);
        if (ammoCount <= 0) {
            return;
        }
        ResourceLocation gunId = getGunId(gunItem);
        TimelessAPI.getCommonGunIndex(gunId).ifPresent(index -> {
            // 如果使用的是虚拟备弹，返还至虚拟备弹
            if (useDummyAmmo(gunItem)) {
                setCurrentAmmoCount(gunItem, 0);
                // 燃料罐类型的换弹不返还
                if (index.getGunData().getReloadData().getType().equals(FeedType.FUEL)) {
                    return;
                }
                addDummyAmmoAmount(gunItem, ammoCount);
                return;
            }

            ResourceLocation ammoId = index.getGunData().getAmmoId();
            // 创造模式类型的换弹，只填满子弹总数，不进行任何卸载弹药逻辑
            if (player.isCreative()) {
                int maxAmmCount = AttachmentDataUtils.getAmmoCountWithAttachment(gunItem, index.getGunData());
                setCurrentAmmoCount(gunItem, maxAmmCount);
                return;
            }
            // 燃料罐类型的只清空不返还
            if (index.getGunData().getReloadData().getType().equals(FeedType.FUEL)) {
                setCurrentAmmoCount(gunItem, 0);
                return;
            }
            TimelessAPI.getCommonAmmoIndex(ammoId).ifPresent(ammoIndex -> {
                int stackSize = ammoIndex.getStackSize();
                int tmpAmmoCount = ammoCount;
                int roundCount = tmpAmmoCount / (stackSize + 1);
                for (int i = 0; i <= roundCount; i++) {
                    int count = Math.min(tmpAmmoCount, stackSize);
                    ItemStack ammoItem = AmmoItemBuilder.create().setId(ammoId).setCount(count).build();
                    ItemHandlerHelper.giveItemToPlayer(player, ammoItem);
                    tmpAmmoCount -= stackSize;
                }
                setCurrentAmmoCount(gunItem, 0);
            });
        });
    }

    /**
     * 枪械寻弹和扣除背包弹药逻辑
     *
     * @param itemHandler   目标实体的背包
     * @param gunItem       枪械物品
     * @param needAmmoCount 需要的弹药 (物品) 数量
     * @return 寻找到的弹药 (物品) 数量
     */
    @Deprecated
    public int findAndExtractInventoryAmmos(IItemHandler itemHandler, ItemStack gunItem, int needAmmoCount) {
        return findAndExtractInventoryAmmo(itemHandler, gunItem, needAmmoCount);
    }

    /**
     * 枪械寻弹和扣除背包弹药逻辑
     *
     * @param itemHandler   目标实体的背包
     * @param gunItem       枪械物品
     * @param needAmmoCount 需要的弹药 (物品) 数量
     * @return 寻找到的弹药 (物品) 数量
     */
    public int findAndExtractInventoryAmmo(IItemHandler itemHandler, ItemStack gunItem, int needAmmoCount) {
        fr.infuseting.tacz.magazine.GunMagazineInitializer.ensureMagazineForLoadedGun(gunItem);
        CommonGunIndex managedIndex = getManagedGunIndex(this, gunItem);
        if (managedIndex != null) {
            LivingEntity shooter = CURRENT_SHOOTER.get();
            CURRENT_SHOOTER.remove();

            if (shooter instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                    && fr.infuseting.tacz.network.OpenSelectorPacket.SELECTING_PLAYERS.contains(serverPlayer.getUUID())) {
                return 0;
            }

            boolean isFastReload = gunItem.hasTag() && gunItem.getTag().getBoolean("TaCZMag_FastReload");
            if (gunItem.hasTag()) gunItem.getTag().remove("TaCZMag_FastReload");

            int currentAmmo = getCurrentAmmoCount(gunItem);
            ResourceLocation gunAmmoId = managedIndex.getGunData().getAmmoId();
            boolean ejectFailed = false;

            fr.infuseting.tacz.capability.GunMagazineCapability cap = fr.infuseting.tacz.capability.GunMagazineCapability.of(gunItem);
            if (cap.hasMagazine()) {
                ItemStack stored = cap.getStoredMagazine();

                writeGunAmmoToMagazine(stored, currentAmmo, gunAmmoId);
                clearLegacyCreativeSourceMarker(stored);
                cap.setStoredMagazine(stored);
                stored = cap.getStoredMagazine();

                if (isFastReload && shooter instanceof Player player) {
                    ItemHandlerHelper.dropAtFeet(player, stored, 100);
                    cap.clearMagazine();
                    setCurrentAmmoCount(gunItem, 0);
                } else {
                    ItemStack leftover = ItemHandlerHelper.insertItemStackedFromEnd(itemHandler, stored, false);
                    if (!leftover.isEmpty() && shooter instanceof Player player) {
                        ItemHandlerHelper.dropAtFeet(player, leftover, 40);
                    }
                    cap.clearMagazine();
                    setCurrentAmmoCount(gunItem, 0);
                }
            }

            if (ejectFailed) {
                return 0;
            }

            int selectedSlot = -1;
            if (gunItem.hasTag() && gunItem.getTag().contains("TaCZMag_SelectedSlot")) {
                selectedSlot = gunItem.getTag().getInt("TaCZMag_SelectedSlot");
                gunItem.getTag().remove("TaCZMag_SelectedSlot");
            }

            boolean creativeReload = gunItem.hasTag()
                    && gunItem.getTag().getBoolean("TaCZMag_CreativeReload");
            if (gunItem.hasTag()) gunItem.getTag().remove("TaCZMag_CreativeReload");

            ItemStack magazine = creativeReload
                    ? fr.infuseting.tacz.item.MagazineReloadSource.createCreativeReloadMagazine(itemHandler, gunItem, selectedSlot)
                    : fr.infuseting.tacz.item.MagazineReloadSource.extract(itemHandler, gunItem, selectedSlot);
            if (!(magazine.getItem() instanceof fr.infuseting.tacz.item.MagazineItem magItem)) {
                return 0;
            }

            int ammo = magItem.getAmmoCount(magazine);
            cap.setStoredMagazine(magazine);
            fr.infuseting.tacz.TaCZMagazines.LOGGER.debug("Loaded magazine with {} rounds", ammo);
            return ammo;
        }
        int cnt = needAmmoCount;
        // 背包检查
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack checkAmmoStack = itemHandler.getStackInSlot(i);
            if (checkAmmoStack.getItem() instanceof IAmmo iAmmo && iAmmo.isAmmoOfGun(gunItem, checkAmmoStack)) {
                ItemStack extractItem = itemHandler.extractItem(i, cnt, false);
                cnt = cnt - extractItem.getCount();
                if (cnt <= 0) {
                    break;
                }
            }
            if (checkAmmoStack.getItem() instanceof IAmmoBox iAmmoBox && iAmmoBox.isAmmoBoxOfGun(gunItem, checkAmmoStack)) {
                int boxAmmoCount = iAmmoBox.getAmmoCount(checkAmmoStack);
                int extractCount = Math.min(boxAmmoCount, cnt);
                int remainCount = boxAmmoCount - extractCount;
                iAmmoBox.setAmmoCount(checkAmmoStack, remainCount);
                if (remainCount <= 0) {
                    iAmmoBox.setAmmoId(checkAmmoStack, DefaultAssets.EMPTY_AMMO_ID);
                }
                cnt = cnt - extractCount;
                if (cnt <= 0) {
                    break;
                }
            }
        }
        return needAmmoCount - cnt;
    }

    /**
     * 扣除虚拟弹药逻辑，该方法具有通用的实现，放在此处
     *
     * @param gunItem       枪械物品
     * @param needAmmoCount 需要的弹药(物品)数量
     * @return 找到的弹药(物品)数量
     */
    public int findAndExtractDummyAmmo(ItemStack gunItem, int needAmmoCount) {
        int dummyAmmoCount = getDummyAmmoAmount(gunItem);
        int extractCount = Math.min(dummyAmmoCount, needAmmoCount);
        addDummyAmmoAmount(gunItem, -extractCount);
        return extractCount;
    }

    /**
     * 检查枪械是否允许安装指定的物品作为配件
     */
    @Override
    public boolean allowAttachment(ItemStack gun, ItemStack attachmentItem) {
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun != null && iAttachment != null) {
            ResourceLocation gunId = iGun.getGunId(gun);
            ResourceLocation attachmentId = iAttachment.getAttachmentId(attachmentItem);
            return AllowAttachmentTagMatcher.match(gunId, attachmentId);
        }
        return false;
    }

    /**
     * 检查枪械是否允许安装某种类型的配件
     */
    @Override
    public boolean allowAttachmentType(ItemStack gun, AttachmentType type) {
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun != null) {
            return TimelessAPI.getCommonGunIndex(iGun.getGunId(gun)).map(gunIndex -> {
                List<AttachmentType> allowAttachments = gunIndex.getGunData().getAllowAttachments();
                if (allowAttachments == null) {
                    return false;
                }
                return allowAttachments.contains(type);
            }).orElse(false);
        } else {
            return false;
        }
    }

    /**
     * 获取枪械的显示名称
     */
    @Override
    @Nonnull
    @Environment(EnvType.CLIENT)
    public Component getName(@Nonnull ItemStack stack) {
        ResourceLocation gunId = this.getGunId(stack);
        Optional<ClientGunIndex> gunIndex = TimelessAPI.getClientGunIndex(gunId);
        if (gunIndex.isPresent()) {
            return Component.translatable(gunIndex.get().getName());
        }
        return super.getName(stack);
    }

    /**
     * 获取某一类 TabType 的所有枪械物品的实例。用于填充创造物品栏和枪械制造台。
     */
    public static NonNullList<ItemStack> fillItemCategory(GunTabType type) {
        NonNullList<ItemStack> stacks = NonNullList.create();
        TimelessAPI.getAllCommonGunIndex().stream().sorted(idNameSort()).forEach(entry -> {
            CommonGunIndex index = entry.getValue();
            GunData gunData = index.getGunData();
            String key = type.name().toLowerCase(Locale.US);
            String indexType = index.getType();
            if (key.equals(indexType)) {
                FireMode initialMode = gunData.getFireModeSet().stream().filter(m -> m != FireMode.SAFE).findFirst().orElse(FireMode.SAFE);
                ItemStack itemStack = GunItemBuilder.create()
                        .setId(entry.getKey())
                        .setFireMode(initialMode)
                        .setAmmoCount(gunData.getAmmoAmount())
                        .setHeatData(gunData.hasHeatData())
                        .setAmmoInBarrel(true)
                        .build();
                stacks.add(itemStack);
            }
        });
        return stacks;
    }

    /**
     * 阻止玩家手臂挥动
     */
    @Override
    public boolean tacz$onEntitySwing(ItemStack stack, LivingEntity entity) {
        return true;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public BuiltinItemRendererRegistry.DynamicItemRenderer getCustomRenderer() {
        return GunItemRendererWrapper.INSTANCE.get();
    }

    /**
     * 获取在 Tooltip 中渲染的图片
     */
    @Override
    @Nonnull
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        if (stack.getItem() instanceof IGun iGun) {
            Optional<CommonGunIndex> optional = TimelessAPI.getCommonGunIndex(this.getGunId(stack));
            if (optional.isPresent()) {
                CommonGunIndex gunIndex = optional.get();
                ResourceLocation ammoId = gunIndex.getGunData().getAmmoId();
                return Optional.of(new GunTooltip(stack, iGun, ammoId, gunIndex));
            }
        }
        return Optional.empty();
    }

    /**
     * 获取是否使用弹药直读
     *
     * @param gun 枪械
     * @return 是否使用弹药直读
     */
    @Override
    public boolean useInventoryAmmo(ItemStack gun) {
        if (gun.getItem() instanceof IGun) {
            Optional<CommonGunIndex> gunIndexOptional = TimelessAPI.getCommonGunIndex(this.getGunId(gun));
            if (gunIndexOptional.isEmpty()) {
                return false;
            }
            CommonGunIndex gunIndex = gunIndexOptional.get();
            // 是否为弹药直读
            return gunIndex.getGunData().getReloadData().getType().equals(FeedType.INVENTORY);
        }
        return false;
    }

    /**
     * 获取是否有供给弹药直读的弹药
     *
     * @param gun 枪械
     * @return 是否有供给弹药直读的弹药
     */
    @Override
    public boolean hasInventoryAmmo(LivingEntity shooter, ItemStack gun, boolean needCheckAmmo) {
        // 如果不是背包直读，则直接返回 false
        if (!useInventoryAmmo(gun)) {
            return false;
        }
        // 如果不需要检查子弹，则直接返回 true
        if (!needCheckAmmo) {
            return true;
        }
        // 虚拟备弹处理
        if (useDummyAmmo(gun)) {
            return getDummyAmmoAmount(gun) > 0;
        }
        // 检查背包内的弹药数量
        return shooter.tacz$getItemHandler(null).map(cap -> {
            // 背包检查
            for (int i = 0; i < cap.getSlots(); i++) {
                ItemStack checkAmmoStack = cap.getStackInSlot(i);
                if (checkAmmoStack.getItem() instanceof IAmmo iAmmo && iAmmo.isAmmoOfGun(gun, checkAmmoStack)) {
                    return true;
                }
                if (checkAmmoStack.getItem() instanceof IAmmoBox iAmmoBox && iAmmoBox.isAmmoBoxOfGun(gun, checkAmmoStack)) {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    /**
     * 获取 RPM
     *
     * @param gun 枪械
     * @return RPM 数值
     */
    public int getRPM(ItemStack gun) {
        if (gun.getItem() instanceof IGun iGun) {
            return TimelessAPI.getCommonGunIndex(this.getGunId(gun))
                    .map(CommonGunIndex::getGunData)
                    .map(gunData -> {
                        FireMode fireMode = getFireMode(gun);
                        int rpm = gunData.getRoundsPerMinute(fireMode);
                        if (iGun.hasHeatData(gun)) {
                            rpm *= (int) iGun.lerpRPM(gun);
                        }
                        return rpm;
                    }).orElse(300);
        }
        return 300;
    }

    /**
     * 获取是否可以趴下射击
     *
     * @param gun 枪械
     * @return 是否可以趴下射击
     */
    public boolean isCanCrawl(ItemStack gun) {
        if (gun.getItem() instanceof IGun) {
            return TimelessAPI.getCommonGunIndex(this.getGunId(gun))
                    .map(CommonGunIndex::getGunData)
                    .map(GunData::isCanCrawl)
                    .orElse(false);
        }
        return false;
    }

    @Override
    public boolean isSame(ItemStack i, ItemStack j) {
        IGun iGun1 = IGun.getIGunOrNull(i);
        IGun iGun2 = IGun.getIGunOrNull(j);
        if (iGun1 != null && iGun2 != null) {
            return iGun1.getGunId(i).equals(iGun2.getGunId(j)) && iGun1.getGunDisplayId(i).equals(iGun2.getGunDisplayId(j));
        }
        if (i.isEmpty() || j.isEmpty()) {
            return i.isEmpty() && j.isEmpty();
        }
        return ItemStack.matches(i, j);
    }
}
