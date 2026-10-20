package appeng.integration.modules.emi;

import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.FluidEmiStack;
import dev.emi.emi.api.stack.ItemEmiStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

public final class EmiStackHelper {
    private EmiStackHelper() {
    }

    @Nullable
    public static AEKey toAEKey(@NotNull EmiStack stack) {
        if (stack.isEmpty())
            return null;
        var key = stack.getAEKey();
        if (key != null)
            return key;
        if (stack.getKey() instanceof Item item)
            return AEItemKey.of(item, stack.getNbt());
        if (stack.getKey() instanceof Fluid fluid)
            return AEFluidKey.of(fluid, stack.getNbt());
        return null;
    }

    @Nullable
    public static GenericStack toGenericStack(@NotNull EmiStack emiStack) {
        var key = toAEKey(emiStack);
        return key == null ? null : new GenericStack(key, emiStack.getAmount());
    }

    public static List<GenericStack> toGenericStack(@NotNull EmiIngredient ingredient) {
        if (ingredient.isEmpty())
            return Collections.emptyList();
        var stacks = ingredient.getEmiStacks();
        var result = new ObjectArrayList<GenericStack>(stacks.size());
        long amount = ingredient.getAmount();
        for (var stack : stacks) {
            var key = toAEKey(stack);
            if (key != null)
                result.add(new GenericStack(key, amount));
        }
        return result;
    }

    @Nullable
    public static EmiStack toEmiStack(@NotNull GenericStack stack) {
        AEKey key = stack.what();
        if (key instanceof AEItemKey itemKey) {
            return new ItemEmiStack(itemKey, stack.amount());
        } else if (key instanceof AEFluidKey fluidKey) {
            return new FluidEmiStack(fluidKey, stack.amount());
        }
        return null;
    }

    public static List<List<GenericStack>> ofInputs(EmiRecipe recipe) {
        var inputs = recipe.getInputs();
        var result = new ObjectArrayList<List<GenericStack>>(inputs.size());
        for (var input : inputs)
            result.add(toGenericStack(input));
        return result;
    }

    public static List<GenericStack> ofOutputs(EmiRecipe recipe) {
        var outputs = recipe.getOutputs();
        var result = new ObjectArrayList<GenericStack>(outputs.size());
        for (var output : outputs) {
            var converted = toGenericStack(output);
            if (converted != null)
                result.add(converted);
        }
        return result;
    }

}
