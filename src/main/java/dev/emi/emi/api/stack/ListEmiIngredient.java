package dev.emi.emi.api.stack;

import com.google.common.collect.Lists;
import dev.emi.emi.EmiPort;
import dev.emi.emi.api.render.EmiRender;
import dev.emi.emi.screen.tooltip.EmiTextTooltipWrapper;
import dev.emi.emi.screen.tooltip.IngredientTooltipComponent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import org.jetbrains.annotations.ApiStatus;
import java.util.List;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

@ApiStatus.Internal
public class ListEmiIngredient implements EmiIngredient {
    private final List<? extends EmiIngredient> ingredients;
    private final ObjectArrayList<EmiStack> fullList;
    private final int hash;
    private long amount;
    private float chance = 1;

    /** Ingredients must contain at least one stack; normalize empty inputs through {@link EmiIngredient#of(List)}. */
    @ApiStatus.Internal
    public ListEmiIngredient(@NotNull List<? extends @NotNull EmiIngredient> ingredients, long amount) {
        this.ingredients = ingredients;
        int size = 0;
        for (var ingredient : ingredients)
            size += ingredient.getEmiStacks().size();
        this.fullList = new ObjectArrayList<>(size);
        for (var ingredient : ingredients)
            fullList.addAll(ingredient.getEmiStacks());
        this.hash = fullList.hashCode();
        this.amount = amount;
    }

    private ListEmiIngredient(List<? extends EmiIngredient> ingredients, ObjectArrayList<EmiStack> fullList,
            int hash, long amount, float chance) {
        this.ingredients = ingredients;
        this.fullList = fullList;
        this.hash = hash;
        this.amount = amount;
        this.chance = chance;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ListEmiIngredient other) {
            return other.getEmiStacks().equals(this.getEmiStacks());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public EmiIngredient copy() {
        return new ListEmiIngredient(ingredients, fullList, hash, amount, chance);
    }

    @Override
    public String toString() {
        return "Ingredient" + getEmiStacks();
    }

    @Override
    public @NotNull @UnmodifiableView List<@NotNull EmiStack> getEmiStacks() {
        return fullList;
    }

    @Override
    public long getAmount() {
        return amount;
    }

    @Override
    @Contract("_ -> this")
    public @NotNull EmiIngredient setAmount(long amount) {
        this.amount = amount;
        return this;
    }

    @Override
    public float getChance() {
        return chance;
    }

    @Override
    @Contract("_ -> this")
    public @NotNull EmiIngredient setChance(float chance) {
        this.chance = chance;
        return this;
    }

    @Override
    public void render(GuiGraphics draw, int x, int y, float delta, int flags) {
        int item = (int) (System.currentTimeMillis() / 1000 % ingredients.size());
        EmiIngredient current = ingredients.get(item);
        if ((flags & RENDER_ICON) != 0) {
            current.render(draw, x, y, delta, -1 ^ RENDER_AMOUNT);
        }
        if ((flags & RENDER_AMOUNT) != 0) {
            current.copy().setAmount(amount).render(draw, x, y, delta, RENDER_AMOUNT);
        }
        if ((flags & RENDER_INGREDIENT) != 0) {
            EmiRender.renderIngredientIcon(this, draw, x, y);
        }
    }

    @Override
    public List<ClientTooltipComponent> getTooltip() {
        List<ClientTooltipComponent> tooltip = Lists.newArrayList();
        tooltip.add(new EmiTextTooltipWrapper(this, EmiPort.ordered(EmiPort.translatable("tooltip.emi.accepts"))));
        tooltip.add(new IngredientTooltipComponent(ingredients));
        int item = (int) (System.currentTimeMillis() / 1000 % ingredients.size());
        tooltip.addAll(ingredients.get(item).copy().setAmount(amount).getTooltip());
        return tooltip;
    }

    @ApiStatus.Internal
    public List<? extends EmiIngredient> getIngredients() {
        return ingredients;
    }
}
