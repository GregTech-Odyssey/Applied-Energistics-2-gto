package dev.emi.emi.api;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.material.Fluids;

import dev.emi.emi.VanillaPlugin;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.stack.*;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.stack.ListEmiIngredient;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.recipe.EmiSyntheticIngredientRecipe;
import dev.emi.emi.recipe.EmiTagRecipe;
import dev.emi.emi.registry.EmiRecipes;
import dev.emi.emi.registry.EmiStackList;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiHistory;
import dev.emi.emi.runtime.EmiSidebars;
import dev.emi.emi.screen.BoMScreen;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.RecipeScreen;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;

public class EmiApi {
    private static final Minecraft client = Minecraft.getInstance();

    public static List<EmiStack> getIndexStacks() {
        return EmiStackList.stacks;
    }

    public static EmiRecipeManager getRecipeManager() {
        return EmiRecipes.manager;
    }

    public static boolean isCheatMode() {
        return switch (EmiConfig.cheatMode) {
            case TRUE -> true;
            case CREATIVE -> client.player == null || client.player.isCreative();
            case FALSE -> false;
        };
    }

    /**
     * @return Current search text
     */
    public static String getSearchText() {
        return EmiScreenManager.search.getValue();
    }

    /**
     * Sets the current search to the provided query
     */
    public static void setSearchText(String text) {
        EmiScreenManager.search.setValue(text);
    }

    public static boolean isSearchFocused() {
        return EmiScreenManager.search.isFocused();
    }

    /**
     * Gets the currently hovered EmiIngredient at the provided screen coordinates, or {@link EmiStack#EMPTY} if none.
     *
     * @param includeStandard Whether to include the EmiIngredient representation of standard stacks in slots or
     *                        otherwise provided to EMI.
     */
    public static EmiStackInteraction getHoveredStack(int mouseX, int mouseY, boolean includeStandard) {
        return EmiScreenManager.getHoveredStack(mouseX, mouseY, includeStandard);
    }

    /**
     * Gets the currently hovered EmiIngredient at the mouse or {@link EmiStack#EMPTY} if none.
     *
     * @param includeStandard Whether to include the EmiIngredient representation of standard stacks in slots or
     *                        otherwise provided to EMI.
     */
    public static EmiStackInteraction getHoveredStack(boolean includeStandard) {
        return EmiScreenManager.getHoveredStack(EmiScreenManager.lastMouseX, EmiScreenManager.lastMouseY,
                includeStandard);
    }

    /**
     * @return Recipe context associated with specific ingredient implementations. This could be favorites, craftables,
     *         or something else.
     */
    public static @Nullable EmiRecipe getRecipeContext(EmiIngredient stack) {
        if (stack instanceof EmiFavorite fav) {
            return fav.getRecipe();
        }
        return null;
    }

    public static AbstractContainerScreen<?> getHandledScreen() {
        Screen s = client.screen;
        if (s instanceof AbstractContainerScreen<?> hs) {
            return hs;
        } else if (s instanceof RecipeScreen rs) {
            return rs.old;
        } else if (s instanceof BoMScreen bs) {
            return bs.old;
        }
        return null;
    }

    public static void displayAllRecipes() {
        var manager = getRecipeManager();
        var categories = manager.getCategories();
        var recipes = new Reference2ObjectOpenHashMap<EmiRecipeCategory, List<EmiRecipe>>(categories.size());
        for (var category : categories)
            recipes.put(category, manager.getRecipes(category));
        setPages(recipes, EmiStack.EMPTY);
    }

    public static void displayRecipeCategory(EmiRecipeCategory category) {
        setPages(Map.of(category, getRecipeManager().getRecipes(category)), EmiStack.EMPTY);
    }

    public static void displayRecipe(EmiRecipe recipe) {
        setPages(Map.of(recipe.getCategory(), List.of(recipe)), EmiStack.EMPTY);
    }

