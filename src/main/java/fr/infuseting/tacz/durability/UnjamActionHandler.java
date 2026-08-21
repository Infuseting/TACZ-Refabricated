package fr.infuseting.tacz.durability;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import fr.infuseting.tacz.config.MechanicsConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class UnjamActionHandler {

    private UnjamActionHandler() {
    }

    public static void handleUnjam(ServerPlayer player) {
        if (player == null || player.isSpectator()) {
            return;
        }
        ItemStack gun = player.getMainHandItem();
        if (!(gun.getItem() instanceof IGun iGun)) {
            return;
        }

        if (!iGun.isJammed(gun)) {
            return;
        }

        // If a bullet is in the barrel, eject it
        if (iGun.hasBulletInBarrel(gun)) {
            if (MechanicsConfig.DROP_JAMMED_BULLET.get()) {
                ResourceLocation ammoId = iGun.getBarrelAmmoId(gun);
                if (ammoId == null || DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) {
                    ammoId = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
                            .map(idx -> idx.getGunData().getAmmoId())
                            .orElse(DefaultAssets.EMPTY_AMMO_ID);
                }
                if (ammoId != null && !DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) {
                    ItemStack droppedBullet = AmmoItemBuilder.create().setId(ammoId).setCount(1).build();
                    player.drop(droppedBullet, false);
                }
            }
            iGun.setBulletInBarrel(gun, false);
        }

        iGun.setJammed(gun, false);
        player.displayClientMessage(Component.translatable("message.taczmagazines.gun_unjammed"), true);
    }
}
