package com.tacz.guns.security;

import com.google.common.collect.Maps;
import com.tacz.guns.GunMod;
import com.tacz.guns.config.SecurityConfig;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.*;
import com.tacz.guns.server.ServerPlayerProtectionHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class GunPackSecurityManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(GunPackSecurityManager.class);
    private static final GunPackSecurityManager INSTANCE = new GunPackSecurityManager();

    public static GunPackSecurityManager getInstance() {
        return INSTANCE;
    }

    private volatile String currentSha256 = "";
    private volatile List<byte[]> chunks = Collections.emptyList();
    private volatile byte[] sessionKey = new byte[0];
    private volatile byte[] sessionIv = new byte[0];
    private volatile int totalSize = 0;
    private volatile boolean initialized = false;

    private final Map<UUID, PlayerSyncState> playerSyncStates = new ConcurrentHashMap<>();

    public enum SyncStatus {
        IDLE,
        HANDSHAKING,
        STREAMING,
        AWAITING_ACK,
        COMPLETED
    }

    public static class PlayerSyncState {
        public final UUID playerId;
        public SyncStatus status;
        public int currentChunkIndex;
        public int totalChunks;
        public long lastPacketTime;

        public PlayerSyncState(UUID playerId, int totalChunks) {
            this.playerId = playerId;
            this.status = SyncStatus.HANDSHAKING;
            this.currentChunkIndex = 0;
            this.totalChunks = totalChunks;
            this.lastPacketTime = System.currentTimeMillis();
        }
    }

    public synchronized void initializeServerPacks() {
        if (SecurityConfig.ENABLE_SERVER_PACK_SYNC != null && !SecurityConfig.ENABLE_SERVER_PACK_SYNC.get()) {
            LOGGER.info("[GunPackSecurity] Server pack sync is disabled in config.");
            clear();
            return;
        }
        var server = cn.sh1rocu.tacz.TaCZFabric.getServer();
        if (server != null && !server.isDedicatedServer()) {
            LOGGER.info("[GunPackSecurity] Singleplayer integrated server detected; skipping server pack sync.");
            clear();
            return;
        }
        try {
            Path packsPath = FabricLoader.getInstance().getGameDir().resolve("tacz");
            if (!Files.exists(packsPath) || !Files.isDirectory(packsPath)) {
                LOGGER.info("[GunPackSecurity] No tacz directory found on server.");
                clear();
                return;
            }

            Map<String, byte[]> assetFiles = new HashMap<>();
            scanAndCollectAssets(packsPath, assetFiles);

            if (assetFiles.isEmpty()) {
                LOGGER.info("[GunPackSecurity] No client assets found in server gun packs to bundle.");
                clear();
                return;
            }

            LOGGER.info("[GunPackSecurity] Packaging {} client asset files for encrypted streaming...", assetFiles.size());

            byte[] zipBytes = GunPackCryptoUtil.createZipArchive(assetFiles);
            byte[] compressedBytes = GunPackCryptoUtil.compressGzip(zipBytes);

            this.sessionKey = GunPackCryptoUtil.generateKey();
            this.sessionIv = GunPackCryptoUtil.generateIv();
            byte[] encryptedBundle = GunPackCryptoUtil.encrypt(compressedBytes, sessionKey, sessionIv);
            this.currentSha256 = GunPackCryptoUtil.sha256Hex(encryptedBundle);
            this.totalSize = encryptedBundle.length;

            int chunkSize = SecurityConfig.CHUNK_SIZE_BYTES != null ? SecurityConfig.CHUNK_SIZE_BYTES.get() : 65536;
            List<byte[]> chunkList = new ArrayList<>();
            int offset = 0;
            while (offset < totalSize) {
                int len = Math.min(chunkSize, totalSize - offset);
                byte[] chunk = new byte[len];
                System.arraycopy(encryptedBundle, offset, chunk, 0, len);
                chunkList.add(chunk);
                offset += len;
            }
            this.chunks = Collections.unmodifiableList(chunkList);
            this.initialized = true;

            LOGGER.info("[GunPackSecurity] Pack bundle ready: SHA256={}, TotalSize={} bytes, Chunks={} ({} KB/chunk)",
                    currentSha256, totalSize, chunks.size(), chunkSize / 1024);
        } catch (Exception e) {
            LOGGER.error("[GunPackSecurity] Failed to initialize encrypted server pack bundle", e);
            clear();
        }
    }

    private void clear() {
        this.currentSha256 = "";
        this.chunks = Collections.emptyList();
        this.sessionKey = new byte[0];
        this.sessionIv = new byte[0];
        this.totalSize = 0;
        this.initialized = false;
        this.playerSyncStates.clear();
    }

    private void scanAndCollectAssets(Path packsDir, Map<String, byte[]> targetMap) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(packsDir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    collectFromDirectory(entry, entry, targetMap);
                } else if (entry.toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    collectFromZip(entry, targetMap);
                }
            }
        } catch (IOException e) {
            LOGGER.error("[GunPackSecurity] Error scanning server packs directory: {}", packsDir, e);
        }
    }

    private void collectFromDirectory(Path root, Path current, Map<String, byte[]> targetMap) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    collectFromDirectory(root, path, targetMap);
                } else {
                    String relative = root.relativize(path).toString().replace('\\', '/');
                    if (isClientAsset(relative)) {
                        targetMap.put(relative, Files.readAllBytes(path));
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("[GunPackSecurity] Error reading pack directory: {}", current, e);
        }
    }

    private void collectFromZip(Path zipPath, Map<String, byte[]> targetMap) {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    String name = entry.getName().replace('\\', '/');
                    if (isClientAsset(name)) {
                        try (InputStream is = zipFile.getInputStream(entry)) {
                            targetMap.put(name, is.readAllBytes());
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("[GunPackSecurity] Error reading zip pack: {}", zipPath, e);
        }
    }

    private boolean isClientAsset(String relativePath) {
        String lower = relativePath.toLowerCase(Locale.ROOT);
        return lower.startsWith("assets/") ||
                lower.equals("gunpack.meta.json") ||
                lower.endsWith("/gunpack_info.json") ||
                lower.equals("pack.mcmeta") ||
                lower.equals("pack.png");
    }

    public boolean hasPacksToSync() {
        return initialized && !chunks.isEmpty();
    }

    public String getCurrentSha256() {
        return currentSha256;
    }

    public int getTotalSize() {
        return totalSize;
    }

    public int getChunkCount() {
        return chunks.size();
    }

    public byte[] getSessionKey() {
        return sessionKey;
    }

    public byte[] getSessionIv() {
        return sessionIv;
    }

    public void onPlayerJoin(ServerPlayer player) {
        if (SecurityConfig.ENABLE_SERVER_PACK_SYNC != null && !SecurityConfig.ENABLE_SERVER_PACK_SYNC.get()) {
            return;
        }
        if (player == null || player.server == null || !player.server.isDedicatedServer()) {
            return;
        }
        if (!hasPacksToSync()) {
            return;
        }

        // Freeze and protect player during synchronization
        ServerPlayerProtectionHandler.protect(player);

        PlayerSyncState state = new PlayerSyncState(player.getUUID(), chunks.size());
        playerSyncStates.put(player.getUUID(), state);

        // Send handshake packet
        NetworkHandler.sendToClientPlayer(new ServerMessageGunPackHandshake(currentSha256, totalSize, chunks.size()), player);
        LOGGER.info("[GunPackSecurity] Started handshake with player {} (SHA256: {})", player.getName().getString(), currentSha256);
    }

    public void onPlayerHandshakeResponse(ServerPlayer player, String clientSha256, boolean hasValidCache) {
        PlayerSyncState state = playerSyncStates.get(player.getUUID());
        if (state == null) {
            return;
        }

        if (hasValidCache && currentSha256.equals(clientSha256)) {
            LOGGER.info("[GunPackSecurity] Player {} already has cached pack. Sending session key...", player.getName().getString());
            state.status = SyncStatus.AWAITING_ACK;
            NetworkHandler.sendToClientPlayer(new ServerMessageGunPackKey(currentSha256, sessionKey, sessionIv), player);
        } else {
            LOGGER.info("[GunPackSecurity] Player {} needs full pack stream. Starting chunk transfer...", player.getName().getString());
            state.status = SyncStatus.STREAMING;
            state.currentChunkIndex = 0;
        }
    }

    private long tickStartTimeNanos = System.nanoTime();

    public void onServerTickStart() {
        tickStartTimeNanos = System.nanoTime();
    }

    public static boolean hasPriority(ServerPlayer player) {
        if (player == null) return false;
        String permission = SecurityConfig.PRIORITY_PERMISSION != null ? SecurityConfig.PRIORITY_PERMISSION.get() : "metrorp.priority";
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("fabric-permissions-api-v0")) {
            try {
                Class<?> clazz = Class.forName("me.lucko.fabric.api.permissions.v1.Permissions");
                java.lang.reflect.Method method = clazz.getMethod("check", net.minecraft.world.entity.Entity.class, String.class, boolean.class);
                return (boolean) method.invoke(null, player, permission, player.hasPermissions(2));
            } catch (Throwable ignored) {}
        }
        return player.hasPermissions(2);
    }

    public void onServerTick() {
        var server = cn.sh1rocu.tacz.TaCZFabric.getServer();
        if (server != null) {
            onServerTickEnd(server);
        }
    }

    public void onServerTickEnd(MinecraftServer server) {
        if (!hasPacksToSync() || playerSyncStates.isEmpty()) {
            return;
        }

        // 1. PREEMPTIVE TIME BUDGETING: Check elapsed time consumed by gameplay calculations
        long elapsedNanos = System.nanoTime() - tickStartTimeNanos;
        double elapsedMs = elapsedNanos / 1_000_000.0;
        double deadlineMs = SecurityConfig.PREEMPTIVE_DEADLINE_MS != null ? SecurityConfig.PREEMPTIVE_DEADLINE_MS.get() : 42.0;

        // If server gameplay took longer than deadline (e.g. 42ms out of 50ms), skip sending chunks to preserve 19-20 TPS!
        if (elapsedMs >= deadlineMs) {
            return;
        }

        int regularBurstLimit = SecurityConfig.BURST_CHUNKS_PER_TICK != null ? SecurityConfig.BURST_CHUNKS_PER_TICK.get() : 4;
        int priorityBurstLimit = SecurityConfig.PRIORITY_BURST_CHUNKS_PER_TICK != null ? SecurityConfig.PRIORITY_BURST_CHUNKS_PER_TICK.get() : 8;
        int globalBurstLimit = SecurityConfig.GLOBAL_MAX_BURST_CHUNKS_PER_TICK != null ? SecurityConfig.GLOBAL_MAX_BURST_CHUNKS_PER_TICK.get() : 16;

        // 2. DYNAMIC TPS THROTTLING
        int effectiveGlobalLimit = globalBurstLimit;
        if (SecurityConfig.AUTO_THROTTLE_ON_LOW_TPS != null && SecurityConfig.AUTO_THROTTLE_ON_LOW_TPS.get()) {
            float avgTickTimeMs = server.getAverageTickTime();
            double currentTps = avgTickTimeMs > 0 ? Math.min(20.0, 1000.0 / Math.max(50.0f, avgTickTimeMs)) : 20.0;
            double threshold = SecurityConfig.TARGET_TPS_THRESHOLD != null ? SecurityConfig.TARGET_TPS_THRESHOLD.get() : 18.5;
            if (currentTps < threshold) {
                double factor = Math.max(0.1, Math.max(0.0, currentTps - 8.0) / (threshold - 8.0));
                effectiveGlobalLimit = Math.max(1, (int) Math.round(globalBurstLimit * factor));
            }
        }

        // 3. SEPARATE PLAYERS INTO PRIORITY QUEUE AND REGULAR QUEUE
        java.util.List<PlayerSyncState> priorityPlayers = new java.util.ArrayList<>();
        java.util.List<PlayerSyncState> regularPlayers = new java.util.ArrayList<>();

        Iterator<Map.Entry<UUID, PlayerSyncState>> iterator = playerSyncStates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PlayerSyncState> entry = iterator.next();
            PlayerSyncState state = entry.getValue();
            if (state.status == SyncStatus.STREAMING) {
                ServerPlayer player = server.getPlayerList().getPlayer(state.playerId);
                if (player == null || player.hasDisconnected()) {
                    iterator.remove();
                    continue;
                }
                if (hasPriority(player)) {
                    priorityPlayers.add(state);
                } else {
                    regularPlayers.add(state);
                }
            }
        }

        if (priorityPlayers.isEmpty() && regularPlayers.isEmpty()) {
            return;
        }

        int totalChunksSentThisTick = 0;

        // 4. SERVE PRIORITY PLAYERS FIRST
        if (!priorityPlayers.isEmpty()) {
            int priorityQuota = Math.max(1, Math.min(priorityBurstLimit, effectiveGlobalLimit / priorityPlayers.size()));
            for (PlayerSyncState state : priorityPlayers) {
                if (totalChunksSentThisTick >= effectiveGlobalLimit) break;
                if ((System.nanoTime() - tickStartTimeNanos) / 1_000_000.0 >= 46.0) break; // Hard safety cut-off

                ServerPlayer player = server.getPlayerList().getPlayer(state.playerId);
                if (player == null || player.hasDisconnected()) continue;

                int toSend = Math.min(priorityQuota, effectiveGlobalLimit - totalChunksSentThisTick);
                for (int i = 0; i < toSend && state.currentChunkIndex < chunks.size(); i++) {
                    byte[] chunkData = chunks.get(state.currentChunkIndex);
                    NetworkHandler.sendToClientPlayer(
                            new ServerMessageGunPackChunk(state.currentChunkIndex, chunks.size(), chunkData),
                            player
                    );
                    state.currentChunkIndex++;
                    state.lastPacketTime = System.currentTimeMillis();
                    totalChunksSentThisTick++;
                }

                if (state.currentChunkIndex >= chunks.size()) {
                    state.status = SyncStatus.AWAITING_ACK;
                    NetworkHandler.sendToClientPlayer(new ServerMessageGunPackKey(currentSha256, sessionKey, sessionIv), player);
                    LOGGER.info("[GunPackSecurity] (VIP/Priority) All chunks sent to {}. Transmitted session key.", player.getName().getString());
                }
            }
        }

        // 5. SERVE REGULAR PLAYERS WITH REMAINING BUDGET
        int remainingBudget = effectiveGlobalLimit - totalChunksSentThisTick;
        if (!regularPlayers.isEmpty() && remainingBudget > 0) {
            int regularQuota = Math.max(1, Math.min(regularBurstLimit, remainingBudget / regularPlayers.size()));
            for (PlayerSyncState state : regularPlayers) {
                if (totalChunksSentThisTick >= effectiveGlobalLimit) break;
                if ((System.nanoTime() - tickStartTimeNanos) / 1_000_000.0 >= 46.0) break; // Hard safety cut-off

                ServerPlayer player = server.getPlayerList().getPlayer(state.playerId);
                if (player == null || player.hasDisconnected()) continue;

                int toSend = Math.min(regularQuota, effectiveGlobalLimit - totalChunksSentThisTick);
                for (int i = 0; i < toSend && state.currentChunkIndex < chunks.size(); i++) {
                    byte[] chunkData = chunks.get(state.currentChunkIndex);
                    NetworkHandler.sendToClientPlayer(
                            new ServerMessageGunPackChunk(state.currentChunkIndex, chunks.size(), chunkData),
                            player
                    );
                    state.currentChunkIndex++;
                    state.lastPacketTime = System.currentTimeMillis();
                    totalChunksSentThisTick++;
                }

                if (state.currentChunkIndex >= chunks.size()) {
                    state.status = SyncStatus.AWAITING_ACK;
                    NetworkHandler.sendToClientPlayer(new ServerMessageGunPackKey(currentSha256, sessionKey, sessionIv), player);
                    LOGGER.info("[GunPackSecurity] All chunks sent to {}. Transmitted session key.", player.getName().getString());
                }
            }
        }
    }

    public void onPlayerAck(ServerPlayer player, String ackSha256) {
        PlayerSyncState state = playerSyncStates.remove(player.getUUID());
        if (state != null) {
            state.status = SyncStatus.COMPLETED;
        }
        ServerPlayerProtectionHandler.unprotect(player);
        LOGGER.info("[GunPackSecurity] Player {} acknowledged pack load. Restored normal gameplay.", player.getName().getString());
    }

    public void onPlayerLoggedOut(ServerPlayer player) {
        playerSyncStates.remove(player.getUUID());
        ServerPlayerProtectionHandler.unprotect(player);
    }
}
