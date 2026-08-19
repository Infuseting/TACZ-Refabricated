package fr.infuseting.tacz.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class OpenSelectorPacket {

    // Server-side set of players currently in selector mode.
    public static final Set<UUID> SELECTING_PLAYERS = Collections.synchronizedSet(new HashSet<>());

    private final boolean open;

    public OpenSelectorPacket(boolean open) {
        this.open = open;
    }

    public boolean isOpen() {
        return open;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(open);
    }

    public static OpenSelectorPacket decode(FriendlyByteBuf buf) {
        return new OpenSelectorPacket(buf.readBoolean());
    }

    public void handle(ServerPlayer player) {
        if (player == null) return;
        if (open) {
            SELECTING_PLAYERS.add(player.getUUID());
        } else {
            SELECTING_PLAYERS.remove(player.getUUID());
        }
    }
}
