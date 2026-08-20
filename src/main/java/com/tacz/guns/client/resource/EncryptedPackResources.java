package com.tacz.guns.client.resource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.tacz.guns.GunMod;
import com.tacz.guns.security.GunPackCryptoUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Pack de ressources virtuel chiffré.<br/>
 * Déchiffre les fichiers spécifiques à la demande (on-the-fly) sans saturer la RAM
 * et sans jamais créer de fichier déchiffré sur le disque.
 */
@Environment(EnvType.CLIENT)
public class EncryptedPackResources extends AbstractPackResources {
    private static final Logger LOGGER = LoggerFactory.getLogger(EncryptedPackResources.class);

    private final Path encryptedFilePath;
    private final byte[] sessionKey;
    private final byte[] sessionIv;
    private final PackMetadataSection packMeta;

    // Table d'index en RAM : ResourceLocation -> Table d'octets du fichier déchiffré
    // Pour les métadonnées et accès ultra-rapide aux JSONs et flux textures/sons
    private final Map<PackType, Map<String, Map<ResourceLocation, byte[]>>> memoryResources = new EnumMap<>(PackType.class);
    private final Map<PackType, Set<String>> namespaces = new EnumMap<>(PackType.class);

    public EncryptedPackResources(String packId, Path encryptedFilePath, byte[] sessionKey, byte[] sessionIv, PackMetadataSection packMeta) {
        super(packId, false);
        this.encryptedFilePath = encryptedFilePath;
        this.sessionKey = sessionKey;
        this.sessionIv = sessionIv;
        this.packMeta = packMeta;

        for (PackType type : PackType.values()) {
            memoryResources.put(type, new HashMap<>());
            namespaces.put(type, new HashSet<>());
        }

        initializeFromEncryptedFile();
    }

    private void initializeFromEncryptedFile() {
        try {
            if (!Files.exists(encryptedFilePath)) {
                LOGGER.error("[EncryptedPackResources] Encrypted cache file not found: {}", encryptedFilePath);
                return;
            }

            byte[] cipherBytes = Files.readAllBytes(encryptedFilePath);
            byte[] decryptedGzip = GunPackCryptoUtil.decrypt(cipherBytes, sessionKey, sessionIv);
            byte[] zipBytes = GunPackCryptoUtil.decompressGzip(decryptedGzip);

            // Zeroize sensitive AES session keys and intermediate decryption byte arrays immediately
            GunPackCryptoUtil.zeroize(sessionKey);
            GunPackCryptoUtil.zeroize(sessionIv);
            GunPackCryptoUtil.zeroize(decryptedGzip);

            Map<String, byte[]> extractedFiles = GunPackCryptoUtil.extractZipArchive(zipBytes);
            GunPackCryptoUtil.zeroize(zipBytes);

            for (Map.Entry<String, byte[]> entry : extractedFiles.entrySet()) {
                try {
                    String path = entry.getKey().replace('\\', '/');
                    if (path.startsWith("/")) {
                        path = path.substring(1);
                    }

                    if (path.startsWith("assets/")) {
                        String sub = path.substring("assets/".length());
                        int slashIndex = sub.indexOf('/');
                        if (slashIndex > 0) {
                            String namespace = sub.substring(0, slashIndex).toLowerCase(Locale.ROOT);
                            String resourcePath = sub.substring(slashIndex + 1).toLowerCase(Locale.ROOT);
                            ResourceLocation loc = ResourceLocation.tryBuild(namespace, resourcePath);

                            if (loc != null) {
                                memoryResources.get(PackType.CLIENT_RESOURCES)
                                        .computeIfAbsent(namespace, k -> new HashMap<>())
                                        .put(loc, entry.getValue());
                                namespaces.get(PackType.CLIENT_RESOURCES).add(namespace);
                            }
                        }
                    } else if (path.startsWith("data/")) {
                        String sub = path.substring("data/".length());
                        int slashIndex = sub.indexOf('/');
                        if (slashIndex > 0) {
                            String namespace = sub.substring(0, slashIndex).toLowerCase(Locale.ROOT);
                            String resourcePath = sub.substring(slashIndex + 1).toLowerCase(Locale.ROOT);
                            ResourceLocation loc = ResourceLocation.tryBuild(namespace, resourcePath);

                            if (loc != null) {
                                memoryResources.get(PackType.SERVER_DATA)
                                        .computeIfAbsent(namespace, k -> new HashMap<>())
                                        .put(loc, entry.getValue());
                                namespaces.get(PackType.SERVER_DATA).add(namespace);
                            }
                        }
                    }
                } catch (Exception fileEx) {
                    LOGGER.warn("[EncryptedPackResources] Skipping unparseable entry: {}", entry.getKey(), fileEx);
                }
            }

            int clientTotal = 0;
            int gunsCount = 0, ammoCount = 0, attachCount = 0, blockCount = 0, modelCount = 0, animCount = 0, texCount = 0, soundCount = 0, scriptCount = 0;

            for (Map<ResourceLocation, byte[]> nsMap : memoryResources.get(PackType.CLIENT_RESOURCES).values()) {
                for (ResourceLocation loc : nsMap.keySet()) {
                    clientTotal++;
                    String p = loc.getPath();
                    if (p.startsWith("display/guns/")) gunsCount++;
                    else if (p.startsWith("display/ammo/")) ammoCount++;
                    else if (p.startsWith("display/attachments/")) attachCount++;
                    else if (p.startsWith("display/blocks/")) blockCount++;
                    else if (p.startsWith("geo_models/")) modelCount++;
                    else if (p.startsWith("animations/")) animCount++;
                    else if (p.startsWith("textures/")) texCount++;
                    else if (p.startsWith("sounds/") || p.equals("sounds.json")) soundCount++;
                    else if (p.startsWith("scripts/")) scriptCount++;
                }
            }

            LOGGER.info("[EncryptedPackResources] Mounted '{}': {} total client assets (guns={}, ammo={}, attachments={}, blocks={}, models={}, anims={}, textures={}, sounds={}, scripts={}) across {} namespaces: {}",
                    packId(),
                    clientTotal,
                    gunsCount, ammoCount, attachCount, blockCount, modelCount, animCount, texCount, soundCount, scriptCount,
                    namespaces.get(PackType.CLIENT_RESOURCES).size(),
                    namespaces.get(PackType.CLIENT_RESOURCES)
            );
        } catch (Exception e) {
            LOGGER.error("[EncryptedPackResources] Failed to decrypt and mount pack: {}", packId(), e);
        }
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... paths) {
        if (paths.length == 1 && "pack.mcmeta".equals(paths[0])) {
            int format = packMeta != null ? packMeta.getPackFormat() : 15;
            String mcmeta = "{\"pack\":{\"pack_format\":" + format + ",\"description\":\"TACZ Secure Server Pack\"}}";
            return () -> new ByteArrayInputStream(mcmeta.getBytes(StandardCharsets.UTF_8));
        }
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        Map<String, Map<ResourceLocation, byte[]>> typeMap = memoryResources.get(type);
        if (typeMap == null) {
            return null;
        }

        Map<ResourceLocation, byte[]> nsMap = typeMap.get(location.getNamespace());
        if (nsMap == null) {
            return null;
        }

        byte[] data = nsMap.get(location);
        if (data != null) {
            return () -> new ByteArrayInputStream(data);
        }

        return null;
    }

