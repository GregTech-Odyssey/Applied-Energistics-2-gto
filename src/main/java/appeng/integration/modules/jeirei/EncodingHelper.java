package appeng.integration.modules.jeirei;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import com.google.common.math.LongMath;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyIntMap;
import appeng.api.stacks.GenericStack;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.FakeSlot;
import appeng.parts.encoding.EncodingMode;
import appeng.util.CraftingRecipeUtil;

public final class EncodingHelper {
    private EncodingHelper() {
    }

    /**
     * Order of priority: - Craftable Items - Undamaged Items - Items the player has the most of
     */
    static final Comparator<GridInventoryEntry> ENTRY_COMPARATOR = Comparator
            .<GridInventoryEntry>comparingInt(entry -> entry.isCraftable() ? 1 : 0)
            .thenComparingInt(entry -> isUndamaged(entry) ? 1 : 0)
            .thenComparingLong(GridInventoryEntry::getStoredAmount);

    private static boolean isUndamaged(GridInventoryEntry entry) {
        return !(entry.getWhat() instanceof AEItemKey itemKey) || !itemKey.isDamaged();
    }

    public static void encodeProcessingRecipe(PatternEncodingTermMenu menu, List<List<GenericStack>> genericIngredients,
            List<GenericStack> genericResults) {
        menu.setMode(EncodingMode.PROCESSING);

        // Note that this runs on the client and getClientRepo() is guaranteed to be available there.
        var ingredientPriorities = getIngredientPriorities(menu, ENTRY_COMPARATOR);

        encodeBestMatchingStacksIntoSlots(
                genericIngredients,
                ingredientPriorities,
                menu.getProcessingInputSlots());
        var outputs = new ObjectArrayList<GenericStack>(genericResults.size());
        for (var output : genericResults)
            addOrMerge(outputs, output);
        writeSlots(outputs, menu.getProcessingOutputSlots());
    }

    private static void encodeBestMatchingStacksIntoSlots(List<List<GenericStack>> possibleInputsBySlot,
            Reference2IntOpenHashMap<AEKey> ingredientPriorities,
            FakeSlot[] slots) {
        var encodedInputs = new ObjectArrayList<GenericStack>(possibleInputsBySlot.size());
        for (var genericIngredient : possibleInputsBySlot) {
            if (!genericIngredient.isEmpty()) {
                addOrMerge(encodedInputs, findBestIngredient(ingredientPriorities, genericIngredient));
            }
        }

        writeSlots(encodedInputs, slots);
    }

    private static void writeSlots(ObjectArrayList<GenericStack> stacks, FakeSlot[] slots) {
        for (int i = 0; i < slots.length; i++) {
            slots[i].setFilterTo(i < stacks.size() ? GenericStack.wrapInItemStack(stacks.get(i)) : ItemStack.EMPTY);
        }
    }

    public static boolean isSupportedCraftingRecipe(@Nullable Recipe<?> recipe) {
        if (recipe == null) {
            return false;
        }
        var recipeType = recipe.getType();

        return recipeType == RecipeType.CRAFTING
                || recipeType == RecipeType.STONECUTTING
                || recipeType == RecipeType.SMITHING;
    }

