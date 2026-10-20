package dev.emi.emi.jemi.impl;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.jemi.JemiUtil;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback;
import mezz.jei.api.gui.ingredient.IRecipeSlotTooltipCallback;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("unchecked")
public class JemiRecipeSlot implements IRecipeSlotView {
	public final RecipeIngredientRole role;
	public final boolean large, defaultBackground;
	public final int x, y;
	public final Optional<String> name;
    public final @Nullable IRecipeSlotTooltipCallback tooltipCallback;
    public final @Nullable IRecipeSlotRichTooltipCallback richTooltipCallback;
    public final @Nullable OffsetDrawable background, overlay;
	public final Map<IIngredientType<?>, IngredientRenderer<?>> renderers;
    public final @Nullable TankInfo tankInfo;
	public final EmiIngredient stack;
	public SlotWidget widget;
	public int highlight = 0;

	public JemiRecipeSlot(JemiRecipeSlotBuilder builder) {
		this.role = builder.acceptor.role;
		this.large = builder.large;
		this.defaultBackground = builder.defaultBackground;
		this.x = builder.x;
		this.y = builder.y;
		this.name = builder.name;
		this.tooltipCallback = builder.tooltipCallback;
		this.richTooltipCallback = builder.richTooltipCallback;
		this.background = builder.background;
		this.overlay = builder.overlay;
		this.renderers = builder.renderers;
		this.tankInfo = builder.tankInfo;
		this.stack = builder.acceptor.build();
	}

	public JemiRecipeSlot(RecipeIngredientRole role, EmiStack stack) {
		this.role = role;
		this.large = false;
		this.defaultBackground = false;
		this.x = 0;
		this.y = 0;
		this.name = Optional.empty();
		this.tooltipCallback = null;
		this.richTooltipCallback = null;
		this.background = null;
		this.overlay = null;
		this.renderers = null;
		this.tankInfo = null;
		this.stack = stack;
	}

	@Override
    public <T> @NotNull Stream<T> getIngredients(@NotNull IIngredientType<T> ingredientType) {
        // JEI requires a lazy Stream: one fused stage keeps findFirst() from expanding every alternative.
        return stack.getEmiStacks().stream().<T>mapMulti((candidate, consumer) -> {
            var typed = JemiUtil.getTyped(candidate).orElse(null);
            if (typed != null && typed.getType() == ingredientType)
                consumer.accept((T) typed.getIngredient());
        });
    }

    public boolean hasIngredients(@NotNull IIngredientType<?> ingredientType) {
        for (var candidate : stack.getEmiStacks()) {
            var typed = JemiUtil.getTyped(candidate).orElse(null);
            if (typed != null && typed.getType() == ingredientType)
                return true;
        }
        return false;
	}

	@Override
    public @NotNull Stream<ITypedIngredient<?>> getAllIngredients() {
        return stack.getEmiStacks().stream().mapMulti((candidate, consumer) -> {
            var typed = JemiUtil.getTyped(candidate).orElse(null);
            if (typed != null)
                consumer.accept(typed);
        });
	}

	@Override
	public boolean isEmpty() {
		return stack.isEmpty();
	}

	@Override
	public <T> Optional<T> getDisplayedIngredient(IIngredientType<T> ingredientType) {
		Optional<ITypedIngredient<?>> ing = getDisplayedIngredient();
		if (ing.isPresent() && ing.get().getType() == ingredientType) {
			return (Optional<T>) Optional.of(ing.get().getIngredient());
		}
		return Optional.empty();
	}

	@Override
	public Optional<ITypedIngredient<?>> getDisplayedIngredient() {
		return JemiUtil.getTyped(stack.getEmiStacks().get(0));
	}

	@Override
	public Optional<String> getSlotName() {
		return name;
	}

	@Override
	public RecipeIngredientRole getRole() {
		return role;
	}

	@Override
	public void drawHighlight(GuiGraphics raw, int color) {
		this.highlight = color;
	}

    public static record OffsetDrawable(IDrawable drawable, int xOff, int yOff) {
	}

    public static record IngredientRenderer<T>(IIngredientType<T> type, IIngredientRenderer<T> renderer) {
	}

	public static record TankInfo(int width, int height, long capacity, boolean showCapacity) {
	}
}
