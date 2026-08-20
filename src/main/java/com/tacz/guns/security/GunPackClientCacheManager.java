package com.tacz.guns.security;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Gestionnaire du cache local de conteneurs chiffrés (.tacz_cache/).<br/>
 * Ne stocke QUE des fichiers .enc illisibles sans la clé de session du serveur.
 */
@Environment(EnvType.CLIENT)
public class GunPackClientCacheManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(GunPackClientCacheManager.class);
    private static final String CACHE_DIR_NAME = ".tacz_cache";
    private static final String FILE_EXTENSION = ".enc";

    public static Path getCacheDir() {
        Path path = FabricLoader.getInstance().getGameDir().resolve(CACHE_DIR_NAME);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                LOGGER.error("[GunPackCache] Failed to create cache directory: {}", path, e);
            }
        }
        return path;
    }

    public static Path getCacheFile(String sha256) {
        return getCacheDir().resolve(sha256 + FILE_EXTENSION);
    }

    public static boolean hasValidCache(String sha256) {
        if (sha256 == null || sha256.isEmpty()) {
            return false;
        }
        Path file = getCacheFile(sha256);
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            return false;
        }

        try {
            long size = Files.size(file);
            if (size <= 0) {
                return false;
            }
            String computedHash = GunPackCryptoUtil.sha256Hex(file);
            return sha256.equalsIgnoreCase(computedHash);
        } catch (IOException e) {
            LOGGER.error("[GunPackCache] Error verifying cache for hash {}", sha256, e);
            return false;
        }
    }

    public static void saveEncryptedChunks(String sha256, byte[][] chunks) throws IOException {
        Path targetFile = getCacheFile(sha256);
        Path tempFile = getCacheDir().resolve(sha256 + ".tmp");

        long totalWritten = 0;
        try {
            try (java.io.OutputStream fos = new java.io.BufferedOutputStream(Files.newOutputStream(tempFile))) {
                for (byte[] chunk : chunks) {
                    if (chunk != null) {
                        fos.write(chunk);
                        totalWritten += chunk.length;
                    }
                }
                fos.flush();
            }

            // Atomically replace target file to prevent partial corrupt files on crash
            try {
                Files.move(tempFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tempFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable t) {
            Files.deleteIfExists(tempFile);
            throw t;
        }

        LOGGER.info("[GunPackCache] Saved encrypted pack to cache: {} ({} bytes)", targetFile.getFileName(), totalWritten);
        cleanOldCaches(sha256);
    }

    public static void saveEncryptedBytes(String sha256, byte[] data) throws IOException {
        Path file = getCacheFile(sha256);
        Files.write(file, data);
        LOGGER.info("[GunPackCache] Saved encrypted pack to cache: {} ({} bytes)", file.getFileName(), data.length);
        cleanOldCaches(sha256);
    }

    public static void cleanOldCaches(String currentSha256) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(getCacheDir())) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (name.endsWith(FILE_EXTENSION) && !name.startsWith(currentSha256)) {
                    Files.deleteIfExists(entry);
                    LOGGER.info("[GunPackCache] Purged outdated encrypted cache file: {}", name);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[GunPackCache] Failed to clean old cache files", e);
        }
    }
}
