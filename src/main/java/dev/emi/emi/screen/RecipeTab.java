package dev.emi.emi.screen;

import com.google.common.collect.Lists;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.SidebarSide;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class RecipeTab {
    private static final int RECIPE_PADDING = 10;
    private final ObjectArrayList<RecipeDisplay> displays;
    private final int width;
    private List<List<RecipeDisplay>> pages = Lists.newArrayList();
    public final EmiRecipeCategory category;

    public RecipeTab(@NotNull EmiRecipeCategory category, @NotNull List<EmiRecipe> recipes) {
        this.category = category;
        displays = new ObjectArrayList<>(recipes.size());
        int maxWidth = 0;
        for (var recipe : recipes) {
            RecipeDisplay display;
            try {
                display = new RecipeDisplay(recipe);
            } catch (Throwable failure) {
                display = new RecipeDisplay(failure);
            }
            displays.add(display);
            maxWidth = Math.max(maxWidth, display.getWidth());
        }
        width = maxWidth;
    }

    public List<dev.emi.emi.screen.WidgetGroup> constructWidgets(int page, int x, int y, int backgroundWidth,
            int backgroundHeight) {
        var groups = new ObjectArrayList<WidgetGroup>(pages.get(page).size());
        int width = backgroundWidth - 16;
        int height = getVerticalRecipeSpace(backgroundHeight);
        int off = 0;
        for (RecipeDisplay display : pages.get(page)) {
            int wx = x + 8;
            int wy = y + 37 + off;
            groups.add(display.getWidgets(wx, wy, width, height));
            off += display.getHeight() + RECIPE_PADDING;
        }
        return groups;
    }

    private int getVerticalRecipeSpace(int backgroundHeight) {
        int height = backgroundHeight - 46;
        if (EmiConfig.workstationLocation == SidebarSide.BOTTOM) {
            if (!EmiApi.getRecipeManager().getWorkstations(category).isEmpty() || RecipeScreen.resolve != null) {
                height -= 23;
            }
        }
        return height;
    }

    public void bakePages(int height) {
        height = getVerticalRecipeSpace(height);
        pages.clear();
        List<RecipeDisplay> current = Lists.newArrayList();
        int h = 0;
        for (RecipeDisplay recipe : displays) {
            int rh = recipe.getHeight();
            if (!current.isEmpty() && h + rh > height) {
                pages.add(current);
                current = Lists.newArrayList();
                h = 0;
            }
            h += rh + RECIPE_PADDING;
            current.add(recipe);
        }
        if (!current.isEmpty()) {
            pages.add(current);
        }
    }

    public int getWidth() {
        return width;
    }

    public int getPageCount() {
        return pages.size();
    }

    public List<RecipeDisplay> getPage(int page) {
        if (page >= 0 && page < getPageCount()) {
            return pages.get(page);
        }
        return List.of();
    }
}
