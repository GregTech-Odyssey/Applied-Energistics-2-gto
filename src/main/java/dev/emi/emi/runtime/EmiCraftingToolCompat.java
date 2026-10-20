package dev.emi.emi.runtime;

import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EmiCraftingToolCompat {
	private static final String GT_TOOL_INTERFACE = "com.gregtechceu.gtceu.api.item.IGTTool";
	private static final Map<Class<?>, Boolean> GT_TOOL_CACHE = new ConcurrentHashMap<>();
	private static final Map<Class<?>, Method> TOOL_STATS_METHOD_CACHE = new ConcurrentHashMap<>();
	private static final Map<Class<?>, Method> CRAFT_DAMAGE_METHOD_CACHE = new ConcurrentHashMap<>();

	private EmiCraftingToolCompat() {
	}

	public static boolean isGtTool(EmiStack stack) {
		return stack != null && !stack.isEmpty() && isGtTool(stack.getItemStack());
	}

	public static boolean isGtTool(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return GT_TOOL_CACHE.computeIfAbsent(stack.getItem().getClass(), EmiCraftingToolCompat::implementsGtTool);
	}

	public static boolean isReusable(EmiStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		if (isGtTool(stack)) {
			return true;
		}
		try {
			EmiStack remainder = stack.getRemainder();
			return remainder != null && !remainder.isEmpty() && remainder.getKey().equals(stack.getKey());
		} catch (Throwable ignored) {
			return false;
		}
	}

	public static long getSafeCraftingUses(EmiStack stack) {
		if (stack == null || stack.isEmpty()) {
			return -1L;
		}
		return getSafeCraftingUses(stack.getItemStack());
	}

	public static long getSafeCraftingUses(ItemStack stack) {
		if (!isGtTool(stack)) {
			return -1L;
		}
		if (!stack.isDamageableItem()) {
			return Long.MAX_VALUE;
		}
		int maxDamage = stack.getMaxDamage();
		int damage = stack.getDamageValue();
		if (maxDamage <= 0) {
			return Long.MAX_VALUE;
		}
		int damagePerCraft = getToolDamagePerCraft(stack);
		if (damagePerCraft <= 0) {
			return 0L;
		}
		long remaining = Math.max(0L, (long) maxDamage - damage);
		return remaining / damagePerCraft;
	}

	public static boolean matches(EmiStack expected, EmiStack actual) {
		if (expected == null || actual == null || expected.isEmpty() || actual.isEmpty()) {
			return false;
		}
		if (expected.isEqual(actual)) {
			return true;
		}
		if (isReusable(expected) && expected.getKey().equals(actual.getKey())) {
			return true;
		}
		ItemStack expectedStack = expected.getItemStack();
		ItemStack actualStack = actual.getItemStack();
		return isGtTool(expectedStack) && isGtTool(actualStack) && expectedStack.getItem() == actualStack.getItem();
	}

	public static boolean matches(ItemStack expected, ItemStack actual) {
		if (expected == null || actual == null || expected.isEmpty() || actual.isEmpty()) {
			return false;
		}
		if (ItemStack.isSameItemSameTags(expected, actual)) {
			return true;
		}
		return isGtTool(expected) && isGtTool(actual) && expected.getItem() == actual.getItem();
	}

	private static int getToolDamagePerCraft(ItemStack stack) {
		try {
			Object item = stack.getItem();
			Method statsMethod = TOOL_STATS_METHOD_CACHE.computeIfAbsent(item.getClass(), type -> {
				try {
					return type.getMethod("getToolStats");
				} catch (ReflectiveOperationException ignored) {
					return null;
				}
			});
			if (statsMethod == null) {
				return 0;
			}
			Object stats = statsMethod.invoke(item);
			if (stats == null) {
				return 0;
			}
			Method damageMethod = CRAFT_DAMAGE_METHOD_CACHE.computeIfAbsent(stats.getClass(), type -> {
				try {
					return type.getMethod("getToolDamagePerCraft", ItemStack.class);
				} catch (ReflectiveOperationException ignored) {
					return null;
				}
			});
			if (damageMethod == null) {
				return 0;
			}
			Object value = damageMethod.invoke(stats, stack);
			return value instanceof Number number ? Math.max(0, number.intValue()) : 0;
		} catch (Throwable ignored) {
			return 0;
		}
	}

	private static boolean implementsGtTool(Class<?> type) {
		if (type == null) {
			return false;
		}
		for (Class<?> iface : type.getInterfaces()) {
			if (GT_TOOL_INTERFACE.equals(iface.getName()) || implementsGtTool(iface)) {
				return true;
			}
		}
		return implementsGtTool(type.getSuperclass());
	}
}
