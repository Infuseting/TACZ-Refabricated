package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.gui.GunPackDownloadScreen;
import com.tacz.guns.security.GunPackClientCacheManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerMessageGunPackHandshake implements FabricPacket {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerMessageGunPackHandshake.class);
    public static final PacketType<ServerMessageGunPackHandshake> TYPE = PacketType.create(
            new ResourceLocation(GunMod.MOD_ID, "s2c_gunpack_handshake"),
            ServerMessageGunPackHandshake::new
    );

    private final String sha256;
    private final int totalSize;
    private final int totalChunks;

    public ServerMessageGunPackHandshake(String sha256, int totalSize, int totalChunks) {
        this.sha256 = sha256;
        this.totalSize = totalSize;
        this.totalChunks = totalChunks;
    }

    public ServerMessageGunPackHandshake(FriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(sha256);
        buf.writeVarInt(totalSize);
        buf.writeVarInt(totalChunks);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    @Environment(EnvType.CLIENT)
    public void handle(LocalPlayer player, PacketSender responseSender) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            boolean hasCache = GunPackClientCacheManager.hasValidCache(sha256);
            LOGGER.info("[GunPackHandshake] Received server pack info: SHA256={}, Size={} bytes, Chunks={}, CacheAvailable={}",
                    sha256, totalSize, totalChunks, hasCache);

            if (hasCache) {
                // Already have matching encrypted cache, request only the ephemeral session key
                responseSender.sendPacket(new ClientMessageGunPackHandshake(sha256, true));
            } else {
                // Need to download chunks: open download screen and prepare buffer
                GunPackDownloadScreen.open(sha256, totalSize, totalChunks);
                responseSender.sendPacket(new ClientMessageGunPackHandshake(sha256, false));
            }
        });
    }

    public String getSha256() {
        return sha256;
    }

    public int getTotalSize() {
        return totalSize;
    }

    public int getTotalChunks() {
        return totalChunks;
    }
}
