package com.tacz.guns.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServerPlayerProtectionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerPlayerProtectionHandler.class);
    private static final Set<UUID> PROTECTED_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, FreezeData> FROZEN_POSITIONS = new ConcurrentHashMap<>();

    public static class FreezeData {
        public final double x, y, z;
        public final float yRot, xRot;

        public FreezeData(double x, double y, double z, float yRot, float xRot) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yRot = yRot;
            this.xRot = xRot;
        }
    }

    public static void protect(ServerPlayer player) {
        if (player == null) return;
        PROTECTED_PLAYERS.add(player.getUUID());
        FROZEN_POSITIONS.put(player.getUUID(), new FreezeData(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
        player.setInvulnerable(true);
        player.setDeltaMovement(0, 0, 0);
        player.resetFallDistance();
        LOGGER.debug("[PlayerProtection] Enabled sync protection & movement freeze for player {}", player.getName().getString());
    }

    public static void unprotect(ServerPlayer player) {
        if (player == null) return;
        PROTECTED_PLAYERS.remove(player.getUUID());
        FROZEN_POSITIONS.remove(player.getUUID());
        if (!player.isCreative() && !player.isSpectator()) {
            player.setInvulnerable(false);
        }
        player.resetFallDistance();
        LOGGER.debug("[PlayerProtection] Disabled sync protection for player {}", player.getName().getString());
    }

    public static boolean isProtected(Entity entity) {
        return entity instanceof ServerPlayer player && PROTECTED_PLAYERS.contains(player.getUUID());
    }

    public static FreezeData getFreezeData(Entity entity) {
        return entity != null ? FROZEN_POSITIONS.get(entity.getUUID()) : null;
    }

    public static void onPlayerTick(ServerPlayer player) {
        if (player == null || !isProtected(player)) return;

        FreezeData pos = FROZEN_POSITIONS.get(player.getUUID());
        if (pos != null) {
            player.setDeltaMovement(0, 0, 0);
            player.resetFallDistance();
            if (player.distanceToSqr(pos.x, pos.y, pos.z) > 0.001) {
                player.teleportTo(pos.x, pos.y, pos.z);
            }
        }
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        if (player != null) {
            PROTECTED_PLAYERS.remove(player.getUUID());
            FROZEN_POSITIONS.remove(player.getUUID());
        }
    }
}
