package dev.emi.emi.runtime;

import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiResolutionRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.bom.MaterialNode;
import dev.emi.emi.bom.MaterialTree;
import dev.emi.emi.runtime.EmiFavorite.Role;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class RecipeFavoriteActions {
    private static final int MAX_TREE_RECIPES = 96;

    private RecipeFavoriteActions() {
    }

    public static boolean isSaved(EmiRecipe recipe) {
        return find(recipe) != null;
    }

    public static @Nullable EmiFavorite find(EmiRecipe recipe) {
        if (recipe == null || recipe.getId() == null) {
            return null;
        }
        int page = EmiFavorites.currentFavoritePage();
        for (EmiFavorite favorite : EmiFavorites.favorites) {
            EmiRecipe context = favorite.getRecipe();
            if (EmiFavorites.getFavoritePage(favorite) == page && context != null && context.getId() != null && context.getId().equals(recipe.getId()) && favorite.getRole() == Role.RESULT) {
                return favorite;
            }
        }
        return null;
    }

    public static void toggleWithRecipe(EmiRecipe recipe, boolean preserveCount) {
        if (!valid(recipe)) {
            return;
        }
        if (!preserveCount && isSaved(recipe)) {
            EmiFavorites.removeRecipeFavorites(recipe);
            return;
        }
        saveRecipe(recipe, preserveCount, preserveCount);
    }

    public static boolean saveSharedRecipe(EmiRecipe recipe) {
        if (!valid(recipe) || isSaved(recipe)) {
            return false;
        }
        List<EmiIngredient> outputs = aggregate(recipe.getOutputs());
        if (outputs.isEmpty()) {
            return false;
        }
        return EmiFavorites.addRecipeFavorite(outputs.get(0), recipe, Role.RESULT, true) != null;
    }

    private static void saveRecipe(EmiRecipe root, boolean preserveCount, boolean includeSavedIngredientRecipes) {
        EmiFavorite existingRoot = find(root);
        EmiFavoriteGroups.Group existingGroup = existingRoot == null ? null : EmiFavoriteGroups.groupFor(existingRoot);
        List<EmiRecipe> recipes = new ArrayList<>();
        if (includeSavedIngredientRecipes) {
            recipes.addAll(resolveRecipeRows(root));
        } else {
            recipes.add(root);
        }
        if (existingGroup != null) {
            EmiFavoriteGroups.removeGroup(existingGroup);
        }

        List<EmiFavorite> favorites = new ArrayList<>();
        for (EmiRecipe recipe : recipes) {
            for (EmiFavorite favorite : addRecipeEntries(recipe, preserveCount)) {
                if (!containsIdentity(favorites, favorite)) {
                    favorites.add(favorite);
                }
            }
        }
        EmiFavoriteGroups.Group group = EmiFavoriteGroups.createGroupFromFavorites(favorites, false);
        if (group == null) {
            EmiFavorites.finishFavoriteBatch();
        } else {
            if (!preserveCount) {
                for (EmiRecipe recipe : recipes) {
                    EmiFavoriteGroups.setRecipeQuantity(group, recipe, 0L);
                }
            } else {
                EmiFavoriteGroups.applyQuantity(group);
            }
        }
    }

    public static void saveRecipeTree(EmiRecipe root) {
        if (!valid(root)) {
            return;
        }
        EmiPlayerInventory inventory = EmiScreenManager.lastPlayerInventory;
        if (inventory == null) {
            inventory = EmiPlayerInventory.of(Minecraft.getInstance().player);
        }
        List<EmiRecipe> recipes = new ArrayList<>();
        collectTree(root, inventory, recipes, new HashSet<>());
        List<EmiFavorite> favorites = new ArrayList<>();
        for (EmiRecipe recipe : recipes) {
            for (EmiFavorite favorite : addRecipeEntries(recipe, true)) {
                if (!containsIdentity(favorites, favorite)) {
                    favorites.add(favorite);
                }
            }
        }
        if (EmiFavoriteGroups.createGroupFromFavorites(favorites, true) == null) {
            EmiFavorites.finishFavoriteBatch();
        }
    }

    private static List<EmiFavorite> addRecipeEntries(EmiRecipe recipe, boolean preserveAmount) {
        List<EmiFavorite> added = new ArrayList<>();
        for (EmiIngredient output : aggregate(recipe.getOutputs())) {
            EmiFavorite favorite = EmiFavorites.addRecipeFavoriteQuiet(output, recipe, Role.RESULT, preserveAmount);
            if (favorite != null && !containsIdentity(added, favorite)) {
                added.add(favorite);
            }
        }
        for (EmiIngredient input : aggregate(recipe.getInputs())) {
            EmiFavorite favorite = EmiFavorites.addRecipeFavoriteQuiet(input, recipe, Role.INGREDIENT, preserveAmount);
            if (favorite != null && !containsIdentity(added, favorite)) {
                added.add(favorite);
            }
        }
        for (EmiIngredient catalyst : aggregate(recipe.getCatalysts())) {
            EmiIngredient single = catalyst.copy().setAmount(1);
            EmiFavorite favorite = EmiFavorites.addRecipeFavoriteQuiet(single, recipe, Role.CATALYST, true);
            if (favorite != null && !containsIdentity(added, favorite)) {
                added.add(favorite);
            }
        }
        return added;
    }

    private static List<EmiRecipe> resolveRecipeRows(EmiRecipe root) {
        List<EmiRecipe> resolved = new ArrayList<>();
        MaterialTree tree;
        if (BoM.tree != null && BoM.tree.goal != null && sameRecipe(BoM.tree.goal.recipe, root)) {
            tree = BoM.tree;
        } else {
            tree = new MaterialTree(root);
        }
        collectMaterialRecipes(tree.goal, resolved, new LinkedHashSet<>());
        if (resolved.isEmpty()) {
            resolved.add(root);
        }
        EmiLog.info("Bookmark recipe tree " + root.getId() + " resolved " + resolved.size() + " recipe row(s)");
        return resolved;
    }

    private static void collectMaterialRecipes(MaterialNode node, List<EmiRecipe> result, Set<ResourceLocation> visited) {
        if (node == null || result.size() >= MAX_TREE_RECIPES) {
            return;
        }
        EmiRecipe recipe = node.recipe;
        if (recipe instanceof EmiResolutionRecipe) {
            if (node.children != null) {
                for (MaterialNode child : node.children) {
                    collectMaterialRecipes(child, result, visited);
                }
            }
            return;
        }
        if (valid(recipe) && visited.add(recipe.getId())) {
            result.add(recipe);
        }
        if (node.children != null) {
            for (MaterialNode child : node.children) {
                collectMaterialRecipes(child, result, visited);
            }
        }
    }

    private static boolean sameRecipe(EmiRecipe a, EmiRecipe b) {
        if (a == b) {
            return true;
        }
        return a != null && b != null && a.getId() != null && b.getId() != null && a.getId().equals(b.getId());
    }

    private static List<EmiIngredient> aggregate(List<? extends EmiIngredient> source) {
        List<EmiIngredient> result = new ArrayList<>();
        for (EmiIngredient ingredient : source) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            int match = -1;
            EmiIngredient normalized = normalize(ingredient);
            for (int i = 0; i < result.size(); i++) {
                if (EmiIngredient.areEqual(normalize(result.get(i)), normalized)) {
                    match = i;
                    break;
                }
            }
            if (match < 0) {
                result.add(ingredient.copy());
            } else {
                EmiIngredient old = result.get(match);
                result.set(match, old.copy().setAmount(safeAdd(Math.max(1L, old.getAmount()), Math.max(1L, ingredient.getAmount()))));
            }
        }
        return result;
    }

    private static EmiIngredient normalize(EmiIngredient ingredient) {
        try {
            return ingredient.copy().setAmount(1).setChance(1);
        } catch (Throwable ignored) {
            return ingredient;
        }
    }

    private static long safeAdd(long a, long b) {
        if (a > Long.MAX_VALUE - b) {
            return Long.MAX_VALUE;
        }
        return a + b;
    }

    private static void collectTree(EmiRecipe recipe, EmiPlayerInventory inventory, List<EmiRecipe> result, Set<EmiRecipe> path) {
        if (recipe == null || result.size() >= MAX_TREE_RECIPES || !path.add(recipe)) {
            return;
        }
        try {
            if (!result.contains(recipe)) {
                result.add(recipe);
            }
            if (!recipe.supportsRecipeTree()) {
                return;
            }
            for (EmiIngredient input : recipe.getInputs()) {
                if (input == null || input.isEmpty() || result.size() >= MAX_TREE_RECIPES) {
                    continue;
                }
                EmiRecipe child = BoM.getRecipe(input);
                if (child == null) {
                    child = EmiUtil.getPreferredRecipe(input, inventory, false);
                }
                if (child != null && child != recipe) {
                    collectTree(child, inventory, result, path);
                }
            }
        } finally {
            path.remove(recipe);
        }
    }

    public static void sendRecipeToChat(EmiRecipe recipe) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !valid(recipe)) {
            return;
        }
        String link = RecipeShareLink.create(recipe);
        if (!link.isEmpty()) {
            client.player.connection.sendChat("[EMI Recipe] " + link);
        }
    }

    private record FavoriteSeed(EmiIngredient stack, @Nullable EmiRecipe recipe, Role role) {
    }

    private static boolean valid(EmiRecipe recipe) {
        return recipe != null && recipe.getId() != null && !recipe.getOutputs().isEmpty();
    }

    private static boolean containsIdentity(List<EmiFavorite> list, EmiFavorite target) {
        for (EmiFavorite favorite : list) {
            if (favorite == target) {
                return true;
            }
        }
        return false;
    }
}
