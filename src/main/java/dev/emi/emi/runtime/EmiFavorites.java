package dev.emi.emi.runtime;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiResolutionRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.ItemEmiStack;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.bom.ChanceMaterialCost;
import dev.emi.emi.bom.FlatMaterialCost;
import dev.emi.emi.bom.MaterialNode;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.screen.EmiScreenManager;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.*;

public class EmiFavorites {
	public static List<dev.emi.emi.runtime.EmiFavorite> favorites = Lists.newArrayList();
	public static List<dev.emi.emi.runtime.EmiFavorite.Synthetic> syntheticFavorites = Lists.newArrayList();
	public static List<dev.emi.emi.runtime.EmiFavorite> favoriteSidebar = new FavoriteSidebarList();
	private static final String EMBEDDED_GROUPS_KEY = "__crazerium_favorite_groups";
	private static boolean unresolvedRecipeFavorites;
	private static long nextRecipeResolveSweepNanos;
	private static JsonArray embeddedFavoriteGroups;
	private static final IdentityHashMap<dev.emi.emi.runtime.EmiFavorite, Integer> favoritePages = new IdentityHashMap<>();
	private static int favoritePageCount = 1;

	public static JsonArray save() {
		JsonArray arr = new JsonArray();
		for (dev.emi.emi.runtime.EmiFavorite fav : favorites) {
			JsonElement stack = EmiIngredientSerializer.getSerialized(fav.getStack());
			if (stack != null) {
				JsonObject obj = new JsonObject();
				obj.add("stack", stack);
				ResourceLocation recipeId = fav.getRecipeId();
				if (recipeId != null) {
					obj.addProperty("recipe", recipeId.toString());
					obj.addProperty("role", fav.getRole().name().toLowerCase(Locale.ROOT));
				}
				int page = getFavoritePage(fav);
				if (page > 0) {
					obj.addProperty("bookmark_page", page);
				}
				arr.add(obj);
			}
		}
		JsonArray groups = EmiFavoriteGroups.save();
		if (groups.size() > 0) {
			JsonObject marker = new JsonObject();
			marker.add(EMBEDDED_GROUPS_KEY, groups);
			arr.add(marker);
		}
		return arr;
	}

	public static void load(JsonArray arr) {
		favorites.clear();
		favoritePages.clear();
		favoritePageCount = 1;
		embeddedFavoriteGroups = null;
		for (JsonElement el : arr) {
			if (el.isJsonObject()) {
				JsonObject json = el.getAsJsonObject();
				if (json.has(EMBEDDED_GROUPS_KEY) && json.get(EMBEDDED_GROUPS_KEY).isJsonArray()) {
					embeddedFavoriteGroups = json.getAsJsonArray(EMBEDDED_GROUPS_KEY).deepCopy();
					continue;
				}
				ResourceLocation recipeId = null;
				EmiRecipe recipe = null;
				if (GsonHelper.isStringValue(json, "recipe")) {
					recipeId = EmiPort.id(GsonHelper.getAsString(json, "recipe"));
					recipe = EmiApi.getRecipeManager().getRecipe(recipeId);
				}
				if (GsonHelper.isValidNode(json, "stack")) {
					EmiIngredient ingredient = EmiIngredientSerializer.getDeserialized(json.get("stack"));
					if (ingredient.isEmpty()) {
						continue;
					}
					if (ingredient instanceof EmiStack es) {
						ingredient = es.copy();
					}
					dev.emi.emi.runtime.EmiFavorite.Role role = recipeId == null ? dev.emi.emi.runtime.EmiFavorite.Role.ITEM : dev.emi.emi.runtime.EmiFavorite.Role.RESULT;
					if (recipeId != null && GsonHelper.isStringValue(json, "role")) {
						try {
							role = dev.emi.emi.runtime.EmiFavorite.Role.valueOf(GsonHelper.getAsString(json, "role").toUpperCase(Locale.ROOT));
						} catch (IllegalArgumentException ignored) {
						}
					}
					dev.emi.emi.runtime.EmiFavorite favorite = new dev.emi.emi.runtime.EmiFavorite(ingredient, recipe, recipeId, role);
					favorites.add(favorite);
					int page = Math.max(0, GsonHelper.getAsInt(json, "bookmark_page", 0));
					favoritePages.put(favorite, page);
					favoritePageCount = Math.max(favoritePageCount, page + 1);
				}
			}
		}
		unresolvedRecipeFavorites = false;
		nextRecipeResolveSweepNanos = 0L;
		for (dev.emi.emi.runtime.EmiFavorite favorite : favorites) {
			if (favorite.hasUnresolvedRecipeReference()) {
				unresolvedRecipeFavorites = true;
				break;
			}
		}
		EmiFavoriteGroups.onFavoritesChanged();
	}

