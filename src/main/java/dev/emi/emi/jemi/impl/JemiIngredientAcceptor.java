package dev.emi.emi.jemi.impl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluid;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.jemi.JemiConversionCache;
import dev.emi.emi.jemi.JemiStack;
import dev.emi.emi.jemi.JemiUtil;
import dev.emi.emi.jemi.impl.JemiRecipeSlot.IngredientRenderer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.ingredient.IRecipeSlotTooltipCallback;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;

public class JemiIngredientAcceptor implements IIngredientAcceptor<JemiIngredientAcceptor> {
    public static final Pattern FLUID_END = Pattern.compile("(^|\\s)([\\d,]+)\\s*mB$");
    public final RecipeIngredientRole role;
    public final ObjectArrayList<EmiStack> stacks = new ObjectArrayList<>(1);
    private final @Nullable JemiConversionCache conversions;

    public JemiIngredientAcceptor(RecipeIngredientRole role) {
        this(role, null);
    }

    public JemiIngredientAcceptor(RecipeIngredientRole role, @Nullable JemiConversionCache conversions) {
        this.role = role;
        this.conversions = conversions;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void coerceStacks(IRecipeSlotTooltipCallback tooltipCallback,
            Map<IIngredientType<?>, IngredientRenderer<?>> renderers) {
        if (tooltipCallback == null && renderers == null) {
            return;
        }
        for (EmiStack stack : stacks) {
            if (!(stack instanceof JemiStack) && !(stack.getKey() instanceof Fluid))
                continue;
            ITypedIngredient typed = JemiUtil.getTyped(stack).orElse(null);
            if (typed != null) {
                var base = new ObjectArrayList<Component>(2);
                var renderer = renderers == null ? null : renderers.get(typed.getType());
                if (renderer != null)
                    base.addAll(((IngredientRenderer) renderer).renderer().getTooltip(typed.getIngredient(),
                            TooltipFlag.Default.NORMAL));
                if (base.isEmpty()) {
                    if (tooltipCallback == null) {
                        continue;
                    }
                    base.add(stack.getName());
                    base.add(EmiPort.literal(""));
                }
                if (tooltipCallback != null) {
                    JemiRecipeSlot jsr = new JemiRecipeSlot(role, stack);
                    tooltipCallback.onTooltip(jsr, base);
                }
                for (int i = 0; i < 2 && i < base.size(); i++) {
                    Component t = base.get(i);
                    if (t != null) {
                        Matcher m = FLUID_END.matcher(t.getString());
                        if (m.find()) {
                            long amount = Long.parseLong(m.group(2).replace(",", ""));
                            if (amount != stack.getAmount()) {
                                stack.setAmount(amount);
                            }
                        }
                    }
                }
            }
        }
    }

    public EmiIngredient build() {
        return conversions == null ? EmiIngredient.of(stacks) : conversions.getIngredient(stacks);
    }

    private void addStack(EmiStack stack) {
        if (!stack.isEmpty()) {
            stacks.add(stack);
        }
    }

    @Override
    public <I> JemiIngredientAcceptor addIngredients(IIngredientType<I> ingredientType, List<@Nullable I> ingredients) {
        for (I i : ingredients) {
            addIngredient(ingredientType, i);
        }
        return this;
    }

    @Override
    public <I> JemiIngredientAcceptor addIngredient(IIngredientType<I> ingredientType, I ingredient) {
        if (ingredient != null)
            addStack(conversions == null ? JemiUtil.getStack(ingredientType, ingredient)
                    : conversions.getStack(ingredientType, ingredient));
        return this;
    }

    @Override
    public JemiIngredientAcceptor addIngredientsUnsafe(List<?> ingredients) {
        for (Object o : ingredients) {
            if (o != null)
                addStack(conversions == null ? JemiUtil.getStack(o) : conversions.getStack(o));
        }
        return this;
    }

    @Override
    public JemiIngredientAcceptor addFluidStack(Fluid fluid) {
        addStack(EmiStack.of(fluid));
        return this;
    }

    @Override
    public JemiIngredientAcceptor addFluidStack(Fluid fluid, long amount) {
        addStack(EmiStack.of(fluid, amount));
        return this;
    }

    @Override
    public JemiIngredientAcceptor addFluidStack(Fluid fluid, long amount, CompoundTag tag) {
        addStack(EmiStack.of(fluid, tag, amount));
        return this;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public JemiIngredientAcceptor addTypedIngredients(List<ITypedIngredient<?>> ingredients) {
        for (ITypedIngredient<?> i : ingredients) {
            addIngredient(((IIngredientType) i.getType()), i.getIngredient());
        }
        return this;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public JemiIngredientAcceptor addOptionalTypedIngredients(List<Optional<ITypedIngredient<?>>> ingredients) {
        for (Optional<ITypedIngredient<?>> opt : ingredients) {
            if (opt.isPresent()) {
                ITypedIngredient<?> i = opt.get();
                addIngredient(((IIngredientType) i.getType()), i.getIngredient());
            }
        }
        return this;
    }
}
