package dev.emi.emi.registry;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

public class EmiRecipeSorter {
    private static final Reference2ObjectOpenHashMap<EmiRecipe, IntArrayList> inputCache = new Reference2ObjectOpenHashMap<>();
    private static final Reference2ObjectOpenHashMap<EmiRecipe, IntArrayList> outputCache = new Reference2ObjectOpenHashMap<>();

    public static IntList getInput(EmiRecipe recipe) {
        IntArrayList list = inputCache.get(recipe);
        if (list == null) {
            list = bakedList(recipe.getInputs());
            inputCache.put(recipe, list);
        }
        return list;
    }

    public static IntList getOutput(EmiRecipe recipe) {
        IntArrayList list = outputCache.get(recipe);
        if (list == null) {
            list = bakedList(recipe.getOutputs());
            outputCache.put(recipe, list);
        }
        return list;
    }

    public static void clear() {
        inputCache.clear();
        outputCache.clear();
    }

    private static IntArrayList bakedList(List<? extends EmiIngredient> stacks) {
        var list = new IntArrayList(stacks.size());
        for (EmiIngredient stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            int value = EmiStackList.getIndex(stack.getEmiStacks().get(0));
            list.add(value);
        }
        return list;
    }
}
