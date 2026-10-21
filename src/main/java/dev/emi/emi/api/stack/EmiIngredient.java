package dev.emi.emi.api.stack;

import dev.emi.emi.api.render.EmiRenderable;
import dev.emi.emi.registry.EmiIngredientCache;
import dev.emi.emi.registry.EmiTags;
import dev.emi.emi.runtime.EmiTagKey;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.List;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;
import net.minecraft.world.level.ItemLike;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public interface EmiIngredient extends EmiRenderable {
    int RENDER_ICON = 1;
    int RENDER_AMOUNT = 2;
    int RENDER_INGREDIENT = 4;
    int RENDER_REMAINDER = 8;

    /**
     * @return The {@link EmiStack}s represented by this ingredient. List is never empty. For an empty ingredient, us
     *         {@link EmiStack#EMPTY}
     */
    @NotNull
    @UnmodifiableView
    List<@NotNull EmiStack> getEmiStacks();

    default boolean isEmpty() {
        for (EmiStack stack : getEmiStacks()) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    EmiIngredient copy();

    long getAmount();

    EmiIngredient setAmount(long amount);

    float getChance();

    EmiIngredient setChance(float chance);

    @Override
    default void render(GuiGraphics draw, int x, int y, float delta) {
        render(draw, x, y, delta, -1);
    }

    void render(GuiGraphics draw, int x, int y, float delta, int flags);

    List<ClientTooltipComponent> getTooltip();

    static boolean areEqual(EmiIngredient a, EmiIngredient b) {
        List<EmiStack> as = a.getEmiStacks();
        List<EmiStack> bs = b.getEmiStacks();
        if (as.size() != bs.size()) {
            return false;
        }
        for (int i = 0; i < as.size(); i++) {
            if (!as.get(i).isEqual(bs.get(i))) {
                return false;
            }
        }
        return true;
    }

    static <T> EmiIngredient of(TagKey<T> key) {
        return of(key, 1);
    }

    static <T> EmiIngredient of(TagKey<T> key, long amount) {
        return EmiIngredient.of(EmiTags.getRawValues(EmiTagKey.of(key)), amount);
    }

    static EmiIngredient of(@Nullable Ingredient ingredient) {
        return EmiIngredientCache.of(ingredient);
    }

    static EmiIngredient of(@Nullable Ingredient ingredient, long amount) {
        return EmiIngredientCache.of(ingredient).setAmount(amount);
    }

    static @NotNull EmiIngredient ofItems(@NotNull Collection<? extends ItemLike> items) {
        var stacks = new ObjectArrayList<EmiIngredient>(items.size());
        for (var item : items)
            stacks.add(EmiStack.of(item));
        return of(stacks);
    }

    static EmiIngredient of(List<? extends EmiIngredient> list) {
        return of(list, 1);
    }

    static EmiIngredient of(List<? extends EmiIngredient> list, long amount) {
        if (list.isEmpty()) {
            return EmiStack.EMPTY;
        } else if (list.size() == 1) {
            EmiIngredient stack = list.getFirst();
            if (stack.getAmount() < amount) {
                return stack.copy().setAmount(amount);
            } else {
                return stack;
            }
        } else {
            long internalAmount = list.getFirst().getAmount();
            for (EmiIngredient i : list) {
                if (i.getAmount() != internalAmount) {
                    internalAmount = 1;
                }
            }
            if (internalAmount > 1) {
                amount = internalAmount;
                var normalized = new ObjectArrayList<EmiIngredient>(list.size());
                for (var ingredient : list)
                    normalized.add(ingredient.copy().setAmount(1));
                list = normalized;
            }
            Class<?> tagType = null;
            for (EmiIngredient i : list) {
                for (EmiStack s : i.getEmiStacks()) {
                    if (!s.isEmpty()) {
                        if (tagType == null) {
                            tagType = EmiTags.ADAPTERS_BY_CLASS.getKey(s.getKey().getClass());
                        }
                        if (tagType == null || !tagType.isAssignableFrom(s.getKey().getClass())) {
                            return new ListEmiIngredient(list, amount);
                        }
                    }
                }
            }
            int size = 0;
            for (var ingredient : list)
                size += ingredient.getEmiStacks().size();
            var flattened = new ObjectArrayList<EmiStack>(size);
            for (var ingredient : list)
                flattened.addAll(ingredient.getEmiStacks());
            return EmiTags.getIngredient(tagType, flattened, amount);
        }
    }
}
