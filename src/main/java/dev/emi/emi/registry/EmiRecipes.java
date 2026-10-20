package dev.emi.emi.registry;

import com.google.common.collect.Lists;
import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.recipe.*;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.ListEmiIngredient;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.data.EmiData;
import dev.emi.emi.data.EmiRecipeCategoryProperties;
import dev.emi.emi.runtime.EmiHidden;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.runtime.EmiReloadLog;
import dev.emi.emi.runtime.dev.EmiDev;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.Collections;
import com.gto.fastcollection.fastutil.O2IOpenCacheHashMap;
import com.gto.fastcollection.fastutil.O2OOpenCacheHashMap;
import com.gto.fastcollection.fastutil.O2OOpenCustomCacheHashMap;
import com.gto.fastcollection.fastutil.OpenCacheHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

public class EmiRecipes {
    public static volatile Worker activeWorker = null;
    public static EmiRecipeManager manager = Manager.EMPTY;
    public static List<Consumer<Consumer<EmiRecipe>>> lateRecipes = Lists.newArrayList();
    public static List<Predicate<EmiRecipe>> invalidators = Lists.newArrayList();

    public static List<EmiRecipeCategory> categories = Lists.newArrayList();
    private static Reference2ObjectOpenHashMap<EmiRecipeCategory, ObjectArrayList<EmiIngredient>> workstations = new Reference2ObjectOpenHashMap<>();
    private static List<EmiRecipe> recipes = Lists.newArrayList();

    private static final O2OOpenCacheHashMap<EmiStack, List<EmiRecipe>> WORKSTATION_INDEX = new O2OOpenCacheHashMap<>();
    // Preserve the Map field descriptor used by existing EMI integrations.
    public static Map<EmiStack, List<EmiRecipe>> byWorkstation = WORKSTATION_INDEX;

    static void indexWorkstation(EmiStack stack, List<EmiRecipe> recipes) {
        WORKSTATION_INDEX.computeIfAbsent(stack, key -> new ObjectArrayList<>(recipes.size())).addAll(recipes);
    }

    public static List<EmiRecipeDecorator> decorators = Lists.newArrayList();

    public static void clear() {
        setWorker(null);
        lateRecipes.clear();
        invalidators.clear();
        categories.clear();
        workstations.clear();
        recipes.clear();
        WORKSTATION_INDEX.clear();
        decorators.clear();
        manager = Manager.EMPTY;
    }

    public static void bake() {
        long start = System.currentTimeMillis();
        for (var supplier : EmiData.recipes)
            recipes.add(supplier.get());
        categories.sort(Comparator.comparingInt(EmiRecipeCategoryProperties::getOrder));
        invalidators.addAll(EmiData.recipeFilters);
        invalidators.add(recipe -> hasDisabled(recipe.getInputs()) || hasDisabled(recipe.getOutputs())
                || hasDisabled(recipe.getCatalysts()));
        var filtered = new ObjectArrayList<EmiRecipe>(recipes.size());
        outer: for (var recipe : recipes) {
            for (var predicate : invalidators)
                if (predicate.test(recipe))
                    continue outer;
            filtered.add(recipe);
        }
        var filteredWorkstations = new Reference2ObjectOpenHashMap<EmiRecipeCategory, ObjectArrayList<EmiIngredient>>(
                workstations.size());
        for (var iterator = workstations.reference2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
            var entry = iterator.next();
            var original = entry.getValue();
            var visible = new ObjectArrayList<EmiIngredient>(original.size());
            for (var stack : original)
                if (!EmiHidden.isDisabled(stack))
                    visible.add(stack);
            if (!visible.isEmpty())
                filteredWorkstations.put(entry.getKey(), visible);
        }
        manager = new Manager(categories, filteredWorkstations, filtered, false);
        setWorker(new Worker(categories, filteredWorkstations, filtered));
        EmiLog.info("Baked " + recipes.size() + " recipes in " + (System.currentTimeMillis() - start) + "ms");
    }

    private static boolean hasDisabled(List<? extends EmiIngredient> ingredients) {
        for (var ingredient : ingredients)
            if (EmiHidden.isDisabled(ingredient))
                return true;
        return false;
    }

    public static void addCategory(EmiRecipeCategory category) {
        categories.add(category);
    }

    public static void addWorkstation(EmiRecipeCategory category, EmiIngredient workstation) {
        workstations.computeIfAbsent(category, k -> new ObjectArrayList<>(1)).add(workstation);
    }

