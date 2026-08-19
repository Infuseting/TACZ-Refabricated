package fr.infuseting.tacz.network;

import fr.infuseting.tacz.ammo.AmmoStack;
import fr.infuseting.tacz.item.MagazineAmmoSource;
import fr.infuseting.tacz.item.MagazineItem;
import fr.infuseting.tacz.item.SoundRegistrar;
import fr.infuseting.tacz.magazine.MagazineFamilySystem;
import com.tacz.guns.api.DefaultAssets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

// Loads exactly one bullet from the player's inventory
public class LoadOneFromHandPacket {

    public LoadOneFromHandPacket() {}

    public void encode(FriendlyByteBuf buf) {}

    public static LoadOneFromHandPacket decode(FriendlyByteBuf buf) {
        return new LoadOneFromHandPacket();
    }

    public void handle(ServerPlayer player) {
        if (player == null || !RateLimiter.canProcess(player)) return;

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !(held.getItem() instanceof MagazineItem magItem)) return;

        String familyId = MagazineItem.getMagazineFamilyId(held);
        if (familyId == null) return;

        ResourceLocation familyAmmo = MagazineFamilySystem.getAmmoTypeForFamily(familyId);
        if (familyAmmo == null) return;

        int maxCap  = MagazineItem.getMaxCapacity(held);
        int current = magItem.getAmmoCount(held);
        if (current >= maxCap) return;

        ResourceLocation takenAmmo = MagazineAmmoSource.takeOneFromInventory(player, familyAmmo);
        if (takenAmmo == null) return;

        ItemStack extras = ItemStack.EMPTY;
        if (held.getCount() > 1) {
            extras = held.split(held.getCount() - 1);
        }

        AmmoStack ammoStack = AmmoStack.fromItemStack(held);
        ammoStack.push(takenAmmo, 1);
        ammoStack.saveToItemStack(held);

        if (!extras.isEmpty()) {
            if (!player.getInventory().add(extras)) player.drop(extras, false);
        }

        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        SoundRegistrar.playMagazineLoad(player);
    }
}

