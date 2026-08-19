package fr.infuseting.tacz.item;

import fr.infuseting.tacz.ammo.AmmoStack;
import fr.infuseting.tacz.config.MechanicsConfig;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// Resolves and consumes ammunition used to load magazines.
public final class MagazineAmmoSource {
    private MagazineAmmoSource() {}

    public static ResourceLocation compatibleAmmoId(ItemStack source, ResourceLocation requiredAmmo) {
        if (source.isEmpty()) return null;

        if (source.getItem() instanceof IAmmo ammo) {
            ResourceLocation ammoId = ammo.getAmmoId(source);
            return AmmoStack.isAmmoCompatible(requiredAmmo, ammoId) ? ammoId : null;
        }

        if (MechanicsConfig.LOAD_MAGAZINES_FROM_AMMO_BOXES.get()
                && AmmoBoxMagazineStorage.isExternalAmmoBox(source)
                && source.getItem() instanceof IAmmoBox box) {
            if (box.isAllTypeCreative(source)) return requiredAmmo;
            ResourceLocation ammoId = box.getAmmoId(source);
            if (!AmmoStack.isAmmoCompatible(requiredAmmo, ammoId)) return null;
            if (box.getAmmoCount(source) <= 0 && !box.isCreative(source)) return null;
            return ammoId;
        }

        return null;
    }

    public static int available(ItemStack source) {
        if (source.getItem() instanceof IAmmo) return source.getCount();
        if (AmmoBoxMagazineStorage.isExternalAmmoBox(source)
                && source.getItem() instanceof IAmmoBox box) {
            if (box.isCreative(source) || box.isAllTypeCreative(source)) return Integer.MAX_VALUE;
            return Math.max(0, box.getAmmoCount(source));
        }
        return 0;
    }

    public static void consume(ItemStack source, int amount) {
        if (amount <= 0) return;
        if (source.getItem() instanceof IAmmo) {
            source.shrink(amount);
            return;
        }
        if (AmmoBoxMagazineStorage.isExternalAmmoBox(source)
                && source.getItem() instanceof IAmmoBox box
                && !box.isCreative(source)
                && !box.isAllTypeCreative(source)) {
            int remaining = Math.max(0, box.getAmmoCount(source) - amount);
            box.setAmmoCount(source, remaining);
            if (remaining == 0) box.setAmmoId(source, DefaultAssets.EMPTY_AMMO_ID);
        }
    }

    public static ResourceLocation takeOneFromInventory(Player player, ResourceLocation requiredAmmo) {
        if (player.getAbilities().instabuild) return requiredAmmo;

        boolean looseFirst = MechanicsConfig.PREFER_PLAYER_INVENTORY.get();
        ResourceLocation id = null;
        if (looseFirst) {
            id = takeLoose(player, requiredAmmo);
            if (id != null) return id;
        }
        id = takeFromBox(player, requiredAmmo);
        if (id != null) return id;
        if (!looseFirst) {
            return takeLoose(player, requiredAmmo);
        }
        return null;
    }

    private static ResourceLocation takeLoose(Player player, ResourceLocation requiredAmmo) {
        for (ItemStack stack : player.getInventory().items) {
            if (!(stack.getItem() instanceof IAmmo)) continue;
            ResourceLocation ammoId = compatibleAmmoId(stack, requiredAmmo);
            if (ammoId == null) continue;
            stack.shrink(1);
            player.getInventory().setChanged();
            return ammoId;
        }
        return null;
    }

    private static ResourceLocation takeFromBox(Player player, ResourceLocation requiredAmmo) {
        if (!MechanicsConfig.LOAD_MAGAZINES_FROM_AMMO_BOXES.get()) return null;
        for (ItemStack stack : player.getInventory().items) {
            if (!AmmoBoxMagazineStorage.isExternalAmmoBox(stack)) continue;
            ResourceLocation ammoId = compatibleAmmoId(stack, requiredAmmo);
            if (ammoId == null || available(stack) <= 0) continue;
            consume(stack, 1);
            player.getInventory().setChanged();
            return ammoId;
        }
        return null;
    }
}
