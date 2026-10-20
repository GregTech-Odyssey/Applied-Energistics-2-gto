package dev.emi.emi.registry;

import java.util.*;

import com.gto.fastcollection.fastutil.O2OOpenCacheHashMap;
import com.gto.fastcollection.fastutil.O2OOpenCustomCacheHashMap;
import com.gto.fastcollection.fastutil.OpenCacheHashSet;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import it.unimi.dsi.fastutil.Hash;

/** Recipe index shared with integrations that supply their own recipe population and ordering. */
public class EmiRecipeIndex implements EmiRecipeManager {
    private static final Hash.Strategy<EmiStack> STACK_STRATEGY = new EmiStackList.ComparisonHashStrategy();
    private final List<EmiRecipeCategory> categories;
    private final Map<EmiRecipeCategory, List<EmiIngredient>> workstations;
    private final List<EmiRecipe> recipes;
    private final O2OOpenCustomCacheHashMap<EmiStack, List<EmiRecipe>> byInputList = new O2OOpenCustomCacheHashMap<>(
            STACK_STRATEGY);
    private final O2OOpenCustomCacheHashMap<EmiStack, List<EmiRecipe>> byOutputList = new O2OOpenCustomCacheHashMap<>(
            STACK_STRATEGY);
    private final O2OOpenCacheHashMap<EmiRecipeCategory, List<EmiRecipe>> byCategory;
    private final O2OOpenCacheHashMap<ResourceLocation, EmiRecipe> byId;

    public EmiRecipeIndex(List<EmiRecipeCategory> categories, Map<EmiRecipeCategory, List<EmiIngredient>> workstations,
            List<EmiRecipe> recipes) {
        this.categories = categories;
        this.workstations = workstations;
        this.recipes = recipes;
        byCategory = new O2OOpenCacheHashMap<>(categories.size());
        byId = new O2OOpenCacheHashMap<>(recipes.size());
        var byInput = new O2OOpenCustomCacheHashMap<EmiStack, OpenCacheHashSet<EmiRecipe>>(STACK_STRATEGY);
        var byOutput = new O2OOpenCustomCacheHashMap<EmiStack, OpenCacheHashSet<EmiRecipe>>(STACK_STRATEGY);

        for (EmiRecipe recipe : recipes) {
            ResourceLocation id = recipe.getId();
            EmiRecipeCategory category = recipe.getCategory();
            byCategory.computeIfAbsent(category, a -> new ArrayList<>()).add(recipe);
            if (id != null) {
                byId.put(id, recipe);
            }

            for (EmiIngredient input : recipe.getInputs()) {
                for (EmiStack stack : input.getEmiStacks()) {
                    byInput.computeIfAbsent(stack, b -> new OpenCacheHashSet<>()).add(recipe);
                }
            }

            for (EmiIngredient catalyst : recipe.getCatalysts()) {
                for (EmiStack stack : catalyst.getEmiStacks()) {
                    byInput.computeIfAbsent(stack, b -> new OpenCacheHashSet<>()).add(recipe);
                }
            }

            for (EmiStack output : recipe.getOutputs()) {
                byOutput.computeIfAbsent(output, b -> new OpenCacheHashSet<>()).add(recipe);
            }
        }

        for (var iterator = byCategory.object2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
            var entry = iterator.next();
            var cat = entry.getKey();
            var list = entry.getValue();
            sortRecipes(cat, list);
            for (EmiIngredient ingredient : workstations.getOrDefault(cat, Collections.emptyList())) {
                for (EmiStack stack : ingredient.getEmiStacks()) {
                    EmiRecipes.byWorkstation.computeIfAbsent(stack, (s) -> new ArrayList<>()).addAll(list);
                }
            }
        }

        byInput.object2ObjectEntrySet()
                .fastForEach(e -> byInputList.put(e.getKey(), Arrays.asList(e.getValue().toArray(new EmiRecipe[0]))));
        byOutput.object2ObjectEntrySet()
                .fastForEach(e -> byOutputList.put(e.getKey(), Arrays.asList(e.getValue().toArray(new EmiRecipe[0]))));
    }

    protected void sortRecipes(EmiRecipeCategory category, List<EmiRecipe> recipes) {
    }

    @Override
    public List<EmiRecipeCategory> getCategories() {
        return categories;
    }

    @Override
    public List<EmiIngredient> getWorkstations(EmiRecipeCategory category) {
        return workstations.getOrDefault(category, Collections.emptyList());
    }

    @Override
    public List<EmiRecipe> getRecipes() {
        return recipes;
    }

    @Override
    public List<EmiRecipe> getRecipes(EmiRecipeCategory category) {
        return byCategory.getOrDefault(category, Collections.emptyList());
    }

    @Override
    public @Nullable EmiRecipe getRecipe(ResourceLocation id) {
        return byId.getOrDefault(id, null);
    }

    @Override
    public List<EmiRecipe> getRecipesByInput(EmiStack stack) {
        return byInputList.getOrDefault(stack, Collections.emptyList());
    }

    @Override
    public List<EmiRecipe> getRecipesByOutput(EmiStack stack) {
        return byOutputList.getOrDefault(stack, Collections.emptyList());
    }
}
