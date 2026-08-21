package fr.infuseting.tacz.network;

import fr.infuseting.tacz.firemode.SafeFireModeHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class ToggleSafePacket {

    public ToggleSafePacket() {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public static ToggleSafePacket decode(FriendlyByteBuf buf) {
        return new ToggleSafePacket();
    }

    public void handle(ServerPlayer player) {
        SafeFireModeHandler.handleServerToggleSafe(player);
    }
}
