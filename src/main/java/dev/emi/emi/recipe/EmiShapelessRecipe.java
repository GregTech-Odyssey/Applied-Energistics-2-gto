package dev.emi.emi.recipe;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class EmiShapelessRecipe extends EmiCraftingRecipe {

    public EmiShapelessRecipe(ShapelessRecipe recipe) {
        super(convertIngredients(recipe),
                EmiStack.of(EmiPort.getOutput(recipe)), EmiPort.getId(recipe));
        EmiShapedRecipe.setRemainders(input, recipe);
    }

    private static ObjectArrayList<EmiIngredient> convertIngredients(ShapelessRecipe recipe) {
        var ingredients = recipe.getIngredients();
        var inputs = new ObjectArrayList<EmiIngredient>(ingredients.size());
        for (var ingredient : ingredients)
            inputs.add(EmiIngredient.of(ingredient));
        return inputs;
    }

    @Override
    public boolean canFit(int width, int height) {
        return input.size() <= width * height;
    }
}
