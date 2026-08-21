package fr.infuseting.tacz.config;

import fr.infuseting.tacz.TaCZMagazines;
import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

public class MechanicsConfig {

    public static final ForgeConfigSpec SPEC;

    // Whether tick-based loading/unloading is active.
    public static final ForgeConfigSpec.BooleanValue TICK_BASED;
    // Ticks between each bullet being inserted
    public static final ForgeConfigSpec.IntValue LOAD_TICKS;
    // Ticks between each bullet being ejected. Same scale as LOAD_TICKS.
    public static final ForgeConfigSpec.IntValue UNLOAD_TICKS;
    // Whether extended magazines can be used without the extended-mag attachment installed.
    public static final ForgeConfigSpec.BooleanValue ALLOW_EXTENDED_WITHOUT_ATTACHMENT;

    // Whether to replace TaCZ's reserve-ammo number with a RoN-style magazine silhouette row.
    public static final ForgeConfigSpec.BooleanValue OVERRIDE_AMMO_HUD;

    // Whether holding a magazine in-hand enables tick-based load/unload sessions.
    public static final ForgeConfigSpec.BooleanValue IN_HAND_TICK_BASED;

    // Whether TaCZ ammo boxes may store magazine item stacks.
    public static final ForgeConfigSpec.BooleanValue MAGAZINES_IN_AMMO_BOXES;
    // Whether magazine loading may consume loose rounds from TaCZ ammo boxes.
    public static final ForgeConfigSpec.BooleanValue LOAD_MAGAZINES_FROM_AMMO_BOXES;
    // Whether bullets and magazines must use separate ammo boxes.
    public static final ForgeConfigSpec.BooleanValue SEPARATE_AMMO_BOX_CONTENTS;
    // Source ordering for both gun reloads and loose-round loading.
    public static final ForgeConfigSpec.BooleanValue PREFER_PLAYER_INVENTORY;

    // Durability & Jamming system (EFT-style)
    public static final ForgeConfigSpec.BooleanValue ENABLE_DURABILITY;
    public static final ForgeConfigSpec.DoubleValue DURABILITY_LOSS_PER_SHOT;
    public static final ForgeConfigSpec.DoubleValue JAM_SAFETY_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue MAX_JAM_CHANCE;
    public static final ForgeConfigSpec.DoubleValue STAT_PENALTY_VELOCITY_MAX;
    public static final ForgeConfigSpec.DoubleValue STAT_PENALTY_DAMAGE_MAX;
    public static final ForgeConfigSpec.DoubleValue STAT_PENALTY_SPREAD_MAX;
    public static final ForgeConfigSpec.DoubleValue CLEANING_MAX_CAP_LOSS_REGULAR;
    public static final ForgeConfigSpec.DoubleValue CLEANING_MAX_CAP_LOSS_HEAVY;
    public static final ForgeConfigSpec.DoubleValue CLEANING_RESTORE_WEAR_FACTOR;
    public static final ForgeConfigSpec.BooleanValue DROP_JAMMED_BULLET;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("loading");

        TICK_BASED = b
                .comment("Set to false to use drop-in loading/unloading.",
                         "  DROP-IN mode (false):",
                         "    Right-click ammo onto a magazine â†’ fill instantly.",
                         "    Right-click an empty cursor onto a magazine â†’ unload all ammo instantly.",
                         "  TICK-BASED mode (true, default):",
                         "    Left-click ammo onto magazine â†’ auto-loads one bullet per interval, EFT-style spinner.",
                         "    Right-click empty cursor onto magazine â†’ auto-ejects one bullet per interval.")
                .define("tick_based", true);

        LOAD_TICKS = b
                .comment("Ticks between each bullet being loaded (tick-based mode).",
                         "20 ticks = 1 second, 10 = 0.5 s, 0 = fastest (1 tick).",
                         "Range: 0 â€“ 60")
                .defineInRange("load_ticks", 5, 0, 60);

        UNLOAD_TICKS = b
                .comment("Ticks between each bullet being unloaded (tick-based mode).",
                         "Same scale as load_ticks.")
                .defineInRange("unload_ticks", 5, 0, 60);

        b.pop();
        b.push("extended_magazines");

        ALLOW_EXTENDED_WITHOUT_ATTACHMENT = b
                .comment("When true, extended magazines can be loaded into a gun even if the",
                         "extended-mag attachment is not installed on that gun.",
                         "The gun will also visually display the extended mag bone and use the",
                         "correct extended capacity as if the attachment were present.",
                         "Only applies to guns that use the magazine system.")
                .define("allow_extended_without_attachment", false);

        b.pop();
        b.push("hud");

        OVERRIDE_AMMO_HUD = b
                .comment("When true, replaces TaCZ's reserve-ammo counter with a row of magazine silhouettes",
                         "that each show how full they are (Ready Or Not-style magazine check UI).",
                         "Only applies to guns that use the magazine system.")
                .define("override_ammo_hud", true);

        b.pop();
        b.push("in_hand_loading");

