package com.tacz.guns.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class SecurityConfig {
    public static ForgeConfigSpec.BooleanValue ENABLE_SERVER_PACK_SYNC;
    public static ForgeConfigSpec.IntValue CHUNK_SIZE_BYTES;
    public static ForgeConfigSpec.IntValue BURST_CHUNKS_PER_TICK;
    public static ForgeConfigSpec.IntValue GLOBAL_MAX_BURST_CHUNKS_PER_TICK;
    public static ForgeConfigSpec.BooleanValue AUTO_THROTTLE_ON_LOW_TPS;
    public static ForgeConfigSpec.DoubleValue TARGET_TPS_THRESHOLD;
    public static ForgeConfigSpec.ConfigValue<String> PRIORITY_PERMISSION;
    public static ForgeConfigSpec.IntValue PRIORITY_BURST_CHUNKS_PER_TICK;
    public static ForgeConfigSpec.DoubleValue PREEMPTIVE_DEADLINE_MS;
    public static ForgeConfigSpec.BooleanValue ENFORCE_MEMORY_ONLY_CLIENT;
    public static ForgeConfigSpec.BooleanValue PROTECT_PLAYER_DURING_SYNC;

    public static void init(ForgeConfigSpec.Builder builder) {
        builder.push("security_and_pack_sync");

        builder.comment("Enable secure server-side gun pack distribution and in-memory client encryption");
        ENABLE_SERVER_PACK_SYNC = builder.define("EnableServerPackSync", true);

        builder.comment("Size in bytes for each network chunk sent to client (default: 65536 = 64KB)");
        CHUNK_SIZE_BYTES = builder.defineInRange("ChunkSizeBytes", 65536, 4096, 524288);

        builder.comment("Maximum number of chunks sent per game tick for regular players (default: 4 = 256KB/tick)");
        BURST_CHUNKS_PER_TICK = builder.defineInRange("BurstChunksPerTick", 4, 1, 64);

        builder.comment("Permission required for VIP/priority fast download queue");
        PRIORITY_PERMISSION = builder.define("PriorityPermission", "metrorp.priority");

        builder.comment("Maximum number of chunks sent per game tick for priority/VIP players (default: 8 = 512KB/tick)");
        PRIORITY_BURST_CHUNKS_PER_TICK = builder.defineInRange("PriorityBurstChunksPerTick", 8, 1, 64);

        builder.comment("Global maximum number of chunks sent per game tick across ALL connecting players combined to prevent TPS lag (default: 16 = 1MB/tick)");
        GLOBAL_MAX_BURST_CHUNKS_PER_TICK = builder.defineInRange("GlobalMaxBurstChunksPerTick", 16, 1, 256);

        builder.comment("Automatically throttle and reduce download speed if server TPS drops below threshold");
        AUTO_THROTTLE_ON_LOW_TPS = builder.define("AutoThrottleOnLowTps", true);

        builder.comment("TPS threshold below which download speed starts scaling down (default: 18.5 TPS)");
        TARGET_TPS_THRESHOLD = builder.defineInRange("TargetTpsThreshold", 18.5, 10.0, 20.0);

        builder.comment("Preemptive tick time deadline in ms (if tick gameplay calculation takes longer than this, skip sending chunks this tick to preserve 19-20 TPS)");
        PREEMPTIVE_DEADLINE_MS = builder.defineInRange("PreemptiveDeadlineMs", 42.0, 20.0, 48.0);

        builder.comment("Enforce zero plain files on client disk (decrypt 100% in RAM only)");
        ENFORCE_MEMORY_ONLY_CLIENT = builder.define("EnforceMemoryOnlyClient", true);

        builder.comment("Freeze and protect player (invulnerable, no damage, no AI target) while downloading/loading gun packs");
        PROTECT_PLAYER_DURING_SYNC = builder.define("ProtectPlayerDuringSync", true);

        builder.pop();
    }
}
