package dev.emi.emi.api.recipe;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface EmiRecipeManager {
    
    List<dev.emi.emi.api.recipe.EmiRecipeCategory> getCategories();

    List<EmiIngredient> getWorkstations(EmiRecipeCategory category);

    List<dev.emi.emi.api.recipe.EmiRecipe> getRecipes();

    List<dev.emi.emi.api.recipe.EmiRecipe> getRecipes(EmiRecipeCategory category);

    @Nullable dev.emi.emi.api.recipe.EmiRecipe getRecipe(ResourceLocation id);

    List<dev.emi.emi.api.recipe.EmiRecipe> getRecipesByInput(EmiStack stack);

    List<EmiRecipe> getRecipesByOutput(EmiStack stack);
}