        IN_HAND_TICK_BASED = b
                .comment("When true, holding a magazine in your main hand enables tick-based loading/unloading.",
                         "  Left-click  â†’ start/stop adding bullets one per load_ticks interval.",
                         "  Right-click â†’ start/stop removing bullets one per unload_ticks interval.",
                         "  Scrolling to another slot or dropping the magazine cancels the session.",
                         "  Progress appears as a ring on the hotbar slot icon, not the cursor.",
                         "  Uses the same load_ticks / unload_ticks intervals as inventory tick-based mode.",
                         "  Block breaking and arm-swing are suppressed whenever a magazine is held.")
                .define("in_hand_tick_based", true);

        b.pop();
        b.push("ammo_boxes");

        MAGAZINES_IN_AMMO_BOXES = b
                .comment("Allow magazine items to be placed inside TaCZ ammo boxes.",
                         "Right-click a magazine onto a box to store it; right-click the box",
                         "onto an empty inventory slot to take a stored magazine back out.")
                .define("store_magazines", true);

        LOAD_MAGAZINES_FROM_AMMO_BOXES = b
                .comment("Allow magazines to take compatible loose rounds from TaCZ ammo boxes.",
                         "Creative ammo boxes are treated as infinite sources.")
                .define("supply_bullets_to_magazines", true);

        SEPARATE_AMMO_BOX_CONTENTS = b
                .comment("When true, bullets and magazines cannot share the same ammo box.",
                         "A box containing bullets rejects magazines, and a box containing",
                         "magazines rejects bullets. Set false to share the box's stack slots.")
                .define("separate_magazines_and_ammo", true);

        PREFER_PLAYER_INVENTORY = b
                .comment("When true, loose ammo and loose magazines in the player inventory",
                         "are used before ammo-box contents. Set false to drain boxes first.")
                .define("prefer_player_inventory", true);

        b.pop();
        b.push("durability");

        ENABLE_DURABILITY = b
                .comment("Enable or disable the Tarkov-style durability, degradation and jamming (JAM) system.")
                .define("enable_durability", true);

        DURABILITY_LOSS_PER_SHOT = b
                .comment("Base durability lost per bullet fired (0-100 scale). Default: 0.1% (~1000 shots per gun).")
                .defineInRange("durability_loss_per_shot", 0.1, 0.0, 10.0);

        JAM_SAFETY_THRESHOLD = b
                .comment("Durability percentage above which weapon has 0% chance of jamming and 100% stats (Tarkov threshold).")
                .defineInRange("jam_safety_threshold", 90.0, 0.0, 100.0);

        MAX_JAM_CHANCE = b
                .comment("Maximum jam probability when weapon durability is at 0%. Default: 0.25 (25% chance per shot).")
                .defineInRange("max_jam_chance", 0.25, 0.0, 1.0);

        STAT_PENALTY_VELOCITY_MAX = b
                .comment("Maximum bullet velocity penalty when durability is 0% (e.g. 0.20 = -20% bullet velocity).")
                .defineInRange("stat_penalty_velocity_max", 0.20, 0.0, 1.0);

        STAT_PENALTY_DAMAGE_MAX = b
                .comment("Maximum bullet damage penalty when durability is 0% (e.g. 0.20 = -20% damage).")
                .defineInRange("stat_penalty_damage_max", 0.20, 0.0, 1.0);

        STAT_PENALTY_SPREAD_MAX = b
                .comment("Maximum bullet spread / inaccuracy multiplier when durability is 0% (e.g. 2.5 = 250% spread).")
                .defineInRange("stat_penalty_spread_max", 2.5, 1.0, 10.0);

        CLEANING_MAX_CAP_LOSS_REGULAR = b
                .comment("Base permanent max durability cap loss when cleaning a regularly maintained gun (>80% durability). Default: 2.5%")
                .defineInRange("cleaning_max_cap_loss_regular", 2.5, 0.0, 50.0);

        CLEANING_MAX_CAP_LOSS_HEAVY = b
                .comment("Base permanent max durability cap loss when cleaning a heavily worn gun (<40% durability). Default: 8.0%")
                .defineInRange("cleaning_max_cap_loss_heavy", 8.0, 0.0, 50.0);

        CLEANING_RESTORE_WEAR_FACTOR = b
                .comment("Permanent max cap degradation factor based on repaired durability (e.g. 0.10 = 10% of restored durability permanently reduces max cap).")
                .defineInRange("cleaning_restore_wear_factor", 0.10, 0.0, 1.0);

        DROP_JAMMED_BULLET = b
                .comment("Whether unjamming a jammed weapon drops the ejected round on the ground.")
                .define("drop_jammed_bullet", true);

        b.pop();
        SPEC = b.build();
    }

    public static void register() {
        ForgeConfigRegistry.INSTANCE.register(TaCZMagazines.MODID, ModConfig.Type.COMMON, SPEC, "taczmagazines-mechanics.toml");
    }

    // Effective load interval â€” always at least 1 tick so we don't spam packets.
    public static int effectiveLoadTicks() {
        return Math.max(1, LOAD_TICKS.get());
    }

    public static int effectiveUnloadTicks() {
        return Math.max(1, UNLOAD_TICKS.get());
    }
}

