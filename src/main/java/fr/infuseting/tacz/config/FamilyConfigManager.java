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

// Controls which gun model is used to represent each magazine family.
public class FamilyConfigManager {

    private static final Path CONFIG_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("taczmagazines-families.toml");

    private static final ForgeConfigSpec SPEC;
    @SuppressWarnings("unused")
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> OVERRIDES_VALUE;

    private static final Map<String, ResourceLocation> RESOLVED = new LinkedHashMap<>();
    private static final List<String> CONFIG_ERRORS = new ArrayList<>();

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("family_models")
               .comment("Controls which gun's magazine model is used to represent each magazine family.")
               .comment("")
               .comment("Format:  \"family_id = modid:gun_id\"")
               .comment("Example: \"9x19mm_17 = tacz:m9\"")
               .comment("")
               .comment("The gun must be compatible with that family.")
               .comment("If no override is set for a family, the first alphabetically-sorted gun is used.")
               .comment("Family IDs are printed to the log on startup (search 'Discovered magazine family').")
               .comment("Changes take effect after F3+T.");
        OVERRIDES_VALUE = builder.defineListAllowEmpty(List.of("overrides"), Collections::emptyList, e -> e instanceof String);
        builder.pop();
        SPEC = builder.build();
    }

    public static void register() {
        ForgeConfigRegistry.INSTANCE.register(TaCZMagazines.MODID, ModConfig.Type.COMMON, SPEC, "taczmagazines-families.toml");
    }

    public static void load() {
        CONFIG_ERRORS.clear();
        Map<String, String> stored = readFromDisk();
        buildResolved(stored);
    }

    public static ResourceLocation getOverride(String familyId) {
        return RESOLVED.get(familyId);
    }

    public static List<String> getErrors() {
        return Collections.unmodifiableList(CONFIG_ERRORS);
    }

    // â”€â”€ Disk reading â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private static final Pattern OVERRIDES_KEY =
            Pattern.compile("^\\s*overrides\\s*=\\s*\\[", Pattern.MULTILINE);

    private static Map<String, String> readFromDisk() {
        if (!Files.exists(CONFIG_FILE)) return new LinkedHashMap<>();
        try {
            String content = Files.readString(CONFIG_FILE, StandardCharsets.UTF_8);
            Matcher m = OVERRIDES_KEY.matcher(content);
            if (!m.find()) return new LinkedHashMap<>();
            int arrayStart = m.end() - 1;
            int arrayEnd   = content.indexOf(']', arrayStart);
            if (arrayEnd < 0) return new LinkedHashMap<>();
            return parseArrayContent(content.substring(arrayStart + 1, arrayEnd));
        } catch (IOException e) {
            TaCZMagazines.LOGGER.warn("[FamilyConfig] Could not read config file: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private static Map<String, String> parseArrayContent(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String item : raw.split(",\\s*")) {
            item = item.strip();
            if (item.startsWith("\"")) item = item.substring(1);
            if (item.endsWith("\""))   item = item.substring(0, item.length() - 1);
            int eq = item.indexOf('=');
            if (eq < 0) {
                if (!item.isEmpty()) {
                    addError("families.toml [family_models]: \"" + item
                           + "\" â€” missing '='.  Expected format:  family_id = modid:gun_id  "
                           + "e.g.  9x19mm_17 = tacz:m9");
                }
                continue;
            }
            String key = item.substring(0, eq).strip();
            String val = item.substring(eq + 1).strip();
            if (!key.isEmpty() && !val.isEmpty()) result.put(key, val);
        }
        return result;
    }

    private static void buildResolved(Map<String, String> stored) {
        RESOLVED.clear();
        for (Map.Entry<String, String> entry : stored.entrySet()) {
            String familyId = entry.getKey();
            String rawGun   = entry.getValue();

            if (!MagazineFamilySystem.getAllFamilies().contains(familyId)) {
                addError("families.toml [family_models]: family \"" + familyId
                       + "\" does not exist â€” check the log for valid family IDs "
                       + "(search 'Discovered magazine family')");
                continue;
            }

            ResourceLocation gunId;
            try { gunId = new ResourceLocation(rawGun); }
            catch (Exception e) {
                addError("families.toml [family_models]: \"" + rawGun
                       + "\" is not a valid gun ID for family \"" + familyId
                       + "\".  Expected format:  modid:gun_name  e.g.  tacz:m9");
                continue;
            }

            if (!MagazineFamilySystem.getCompatibleGuns(familyId).contains(gunId)) {
                addError("families.toml [family_models]: gun \"" + gunId
                       + "\" is not compatible with family \"" + familyId
                       + "\" â€” it must be in the family's gun list (check the log)");
                continue;
            }

            ResourceLocation def = MagazineFamilySystem.getDefaultRepresentativeGun(familyId);
            if (!gunId.equals(def)) {
                RESOLVED.put(familyId, gunId);
                TaCZMagazines.LOGGER.info("[FamilyConfig] '{}' â†’ '{}'", familyId, gunId);
            }
        }
    }

    private static void addError(String msg) {
        CONFIG_ERRORS.add(msg);
        TaCZMagazines.LOGGER.warn("[FamilyConfig] Config error: {}", msg);
    }
}

