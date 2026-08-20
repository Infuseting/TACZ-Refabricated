package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.security.GunPackSecurityManager;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class ClientMessageGunPackHandshake implements FabricPacket {
    public static final PacketType<ClientMessageGunPackHandshake> TYPE = PacketType.create(
            new ResourceLocation(GunMod.MOD_ID, "c2s_gunpack_handshake"),
            ClientMessageGunPackHandshake::new
    );

    private final String sha256;
    private final boolean hasValidCache;

    public ClientMessageGunPackHandshake(String sha256, boolean hasValidCache) {
        this.sha256 = sha256;
        this.hasValidCache = hasValidCache;
    }

    public ClientMessageGunPackHandshake(FriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readBoolean());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(sha256);
        buf.writeBoolean(hasValidCache);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    public void handle(ServerPlayer player, PacketSender responseSender) {
        player.server.execute(() -> {
            GunPackSecurityManager.getInstance().onPlayerHandshakeResponse(player, sha256, hasValidCache);
        });
    }

    public String getSha256() {
        return sha256;
    }

    public boolean hasValidCache() {
        return hasValidCache;
    }
}
