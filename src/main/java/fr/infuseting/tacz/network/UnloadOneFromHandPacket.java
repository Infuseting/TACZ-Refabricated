package fr.infuseting.tacz.network;

import fr.infuseting.tacz.ammo.AmmoStack;
import fr.infuseting.tacz.item.MagazineItem;
import fr.infuseting.tacz.item.SoundRegistrar;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

// Removes exactly one bullet from the magazine held in the player's main hand.
public class UnloadOneFromHandPacket {

    public UnloadOneFromHandPacket() {}

    public void encode(FriendlyByteBuf buf) {}

    public static UnloadOneFromHandPacket decode(FriendlyByteBuf buf) {
        return new UnloadOneFromHandPacket();
    }

    public void handle(ServerPlayer player) {
        if (player == null || !RateLimiter.canProcess(player)) return;

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !(held.getItem() instanceof MagazineItem magItem)) return;

        AmmoStack ammoStack = AmmoStack.fromItemStack(held);
        if (ammoStack.isEmpty()) return;

        ItemStack extras = ItemStack.EMPTY;
        if (held.getCount() > 1) {
            extras = held.split(held.getCount() - 1);
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
        }

        ResourceLocation poppedAmmoId = ammoStack.pop();
        ammoStack.saveToItemStack(held);

        if (!extras.isEmpty()) {
            if (!player.getInventory().add(extras)) player.drop(extras, false);
        }

        ItemStack bullet = AmmoItemBuilder.create().setId(poppedAmmoId).setCount(1).build();
        if (!player.getInventory().add(bullet)) player.drop(bullet, false);

        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        SoundRegistrar.playMagazineUnload(player);
    }
}

