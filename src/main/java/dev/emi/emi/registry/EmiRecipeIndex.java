package dev.emi.emi.registry;

import java.util.*;
import com.gto.fastcollection.fastutil.O2OOpenCacheHashMap;
import com.gto.fastcollection.fastutil.O2OOpenCustomCacheHashMap;
import org.jetbrains.annotations.Nullable;
import net.minecraft.resources.ResourceLocation;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import it.unimi.dsi.fastutil.Hash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

/** Recipe index shared with integrations that supply their own recipe population and ordering. */
public class EmiRecipeIndex implements EmiRecipeManager {
    private static final Hash.Strategy<EmiStack> STACK_STRATEGY = new EmiStackList.ComparisonHashStrategy();
    private final List<EmiRecipeCategory> categories;
    private final Map<EmiRecipeCategory, ? extends List<EmiIngredient>> workstations;
    private final List<EmiRecipe> recipes;
    private final O2OOpenCustomCacheHashMap<EmiStack, ObjectArrayList<EmiRecipe>> byInputList = new O2OOpenCustomCacheHashMap<>(
            STACK_STRATEGY);
    private final O2OOpenCustomCacheHashMap<EmiStack, ObjectArrayList<EmiRecipe>> byOutputList = new O2OOpenCustomCacheHashMap<>(
            STACK_STRATEGY);
    private final Reference2ObjectOpenHashMap<EmiRecipeCategory, ObjectArrayList<EmiRecipe>> byCategory;
    private final O2OOpenCacheHashMap<ResourceLocation, EmiRecipe> byId;

    public EmiRecipeIndex(@NotNull List<@NotNull EmiRecipeCategory> categories,
            @NotNull Map<EmiRecipeCategory, ? extends List<EmiIngredient>> workstations,
            @NotNull List<@NotNull EmiRecipe> recipes) {
        this.categories = categories;
        this.workstations = workstations;
        this.recipes = recipes;
        byCategory = new Reference2ObjectOpenHashMap<>(categories.size());
        byId = new O2OOpenCacheHashMap<>(recipes.size());
        var indexed = new ReferenceOpenHashSet<EmiRecipe>(recipes.size());

        for (EmiRecipe recipe : recipes) {
            ResourceLocation id = recipe.getId();
            EmiRecipeCategory category = recipe.getCategory();
            byCategory.computeIfAbsent(category, a -> new ObjectArrayList<>()).add(recipe);
            if (id != null) {
                byId.put(id, recipe);
            }

            if (!indexed.add(recipe))
                continue;
            for (EmiIngredient input : recipe.getInputs()) {
                for (EmiStack stack : input.getEmiStacks()) {
                    indexStack(byInputList, stack, recipe, false);
                }
            }

            for (EmiIngredient catalyst : recipe.getCatalysts()) {
                for (EmiStack stack : catalyst.getEmiStacks()) {
                    indexStack(byInputList, stack, recipe, false);
                }
            }

            for (EmiStack output : recipe.getOutputs()) {
                indexStack(byOutputList, output, recipe, false);
            }
        }

        for (var iterator = byCategory.reference2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
            var entry = iterator.next();
            var cat = entry.getKey();
            var list = entry.getValue();
            sortRecipes(cat, list);
            var stations = workstations.get(cat);
            if (stations == null)
                continue;
            for (EmiIngredient ingredient : stations) {
                for (EmiStack stack : ingredient.getEmiStacks()) {
                    EmiRecipes.indexWorkstation(stack, list);
                }
            }
        }

    }

    static void indexStack(O2OOpenCustomCacheHashMap<EmiStack, ObjectArrayList<EmiRecipe>> index,
            EmiStack stack, EmiRecipe recipe, boolean copyKey) {
        var matches = index.get(stack);
        if (matches == null) {
            matches = new ObjectArrayList<>(1);
            index.put(copyKey ? stack.copy() : stack, matches);
        }
        if (matches.isEmpty() || matches.getLast() != recipe)
            matches.add(recipe);
    }

    protected void sortRecipes(EmiRecipeCategory category, List<EmiRecipe> recipes) {
    }

    @Override
    public List<EmiRecipeCategory> getCategories() {
        return categories;
    }

    @Override
    public List<EmiIngredient> getWorkstations(EmiRecipeCategory category) {
        var stations = workstations.get(category);
        return stations == null ? Collections.emptyList() : stations;
    }

    @Override
    public @NotNull @UnmodifiableView List<@NotNull EmiRecipe> getRecipes() {
        return recipes;
    }

    @Override
    public @NotNull @UnmodifiableView List<@NotNull EmiRecipe> getRecipes(EmiRecipeCategory category) {
        var matches = byCategory.get(category);
        return matches == null ? Collections.emptyList() : matches;
    }

    @Override
    public @Nullable EmiRecipe getRecipe(@NotNull ResourceLocation id) {
        return byId.getOrDefault(id, null);
    }

    @Override
    public @NotNull @UnmodifiableView List<@NotNull EmiRecipe> getRecipesByInput(@NotNull EmiStack stack) {
        var matches = byInputList.get(stack);
        return matches == null ? Collections.emptyList() : matches;
    }

    @Override
    public @NotNull @UnmodifiableView List<@NotNull EmiRecipe> getRecipesByOutput(@NotNull EmiStack stack) {
        var matches = byOutputList.get(stack);
        return matches == null ? Collections.emptyList() : matches;
    }
}
