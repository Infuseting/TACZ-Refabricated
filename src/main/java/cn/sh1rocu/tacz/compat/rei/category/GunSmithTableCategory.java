package cn.sh1rocu.tacz.compat.rei.category;

import cn.sh1rocu.tacz.compat.rei.display.GunSmithTableDisplay;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class GunSmithTableCategory implements DisplayCategory<GunSmithTableDisplay> {
    private final Component title;
    private final Renderer icon;
    private final CategoryIdentifier<GunSmithTableDisplay> id;

    public GunSmithTableCategory(Component title, ItemStack icon, CategoryIdentifier<GunSmithTableDisplay> id) {
        this.title = title;
        this.icon = EntryStacks.of(icon);
        this.id = id;
    }

    @Override
    public List<Widget> setupDisplay(GunSmithTableDisplay display, Rectangle bounds) {
        List<EntryIngredient> inputs = display.getInputEntries();
        List<EntryIngredient> outputs = display.getOutputEntries();

        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));

        int startX = bounds.x + 5;
        int startY = bounds.y;

        widgets.add(Widgets.createSlot(new Point(startX + 3, startY + 12)).entries(outputs.get(0)).markOutput());

        int size = inputs.size();
        // 单行排布
        if (size < 7) {
            for (int i = 0; i < size; i++) {
                int xOffset = startX + 35 + 20 * i;
                int yOffset = startY + 12;
                widgets.add(Widgets.createSlot(new Point(xOffset, yOffset)).entries(getInput(inputs, i)).markInput());
            }
        }
        // 双行排布
        else {
            for (int i = 0; i < 6; i++) {
                int xOffset = startX + 35 + 20 * i;
                int yOffset = startY + 2;
                widgets.add(Widgets.createSlot(new Point(xOffset, yOffset)).entries(getInput(inputs, i)).markInput());
            }
            for (int i = 6; i < size; i++) {
                int xOffset = startX + 35 + 20 * (i - 6);
                int yOffset = startY + 22;
                widgets.add(Widgets.createSlot(new Point(xOffset, yOffset)).entries(getInput(inputs, i)).markInput());
            }
        }

        return widgets;
    }

    private EntryIngredient getInput(List<EntryIngredient> inputs, int index) {
        if (index < inputs.size()) {
            return inputs.get(index);
        }
        return EntryIngredient.empty();
    }

    @Override
    public CategoryIdentifier<? extends GunSmithTableDisplay> getCategoryIdentifier() {
        return id;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public Renderer getIcon() {
        return icon;
    }

    @Override
    public int getDisplayHeight() {
        return 40;
    }

    @Override
    public int getDisplayWidth(GunSmithTableDisplay display) {
        return 160;
    }
}