	static JsonArray takeEmbeddedFavoriteGroups() {
		JsonArray groups = embeddedFavoriteGroups;
		embeddedFavoriteGroups = null;
		return groups;
	}

	public static int getFavoritePage(dev.emi.emi.runtime.EmiFavorite favorite) {
		return Math.max(0, favoritePages.getOrDefault(favorite, 0));
	}

	public static int getFavoritePageCount() {
		return Math.max(1, favoritePageCount);
	}

	public static void setFavoritePageCount(int count) {
		favoritePageCount = Math.max(favoritePageCount, Math.max(1, count));
	}

	public static int addFavoritePage() {
		int page = getFavoritePageCount();
		favoritePageCount = page + 1;
		dev.emi.emi.runtime.EmiPersistentData.save();
		return page;
	}

	public static boolean isFavoritePageEmpty(int page) {
		if (page < 0) {
			return true;
		}
		for (dev.emi.emi.runtime.EmiFavorite favorite : favorites) {
			if (getFavoritePage(favorite) == page) {
				return false;
			}
		}
		return true;
	}

	public static boolean removeTrailingEmptyFavoritePage(int page) {
		if (favoritePageCount <= 1 || page != favoritePageCount - 1 || !isFavoritePageEmpty(page)) {
			return false;
		}
		favoritePageCount = Math.max(1, page);
		return true;
	}

	public static boolean trimTrailingEmptyFavoritePages() {
		int highestUsedPage = 0;
		for (dev.emi.emi.runtime.EmiFavorite favorite : favorites) {
			highestUsedPage = Math.max(highestUsedPage, getFavoritePage(favorite));
		}
		int count = Math.max(1, highestUsedPage + 1);
		if (favoritePageCount == count) {
			return false;
		}
		favoritePageCount = count;
		return true;
	}

	static void setFavoritePage(dev.emi.emi.runtime.EmiFavorite favorite, int page) {
		if (favorite == null) {
			return;
		}
		page = Math.max(0, page);
		favoritePages.put(favorite, page);
		favoritePageCount = Math.max(favoritePageCount, page + 1);
	}

	static void forgetFavoritePage(dev.emi.emi.runtime.EmiFavorite favorite) {
		favoritePages.remove(favorite);
	}

	public static int currentFavoritePage() {
		EmiScreenManager.SidebarPanel panel = EmiScreenManager.getPanelFor(SidebarType.FAVORITES);
		if (panel == null || panel.space == null) {
			return 0;
		}
		return EmiFavoriteGroups.namespaceForSidebarPage(panel.space, panel.page);
	}

	private static int pageEndInsertionIndex(int page) {
		int last = -1;
		for (int i = 0; i < favorites.size(); i++) {
			int favoritePage = getFavoritePage(favorites.get(i));
			if (favoritePage == page) {
				last = i;
			} else if (favoritePage > page) {
				return last >= 0 ? last + 1 : i;
			}
		}
		return last >= 0 ? last + 1 : favorites.size();
	}