    public static void encodeCraftingRecipe(PatternEncodingTermMenu menu,
            @Nullable Recipe<?> recipe,
            List<List<GenericStack>> genericIngredients,
            Predicate<ItemStack> visiblePredicate) {
        if (recipe != null && recipe.getType().equals(RecipeType.STONECUTTING)) {
            menu.setMode(EncodingMode.STONECUTTING);
            menu.setStonecuttingRecipeId(recipe.getId());
        } else if (recipe != null && recipe.getType().equals(RecipeType.SMITHING)) {
            menu.setMode(EncodingMode.SMITHING_TABLE);
        } else {
            menu.setMode(EncodingMode.CRAFTING);
        }

        // Note that this runs on the client and getClientRepo() is guaranteed to be available there.
        var prioritizedNetworkInv = getIngredientPriorities(menu, ENTRY_COMPARATOR);

        var encodedInputs = NonNullList.withSize(menu.getCraftingGridSlots().length, ItemStack.EMPTY);

        if (recipe != null) {
            // When we have access to a crafting recipe, we'll switch modes and try to find suitable
            // ingredients based on the recipe ingredients, which allows for fuzzy-matching.
            var ingredients3x3 = CraftingRecipeUtil.ensure3by3CraftingMatrix(recipe);

            // Find a good match for every ingredient
            for (int slot = 0; slot < ingredients3x3.size(); slot++) {
                var ingredient = ingredients3x3.get(slot);
                if (ingredient.isEmpty()) {
                    continue; // Skip empty slots
                }

                // Due to how some crafting recipes work, the ingredient can match more than just one item in the
                // network inventory. We'll find all network inventory entries that it matches and sort them
                // according to their suitability for encoding a pattern
                AEItemKey bestKey = null;
                int bestPriority = Integer.MIN_VALUE;
                for (var iterator = prioritizedNetworkInv.reference2IntEntrySet().fastIterator(); iterator.hasNext();) {
                    var entry = iterator.next();
                    if (entry.getKey() instanceof AEItemKey itemKey && itemKey.matches(ingredient)
                            && (bestKey == null || entry.getIntValue() > bestPriority)) {
                        bestKey = itemKey;
                        bestPriority = entry.getIntValue();
                    }
                }
                ItemStack bestIngredient;
                if (bestKey != null) {
                    bestIngredient = bestKey.toStack();
                } else {
                    var candidates = ingredient.getItems();
                    bestIngredient = candidates[0];
                    for (var candidate : candidates) {
                        if (visiblePredicate.test(candidate)) {
                            bestIngredient = candidate;
                            break;
                        }
                    }
                }

                encodedInputs.set(slot, bestIngredient);
            }
        } else {
            for (int slot = 0; slot < genericIngredients.size(); slot++) {
                var genericIngredient = genericIngredients.get(slot);
                if (genericIngredient.isEmpty()) {
                    continue; // Skip empty slots
                }

                var bestIngredient = findBestIngredient(prioritizedNetworkInv, genericIngredient).what();

                // Clamp amounts to 1 in crafting table mode
                if (bestIngredient instanceof AEItemKey itemKey) {
                    encodedInputs.set(slot, itemKey.toStack());
                } else {
                    encodedInputs.set(slot, GenericStack.wrapInItemStack(bestIngredient, 1));
                }
            }
        }

        for (int i = 0; i < encodedInputs.size(); i++) {
            ItemStack encodedInput = encodedInputs.get(i);
            NetworkHandler.instance().sendToServer(new InventoryActionPacket(
                    InventoryAction.SET_FILTER, menu.getCraftingGridSlots()[i].index, encodedInput));
        }

        // Clear out the processing outputs
        for (var outputSlot : menu.getProcessingOutputSlots()) {
            NetworkHandler.instance().sendToServer(new InventoryActionPacket(
                    InventoryAction.SET_FILTER, outputSlot.index, ItemStack.EMPTY));
        }

    }

    /** Selects the highest priority from a nonempty list, keeping its first candidate when priorities tie. */
    public static @NotNull GenericStack findBestIngredient(
            @NotNull Reference2IntOpenHashMap<AEKey> ingredientPriorities,
            @NotNull List<GenericStack> possibleIngredients) {
        var best = possibleIngredients.getFirst();
        int priority = ingredientPriorities.getOrDefault(best.what(), Integer.MIN_VALUE);
        for (int i = 1; i < possibleIngredients.size(); i++) {
            var candidate = possibleIngredients.get(i);
            int candidatePriority = ingredientPriorities.getOrDefault(candidate.what(), Integer.MIN_VALUE);
            if (candidatePriority > priority) {
                best = candidate;
                priority = candidatePriority;
            }
        }
        return best;
    }

    /**
     * In processing mode it makes sense to merge stacks of the same type together.
     */
    private static void addOrMerge(ObjectArrayList<GenericStack> stacks, GenericStack newStack) {
        for (int i = 0; i < stacks.size(); i++) {
            var existingStack = stacks.get(i);
            if (Objects.equals(existingStack.what(), newStack.what())) {
                // Add the new amount onto the existing amount
                long newAmount = LongMath.saturatedAdd(existingStack.amount(), newStack.amount());
                stacks.set(i, new GenericStack(newStack.what(), newAmount));

                // Determine if the addition overflowed. If it did, add the remainder as a new stack.
                long overflow = newStack.amount() - (newAmount - existingStack.amount());
                if (overflow > 0) {
                    stacks.add(new GenericStack(newStack.what(), overflow));
                }
                return;
            }
        }

        stacks.add(newStack);
    }

    /**
     * Compute a map from all keys in the network inventory to their position when sorted by priority. Also takes the
     * player inventory into account for any items that are not already in the grid.
     * <p/>
     * Higher means higher priority.
     */
    public static Reference2IntOpenHashMap<AEKey> getIngredientPriorities(MEStorageMenu menu,
            Comparator<GridInventoryEntry> comparator) {
        var orderedEntries = new ObjectArrayList<>(menu.getClientRepo().getAllEntries());
        orderedEntries.sort(comparator);

        var result = new AEKeyIntMap<AEKey>(orderedEntries.size());
        for (int i = 0; i < orderedEntries.size(); i++) {
            result.put(orderedEntries.get(i).getWhat(), i);
        }

        // Also consider the player inventory, but only as the last resort
        for (var item : menu.getPlayerInventory().items) {
            var key = AEItemKey.of(item);
            if (key != null) {
                // Use -1 as lower priority than the lowest network entry (which start at 0)
                result.putIfAbsent(key, -1);
            }
        }

        return result;
    }
}
