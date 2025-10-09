package cn.sh1rocu.tacz.compat.rei.display;

import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;

import java.util.List;

public class GunSmithTableDisplay implements Display {
    private final GunSmithTableRecipe recipe;
    private final CategoryIdentifier<GunSmithTableDisplay> id;

    public GunSmithTableDisplay(GunSmithTableRecipe recipe, CategoryIdentifier<GunSmithTableDisplay> id) {
        this.recipe = recipe;
        this.id = id;
    }

    public GunSmithTableRecipe getRecipe() {
        return recipe;
    }

    @Override
    public List<EntryIngredient> getInputEntries() {
        return EntryIngredients.ofIngredients(recipe.getInputs().stream().map(GunSmithTableIngredient::getIngredient).toList());
    }

    @Override
    public List<EntryIngredient> getOutputEntries() {
        return List.of(EntryIngredients.of(recipe.getOutput()));
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return id;
    }
}