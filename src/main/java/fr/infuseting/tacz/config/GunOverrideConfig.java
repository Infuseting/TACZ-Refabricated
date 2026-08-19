package fr.infuseting.tacz.config;

import fr.infuseting.tacz.TaCZMagazines;
import fr.infuseting.tacz.magazine.MagazineFamilySystem;
import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Manages per-gun magazine overrides loaded from taczmagazines-gun-overrides.toml.
public class GunOverrideConfig {

    private static final Path CONFIG_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("taczmagazines-gun-overrides.toml");

    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ENTRIES_VALUE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ISOLATED_GUNS_VALUE;

    private static final List<String> CONFIG_ERRORS = new ArrayList<>();

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("gun_overrides")
               .comment("Override which magazine family a gun uses, or exclude a gun from the magazine system.")
               .comment("")
               .comment("Format:")
               .comment("  \"modid:gun_id = family_id\"  â€” force the gun into a specific magazine family.")
               .comment("  \"modid:gun_id = none\"       â€” exclude the gun (uses TaCZ default ammo behaviour).")
               .comment("")
               .comment("Example: \"tacz:example_pistol = 9x19mm_17\"")
               .comment("Example: \"tacz:example_revolver = none\"")
               .comment("")
               .comment("Family IDs are printed to the log on startup/datapack reload")
               .comment("(search for 'Discovered magazine family').")
               .comment("Changes take effect after F3+T.");
        ENTRIES_VALUE = builder.defineListAllowEmpty(List.of("entries"), Collections::emptyList, e -> e instanceof String);
        builder.pop();

        builder.push("isolated_guns")
               .comment("Guns listed here generate their OWN private magazine family instead of sharing")
               .comment("with other guns of the same ammo type and capacity.")
               .comment("Their magazine item will use that gun's own 3D model for rendering.")
               .comment("")
               .comment("Format: [\"modid:gun_name\", \"modid:gun_name2\", ...]")
               .comment("Example: [\"tacz:vector45\", \"tacz:m1911\"]")
               .comment("")
               .comment("Changes take effect after F3+T.");
        ISOLATED_GUNS_VALUE = builder.defineListAllowEmpty(List.of("guns"), Collections::emptyList, e -> e instanceof String);
        builder.pop();

