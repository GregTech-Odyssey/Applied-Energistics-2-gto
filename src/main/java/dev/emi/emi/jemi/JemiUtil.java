package dev.emi.emi.jemi;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.Sets;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.forge.EmiForgePlatform;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class JemiUtil {

    public static EmiIngredient getIngredient(List<ITypedIngredient<?>> ingredients) {
        if (ingredients.isEmpty()) {
            return EmiStack.EMPTY;
        }
        var stacks = new ObjectArrayList<EmiStack>(ingredients.size());
        for (var ingredient : ingredients) {
            var stack = getStack(ingredient);
            if (!stack.isEmpty())
                stacks.add(stack);
        }
        return EmiIngredient.of(stacks);
    }

    public static EmiStack getStack(Object ingredient) {
        Optional<IIngredientType> optional = (Optional<IIngredientType>) (Optional) JemiPlugin.runtime
                .getIngredientManager()
                .getIngredientTypeChecked(ingredient);
        if (optional.isPresent()) {
            return getStack(optional.get(), ingredient);
        }
        return EmiStack.EMPTY;
    }

    public static EmiStack getStack(ITypedIngredient<?> ingredient) {
        return getStack(ingredient.getType(), ingredient.getIngredient());
    }

    public static EmiStack getStack(IIngredientType<?> type, Object ingredient) {
        if (type == VanillaTypes.ITEM_STACK) {
            return EmiStack.of((ItemStack) ingredient);
        } else if (type == getFluidType()) {
            return getFluidFromJei(ingredient);
        } else {
            IIngredientManager im = JemiPlugin.runtime.getIngredientManager();
            IIngredientHelper helper = im.getIngredientHelper(type);
            if (helper.isValidIngredient(ingredient)) {
                return new JemiStack(type, helper, im.getIngredientRenderer(type), ingredient);
            }
        }
        return EmiStack.EMPTY;
    }

    public static Optional<ITypedIngredient<?>> getTyped(EmiStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        } else if (stack.getKey() instanceof Fluid f) {
            int amount = stack.getAmount() <= 0 ? 1000 : (int) Math.min(Integer.MAX_VALUE, stack.getAmount());
            return (Optional) JemiPlugin.runtime.getIngredientManager().createTypedIngredient(ForgeTypes.FLUID_STACK,
                    new FluidStack(f, amount, stack.getNbt()));
        } else if (stack instanceof JemiStack js) {
            return (Optional) JemiPlugin.runtime.getIngredientManager().createTypedIngredient(js.getType(),
                    js.ingredient);
        }
        return (Optional) JemiPlugin.runtime.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK,
                stack.getItemStack());
    }

    public static EmiStack getFluidFromJei(Object object) {
        if (object instanceof FluidStack fluid) {
            return EmiStack.of(fluid.getFluid(), fluid.getTag(), fluid.getAmount());
        }
        return EmiStack.EMPTY;
    }

    public static IIngredientType getFluidType() {
        return ForgeTypes.FLUID_STACK;
    }

    public static Set<String> getHandledMods() {
        Set<String> set = Sets.newHashSet();
        set.addAll(EmiForgePlatform.getModsWithPlugins());
        return set;
    }
}
