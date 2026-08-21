package fr.infuseting.tacz.network;

import fr.infuseting.tacz.durability.UnjamActionHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class UnjamGunPacket {

    public UnjamGunPacket() {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public static UnjamGunPacket decode(FriendlyByteBuf buf) {
        return new UnjamGunPacket();
    }

    public void handle(ServerPlayer player) {
        UnjamActionHandler.handleUnjam(player);
    }
}
