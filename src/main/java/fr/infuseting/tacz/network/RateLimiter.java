package fr.infuseting.tacz.network;

import me.lucko.fabric.api.permissions.v1.Permissions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Securite serveur pour limiter la cadence d'envoi des paquets de rechargement/dechargement
 * sans kicker le joueur, mais en envoyant une alerte aux personnes possedant la permission "metrorp.logs.ratelimit".
 */
public final class RateLimiter {
    private static final Map<UUID, Long> LAST_PACKET_TIME = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_ALERT_TIME = new ConcurrentHashMap<>();

    private static final long MIN_INTERVAL_MS = 30L;
    private static final long ALERT_COOLDOWN_MS = 2000L;

    public static final String PERMISSION_RATELIMIT_LOGS = "metrorp.logs.ratelimit";

    private RateLimiter() {}

    public static boolean canProcess(ServerPlayer player) {
        if (player == null || player.getAbilities().instabuild) {
            return true;
        }
        long now = System.currentTimeMillis();
        UUID uuid = player.getUUID();
        Long lastTime = LAST_PACKET_TIME.get(uuid);

        if (lastTime != null && (now - lastTime) < MIN_INTERVAL_MS) {
            long delta = now - lastTime;
            Long lastAlert = LAST_ALERT_TIME.get(uuid);
            if (lastAlert == null || (now - lastAlert) > ALERT_COOLDOWN_MS) {
                LAST_ALERT_TIME.put(uuid, now);
                notifyAdmins(player, delta);
            }
            return false;
        }

        LAST_PACKET_TIME.put(uuid, now);
        return true;
    }

    private static void notifyAdmins(ServerPlayer targetPlayer, long intervalMs) {
        if (targetPlayer.getServer() == null) return;

        Component alertMessage = Component.literal("[MetroRP Logs] ")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal("RateLimit: ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal(targetPlayer.getScoreboardName()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" envoie des paquets trop rapidement (" + intervalMs + "ms).").withStyle(ChatFormatting.GRAY));

        for (ServerPlayer player : targetPlayer.getServer().getPlayerList().getPlayers()) {
            if (hasPermission(player, PERMISSION_RATELIMIT_LOGS)) {
                player.sendSystemMessage(alertMessage);
            }
        }
    }

    public static boolean hasPermission(ServerPlayer player, String permission) {
        if (player == null) return false;
        if (FabricLoader.getInstance().isModLoaded("fabric-permissions-api-v0")) {
            return Permissions.check(player, permission, player.hasPermissions(2));
        }
        return player.hasPermissions(2);
    }
}