    public static void displayRecipes(EmiIngredient stack) {
        if (stack.isEmpty())
            return;
        if (stack instanceof EmiFavorite fav) {
            stack = fav.getStack();
        }
        if (stack instanceof TagEmiIngredient tag) {
            for (EmiRecipe recipe : EmiApi.getRecipeManager().getRecipes(VanillaPlugin.TAG)) {
                if (recipe instanceof EmiTagRecipe tr && tr.key.equals(tag.key)) {
                    setPages(Map.of(VanillaPlugin.TAG, List.of(recipe)), stack);
                    break;
                }
            }
        } else if (stack instanceof ListEmiIngredient) {
            setPages(Map.of(VanillaPlugin.INGREDIENT, List.of(new EmiSyntheticIngredientRecipe(stack))), stack);
        } else {
            var recipesByOutput = new ObjectArrayList<EmiRecipe>();
            var fluid = getFluidIngredient(stack);
            EmiStack es = stack.getEmiStacks().getFirst();
            if (fluid != null) {
                EmiStack fes = fluid.getEmiStacks().getFirst();
                recipesByOutput.addAll(pruneSources(EmiApi.getRecipeManager().getRecipesByOutput(fes), fes));
            }
            if (stack.getEmiStacks().size() == 1) {
                recipesByOutput.addAll(pruneSources(EmiApi.getRecipeManager().getRecipesByOutput(es), es));
            } else if (!recipesByOutput.isEmpty()) {
                es = fluid.getEmiStacks().getFirst();
            }
            if (recipesByOutput.isEmpty())
                return;
            setPages(mapRecipes(recipesByOutput), stack);
            EmiApi.focusRecipe(BoM.getRecipe(es));
        }
    }

    public static void displayUses(EmiIngredient stack) {
        if (!stack.isEmpty())
            setPages(mapRecipes(getUses(stack)), stack);
    }

    public static List<EmiRecipe> getUses(EmiIngredient stack) {
        var zero = stack.getEmiStacks().getFirst();
        var combined = new ReferenceLinkedOpenHashSet<EmiRecipe>();
        combined.addAll(pruneUses(getRecipeManager().getRecipesByInput(zero), stack));
        var fluid = getFluidIngredient(stack);
        if (fluid != null)
            combined.addAll(pruneUses(getRecipeManager().getRecipesByInput(fluid), fluid));
        combined.addAll(EmiRecipes.byWorkstation.getOrDefault(zero, Collections.emptyList()));
        return new ObjectArrayList<>(combined);
    }

