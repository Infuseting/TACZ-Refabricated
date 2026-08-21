package fr.infuseting.tacz.firemode;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.event.ServerMessageGunFireSelect;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class SafeFireModeHandler {

    public static final String PREV_FIRE_MODE_TAG = "TaCZ_PrevFireMode";

    private SafeFireModeHandler() {
    }

    public static FireMode toggleSafe(ItemStack gunItem) {
        if (gunItem == null || !(gunItem.getItem() instanceof IGun iGun)) {
            return null;
        }
        CommonGunIndex gunIndex = TimelessAPI.getCommonGunIndex(iGun.getGunId(gunItem)).orElse(null);
        if (gunIndex == null) {
            return null;
        }
        List<FireMode> fireModeSet = gunIndex.getGunData().getFireModeSet();
        FireMode current = iGun.getFireMode(gunItem);

        FireMode targetMode;
        if (current != FireMode.SAFE) {
            // Save current fire mode so we can toggle back to it
            gunItem.getOrCreateTag().putString(PREV_FIRE_MODE_TAG, current.name());
            targetMode = FireMode.SAFE;
        } else {
            // Restore previous mode if valid
            FireMode restored = null;
            CompoundTag tag = gunItem.getTag();
            if (tag != null && tag.contains(PREV_FIRE_MODE_TAG)) {
                try {
                    restored = FireMode.valueOf(tag.getString(PREV_FIRE_MODE_TAG));
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (restored != null && restored != FireMode.SAFE && fireModeSet.contains(restored)) {
                targetMode = restored;
            } else {
                targetMode = fireModeSet.stream()
                        .filter(m -> m != FireMode.SAFE)
                        .findFirst()
                        .orElse(FireMode.SEMI);
            }
        }

        iGun.setFireMode(gunItem, targetMode);
        return targetMode;
    }

    public static void handleServerToggleSafe(ServerPlayer player) {
        if (player == null || player.isSpectator()) {
            return;
        }
        ItemStack mainHandItem = player.getMainHandItem();
        if (!(mainHandItem.getItem() instanceof IGun)) {
            return;
        }
        toggleSafe(mainHandItem);
        AttachmentPropertyManager.postChangeEvent(player, mainHandItem);
        NetworkHandler.sendToTrackingEntity(new ServerMessageGunFireSelect(player.getId(), mainHandItem), player);
    }
}
