package dev.emi.emi.api.recipe;

import com.google.common.collect.Lists;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A convenience type for easy implementation of {@link dev.emi.emi.api.recipe.EmiRecipe}
 */
public abstract class BasicEmiRecipe implements EmiRecipe {
    protected List<EmiIngredient> inputs = Lists.newArrayList();
    protected List<EmiIngredient> catalysts = Lists.newArrayList();
    protected List<EmiStack> outputs = Lists.newArrayList();
    protected dev.emi.emi.api.recipe.EmiRecipeCategory category;
    protected ResourceLocation id;
    protected int width, height;

    public BasicEmiRecipe(EmiRecipeCategory category, ResourceLocation id, int width, int height) {
        this.category = category;
        this.id = id;
        this.width = width;
        this.height = height;
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return category;
    }

    @Override
    public @Nullable ResourceLocation getId() {
        return id;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return inputs;
    }

    @Override
    public List<EmiIngredient> getCatalysts() {
        return catalysts;
    }

    @Override
    public List<EmiStack> getOutputs() {
        return outputs;
    }

    @Override
    public int getDisplayWidth() {
        return width;
    }

    @Override
    public int getDisplayHeight() {
        return height;
    }
}