    public static void addRecipe(EmiRecipe recipe) {
        recipes.add(recipe);
    }

    private static synchronized void setWorker(Worker worker) {
        activeWorker = worker;
        if (worker != null) {
            new Thread(activeWorker).start();
        }
    }

    private static class Manager implements EmiRecipeManager {
        public static final EmiRecipeManager EMPTY = new Manager();
        private final ObjectArrayList<EmiRecipeCategory> categories;
        private final Reference2ObjectOpenHashMap<EmiRecipeCategory, ObjectArrayList<EmiIngredient>> workstations;
        private final List<EmiRecipe> recipes;
        private final O2OOpenCustomCacheHashMap<EmiStack, ObjectArrayList<EmiRecipe>> byInput;
        private final O2OOpenCustomCacheHashMap<EmiStack, ObjectArrayList<EmiRecipe>> byOutput;
        private final Reference2ObjectOpenHashMap<EmiRecipeCategory, ObjectArrayList<EmiRecipe>> byCategory;
        private final O2OOpenCacheHashMap<ResourceLocation, EmiRecipe> byId;

        private Manager() {
            categories = new ObjectArrayList<>(0);
            workstations = new Reference2ObjectOpenHashMap<>(0);
            recipes = Collections.emptyList();
            byInput = new O2OOpenCustomCacheHashMap<>(0, new EmiStackList.ComparisonHashStrategy());
            byOutput = new O2OOpenCustomCacheHashMap<>(0, new EmiStackList.ComparisonHashStrategy());
            byCategory = new Reference2ObjectOpenHashMap<>(0);
            byId = new O2OOpenCacheHashMap<>(0);
        }

        Manager(List<EmiRecipeCategory> categories,
                Reference2ObjectOpenHashMap<EmiRecipeCategory, ObjectArrayList<EmiIngredient>> workstations,
                List<EmiRecipe> recipes, boolean doSort) {
            var registered = new ReferenceOpenHashSet<EmiRecipeCategory>(categories.size());
            this.categories = new ObjectArrayList<>(categories.size());
            for (var category : categories)
                if (registered.add(category))
                    this.categories.add(category);
            this.workstations = workstations;
            this.recipes = recipes;
            byInput = new O2OOpenCustomCacheHashMap<>(new EmiStackList.ComparisonHashStrategy());
            byOutput = new O2OOpenCustomCacheHashMap<>(new EmiStackList.ComparisonHashStrategy());
            byCategory = new Reference2ObjectOpenHashMap<>(categories.size());
            byId = new O2OOpenCacheHashMap<>(recipes.size());
            var duplicateIds = new O2IOpenCacheHashMap<ResourceLocation>();
            var incorrectIds = new OpenCacheHashSet<ResourceLocation>();
            var client = Minecraft.getInstance();
            for (var recipe : recipes) {
                var id = recipe.getId();
                var category = recipe.getCategory();
                if (!registered.contains(category))
                    EmiReloadLog.warn("Recipe " + id + " loaded with unregistered category: " + category.getId());
                if (EmiConfig.logNonTagIngredients && recipe.supportsRecipeTree()) {
                    var seen = new OpenCacheHashSet<EmiIngredient>(recipe.getInputs().size());
                    for (var ingredient : recipe.getInputs()) {
                        if (ingredient instanceof ListEmiIngredient && seen.add(ingredient)) {
                            EmiReloadLog.warn("Recipe " + id + " uses non-tag ingredient: " + ingredient);
                        }
                    }
                }
                byCategory.computeIfAbsent(category, key -> new ObjectArrayList<>()).add(recipe);
                if (id != null) {
                    if (byId.putIfAbsent(id, recipe) != null && EmiConfig.devMode) {
                        int previous = duplicateIds.getInt(id);
                        duplicateIds.put(id, previous == 0 ? 2 : previous + 1);
                    }
                    if (EmiConfig.devMode && !id.getPath().startsWith("/") && client.level != null
                            && client.level.getRecipeManager().byKey(id).isEmpty())
                        incorrectIds.add(id);
                }
            }
            if (EmiConfig.devMode) {
                for (var iterator = duplicateIds.object2IntEntrySet().fastIterator(); iterator.hasNext();) {
                    var entry = iterator.next();
                    EmiReloadLog.warn(entry.getIntValue() + " recipes loaded with the same id: " + entry.getKey());
                }
                for (var id : incorrectIds)
                    EmiReloadLog.warn("Recipe " + id
                            + " not present in recipe manager. Consider prefixing its path with '/' if it is synthetic.");
            }

            var indexed = new ReferenceOpenHashSet<EmiRecipe>(recipes.size());
            for (var iterator = byCategory.reference2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
                var entry = iterator.next();
                var category = entry.getKey();
                var categoryRecipes = entry.getValue();
                String key = EmiUtil.translateId("emi.category.", category.getId());
                if (category.getName().equals(EmiPort.translatable(key)) && !I18n.exists(key)) {
                    EmiReloadLog.warn("Untranslated recipe category " + category.getId());
                }
                var sort = EmiRecipeCategoryProperties.getSort(category);
                if (doSort && sort != EmiRecipeSorting.none()) {
                    categoryRecipes.sort(sort);
                    EmiRecipeSorter.clear();
                }
                for (var recipe : categoryRecipes) {
                    if (!indexed.add(recipe))
                        continue;
                    for (var input : recipe.getInputs()) {
                        for (var stack : input.getEmiStacks())
                            EmiRecipeIndex.indexStack(byInput, stack, recipe, true);
                    }
                    for (var catalyst : recipe.getCatalysts()) {
                        for (var stack : catalyst.getEmiStacks())
                            EmiRecipeIndex.indexStack(byInput, stack, recipe, true);
                    }
                    for (var output : recipe.getOutputs())
                        EmiRecipeIndex.indexStack(byOutput, output, recipe, true);
                }
            }
            for (var stacks : workstations.values()) {
                if (stacks.size() < 2)
                    continue;
                var seen = new OpenCacheHashSet<EmiIngredient>(stacks.size());
                int size = 0;
                for (int i = 0; i < stacks.size(); i++) {
                    var ingredient = stacks.get(i);
                    if (seen.add(ingredient))
                        stacks.set(size++, ingredient);
                }
                stacks.size(size);
            }
            for (var iterator = byCategory.reference2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
                var entry = iterator.next();
                var stacks = workstations.get(entry.getKey());
                if (stacks == null)
                    continue;
                for (var ingredient : stacks) {
                    for (var stack : ingredient.getEmiStacks())
                        indexWorkstation(stack, entry.getValue());
                }
            }
            if (EmiConfig.devMode) {
                EmiDev.duplicateRecipeIds = duplicateIds.keySet();
                EmiDev.incorrectRecipeIds = incorrectIds;
            }
        }

