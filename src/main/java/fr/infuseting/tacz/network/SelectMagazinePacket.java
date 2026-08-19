package fr.infuseting.tacz.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class SelectMagazinePacket {

    private final int slot;
    private final boolean fastReload;

    public SelectMagazinePacket(int slot) {
        this(slot, false);
    }

    public SelectMagazinePacket(int slot, boolean fastReload) {
        this.slot = slot;
        this.fastReload = fastReload;
    }

    public int getSlot() {
        return slot;
    }

    public boolean isFastReload() {
        return fastReload;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(slot);
        buf.writeBoolean(fastReload);
    }

    public static SelectMagazinePacket decode(FriendlyByteBuf buf) {
        int slot = buf.readVarInt();
        boolean fastReload = buf.isReadable() && buf.readBoolean();
        return new SelectMagazinePacket(slot, fastReload);
    }

    public void handle(ServerPlayer player) {
        if (player == null) return;

        // Always unblock atomically regardless of slot value.
        OpenSelectorPacket.SELECTING_PLAYERS.remove(player.getUUID());

        // Only write the slot tag when a real slot was selected (hold + pick).
        // slot = -1 means tap reload — no slot tag needed.
        ItemStack gun = player.getMainHandItem();
        if (!gun.isEmpty()) {
            if (player.getAbilities().instabuild) {
                gun.getOrCreateTag().putBoolean("TaCZMag_CreativeReload", true);
            } else {
                gun.getOrCreateTag().remove("TaCZMag_CreativeReload");
            }
            if (slot >= 0) {
                gun.getOrCreateTag().putInt("TaCZMag_SelectedSlot", slot);
            } else {
                gun.getOrCreateTag().remove("TaCZMag_SelectedSlot");
            }
            if (fastReload) {
                gun.getOrCreateTag().putBoolean("TaCZMag_FastReload", true);
                gun.getOrCreateTag().putBoolean("TaCZMag_FastReloadActive", true);
            } else {
                gun.getOrCreateTag().remove("TaCZMag_FastReload");
                gun.getOrCreateTag().remove("TaCZMag_FastReloadActive");
            }
        }
    }
}

