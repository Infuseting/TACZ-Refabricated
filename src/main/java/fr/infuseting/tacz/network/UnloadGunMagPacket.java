package fr.infuseting.tacz.network;

import fr.infuseting.tacz.capability.GunMagazineCapability;
import fr.infuseting.tacz.item.MagazineItem;
import fr.infuseting.tacz.item.SoundRegistrar;
import fr.infuseting.tacz.magazine.MagazineFamilySystem;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

// Ejects the magazine currently stored in the held gun back to the player's inventory, writing the remaining gun ammo count into it first.
public class UnloadGunMagPacket {

    public UnloadGunMagPacket() {}

    public void encode(FriendlyByteBuf buf) {}

    public static UnloadGunMagPacket decode(FriendlyByteBuf buf) {
        return new UnloadGunMagPacket();
    }

    public void handle(ServerPlayer player) {
        if (player == null) return;

        ItemStack gun = player.getMainHandItem();
        if (!(gun.getItem() instanceof IGun iGun)) return;
        if (!(gun.getItem() instanceof AbstractGunItem abstractGun)) return;

        ResourceLocation gunId = iGun.getGunId(gun);
        if (MagazineFamilySystem.getFamilyForGun(gunId) == null) return;

        GunMagazineCapability magCap = GunMagazineCapability.of(gun);
        if (!magCap.hasMagazine()) {
            boolean hasBulletInBarrel = iGun.hasBulletInBarrel(gun);
            ResourceLocation barrelAmmo = iGun.getBarrelAmmoId(gun);
            if (hasBulletInBarrel && barrelAmmo != null && !DefaultAssets.EMPTY_AMMO_ID.equals(barrelAmmo)) {
                ItemStack bullet = AmmoItemBuilder.create().setId(barrelAmmo).setCount(1).build();
                if (!player.getInventory().add(bullet)) {
                    player.drop(bullet, false);
                }
            }
            iGun.setBulletInBarrel(gun, false);

            fr.infuseting.tacz.ammo.AmmoStack stack = fr.infuseting.tacz.ammo.AmmoStack.fromItemStack(gun);
            if (!stack.isEmpty()) {
                fr.infuseting.tacz.ammo.AmmoStack popped = stack.pop(stack.getTotalCount());
                for (fr.infuseting.tacz.ammo.AmmoStack.AmmoEntry entry : popped.getEntries()) {
                    ItemStack bullet = AmmoItemBuilder.create().setId(entry.getId()).setCount(entry.getCount()).build();
                    if (!player.getInventory().add(bullet)) {
                        player.drop(bullet, false);
                    }
                }
                stack.saveToItemStack(gun);
            } else {
                int currentAmmo = abstractGun.getCurrentAmmoCount(gun);
                if (currentAmmo > 0) {
                    TimelessAPI.getCommonGunIndex(gunId).ifPresent(idx -> {
                        ResourceLocation ammoId = idx.getGunData().getAmmoId();
                        if (ammoId != null && !DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) {
                            ItemStack bullet = AmmoItemBuilder.create().setId(ammoId).setCount(currentAmmo).build();
                            if (!player.getInventory().add(bullet)) {
                                player.drop(bullet, false);
                            }
                        }
                    });
                }
            }

            abstractGun.setCurrentAmmoCount(gun, 0);
            SoundRegistrar.playMagazineUnload(player);
            return;
        }

        ItemStack storedMag = magCap.getStoredMagazine();

        // Write the remaining in-gun ammo back into the magazine before ejecting
        if (storedMag.getItem() instanceof MagazineItem magItem) {
            fr.infuseting.tacz.ammo.AmmoStack stack = fr.infuseting.tacz.ammo.AmmoStack.fromItemStack(storedMag);
            if (stack.isEmpty()) {
                int remaining = abstractGun.getCurrentAmmoCount(gun);
                final ItemStack magToUpdate = storedMag;
                if (remaining > 0) {
                    TimelessAPI.getCommonGunIndex(gunId).ifPresent(idx -> {
                        ResourceLocation ammoId = idx.getGunData().getAmmoId();
                        if (!DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) {
                            stack.push(ammoId, remaining);
                            stack.saveToItemStack(magToUpdate);
                        }
                    });
                }
            }
            magCap.setStoredMagazine(storedMag);
            storedMag = magCap.getStoredMagazine();
        }

        abstractGun.setCurrentAmmoCount(gun, 0);
        magCap.clearMagazine();

        cn.sh1rocu.tacz.util.itemhandler.IItemHandler itemHandler = player.tacz$getItemHandler(net.minecraft.core.Direction.UP).orElse(null);
        ItemStack leftover = cn.sh1rocu.tacz.util.itemhandler.ItemHandlerHelper.insertItemStackedFromEnd(itemHandler, storedMag, false);
        if (!leftover.isEmpty()) {
            cn.sh1rocu.tacz.util.itemhandler.ItemHandlerHelper.dropAtFeet(player, leftover, 40);
        }
    }
}