	private static void addToCurrentPage(dev.emi.emi.runtime.EmiFavorite favorite) {
		int page = currentFavoritePage();
		int index = pageEndInsertionIndex(page);
		if (index >= favorites.size()) {
			favorites.add(favorite);
		} else {
			favorites.add(index, favorite);
		}
		setFavoritePage(favorite, page);
	}

	static boolean resolveRecipeReferences() {
		if (!unresolvedRecipeFavorites) {
			return false;
		}
		long now = System.nanoTime();
		if (now < nextRecipeResolveSweepNanos) {
			return false;
		}
		nextRecipeResolveSweepNanos = now + 250_000_000L;
		boolean changed = false;
		boolean unresolved = false;
		for (dev.emi.emi.runtime.EmiFavorite favorite : favorites) {
			changed |= favorite.resolveRecipeReference();
			unresolved |= favorite.hasUnresolvedRecipeReference();
		}
		unresolvedRecipeFavorites = unresolved;
		return changed;
	}

	public static boolean canFavorite(EmiIngredient stack, EmiRecipe recipe) {
		stack = EmiIngredientSerializer.getDeserialized(EmiIngredientSerializer.getSerialized(stack));
		if (stack.isEmpty()) {
			return false;
		}
		if (recipe != null) {
			return recipe.getId() != null;
		}
		return true;
	}

	private static int indexOf(EmiIngredient stack) {
		if (stack instanceof dev.emi.emi.runtime.EmiFavorite target) {
			for (int i = 0; i < favorites.size(); i++) {
				if (favorites.get(i) == target) {
					return i;
				}
			}
		}
		int page = currentFavoritePage();
		EmiRecipe context = EmiApi.getRecipeContext(stack);
		dev.emi.emi.runtime.EmiFavorite.Role role = stack instanceof dev.emi.emi.runtime.EmiFavorite favorite ? favorite.getRole() : context == null ? dev.emi.emi.runtime.EmiFavorite.Role.ITEM : dev.emi.emi.runtime.EmiFavorite.Role.RESULT;
		for (int i = 0; i < favorites.size(); i++) {
			dev.emi.emi.runtime.EmiFavorite favorite = favorites.get(i);
			if (getFavoritePage(favorite) == page && favorite.strictEquals(stack) && sameRecipe(favorite.getRecipeId(), context) && favorite.getRole() == role) {
				return i;
			}
		}
		return -1;
	}

	public static boolean removeFavorite(EmiIngredient stack) {
		int index = indexOf(stack);
		if (index != -1) {
			dev.emi.emi.runtime.EmiFavorite removed = favorites.remove(index);
			forgetFavoritePage(removed);
			EmiFavoriteGroups.onFavoritesChanged();
			return true;
		}
		return false;
	}

	public static void addFavorite(EmiIngredient stack) {
		addFavorite(stack, null);
	}

	public static void addFavoriteAt(EmiIngredient stack, int offset) {
		int targetPage = currentFavoritePage();
		if (stack instanceof dev.emi.emi.runtime.EmiFavorite.Synthetic) {
			return;
		}
		if (stack instanceof dev.emi.emi.runtime.EmiFavorite.Craftable craftable) {
			stack = craftable.stack;
		}
		dev.emi.emi.runtime.EmiFavorite favorite;
		if (stack instanceof dev.emi.emi.runtime.EmiFavorite fav) {
			int original = indexOf(stack);
			if (original != -1) {
				if (original < offset) {
					offset--;
				}
				favorites.remove(original);
			}
			favorite = fav;
		} else {
			stack = EmiIngredientSerializer.getDeserialized(EmiIngredientSerializer.getSerialized(stack));
			if (stack.isEmpty()) {
				return;
			}
			for (int i = 0; i < favorites.size(); i++) {
				dev.emi.emi.runtime.EmiFavorite fav = favorites.get(i);
				if (getFavoritePage(fav) == targetPage && fav.getRecipeId() == null && fav.strictEquals(stack)) {
					dev.emi.emi.runtime.EmiFavorite removed = favorites.remove(i--);
					forgetFavoritePage(removed);
				}
			}
			favorite = new dev.emi.emi.runtime.EmiFavorite(stack, null, dev.emi.emi.runtime.EmiFavorite.Role.ITEM);
		}
		if (offset < 0) {
			offset = 0;
		}
		if (offset >= favorites.size()) {
			favorites.add(favorite);
		} else {
			favorites.add(offset, favorite);
		}
		setFavoritePage(favorite, targetPage);
		EmiFavoriteGroups.onFavoritesChanged();
		dev.emi.emi.runtime.EmiPersistentData.save();
	}

