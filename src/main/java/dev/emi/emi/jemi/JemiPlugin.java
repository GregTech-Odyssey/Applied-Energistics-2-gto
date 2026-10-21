package dev.emi.emi.jemi;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.BiPredicate;

import com.gto.fastcollection.fastutil.O2OOpenCacheHashMap;
import com.gto.fastcollection.fastutil.OpenCacheHashSet;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.level.material.Fluid;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.*;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.GeneratedSlotWidget;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.forge.EmiForgePlatform;
import dev.emi.emi.jemi.impl.JemiIngredientAcceptor;
import dev.emi.emi.jemi.impl.JemiRecipeLayoutBuilder;
import dev.emi.emi.jemi.runtime.*;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.registry.EmiRecipes;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.runtime.EmiReloadLog;
import dev.emi.emi.runtime.EmiReloadManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.IIngredientTypeWithSubtypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.ISubtypeManager;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.vanilla.IJeiIngredientInfoRecipe;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRuntimeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.library.ingredients.subtypes.SubtypeInterpreters;
import mezz.jei.library.load.registration.SubtypeRegistration;

@JeiPlugin
public class JemiPlugin implements IModPlugin, EmiPlugin {
    private static final JemiStartup STARTUP = new JemiStartup(Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "EMI JEI Loader");
        thread.setDaemon(true);
        return thread;
    }));

    public static boolean deferJeiStart(Runnable starter) {
        return STARTUP.deferStart(starter);
    }

    public static boolean deferJeiStop(Runnable stopper) {
        return STARTUP.deferStop(stopper);
    }

    public static CompletableFuture<Void> startDeferredJeiAsync() {
        return STARTUP.startAsync();
    }

    private static final Reference2ObjectOpenHashMap<EmiRecipeCategory, IRecipeCategory<?>> CATEGORY_MAP = new Reference2ObjectOpenHashMap<>();
    private static @Nullable ISubtypeManager subtypeManager;
    public static volatile @Nullable IJeiRuntime runtime;
    public static BiPredicate<IIngredientTypeWithSubtypes<? extends Object, ? extends Object>, Object> hasSubtype = (a,
            b) -> true;

    @Override
    public ResourceLocation getPluginUid() {
        return EmiPort.id("emi:jemi");
    }

    public void registerItemSubtypes(ISubtypeRegistration registration) {
        try {
            if (((SubtypeRegistration) registration).getInterpreters() != null) {
                hasSubtype = (type, ingredient) -> {
                    @SuppressWarnings("unchecked")
                    IIngredientTypeWithSubtypes<Object, Object> castedType = (IIngredientTypeWithSubtypes<Object, Object>) type;
                    SubtypeInterpreters interpreters = ((SubtypeRegistration) registration).getInterpreters();
                    return interpreters.contains(castedType, ingredient);
                };
            }
            return;
        } catch (Throwable t) {
            t.printStackTrace();
        }
        hasSubtype = (type, ingredient) -> {
            @SuppressWarnings("unchecked")
            IIngredientTypeWithSubtypes<Object, Object> castedType = (IIngredientTypeWithSubtypes<Object, Object>) type;
            return subtypeManager.hasSubtypes(castedType, ingredient);
        };
    }

    @Override
    public void registerIngredients(IModIngredientRegistration registration) {
        subtypeManager = registration.getSubtypeManager();
    }

    @Override
    public void registerRuntime(IRuntimeRegistration registration) {
        registration.setIngredientListOverlay(new JemiIngredientListOverlay());
        registration.setBookmarkOverlay(new JemiBookmarkOverlay());
        registration.setRecipesGui(new JemiRecipesGui());
        registration.setIngredientFilter(new JemiIngredientFilter());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        JemiPlugin.runtime = runtime;
        STARTUP.runtimeAvailable();
    }

    @Override
    public void onRuntimeUnavailable() {
        JemiPlugin.runtime = null;
        STARTUP.runtimeUnavailable();
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void register(EmiRegistry registry) {
        EmiLog.info("[JEMI] Waiting for JEI to finish reloading...");
        EmiReloadManager.step(EmiPort.translatable("emi.reload.jei.waiting"), 20_000);
        startDeferredJeiAsync().join();
        if (runtime == null)
            throw new IllegalStateException("JEI startup finished without a runtime");
        EmiLog.info("[JEMI] JEI reloaded!");
        var plugins = EmiForgePlatform.getPlugins();
        var handledNamespaces = new OpenCacheHashSet<String>(plugins.size());
        for (var plugin : plugins)
            handledNamespaces.add(plugin.id());

        EmiReloadManager.step(EmiPort.translatable("emi.reload.jei.loading"), 5_000);
        registry.addGenericExclusionArea((screen, consumer) -> {
            if (runtime != null && runtime.getScreenHelper() != null) {
                var areas = runtime.getScreenHelper().getGuiExclusionAreas(screen).iterator();
                while (areas.hasNext()) {
                    var r = areas.next();
                    if (r != null) {
                        consumer.accept(new Bounds(r.getX(), r.getY(), r.getWidth(), r.getHeight()));
                    }
                }
            }
        });

        registry.addGenericStackProvider((screen, x, y) -> {
            var ingredients = runtime.getScreenHelper().getClickableIngredientUnderMouse(screen, x, y).iterator();
            return new EmiStackInteraction(
                    ingredients.hasNext() ? JemiUtil.getStack(ingredients.next().getTypedIngredient()) : EmiStack.EMPTY,
                    null, false);
        });

        registry.addGenericDragDropHandler(new JemiDragDropHandler());

        registry.addIngredientSerializer(JemiStack.class,
                new JemiStackSerializer(runtime.getIngredientManager()));

        EmiReloadManager.step(EmiPort.translatable("emi.reload.jei.stacks"), 5_000);
        for (IIngredientType<?> type : runtime.getIngredientManager().getRegisteredIngredientTypes()) {
            if (type == dev.emi.emi.jemi.JemiUtil.getFluidType() || type == VanillaTypes.ITEM_STACK) {
                continue;
            }
            for (Object o : runtime.getIngredientManager().getAllIngredients(type)) {
                EmiStack stack = dev.emi.emi.jemi.JemiUtil.getStack(type, o);
                if (!stack.isEmpty()) {
                    registry.addEmiStack(stack);
                }
            }
        }

        registry.removeEmiStacks(s -> {
            try {
                Optional<ITypedIngredient<?>> opt = dev.emi.emi.jemi.JemiUtil.getTyped(s);
                if (opt.isPresent()) {
                    return !runtime.getIngredientVisibility().isIngredientVisible(opt.get());
                }
            } catch (Throwable t) {
            }
            return false;
        });
        EmiReloadManager.step(EmiPort.translatable("emi.reload.jei.subtypes"), 5_000);
        safely("subtype comparison", () -> parseSubtypes(registry));

        EmiReloadManager.step(EmiPort.translatable("emi.reload.jei.recipes"), 5_000);
        var existingCategories = new OpenCacheHashSet<ResourceLocation>(EmiRecipes.categories.size());
        for (var category : EmiRecipes.categories)
            existingCategories.add(category.getId());
        var categoryMap = new O2OOpenCacheHashMap<RecipeType, EmiRecipeCategory>(12);
        categoryMap.put(RecipeTypes.CRAFTING, VanillaEmiRecipeCategories.CRAFTING);
        categoryMap.put(RecipeTypes.SMELTING, VanillaEmiRecipeCategories.SMELTING);
        categoryMap.put(RecipeTypes.BLASTING, VanillaEmiRecipeCategories.BLASTING);
        categoryMap.put(RecipeTypes.SMOKING, VanillaEmiRecipeCategories.SMOKING);
        categoryMap.put(RecipeTypes.CAMPFIRE_COOKING, VanillaEmiRecipeCategories.CAMPFIRE_COOKING);
        categoryMap.put(RecipeTypes.STONECUTTING, VanillaEmiRecipeCategories.STONECUTTING);
        categoryMap.put(RecipeTypes.SMITHING, VanillaEmiRecipeCategories.SMITHING);
        categoryMap.put(RecipeTypes.ANVIL, VanillaEmiRecipeCategories.ANVIL_REPAIRING);
        categoryMap.put(RecipeTypes.BREWING, VanillaEmiRecipeCategories.BREWING);
        categoryMap.put(RecipeTypes.FUELING, VanillaEmiRecipeCategories.FUEL);
        categoryMap.put(RecipeTypes.COMPOSTING, VanillaEmiRecipeCategories.COMPOSTING);
        categoryMap.put(RecipeTypes.INFORMATION, VanillaEmiRecipeCategories.INFO);

        CATEGORY_MAP.clear();
        EmiRecipeFiller.extraHandlers = JemiPlugin::getRecipeHandler;

        var conversions = new JemiConversionCache(runtime.getIngredientManager());
        var categories = runtime.getRecipeManager().createRecipeCategoryLookup().includeHidden().get().iterator();
        while (categories.hasNext()) {
            IRecipeCategory<?> c = categories.next();
            RecipeType type = c.getRecipeType();
            ResourceLocation id = type.getUid();
            if (isTagCategory(id))
                continue;
            EmiLog.info("[JEMI] Collecting data for " + c.getTitle().getString());
            EmiReloadManager.step(EmiPort.translatable("emi.reload.jei.category", c.getTitle()), 5_000);
            try {
                var catalysts = new ObjectArrayList<EmiStack>();
                var catalystIterator = runtime.getRecipeManager().createRecipeCatalystLookup(type).includeHidden().get()
                        .iterator();
                while (catalystIterator.hasNext())
                    catalysts.add(JemiUtil.getStack(catalystIterator.next()));
                EmiRecipeCategory category = categoryMap.get(type);
                if (category != null) {
                    CATEGORY_MAP.put(category, c);
                    for (EmiStack catalyst : catalysts) {
                        if (!catalyst.isEmpty()) {
                            registry.addWorkstation(category, catalyst);
                        }
                    }
                    if (type == RecipeTypes.INFORMATION) {
                        addInfoRecipes(registry, (IRecipeCategory<IJeiIngredientInfoRecipe>) c);
                    } else if (type == RecipeTypes.CRAFTING) {
                        addCraftingRecipes(registry, (IRecipeCategory<CraftingRecipe>) c, conversions);
                    }
                    continue;
                }
                if (handledNamespaces.contains(id.getNamespace())) {
                    EmiLog.info("[JEMI] Skipping recipe category " + id + " because mod is already handled");
                    continue;
                }
                if (existingCategories.contains(id)) {
                    EmiLog.info("[JEMI] Skipping recipe category " + id
                            + " because native EMI recipe category already exists");
                    continue;
                }
                category = new JemiCategory(c);
                CATEGORY_MAP.put(category, c);
                registry.addCategory(category);
                for (EmiStack catalyst : catalysts) {
                    if (!catalyst.isEmpty()) {
                        registry.addWorkstation(category, catalyst);
                    }
                }
                var recipes = runtime.getRecipeManager().createRecipeLookup(type).includeHidden().get().iterator();
                while (recipes.hasNext()) {
                    var r = recipes.next();
                    try {
                        registry.addRecipe(new JemiRecipe(category, c, r, conversions));
                    } catch (Throwable t) {
                        EmiLog.error("Exception thrown adding adding JEI recipe", t);
                    }
                }
            } catch (Throwable t) {
                EmiLog.error("Exception thrown adding adding JEI recipes", t);
            }
        }
    }

    static boolean isTagCategory(ResourceLocation id) {
        return id.getNamespace().equals("minecraft")
                && (id.getPath().equals("tag_recipes/item") || id.getPath().equals("tag_recipes/block"));
    }

    private void addInfoRecipes(EmiRegistry registry, IRecipeCategory<IJeiIngredientInfoRecipe> category) {
        var recipes = runtime.getRecipeManager().createRecipeLookup(RecipeTypes.INFORMATION).includeHidden().get()
                .iterator();
        var grouped = new O2OOpenCacheHashMap<ObjectArrayList<EmiStack>, ObjectArrayList<IJeiIngredientInfoRecipe>>();
        while (recipes.hasNext()) {
            var recipe = recipes.next();
            var ingredients = recipe.getIngredients();
            var stacks = new ObjectArrayList<EmiStack>(ingredients.size());
            for (var ingredient : ingredients)
                stacks.add(JemiUtil.getStack(ingredient));
            grouped.computeIfAbsent(stacks, key -> new ObjectArrayList<>(1)).add(recipe);
        }
        var identical = new O2OOpenCacheHashMap<Component, ObjectArrayList<EmiIngredient>>(grouped.size());
        for (var iterator = grouped.object2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
            var group = iterator.next();
            var text = EmiPort.literal("");
            boolean hasText = false;
            for (var recipe : group.getValue()) {
                for (var description : recipe.getDescription()) {
                    var current = EmiPort.literal("");
                    description.visit((style, string) -> {
                        current.append(EmiPort.literal(string, style));
                        return Optional.empty();
                    }, Style.EMPTY);
                    if (!current.getString().isBlank()) {
                        if (hasText)
                            text.append(" ");
                        text.append(current);
                        hasText = true;
                    }
                }
            }
            identical.computeIfAbsent(text, key -> new ObjectArrayList<>(group.getKey().size())).addAll(group.getKey());
        }
        identical.forEach((text, stacks) -> registry.addRecipe(new EmiInfoRecipe(stacks, List.of(text), null)));
    }

    private void addCraftingRecipes(EmiRegistry registry, IRecipeCategory<CraftingRecipe> category,
            JemiConversionCache conversions) {
        var replaced = new OpenCacheHashSet<ResourceLocation>();
        var replacements = new ReferenceOpenHashSet<EmiRecipe>();
        var vanillaRecipes = registry.getRecipeManager()
                .getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING);
        var recipes = new ReferenceLinkedOpenHashSet<CraftingRecipe>(vanillaRecipes.size());
        var jeiRecipes = runtime.getRecipeManager().createRecipeLookup(category.getRecipeType()).includeHidden().get()
                .iterator();
        while (jeiRecipes.hasNext())
            recipes.add(jeiRecipes.next());
        for (var recipe : vanillaRecipes)
            if (recipe instanceof CustomRecipe)
                recipes.add(recipe);

        for (CraftingRecipe recipe : recipes) {
            try {
                if (category.isHandled(recipe)) {
                    JemiRecipeLayoutBuilder builder = new JemiRecipeLayoutBuilder(conversions);
                    category.setRecipe(builder, recipe, runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());
                    var inputs = new ObjectArrayList<EmiIngredient>(builder.ingredients.size());
                    boolean hasInput = false, hasOutput = false;
                    var outputs = new ObjectArrayList<EmiStack>(1);
                    for (JemiIngredientAcceptor acceptor : builder.ingredients) {
                        EmiIngredient stack = acceptor.build();
                        if (acceptor.role == RecipeIngredientRole.INPUT) {
                            inputs.add(stack);
                            hasInput |= !stack.isEmpty();
                        } else if (acceptor.role == RecipeIngredientRole.CATALYST) {
                            inputs.add(stack);
                            hasInput |= !stack.isEmpty();
                        } else if (acceptor.role == RecipeIngredientRole.OUTPUT) {
                            outputs.addAll(stack.getEmiStacks());
                            for (var output : stack.getEmiStacks())
                                hasOutput |= !output.isEmpty();
                        }
                    }
                    if (hasInput && hasOutput) {
                        EmiRecipe replacement;
                        if (outputs.size() > 1) {
                            replacement = new EmiPatternCraftingRecipe(inputs, EmiStack.EMPTY,
                                    category.getRegistryName(recipe), builder.shapeless) {

                                @Override
                                public List<EmiStack> getOutputs() {
                                    return outputs;
                                }

                                @Override
                                public SlotWidget getInputWidget(int slot, int x, int y) {
                                    if (slot <= inputs.size()) {
                                        return new SlotWidget(inputs.get(slot), x, y);
                                    } else {
                                        return new SlotWidget(EmiStack.EMPTY, x, y);
                                    }
                                }

                                @Override
                                public SlotWidget getOutputWidget(int x, int y) {
                                    return new GeneratedSlotWidget(r -> outputs.get(r.nextInt(outputs.size())),
                                            recipe.hashCode(), x, y);
                                }

                            };
                        } else {
                            replacement = new EmiCraftingRecipe(inputs, outputs.get(0),
                                    category.getRegistryName(recipe), builder.shapeless);
                        }
                        if (replacement.getId() != null) {
                            replaced.add(replacement.getId());
                        }
                        replacements.add(replacement);
                        registry.addRecipe(replacement);
                    }
                }
            } catch (Throwable t) {
                EmiLog.error("[JEMI] Exception thrown setting JEI crafting recipe", t);
            }
        }
        registry.removeRecipes(
                r -> r instanceof EmiCraftingRecipe && replaced.contains(r.getId()) && !replacements.contains(r));
    }

    @SuppressWarnings({ "unchecked" })
    private void parseSubtypes(EmiRegistry registry) {
        if (subtypeManager != null) {
            IIngredientManager im = runtime.getIngredientManager();
            var types = im.getRegisteredIngredientTypes();
            for (Item item : EmiPort.getItemRegistry()) {
                if (hasSubtype.test(VanillaTypes.ITEM_STACK, item.getDefaultInstance())) {
                    registry.setDefaultComparison(item, Comparison.compareData(stack -> {
                        return subtypeManager.getSubtypeInfo(stack.getItemStack(), UidContext.Recipe);
                    }));
                }
            }
            for (Fluid fluid : EmiPort.getFluidRegistry()) {
                IIngredientTypeWithSubtypes<Object, Object> type = (IIngredientTypeWithSubtypes<Object, Object>) dev.emi.emi.jemi.JemiUtil
                        .getFluidType();
                if (hasSubtype.test(type, new net.minecraftforge.fluids.FluidStack(fluid, 1000))) {
                    registry.setDefaultComparison(fluid, Comparison.compareData(stack -> {
                        ITypedIngredient<?> typed = dev.emi.emi.jemi.JemiUtil.getTyped(stack).orElse(null);
                        if (typed != null) {
                            return subtypeManager.getSubtypeInfo(type, typed.getIngredient(), UidContext.Recipe);
                        }
                        return null;
                    }));
                }
            }
            for (IIngredientType<?> type : types) {
                if (type == VanillaTypes.ITEM_STACK || type == JemiUtil.getFluidType()) {
                    continue;
                }
                if (type instanceof IIngredientTypeWithSubtypes iitws) {
                    var ings = im.getAllIngredients(type);
                    for (Object o : ings) {
                        try {
                            if (hasSubtype.test(iitws, o)) {
                                registry.setDefaultComparison(iitws.getBase(o), Comparison.compareData(stack -> {
                                    if (stack instanceof JemiStack jemi) {
                                        return subtypeManager.getSubtypeInfo(iitws, jemi.ingredient, UidContext.Recipe);
                                    }
                                    return null;
                                }));
                            }
                        } catch (Throwable t) {
                            EmiReloadLog.warn("Exception adding default comparison for JEI ingredient", t);
                        }
                    }
                }
            }
        }
    }

    private static EmiRecipeHandler<?> getRecipeHandler(AbstractContainerMenu handler, EmiRecipe recipe) {
        IRecipeCategory<?> category = CATEGORY_MAP.getOrDefault(recipe.getCategory(), null);
        if (category != null) {
            var transfer = runtime.getRecipeTransferManager().getRecipeTransferHandler(handler, category).orElse(null);
            return transfer == null ? null : new JemiRecipeHandler<>(transfer);
        }
        return null;
    }

    private static void safely(String name, Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable t) {
            EmiReloadLog.warn("Exception thrown when reloading " + name + " step in JEMI plugin", t);
        }
    }
}
