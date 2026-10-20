package dev.emi.emi.runtime;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.emi.emi.EmiPort;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import dev.emi.emi.data.EmiData;
import dev.emi.emi.data.IndexStackData;
import dev.emi.emi.registry.EmiStackList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import com.gto.fastcollection.fastutil.O2ZOpenCacheHashMap;
import com.gto.fastcollection.fastutil.OpenCacheHashSet;

public class EmiHidden {
    // Data loaded
    public static Set<EmiIngredient> disabledStacks = new OpenCacheHashSet<>();
    public static List<IndexStackData.Filter> disabledFilters = Lists.newArrayList();
    private static final O2ZOpenCacheHashMap<String> FILTER_LOOKUP = new O2ZOpenCacheHashMap<>();
    public static Map<String, Boolean> disabledFilterLookup = FILTER_LOOKUP;
    // Plugin defined
    public static Set<EmiIngredient> pluginDisabledStacks = new OpenCacheHashSet<>();
    public static List<Predicate<EmiStack>> pluginDisabledFilters = Lists.newArrayList();
    // User edited
    public static Set<EmiIngredient> hiddenStacks = new LinkedHashSet<>();

    public static void clear() {
        disabledStacks.clear();
        disabledFilters.clear();
        FILTER_LOOKUP.clear();
        pluginDisabledStacks.clear();
        pluginDisabledFilters.clear();
    }

    public static void reload() {
        for (var supplier : EmiData.stackData) {
            var data = supplier.get();
            if (!data.disable())
                continue;
            for (var stack : data.removed()) {
                disabledStacks.add(stack);
                disabledStacks.addAll(stack.getEmiStacks());
            }
            disabledFilters.addAll(data.filters());
        }
    }

    private static boolean matchesDisabledFilter(String id) {
        for (var filter : disabledFilters)
            if (filter.filter().test(id))
                return true;
        return false;
    }

    public static JsonArray save() {
        JsonArray arr = new JsonArray();
        for (EmiIngredient stack : hiddenStacks) {
            JsonElement el = EmiIngredientSerializer.getSerialized(stack);
            if (el != null && !el.isJsonNull()) {
                arr.add(el);
            }
        }
        return arr;
    }

    public static void load(JsonArray arr) {
        hiddenStacks.clear();
        for (JsonElement el : arr) {
            EmiIngredient stack = EmiIngredientSerializer.getDeserialized(el).copy();
            if (!stack.isEmpty()) {
                for (EmiStack es : stack.getEmiStacks()) {
                    es.comparison(c -> EmiPort.compareStrict());
                }
                hiddenStacks.add(stack);
            }
        }
        EmiStackList.bakeFiltered();
    }

    public static boolean isHidden(EmiIngredient stack) {
        return hiddenStacks.contains(stack);
    }

    public static boolean isDisabled(EmiIngredient stack) {
        outer: for (EmiStack s : stack.getEmiStacks()) {
            if (disabledStacks.contains(s) || pluginDisabledStacks.contains(s)) {
                continue;
            }
            for (Predicate<EmiStack> predicate : pluginDisabledFilters) {
                if (predicate.test(s)) {
                    continue outer;
                }
            }
            boolean filtered = !disabledFilters.isEmpty() && FILTER_LOOKUP.computeIfAbsent(s.getId().toString(),
                    (java.util.function.Predicate<String>) EmiHidden::matchesDisabledFilter);
            if (filtered) {
                continue;
            }
            return false;
        }
        return !stack.isEmpty();
    }

    public static void setVisibility(EmiIngredient stack, boolean hide, boolean similar) {
        if (similar && stack.getEmiStacks().size() == 1) {
            EmiStack es = stack.getEmiStacks().get(0);
            for (EmiStack i : EmiStackList.stacks) {
                if (es.getId().equals(i.getId())) {
                    if (hide) {
                        hiddenStacks.add(i.copy().comparison(c -> EmiPort.compareStrict()));
                    } else {
                        hiddenStacks.remove(i);
                    }
                }
            }
        } else {
            if (hide) {
                stack = stack.copy();
                for (EmiStack es : stack.getEmiStacks()) {
                    es.comparison(c -> EmiPort.compareStrict());
                }
                hiddenStacks.add(stack);
            } else {
                hiddenStacks.remove(stack);
            }
        }
        EmiPersistentData.save();
        EmiStackList.bakeFiltered();
    }
}