	public static void addFavorite(EmiIngredient stack, EmiRecipe context) {
		int targetPage = currentFavoritePage();
		if (stack instanceof dev.emi.emi.runtime.EmiFavorite.Synthetic) {
			return;
		}
		if (stack instanceof dev.emi.emi.runtime.EmiFavorite.Craftable craftable) {
			stack = craftable.stack;
		}
		if (stack instanceof dev.emi.emi.runtime.EmiFavorite f) {
			if (!removeFavorite(f)) {
				addToCurrentPage(f);
			}
		} else {
			stack = EmiIngredientSerializer.getDeserialized(EmiIngredientSerializer.getSerialized(stack));
			if (stack instanceof EmiStack es && context != null && context.getId() != null) {
				es = es.copy();
				if (es instanceof ItemEmiStack ies) {
					ies.getItemStack().setCount(1);
				}
				if (!es.isEmpty()) {
					for (int i = 0; i < favorites.size(); i++) {
						dev.emi.emi.runtime.EmiFavorite fav = favorites.get(i);
						if (getFavoritePage(fav) == targetPage && sameRecipe(fav.getRecipeId(), context) && fav.getRole() == dev.emi.emi.runtime.EmiFavorite.Role.RESULT && fav.strictEquals(es)) {
							return;
						}
					}
					addToCurrentPage(new dev.emi.emi.runtime.EmiFavorite(es, context, dev.emi.emi.runtime.EmiFavorite.Role.RESULT));
				}
			} else {
				if (stack.isEmpty()) {
					return;
				}
				for (int i = 0; i < favorites.size(); i++) {
					dev.emi.emi.runtime.EmiFavorite fav = favorites.get(i);
					if (getFavoritePage(fav) == targetPage && fav.getRecipeId() == null && fav.strictEquals(stack)) {
						return;
					}
				}
				addToCurrentPage(new dev.emi.emi.runtime.EmiFavorite(stack, null, dev.emi.emi.runtime.EmiFavorite.Role.ITEM));
			}
		}
		EmiFavoriteGroups.onFavoritesChanged();
		dev.emi.emi.runtime.EmiPersistentData.save();
	}

	public static dev.emi.emi.runtime.EmiFavorite addRecipeFavorite(EmiIngredient stack, EmiRecipe context, boolean preserveAmount) {
		return addRecipeFavorite(stack, context, dev.emi.emi.runtime.EmiFavorite.Role.RESULT, preserveAmount);
	}

	public static dev.emi.emi.runtime.EmiFavorite addRecipeFavorite(EmiIngredient stack, EmiRecipe context, dev.emi.emi.runtime.EmiFavorite.Role role, boolean preserveAmount) {
		return addRecipeFavoriteInternal(stack, context, role, preserveAmount, true);
	}

	static dev.emi.emi.runtime.EmiFavorite addRecipeFavoriteQuiet(EmiIngredient stack, EmiRecipe context, dev.emi.emi.runtime.EmiFavorite.Role role, boolean preserveAmount) {
		return addRecipeFavoriteInternal(stack, context, role, preserveAmount, false);
	}

	static void finishFavoriteBatch() {
		EmiFavoriteGroups.onFavoritesChanged();
		dev.emi.emi.runtime.EmiPersistentData.save();
	}