    @Override
    public void listResources(PackType type, String resourceNamespace, String paths, ResourceOutput resourceOutput) {
        Map<String, Map<ResourceLocation, byte[]>> typeMap = memoryResources.get(type);
        if (typeMap == null) {
            return;
        }

        Map<ResourceLocation, byte[]> nsMap = typeMap.get(resourceNamespace);
        if (nsMap == null) {
            return;
        }

        String prefix = paths.endsWith("/") ? paths : paths + "/";
        int matchCount = 0;
        for (Map.Entry<ResourceLocation, byte[]> entry : nsMap.entrySet()) {
            ResourceLocation id = entry.getKey();
            if (id.getPath().startsWith(paths) || id.getPath().startsWith(prefix)) {
                byte[] data = entry.getValue();
                resourceOutput.accept(id, () -> new ByteArrayInputStream(data));
                matchCount++;
            }
        }
        LOGGER.debug("[EncryptedPackResources] listResources(type={}, ns={}, path={}) returned {} matches", type, resourceNamespace, paths, matchCount);
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return namespaces.getOrDefault(type, Collections.emptySet());
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) throws IOException {
        return deserializer.getMetadataSectionName().equals("pack") ? (T) this.packMeta : null;
    }

    @Override
    public void close() {
        // IMPORTANT: Do NOT clear memoryResources here!
        // Minecraft's Pack.readMetaAndCreate opens and closes PackResources during discovery in a try-with-resources block.
        // Clearing memoryResources here wipes all decrypted data from RAM before Minecraft even finishes loading!
        LOGGER.debug("[EncryptedPackResources] close() called by Minecraft resource manager (data retained in RAM).");
    }

    /**
     * Explicitly destroy and purge all decrypted data from RAM (e.g. on server disconnect).
     */
    public void destroy() {
        LOGGER.info("[EncryptedPackResources] Explicitly destroying in-memory decrypted pack data.");
        memoryResources.values().forEach(typeMap -> {
            typeMap.values().forEach(Map::clear);
            typeMap.clear();
        });
        namespaces.values().forEach(Set::clear);
        memoryResources.clear();
        namespaces.clear();
    }
}
