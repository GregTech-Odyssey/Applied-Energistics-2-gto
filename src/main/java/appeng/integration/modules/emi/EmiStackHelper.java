package appeng.integration.modules.emi;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

public final class EmiStackHelper {
    private EmiStackHelper() {
    }

    private static GenericStack toGenericStack(Object key, CompoundTag nbt, long amount) {
        if (key instanceof Item item) {
            return new GenericStack(AEItemKey.of(item, nbt), amount);
        } else if (key instanceof Fluid fluid) {
            return new GenericStack(AEFluidKey.of(fluid, nbt), amount);
        }
        return null;
    }

    @Nullable
    public static GenericStack toGenericStack(@NotNull EmiStack emiStack) {
        if (emiStack.isEmpty())
            return null;
        return toGenericStack(emiStack.getKey(), emiStack.getNbt(), emiStack.getAmount());
    }

    public static List<GenericStack> toGenericStack(@NotNull EmiIngredient emiIngredient) {
        if (emiIngredient.isEmpty()) {
            return Collections.emptyList();
        }
        var originAmount = emiIngredient.getAmount();
        return emiIngredient.getEmiStacks().stream().map(s -> toGenericStack(s.getKey(), s.getNbt(), originAmount))
                .filter(Objects::nonNull).toList();
    }

    @Nullable
    public static EmiStack toEmiStack(@NotNull GenericStack stack) {
        AEKey key = stack.what();
        if (key instanceof AEItemKey itemKey) {
            return EmiStack.of(itemKey.getItem(), itemKey.getTag(), stack.amount());
        } else if (key instanceof AEFluidKey fluidKey) {
            return EmiStack.of(fluidKey.getFluid(), fluidKey.getTag(), stack.amount());
        }
        return null;
    }

    public static List<List<GenericStack>> ofInputs(EmiRecipe emiRecipe) {
        return emiRecipe.getInputs().stream().map(EmiStackHelper::toGenericStack).toList();
    }

    public static List<GenericStack> ofOutputs(EmiRecipe emiRecipe) {
        return emiRecipe.getOutputs().stream()
                .map(EmiStackHelper::toGenericStack)
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<GenericStack> of(EmiIngredient emiIngredient) {
        if (emiIngredient.isEmpty())
            return Collections.emptyList();

        return emiIngredient.getEmiStacks()
                .stream()
                .map(EmiStackHelper::toGenericStack)
                .filter(Objects::nonNull)
                .map(x -> new GenericStack(x.what(), emiIngredient.getAmount()))
                .toList();
    }
}
