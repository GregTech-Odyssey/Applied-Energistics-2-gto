package dev.emi.emi.runtime;

import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.world.item.ItemStack;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import org.jetbrains.annotations.NotNull;
import net.minecraft.world.item.Item;
import appeng.api.stacks.AEItemKey;

public final class EmiCraftingToolCompat {
    private static Predicate<Item> toolPredicate = item -> false;
    private static ToLongFunction<ItemStack> craftingUses = stack -> -1;

    private EmiCraftingToolCompat() {
    }

    /** Called during EMI registration; the uses function must only read its shared stack argument. */
    public static void setGtToolSupport(@NotNull Predicate<Item> predicate, @NotNull ToLongFunction<ItemStack> uses) {
        toolPredicate = predicate;
        craftingUses = uses;
    }

    public static boolean isGtTool(@NotNull EmiStack stack) {
        return !stack.isEmpty() && stack.getKey() instanceof Item item && toolPredicate.test(item);
    }

    public static boolean isGtTool(@NotNull ItemStack stack) {
        return !stack.isEmpty() && toolPredicate.test(stack.getItem());
    }

    public static boolean isReusable(@NotNull EmiStack stack) {
        if (stack.isEmpty())
            return false;
        if (isGtTool(stack))
            return true;
        var remainder = stack.getRemainder();
        return !remainder.isEmpty() && remainder.getKey().equals(stack.getKey());
    }

    public static long getSafeCraftingUses(@NotNull EmiStack stack) {
        if (!isGtTool(stack))
            return -1;
        var item = stack.getAEKey();
        return craftingUses.applyAsLong(item instanceof AEItemKey key ? key.getReadOnlyStack() : stack.getItemStack());
    }

    public static long getSafeCraftingUses(@NotNull ItemStack stack) {
        return isGtTool(stack) ? craftingUses.applyAsLong(stack) : -1;
    }

    public static boolean matches(@NotNull EmiStack expected, @NotNull EmiStack actual) {
        if (expected.isEmpty() || actual.isEmpty())
            return false;
        if (expected.isEqual(actual))
            return true;
        return expected.getKey().equals(actual.getKey())
                && (isReusable(expected) || isGtTool(expected) && isGtTool(actual));
    }

    public static boolean matches(@NotNull ItemStack expected, @NotNull ItemStack actual) {
        if (expected.isEmpty() || actual.isEmpty())
            return false;
        return ItemStack.isSameItemSameTags(expected, actual)
                || expected.getItem() == actual.getItem() && isGtTool(expected) && isGtTool(actual);
    }
}
