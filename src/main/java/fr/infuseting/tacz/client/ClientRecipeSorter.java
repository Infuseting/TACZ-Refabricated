package fr.infuseting.tacz.client;

import fr.infuseting.tacz.TaCZMagazines;
import fr.infuseting.tacz.config.GunOverrideConfig;
import fr.infuseting.tacz.crafting.GunsmithIntegration;
import fr.infuseting.tacz.magazine.MagazineFamilySystem;
import com.tacz.guns.init.ModRecipe;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

import java.lang.reflect.Field;
import java.util.*;

@Environment(EnvType.CLIENT)
public class ClientRecipeSorter {

    public static void onRecipesUpdated(RecipeManager rm) {
        MagazineFamilySystem.discoverMagazineFamilies();
        GunOverrideConfig.apply();
        GunsmithIntegration.injectTabClientSide();

        if (MagazineFamilySystem.getAllFamilies().isEmpty()) {
            TaCZMagazines.LOGGER.info("[ClientRecipeSorter] No families yet â€” scheduling deferred discovery");
            MagazineLoadingHandler.scheduleDeferredDiscovery();
        }

        if (rm == null) return;

        try {
            Field recipesField = RecipeManager.class.getDeclaredField("recipes");
            Field byNameField = RecipeManager.class.getDeclaredField("byName");
            recipesField.setAccessible(true);
            byNameField.setAccessible(true);

            // --- Sort the byName map (ResourceLocation -> Recipe) ---
            @SuppressWarnings("unchecked")
            Map<ResourceLocation, Recipe<?>> byName =
                    (Map<ResourceLocation, Recipe<?>>) byNameField.get(rm);

            List<Map.Entry<ResourceLocation, Recipe<?>>> byNameEntries = new ArrayList<>(byName.entrySet());
            byNameEntries.sort(Comparator.comparing(e -> e.getKey().getPath()));
            Map<ResourceLocation, Recipe<?>> sortedByName = new LinkedHashMap<>();
            byNameEntries.forEach(e -> sortedByName.put(e.getKey(), e.getValue()));
            byNameField.set(rm, sortedByName);

            // --- Sort the recipes map (RecipeType -> Map<ResourceLocation, Recipe>) ---
            @SuppressWarnings("unchecked")
            Map<Object, Map<ResourceLocation, Recipe<?>>> recipes =
                    (Map<Object, Map<ResourceLocation, Recipe<?>>>) recipesField.get(rm);

            Map<Object, Map<ResourceLocation, Recipe<?>>> mutableRecipes = new LinkedHashMap<>();
            for (Map.Entry<Object, Map<ResourceLocation, Recipe<?>>> typeEntry : recipes.entrySet()) {
                if (typeEntry.getKey().equals(ModRecipe.GUN_SMITH_TABLE_CRAFTING)) {
                    List<Map.Entry<ResourceLocation, Recipe<?>>> entries =
                            new ArrayList<>(typeEntry.getValue().entrySet());
                    entries.sort(Comparator.comparing(e -> e.getKey().getPath()));
                    Map<ResourceLocation, Recipe<?>> sorted = new LinkedHashMap<>();
                    entries.forEach(e -> sorted.put(e.getKey(), e.getValue()));
                    mutableRecipes.put(typeEntry.getKey(), sorted);
                } else {
                    mutableRecipes.put(typeEntry.getKey(), typeEntry.getValue());
                }
            }
            recipesField.set(rm, mutableRecipes);

            long ourCount = sortedByName.keySet().stream()
                    .filter(k -> k.getNamespace().equals("tacz")).count();
            TaCZMagazines.LOGGER.info("[ClientRecipeSorter] Sorted client recipes, {} magazine entries", ourCount);

        } catch (Exception e) {
            TaCZMagazines.LOGGER.warn("[ClientRecipeSorter] Failed: {}", e.getMessage());
        }
    }
}
