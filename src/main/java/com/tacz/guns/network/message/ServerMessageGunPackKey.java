package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.gui.GunPackDownloadScreen;
import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.client.resource.EncryptedPackResources;
import com.tacz.guns.resource.GunPackLoader;
import com.tacz.guns.security.GunPackClientCacheManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class ServerMessageGunPackKey implements FabricPacket {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerMessageGunPackKey.class);
    public static final PacketType<ServerMessageGunPackKey> TYPE = PacketType.create(
            new ResourceLocation(GunMod.MOD_ID, "s2c_gunpack_key"),
            ServerMessageGunPackKey::new
    );

    private final String sha256;
    private final byte[] sessionKey;
    private final byte[] sessionIv;

    public ServerMessageGunPackKey(String sha256, byte[] sessionKey, byte[] sessionIv) {
        this.sha256 = sha256;
        this.sessionKey = sessionKey;
        this.sessionIv = sessionIv;
    }

    public ServerMessageGunPackKey(FriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readByteArray(), buf.readByteArray());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(sha256);
        buf.writeByteArray(sessionKey);
        buf.writeByteArray(sessionIv);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    @Environment(EnvType.CLIENT)
    public void handle(LocalPlayer player, PacketSender responseSender) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            LOGGER.info("[GunPackKey] Received ephemeral session key for pack SHA256: {}", sha256);
            Path cacheFile = GunPackClientCacheManager.getCacheFile(sha256);

            try {
                // Initialize in-memory virtual encrypted pack
                PackMetadataSection meta = new PackMetadataSection(
                        Component.literal("TACZ Secure Server Pack"),
                        SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES)
                );
                EncryptedPackResources securePack = new EncryptedPackResources("tacz_secure_server_pack", cacheFile, sessionKey, sessionIv, meta);
                GunPackLoader.INSTANCE.setSecurePackResources(securePack);

                // Activate pack in Minecraft's PackRepository and trigger Minecraft reload
                var repository = mc.getResourcePackRepository();
                repository.reload();
                LOGGER.info("[GunPackKey] Available pack repository IDs: {}", repository.getAvailableIds());
                java.util.List<String> selectedPacks = new java.util.ArrayList<>(repository.getSelectedIds());
                if (!selectedPacks.contains("tacz_secure_server_pack")) {
                    selectedPacks.add("tacz_secure_server_pack");
                    repository.setSelected(selectedPacks);
                }
                LOGGER.info("[GunPackKey] Selected pack repository IDs for reload: {}", repository.getSelectedIds());

                LOGGER.info("[GunPackKey] Triggering Minecraft resource packs reload...");
                mc.reloadResourcePacks().thenRun(() -> {
                    LOGGER.info("[GunPackKey] Minecraft resource packs reload finished. Rebuilding ClientIndexManager...");
                    // Reload client indexes to bind models, sounds, animations
                    ClientIndexManager.reload();

                    // Close download screen if active
                    GunPackDownloadScreen.close();

                    // Acknowledge success to server so it unfreezes the player
                    responseSender.sendPacket(new ClientMessageGunPackAck(sha256));
                    LOGGER.info("[GunPackKey] Secure pack successfully mounted, textured and indexed in RAM.");
                });
            } catch (Exception e) {
                LOGGER.error("[GunPackKey] Error loading secure pack into game engine", e);
            }
        });
    }

    public String getSha256() {
        return sha256;
    }

    public byte[] getSessionKey() {
        return sessionKey;
    }

    public byte[] getSessionIv() {
        return sessionIv;
    }
}
