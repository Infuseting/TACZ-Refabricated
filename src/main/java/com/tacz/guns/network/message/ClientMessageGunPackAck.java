package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.security.GunPackSecurityManager;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class ClientMessageGunPackAck implements FabricPacket {
    public static final PacketType<ClientMessageGunPackAck> TYPE = PacketType.create(
            new ResourceLocation(GunMod.MOD_ID, "c2s_gunpack_ack"),
            ClientMessageGunPackAck::new
    );

    private final String sha256;

    public ClientMessageGunPackAck(String sha256) {
        this.sha256 = sha256;
    }

    public ClientMessageGunPackAck(FriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(sha256);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    public void handle(ServerPlayer player, PacketSender responseSender) {
        player.server.execute(() -> {
            GunPackSecurityManager.getInstance().onPlayerAck(player, sha256);
        });
    }

    public String getSha256() {
        return sha256;
    }
}