	private static dev.emi.emi.runtime.EmiFavorite addRecipeFavoriteInternal(EmiIngredient stack, EmiRecipe context, dev.emi.emi.runtime.EmiFavorite.Role role,
                                                                             boolean preserveAmount, boolean persist) {
		if (context == null || context.getId() == null || stack == null || stack.isEmpty() || role == dev.emi.emi.runtime.EmiFavorite.Role.ITEM) {
			return null;
		}
		JsonElement serialized = EmiIngredientSerializer.getSerialized(stack);
		if (serialized == null) {
			return null;
		}
		stack = EmiIngredientSerializer.getDeserialized(serialized);
		if (stack.isEmpty()) {
			return null;
		}
		stack = stack.copy();
		if (!preserveAmount) {
			stack.setAmount(1);
		}
		int targetPage = currentFavoritePage();
		for (dev.emi.emi.runtime.EmiFavorite favorite : favorites) {
			if (getFavoritePage(favorite) == targetPage && sameRecipe(favorite.getRecipeId(), context) && favorite.getRole() == role && favorite.strictEquals(stack)) {
				return favorite;
			}
		}
		dev.emi.emi.runtime.EmiFavorite favorite = new dev.emi.emi.runtime.EmiFavorite(stack, context, role);
		addToCurrentPage(favorite);
		if (persist) {
			finishFavoriteBatch();
		}
		return favorite;
	}

	public static List<dev.emi.emi.runtime.EmiFavorite> removeRecipeFavorites(EmiRecipe recipe) {
		List<dev.emi.emi.runtime.EmiFavorite> removed = Lists.newArrayList();
		int page = currentFavoritePage();
		for (int i = favorites.size() - 1; i >= 0; i--) {
			dev.emi.emi.runtime.EmiFavorite favorite = favorites.get(i);
			if (getFavoritePage(favorite) == page && sameRecipe(favorite.getRecipeId(), recipe)) {
				removed.add(favorite);
				favorites.remove(i);
				forgetFavoritePage(favorite);
			}
		}
		if (!removed.isEmpty()) {
			EmiFavoriteGroups.onFavoritesChanged();
			dev.emi.emi.runtime.EmiPersistentData.save();
		}
		return removed;
	}

	private static boolean sameRecipe(ResourceLocation a, EmiRecipe b) {
		if (a == null) {
			return b == null;
		}
		return b != null && b.getId() != null && a.equals(b.getId());
	}

	private static boolean sameRecipe(EmiRecipe a, EmiRecipe b) {
		if (a == b) {
			return true;
		}
		if (a == null || b == null || a.getId() == null || b.getId() == null) {
			return false;
		}
		return a.getId().equals(b.getId());
	}

	public static boolean removeFavorite(dev.emi.emi.runtime.EmiFavorite favorite) {
		for (int i = 0; i < favorites.size(); i++) {
			if (favorites.get(i) == favorite) {
				favorites.remove(i);
				forgetFavoritePage(favorite);
				EmiFavoriteGroups.onFavoritesChanged();
				EmiPersistentData.save();
				return true;
			}
		}
		return false;
	}

