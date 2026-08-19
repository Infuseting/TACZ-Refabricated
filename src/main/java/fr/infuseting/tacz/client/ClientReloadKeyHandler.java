package fr.infuseting.tacz.client;

import fr.infuseting.tacz.item.MagazineItem;
import fr.infuseting.tacz.magazine.MagazineFamilySystem;
import fr.infuseting.tacz.network.PacketHandler;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.ReloadKey;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import com.tacz.guns.util.InputExtraCheck;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public class ClientReloadKeyHandler {

    private static final long HOLD_THRESHOLD_MS = 400;

    private static long keyDownAt = 0;
    private static boolean keyDown = false;
    private static boolean inSelectorMode = false;
    private static boolean cancelled = false;
    private static boolean serverBlocked = false;
    private static boolean fastReloadActive = false;

    // Frozen ext level — set once at reload confirm, held for entire animation,
    // cleared when reload state ends. -1 = not set.
    private static int frozenExtLevel = -1;

    public static int getFrozenExtLevel() { return frozenExtLevel; }
    public static void clearFrozenExtLevel() { frozenExtLevel = -1; }

    public static boolean isFastReloadActive() { return fastReloadActive; }
    public static void setFastReloadActive(boolean active) { fastReloadActive = active; }

    public static void resetFastReload(net.minecraft.client.player.LocalPlayer player) {
        fastReloadActive = false;
        if (player != null && player.getMainHandItem().hasTag()) {
            player.getMainHandItem().getTag().remove("TaCZMag_FastReloadActive");
            player.getMainHandItem().getTag().remove("TaCZMag_FastReload");
        }
    }

    public static boolean isSelectorOpen() {
        return inSelectorMode;
    }

    public static boolean onReloadKeyPressed() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator()) return false;
        ItemStack gun = player.getMainHandItem();
        if (!(gun.getItem() instanceof IGun iGun)) return false;
        if (iGun.useInventoryAmmo(gun)) return false;

        ResourceLocation gunId = iGun.getGunId(gun);
        CommonGunIndex gunIndex = TimelessAPI.getCommonGunIndex(gunId).orElse(null);
        if (gunIndex == null) return false;
        if (!gunIndex.getGunData().getReloadData().getType().equals(FeedType.MAGAZINE)) return false;
        if (MagazineFamilySystem.getFamilyForGun(gunId) == null) return false;

        keyDown = true;
        keyDownAt = System.currentTimeMillis();
        inSelectorMode = false;
        cancelled = false;
        serverBlocked = true;

        PacketHandler.sendOpenSelector(true);
        return true;
    }

    public static void onReloadKeyReleased() {
        if (!keyDown) return;
        keyDown = false;

        if (cancelled) {
            cancelled = false;
            if (serverBlocked) {
                serverBlocked = false;
                PacketHandler.sendOpenSelector(false);
            }
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            if (serverBlocked) {
                serverBlocked = false;
                PacketHandler.sendOpenSelector(false);
            }
            return;
        }

        if (inSelectorMode) {
            inSelectorMode = false;
            int selectedSlot = MagazineSelectorOverlay.getSelectedSlot();
            MagazineSelectorOverlay.close();
            serverBlocked = false;
            if (selectedSlot >= 0) {
                ItemStack mag = player.getInventory().getItem(selectedSlot);
                frozenExtLevel = resolveExtLevel(mag);
                PacketHandler.sendSelectMagazine(selectedSlot);
                IClientPlayerGunOperator.fromLocalPlayer(player).reload();
            } else {
                PacketHandler.sendOpenSelector(false);
            }
        } else {
            // Tap reload — freeze from first compatible mag in inventory
            ItemStack gun = player.getMainHandItem();
            frozenExtLevel = resolveFirstCompatibleExtLevel(gun, player.getInventory());
            serverBlocked = false;
            PacketHandler.sendSelectMagazine(-1, false);
            IClientPlayerGunOperator.fromLocalPlayer(player).reload();
        }
    }

    public static void onRightClickCancel() {
        if (!inSelectorMode) return;
        cancelled = true;
        inSelectorMode = false;
        MagazineSelectorOverlay.close();
    }

    public static void onClientTick(Minecraft client) {
        if (inSelectorMode) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.keyUse.isDown()) {
                onRightClickCancel();
            }
            if (!InputExtraCheck.isInGame()) {
                inSelectorMode = false;
                MagazineSelectorOverlay.close();
                keyDown = false;
                if (serverBlocked) {
                    serverBlocked = false;
                    PacketHandler.sendOpenSelector(false);
                }
            }
            return;
        }

        if (!keyDown) return;
        if (!InputExtraCheck.isInGame()) {
            keyDown = false;
            return;
        }

        if (!ReloadKey.RELOAD_KEY.isDown()) {
            onReloadKeyReleased();
            return;
        }

        if (cancelled) return;

        long heldMs = System.currentTimeMillis() - keyDownAt;
        if (heldMs >= HOLD_THRESHOLD_MS) {
            inSelectorMode = true;
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                MagazineSelectorOverlay.open(player);
            }
        }
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private static int resolveExtLevel(ItemStack mag) {
        if (mag.isEmpty() || !(mag.getItem() instanceof MagazineItem)) return 0;
        String fid = MagazineItem.getMagazineFamilyId(mag);
        if (fid != null && MagazineFamilySystem.isExtendedFamily(fid))
            return MagazineFamilySystem.getExtLevelForFamily(fid);
        return 0;
    }

    private static int resolveFirstCompatibleExtLevel(ItemStack gun, Inventory inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!(s.getItem() instanceof MagazineItem magItem)) continue;
            if (!magItem.isAmmoBoxOfGun(gun, s)) continue;
            return resolveExtLevel(s);
        }
        return 0;
    }
}

