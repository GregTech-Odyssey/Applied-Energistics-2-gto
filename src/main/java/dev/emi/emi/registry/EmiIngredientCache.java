package dev.emi.emi.registry;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.ListEmiIngredient;
import dev.emi.emi.api.stack.TagEmiIngredient;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** Converted templates only; every caller owns the mutable stacks used for recipe remainders. */
@ApiStatus.Internal
public final class EmiIngredientCache {
    private static final Reference2ObjectOpenHashMap<Ingredient, EmiIngredient> TEMPLATES = new Reference2ObjectOpenHashMap<>();

    private EmiIngredientCache() {
    }

    public static EmiIngredient of(@Nullable Ingredient ingredient) {
        if (ingredient == null)
            return EmiStack.EMPTY;
        // Custom ingredients may expose changing alternatives. Keep their conversion uncached.
        if (ingredient.getClass() != Ingredient.class)
            return convert(ingredient);
        var template = TEMPLATES.get(ingredient);
        if (template == null) {
            template = convert(ingredient);
            TEMPLATES.put(ingredient, template);
        }
        return copy(template);
    }

    public static void clear() {
        TEMPLATES.clear();
    }

    private static EmiIngredient convert(Ingredient ingredient) {
        if (ingredient.isEmpty())
            return EmiStack.EMPTY;
        var items = ingredient.getItems();
        int amount = items.length == 0 ? 1 : items[0].getCount();
        var stacks = new ObjectArrayList<EmiStack>(items.length);
        for (var item : items) {
            if (item.getCount() != amount)
                amount = 1;
            stacks.add(EmiStack.of(item));
        }
        return EmiTags.getIngredient(Item.class, stacks, amount);
    }

    private static EmiIngredient copy(EmiIngredient template) {
        // EmiIngredient.copy() deliberately shares alternatives for tags and lists.
        // These copies must also isolate nested stacks before setRemainder mutates them.
        if (template instanceof TagEmiIngredient tag) {
            var originals = tag.getEmiStacks();
            var stacks = new ObjectArrayList<EmiStack>(originals.size());
            for (var stack : originals)
                stacks.add(stack.copy());
            return new TagEmiIngredient(tag.key, stacks, tag.getAmount()).setChance(tag.getChance());
        } else if (template instanceof ListEmiIngredient list) {
            var originals = list.getIngredients();
            var ingredients = new ObjectArrayList<EmiIngredient>(originals.size());
            for (var ingredient : originals)
                ingredients.add(copy(ingredient));
            return new ListEmiIngredient(ingredients, list.getAmount()).setChance(list.getChance());
        }
        return template.copy();
    }
}