        SPEC = builder.build();
    }

    public static void register() {
        ForgeConfigRegistry.INSTANCE.register(TaCZMagazines.MODID, ModConfig.Type.COMMON, SPEC, "taczmagazines-gun-overrides.toml");
    }

    public static void apply() {
        CONFIG_ERRORS.clear();
        applyEntries();
        applyIsolatedGuns();
    }

    public static List<String> getErrors() {
        return Collections.unmodifiableList(CONFIG_ERRORS);
    }

    // â”€â”€ [gun_overrides] entries â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private static void applyEntries() {
        Map<String, String> entries = readKeyValueSection(ENTRIES_PATTERN);
        for (Map.Entry<String, String> kv : entries.entrySet()) {
            String rawKey = kv.getKey();
            String value  = kv.getValue();

            if (!rawKey.contains(":")) {
                addError("gun-overrides.toml [gun_overrides]: \"" + rawKey + " = " + value + "\" â€” "
                       + "invalid gun ID (missing namespace).  Expected:  modid:gun_name = family_id  "
                       + "e.g.  tacz:m9 = none");
                continue;
            }

            ResourceLocation gunId;
            try { gunId = new ResourceLocation(rawKey); }
            catch (Exception ex) {
                addError("gun-overrides.toml [gun_overrides]: \"" + rawKey + "\" â€” not a valid resource location");
                continue;
            }

            if (value.equalsIgnoreCase("none") || value.equalsIgnoreCase("false")
                    || value.equalsIgnoreCase("exclude")) {
                MagazineFamilySystem.excludeGun(gunId);
                TaCZMagazines.LOGGER.info("[GunOverride] '{}' excluded from magazine system", gunId);
            } else {
                if (!MagazineFamilySystem.getAllFamilies().contains(value)) {
                    addError("gun-overrides.toml [gun_overrides]: family \"" + value
                           + "\" not found for gun \"" + gunId
                           + "\" â€” check the log for valid family IDs (search 'Discovered magazine family')");
                    continue;
                }
                MagazineFamilySystem.overrideGunFamily(gunId, value);
                TaCZMagazines.LOGGER.info("[GunOverride] '{}' â†’ family '{}'", gunId, value);
            }
        }
    }

    // â”€â”€ [isolated_guns] guns â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private static void applyIsolatedGuns() {
        List<String> rawList = readStringListSection(ISOLATED_GUNS_PATTERN);
        Set<ResourceLocation> isolatedGuns = new LinkedHashSet<>();

        for (String raw : rawList) {
            if (raw.isEmpty()) continue;
            if (!raw.contains(":")) {
                addError("gun-overrides.toml [isolated_guns]: \"" + raw + "\" â€” "
                       + "invalid gun ID (missing namespace).  Expected:  \"modid:gun_name\"  "
                       + "e.g.  \"tacz:vector45\"");
                continue;
            }
            try {
                isolatedGuns.add(new ResourceLocation(raw));
            } catch (Exception ex) {
                addError("gun-overrides.toml [isolated_guns]: \"" + raw + "\" â€” not a valid resource location");
            }
        }

        if (!isolatedGuns.isEmpty()) {
            MagazineFamilySystem.applyIsolatedGuns(isolatedGuns);
        }
    }

    private static void addError(String msg) {
        CONFIG_ERRORS.add(msg);
        TaCZMagazines.LOGGER.warn("[GunOverride] Config error: {}", msg);
    }

    // â”€â”€ Disk reading â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private static final Pattern ENTRIES_PATTERN =
            Pattern.compile("^\\s*entries\\s*=\\s*\\[", Pattern.MULTILINE);
    private static final Pattern ISOLATED_GUNS_PATTERN =
            Pattern.compile("^\\s*guns\\s*=\\s*\\[", Pattern.MULTILINE);

    private static Map<String, String> readKeyValueSection(Pattern keyPattern) {
        if (!Files.exists(CONFIG_FILE)) return new LinkedHashMap<>();
        try {
            String content = Files.readString(CONFIG_FILE, StandardCharsets.UTF_8);
            Matcher m = keyPattern.matcher(content);
            if (!m.find()) return new LinkedHashMap<>();
            int start = m.end() - 1;
            int end   = content.indexOf(']', start);
            if (end < 0) return new LinkedHashMap<>();
            return parseKeyValues(content.substring(start + 1, end));
        } catch (IOException e) {
            TaCZMagazines.LOGGER.warn("[GunOverride] Could not read config file: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private static List<String> readStringListSection(Pattern keyPattern) {
        if (!Files.exists(CONFIG_FILE)) return new ArrayList<>();
        try {
            String content = Files.readString(CONFIG_FILE, StandardCharsets.UTF_8);
            Matcher m = keyPattern.matcher(content);
            if (!m.find()) return new ArrayList<>();
            int start = m.end() - 1;
            int end   = content.indexOf(']', start);
            if (end < 0) return new ArrayList<>();
            return parseStringList(content.substring(start + 1, end));
        } catch (IOException e) {
            TaCZMagazines.LOGGER.warn("[GunOverride] Could not read config file: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private static Map<String, String> parseKeyValues(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String item : raw.split(",\\s*")) {
            item = stripQuotes(item.strip());
            int eq = item.indexOf('=');
            if (eq < 0) {
                if (!item.isEmpty()) {
                    addError("gun-overrides.toml [gun_overrides]: \"" + item
                           + "\" â€” missing '='.  Expected format:  modid:gun_id = family_id  "
                           + "or  modid:gun_id = none");
                }
                continue;
            }
            String key = item.substring(0, eq).strip();
            String val = item.substring(eq + 1).strip();
            if (!key.isEmpty() && !val.isEmpty()) result.put(key, val);
        }
        return result;
    }

    private static List<String> parseStringList(String raw) {
        List<String> result = new ArrayList<>();
        for (String item : raw.split(",\\s*")) {
            item = stripQuotes(item.strip());
            if (!item.isEmpty()) result.add(item);
        }
        return result;
    }

    private static String stripQuotes(String s) {
        if (s.startsWith("\"")) s = s.substring(1);
        if (s.endsWith("\""))   s = s.substring(0, s.length() - 1);
        return s;
    }
}

