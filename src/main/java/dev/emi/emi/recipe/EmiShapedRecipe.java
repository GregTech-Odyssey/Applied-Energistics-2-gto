package dev.emi.emi.recipe;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiLog;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import appeng.api.stacks.AEItemKey;
import java.util.List;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class EmiShapedRecipe extends EmiCraftingRecipe {

    public EmiShapedRecipe(ShapedRecipe recipe) {
        super(padIngredients(recipe), EmiStack.of(EmiPort.getOutput(recipe)), EmiPort.getId(recipe), false);
        setRemainders(input, recipe);
    }

    public static void setRemainders(List<EmiIngredient> input, CraftingRecipe recipe) {
        try {
            // Exact classes only: subclasses may override getRemainingItems or depend on other slots.
            if (recipe.getClass() == ShapedRecipe.class || recipe.getClass() == ShapelessRecipe.class) {
                for (var ingredient : input) {
                    if (ingredient.isEmpty())
                        continue;
                    for (var stack : ingredient.getEmiStacks()) {
                        if (!hasCraftingRemainingItem(stack))
                            continue;
                        var remainder = stack.getItemStack().getCraftingRemainingItem();
                        if (!remainder.isEmpty())
                            stack.setRemainder(EmiStack.of(remainder));
                    }
                }
                return;
            }
            var inv = EmiUtil.getCraftingInventory();
            var size = input.size();
            for (int i = 0; i < size; i++) {
                var ingredient = input.get(i);
                if (ingredient.isEmpty())
                    continue;
                var stacks = ingredient.getEmiStacks();
                boolean hasPossibleRemainder = false;
                for (var st : stacks) {
                    if (hasCraftingRemainingItem(st)) {
                        hasPossibleRemainder = true;
                        break;
                    }
                }
                if (!hasPossibleRemainder) {
                    continue;
                }
                for (int j = 0; j < size; j++) {
                    if (j == i)
                        continue;
                    ingredient = input.get(j);
                    if (!ingredient.isEmpty()) {
                        inv.setItem(j, ingredient.getEmiStacks().getFirst().getItemStack());
                    }
                }
                for (var stack : stacks) {
                    inv.setItem(i, stack.getItemStack());
                    var remainder = recipe.getRemainingItems(inv).get(i);
                    if (!remainder.isEmpty()) {
                        stack.setRemainder(EmiStack.of(remainder));
                    }
                }
                inv.clearContent();
            }
        } catch (Exception e) {
            EmiLog.error("Exception thrown setting remainders for " + EmiPort.getId(recipe), e);
        }
    }

    private static boolean hasCraftingRemainingItem(EmiStack stack) {
        // The shared key stack has count one; count-sensitive queries need the actual amount.
        if (stack.getAEKey() instanceof AEItemKey key) {
            return key.getReadOnlyStack().hasCraftingRemainingItem();
        }
        return false;
    }

    private static List<EmiIngredient> padIngredients(ShapedRecipe recipe) {
        var list = new ObjectArrayList<EmiIngredient>(9);
        int i = 0;
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                if (x >= recipe.getWidth() || y >= recipe.getHeight() || i >= recipe.getIngredients().size()) {
                    list.add(EmiStack.EMPTY);
                } else {
                    list.add(EmiIngredient.of(recipe.getIngredients().get(i++)));
                }
            }
        }
        return list;
    }
}
