package dev.emi.emi.jemi;

import java.util.Arrays;

import com.gto.fastcollection.fastutil.O2OOpenCacheHashMap;

import org.jetbrains.annotations.Nullable;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.ListEmiIngredient;
import dev.emi.emi.api.stack.TagEmiIngredient;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.runtime.IIngredientManager;

import appeng.api.stacks.AEKey;

/** Shared by one registration pass, never retained by the converted recipes. */
public final class JemiConversionCache {
    private final IIngredientManager ingredientManager;
    private final Reference2ObjectOpenHashMap<IIngredientType<?>, TypeInfo<?>> types = new Reference2ObjectOpenHashMap<>();
    private final O2OOpenCacheHashMap<IngredientKey, EmiIngredient> ingredients = new O2OOpenCacheHashMap<>();
    private final IngredientKey lookup = new IngredientKey();

    public JemiConversionCache(IIngredientManager ingredientManager) {
        this.ingredientManager = ingredientManager;
    }

    @SuppressWarnings("unchecked")
    public <I> EmiStack getStack(IIngredientType<I> type, @Nullable I ingredient) {
        if (ingredient == null)
            return EmiStack.EMPTY;
        if (type == VanillaTypes.ITEM_STACK || type == JemiUtil.getFluidType())
            return JemiUtil.getStack(type, ingredient);
        var info = (TypeInfo<I>) types.get(type);
        if (info == null) {
            info = new TypeInfo<>(ingredientManager.getIngredientHelper(type),
                    ingredientManager.getIngredientRenderer(type));
            types.put(type, info);
        }
        return info.helper().isValidIngredient(ingredient)
                ? new JemiStack<>(type, info.helper(), info.renderer(), ingredient)
                : EmiStack.EMPTY;
    }

    public EmiStack getStack(@Nullable Object ingredient) {
        if (ingredient == null)
            return EmiStack.EMPTY;
        var type = ingredientManager.getIngredientType(ingredient);
        return type == null ? EmiStack.EMPTY : getStack(type, ingredient);
    }

    public EmiIngredient getIngredient(ObjectArrayList<EmiStack> stacks) {
        if (stacks.size() < 2)
            return EmiIngredient.of(stacks);
        // Custom JEI resources can be mutable and have their own comparison semantics.
        if (!lookup.set(stacks))
            return EmiIngredient.of(stacks);
        var template = ingredients.get(lookup);
        if (template == null) {
            var converted = EmiIngredient.of(stacks);
            ingredients.put(new IngredientKey(lookup), copy(converted));
            return converted;
        }
        return copy(template);
    }

    private static EmiIngredient copy(EmiIngredient template) {
        // List/tag copy() shares alternatives; recipe-local mutations must not reach the cache.
        if (template instanceof TagEmiIngredient tag) {
            var originals = tag.getEmiStacks();
            var stacks = new ObjectArrayList<EmiStack>(originals.size());
            for (var stack : originals)
                stacks.add(stack.copy());
            return new TagEmiIngredient(tag.key, stacks, tag.getAmount()).setChance(tag.getChance());
        } else if (template instanceof ListEmiIngredient list) {
            var originals = list.getIngredients();
            var alternatives = new ObjectArrayList<EmiIngredient>(originals.size());
            for (var ingredient : originals)
                alternatives.add(copy(ingredient));
            return new ListEmiIngredient(alternatives, list.getAmount()).setChance(list.getChance());
        }
        return template.copy();
    }

    private record TypeInfo<I>(IIngredientHelper<I> helper, IIngredientRenderer<I> renderer) {
    }

    private static final class IngredientKey {
        private AEKey[] keys;
        private long[] amounts;
        private int size;
        private int hash;

        private IngredientKey() {
            keys = new AEKey[0];
            amounts = new long[0];
        }

        private IngredientKey(IngredientKey lookup) {
            size = lookup.size;
            keys = Arrays.copyOf(lookup.keys, size);
            amounts = Arrays.copyOf(lookup.amounts, size);
            hash = lookup.hash;
        }

        private boolean set(ObjectArrayList<EmiStack> stacks) {
            size = stacks.size();
            if (keys.length < size) {
                keys = new AEKey[size];
                amounts = new long[size];
            }
            int hash = 1;
            for (int i = 0; i < size; i++) {
                var stack = stacks.get(i);
                keys[i] = stack.getAEKey();
                if (keys[i] == null)
                    return false;
                amounts[i] = stack.getAmount();
                hash = 31 * hash + System.identityHashCode(keys[i]);
                hash = 31 * hash + Long.hashCode(amounts[i]);
            }
            this.hash = hash;
            return true;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof IngredientKey key) || size != key.size)
                return false;
            for (int i = 0; i < size; i++)
                if (keys[i] != key.keys[i] || amounts[i] != key.amounts[i])
                    return false;
            return true;
        }
    }
}