	public static void updateSynthetic(EmiPlayerInventory inv) {
		syntheticFavorites.clear();
		if (BoM.tree != null && BoM.craftingMode) {
			BoM.tree.calculateCost();
			Map<EmiIngredient, FlatMaterialCost> originalCosts = Maps.newHashMap(BoM.tree.cost.costs);
			Map<EmiIngredient, ChanceMaterialCost> chancedCosts = Maps.newHashMap(BoM.tree.cost.chanceCosts);
			Object2LongMap<EmiRecipe> originalBatches = new Object2LongLinkedOpenHashMap<>();
			Object2LongMap<EmiRecipe> originalAmounts = new Object2LongLinkedOpenHashMap<>();
			EmiPlayerInventory emptyInventory = new EmiPlayerInventory(List.of());
			emptyInventory.inventory.clear();
			BoM.tree.calculateProgress(emptyInventory);
			countRecipes(originalBatches, originalAmounts, BoM.tree.goal);
			BoM.tree.calculateProgress(inv);
			Object2LongMap<EmiRecipe> batches = new Object2LongLinkedOpenHashMap<>();
			Object2LongMap<EmiRecipe> amounts = new Object2LongLinkedOpenHashMap<>();
			countRecipes(batches, amounts, BoM.tree.goal);
			boolean hasSomething = false;
			for (Object2LongMap.Entry<EmiRecipe> entry : batches.object2LongEntrySet()) {
				EmiRecipe recipe = entry.getKey();
				long amount = amounts.getOrDefault(recipe, 0);
				long batch = entry.getLongValue();
				if (amount == 0) {
					continue;
				}
				hasSomething = true;
				int state = 0;
				if (inv.canCraft(recipe, batch)) {
					state = 2;
				} else if (inv.canCraft(recipe)) {
					state = 1;
				}
				syntheticFavorites.add(new dev.emi.emi.runtime.EmiFavorite.Synthetic(recipe, batch, amount, originalAmounts.getOrDefault(recipe, amount), state));
			}
			if (!hasSomething) {
				BoM.craftingMode = false;
			} else {
				for (FlatMaterialCost cost : BoM.tree.cost.costs.values()) {
					if (cost.amount > 0) {
						syntheticFavorites.add(new dev.emi.emi.runtime.EmiFavorite.Synthetic(cost.ingredient, cost.amount, originalCosts.getOrDefault(cost.ingredient, cost).amount));
					}
				}
				for (ChanceMaterialCost cost : BoM.tree.cost.chanceCosts.values()) {
					if (cost.getEffectiveAmount() > 0) {
						long needed = cost.getEffectiveAmount();
						if (chancedCosts.containsKey(cost.ingredient)) {
							ChanceMaterialCost original = chancedCosts.get(cost.ingredient);
							long done = (long) Math.ceil(original.amount * original.chance - cost.amount * cost.chance);
							needed = original.getEffectiveAmount() - done;
						}
						if (needed > 0) {
							syntheticFavorites.add(new dev.emi.emi.runtime.EmiFavorite.Synthetic(cost.ingredient, needed, needed));
						}
					}
				}
			}
		}
	}

	public static void countRecipes(Object2LongMap<EmiRecipe> batches, Object2LongMap<EmiRecipe> amounts, MaterialNode node) {
		if (node.recipe instanceof EmiResolutionRecipe recipe) {
			countRecipes(batches, amounts, node.children.get(0));
			return;
		}
		// Include empty costs for proper sorting
		if (node.recipe != null) {
			long amount = node.neededBatches;
			if (batches.containsKey(node.recipe)) {
				// Remove?
				amount += batches.getLong(node.recipe);
				batches.removeLong(node.recipe);
			}
			batches.put(node.recipe, amount);
			amount = node.totalNeeded;
			if (amounts.containsKey(node.recipe)) {
				// Remove?
				amount += amounts.getLong(node.recipe);
				amounts.removeLong(node.recipe);
			}
			amounts.put(node.recipe, amount);
			for (MaterialNode child : node.children) {
				countRecipes(batches, amounts, child);
			}
		}
	}

	private static class CompoundList<T> extends AbstractList<T> {
		private List<? extends T> a, b;

		public CompoundList(List<? extends T> a, List<? extends T> b) {
			this.a = a;
			this.b = b;
		}

		@Override
		public T get(int index) {
			if (index >= a.size()) {
				return b.get(index - a.size());
			}
			return a.get(index);
		}

		@Override
		public int size() {
			return a.size() + b.size();
		}
	}

	private static class FavoriteSidebarList extends AbstractList<dev.emi.emi.runtime.EmiFavorite> {
		@Override
		public EmiFavorite get(int index) {
			return EmiFavoriteGroups.sidebarFavorites().get(index);
		}

		@Override
		public int size() {
			return EmiFavoriteGroups.sidebarFavoriteCount();
		}
	}
}
