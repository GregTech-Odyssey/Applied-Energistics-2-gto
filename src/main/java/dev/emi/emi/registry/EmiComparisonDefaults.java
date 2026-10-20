package dev.emi.emi.registry;

import dev.emi.emi.api.stack.Comparison;

import java.util.Map;

public class EmiComparisonDefaults {
	public static Map<Object, Comparison> comparisons = Map.of();

	public static Comparison get(Object obj) {
		if (comparisons.containsKey(obj)) {
			return comparisons.get(obj);
		}
		return Comparison.DEFAULT_COMPARISON;
	}
}