        @Override
        public List<EmiRecipeCategory> getCategories() {
            return categories;
        }

        @Override
        public List<EmiIngredient> getWorkstations(EmiRecipeCategory category) {
            var stacks = workstations.get(category);
            return stacks == null ? Collections.emptyList() : stacks;
        }

        @Override
        public List<EmiRecipe> getRecipes() {
            return recipes;
        }

        @Override
        public List<EmiRecipe> getRecipes(EmiRecipeCategory category) {
            var matches = byCategory.get(category);
            return matches == null ? Collections.emptyList() : matches;
        }

        @Override
        public @Nullable EmiRecipe getRecipe(ResourceLocation id) {
            return byId.get(id);
        }

        @Override
        public List<EmiRecipe> getRecipesByInput(EmiStack stack) {
            var matches = byInput.get(stack);
            return matches == null ? Collections.emptyList() : matches;
        }

        @Override
        public List<EmiRecipe> getRecipesByOutput(EmiStack stack) {
            var matches = byOutput.get(stack);
            return matches == null ? Collections.emptyList() : matches;
        }
    }

    private static class Worker implements Runnable {
        private List<EmiRecipeCategory> categories;
        private Reference2ObjectOpenHashMap<EmiRecipeCategory, ObjectArrayList<EmiIngredient>> workstations;
        private List<EmiRecipe> recipes;

        public Worker(List<EmiRecipeCategory> categories,
                Reference2ObjectOpenHashMap<EmiRecipeCategory, ObjectArrayList<EmiIngredient>> workstations,
                List<EmiRecipe> recipes) {
            this.categories = categories;
            this.workstations = workstations;
            this.recipes = recipes;
        }

        @Override
        public void run() {
            long startTime = System.currentTimeMillis();
            Manager manager = new Manager(categories, workstations, recipes, true);
            if (activeWorker == this) {
                long endTime = System.currentTimeMillis();
                EmiLog.info("Baked recipes after reload in " + (endTime - startTime) + "ms");
                EmiRecipes.manager = manager;
            }
            setWorker(null);
        }
    }
}