    public static @Nullable EmiStack getFluidIngredient(EmiIngredient ingredient) {
        if (!(ingredient instanceof EmiStack stack))
            return null;
        if (stack.getKey() instanceof BucketItem bucket) {
            var fluid = bucket.getFluid();
            return fluid == Fluids.EMPTY ? null : EmiStack.of(fluid);
        }
        var item = stack.getItemStack();
        var wrapped = GenericStack.fromItemStack(item);
        if (wrapped != null && wrapped.what() instanceof AEFluidKey fluid) {
            return new FluidEmiStack(fluid, wrapped.amount());
        }
        if (stack.hasNbt()) {
            var handler = item
                    .getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER_ITEM)
                    .orElse(null);
            if (handler != null && handler.getTanks() > 0) {
                var fluid = handler.getFluidInTank(0);
                if (!fluid.isEmpty())
                    return EmiStack.of(fluid.getFluid(), fluid.getTag(), 1);
            }
        }
        return null;
    }

    public static void viewRecipeTree() {
        if (client.screen == null) {
            client.setScreen(new InventoryScreen(client.player));
        }
        Screen s = client.screen;
        if (s instanceof AbstractContainerScreen<?> hs) {
            push();
            client.setScreen(new BoMScreen(hs));
        } else if (s instanceof RecipeScreen rs) {
            push();
            client.setScreen(new BoMScreen(rs.old));
        }
    }

    public static void focusRecipe(EmiRecipe recipe) {
        if (client.screen instanceof RecipeScreen rs) {
            rs.focusRecipe(recipe);
        }
    }

    private static void push() {
        if (client.screen instanceof RecipeScreen rs) {
            EmiHistory.push(rs);
        } else if (client.screen instanceof BoMScreen bs) {
            EmiHistory.push(bs);
        } else {
            EmiHistory.clear();
            EmiHistory.push(client.screen);
        }
    }

    private static List<EmiRecipe> pruneSources(List<EmiRecipe> recipes, EmiStack context) {
        var result = new ObjectArrayList<EmiRecipe>(recipes.size());
        outer: for (var recipe : recipes) {
            for (var output : recipe.getOutputs()) {
                if (output.isEqual(context)) {
                    result.add(recipe);
                    continue outer;
                }
            }
        }
        return result;
    }

    private static List<EmiRecipe> pruneUses(List<EmiRecipe> recipes, EmiIngredient context) {
        var result = new ObjectArrayList<EmiRecipe>(recipes.size());
        var presence = new Reference2IntOpenHashMap<EmiRecipe>(recipes.size());
        for (var recipe : recipes) {
            boolean matches = false;
            for (var input : recipe.getInputs())
                if (containsAll(input, context)) {
                    matches = true;
                    break;
                }
            if (!matches)
                for (var catalyst : recipe.getCatalysts())
                    if (containsAll(catalyst, context)) {
                        matches = true;
                        break;
                    }
            if (matches) {
                result.add(recipe);
                presence.put(recipe, getSmallestPresence(recipe, context));
            }
        }
        result.sort((a, b) -> Integer.compare(presence.getInt(a), presence.getInt(b)));
        return result;
    }

    private static int getSmallestPresence(EmiRecipe recipe, EmiIngredient context) {
        int ideal = context.getEmiStacks().size();
        int smallestPresence = Integer.MAX_VALUE;
        for (EmiIngredient i : recipe.getInputs()) {
            if (containsAll(i, context)) {
                smallestPresence = Math.min(smallestPresence, i.getEmiStacks().size());
                if (smallestPresence <= ideal) {
                    break;
                }
            }
        }
        return smallestPresence;
    }

    private static Map<EmiRecipeCategory, List<EmiRecipe>> mapRecipes(List<EmiRecipe> list) {
        var map = new Reference2ObjectOpenHashMap<EmiRecipeCategory, List<EmiRecipe>>();
        for (EmiRecipe recipe : list) {
            map.computeIfAbsent(recipe.getCategory(), k -> new ObjectArrayList<>()).add(recipe);
        }
        return map;
    }

    private static boolean containsAll(EmiIngredient collection, EmiIngredient ingredient) {
        outer: for (EmiStack ing : ingredient.getEmiStacks()) {
            for (EmiStack col : collection.getEmiStacks()) {
                if (col.isEqual(ing)) {
                    continue outer;
                }
            }
            return false;
        }
        return true;
    }

    private static void setPages(Map<EmiRecipeCategory, List<EmiRecipe>> recipes, EmiIngredient stack) {
        var nonEmpty = new Reference2ObjectOpenHashMap<EmiRecipeCategory, List<EmiRecipe>>(recipes.size());
        recipes.forEach((category, categoryRecipes) -> {
            if (!categoryRecipes.isEmpty())
                nonEmpty.put(category, categoryRecipes);
        });
        recipes = nonEmpty;
        if (!recipes.isEmpty()) {
            EmiSidebars.lookup(stack);
            if (getHandledScreen() == null) {
                client.setScreen(new InventoryScreen(client.player));
            }
            if (client.screen instanceof AbstractContainerScreen<?> hs) {
                push();
                client.setScreen(new RecipeScreen(hs, recipes));
            } else if (client.screen instanceof BoMScreen bs) {
                push();
                client.setScreen(new RecipeScreen(bs.old, recipes));
            } else if (client.screen instanceof RecipeScreen rs) {
                push();
                RecipeScreen n = new RecipeScreen(rs.old, recipes);
                client.setScreen(n);
                n.focusCategory(rs.getFocusedCategory());
            }
        }
    }
}
