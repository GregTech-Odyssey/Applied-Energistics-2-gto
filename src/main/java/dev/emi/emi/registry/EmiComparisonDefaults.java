package dev.emi.emi.registry;

import dev.emi.emi.api.stack.Comparison;

import java.util.Map;

public class EmiComparisonDefaults {
    public static Map<Object, Comparison> comparisons = Map.of();

    public static Comparison get(Object obj) {
        var comparison = comparisons.get(obj);
        return comparison == null ? Comparison.DEFAULT_COMPARISON : comparison;
    }
}
