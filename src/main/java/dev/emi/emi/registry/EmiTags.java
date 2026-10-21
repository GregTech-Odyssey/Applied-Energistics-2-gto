package dev.emi.emi.registry;

import com.google.common.collect.Lists;
import dev.emi.emi.EmiPort;
import dev.emi.emi.api.stack.*;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.data.TagExclusions;
import dev.emi.emi.runtime.EmiHidden;
import dev.emi.emi.runtime.EmiReloadLog;
import dev.emi.emi.runtime.EmiTagKey;
import dev.emi.emi.util.InheritanceMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.Collections;
import java.util.Comparator;
import com.gto.fastcollection.fastutil.O2OOpenCacheHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

public class EmiTags {
    public static final InheritanceMap<EmiRegistryAdapter<?>> ADAPTERS_BY_CLASS = new InheritanceMap<>(
            new Reference2ObjectOpenHashMap<>());
    public static final Reference2ObjectOpenHashMap<Registry<?>, EmiRegistryAdapter<?>> ADAPTERS_BY_REGISTRY = new Reference2ObjectOpenHashMap<>();
    public static final ResourceLocation HIDDEN_FROM_RECIPE_VIEWERS = EmiPort.id("forge", "hidden_from_recipe_viewers");
    public static final Reference2ObjectOpenHashMap<TagKey<?>, ResourceLocation> MODELED_TAGS = new Reference2ObjectOpenHashMap<>();
    private static final O2OOpenCacheHashMap<Set<?>, ObjectArrayList<EmiTagKey<?>>> CACHED_TAGS = new O2OOpenCacheHashMap<>();
    private static final Reference2ObjectOpenHashMap<EmiTagKey<?>, List<?>> TAG_VALUES = new Reference2ObjectOpenHashMap<>();
    private static final O2OOpenCacheHashMap<ResourceLocation, ObjectArrayList<EmiTagKey<?>>> SORTED_TAGS = new O2OOpenCacheHashMap<>();
    public static final List<EmiTagKey<?>> TAGS = Lists.newArrayList();
    public static TagExclusions exclusions = new TagExclusions();

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> List<EmiStack> getValues(EmiTagKey<T> key) {
        var values = (List<T>) TAG_VALUES.get(key);
        if (values == null)
            return Collections.emptyList();
        var adapter = (EmiRegistryAdapter<T>) ADAPTERS_BY_REGISTRY.get(key.registry());
        if (adapter == null)
            return Collections.emptyList();
        var stacks = new ObjectArrayList<EmiStack>(values.size());
        for (var value : values)
            stacks.add(adapter.of(value, EmiPort.emptyExtraData(), 1));
        return stacks;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> List<EmiStack> getRawValues(EmiTagKey<T> key) {
        var registry = key.registry();
        var values = key.getList();
        var stacks = new ObjectArrayList<EmiStack>(values.size());
        if (registry == EmiPort.getBlockRegistry()) {
            for (var value : values)
                stacks.add(EmiStack.of((Block) value));
        } else {
            var adapter = (EmiRegistryAdapter<T>) ADAPTERS_BY_REGISTRY.get(registry);
            if (adapter == null)
                return Collections.emptyList();
            for (var value : values)
                stacks.add(adapter.of(value, EmiPort.emptyExtraData(), 1));
        }
        return stacks;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> EmiIngredient getIngredient(Class<T> clazz, List<EmiStack> stacks, long amount) {
        var map = new Reference2ObjectOpenHashMap<T, EmiStack>(stacks.size());
        EmiStack first = null;
        for (var stack : stacks) {
            if (stack.isEmpty())
                continue;
            var existing = map.put((T) stack.getKey(), stack);
            if (existing != null && !stack.equals(existing))
                return new ListEmiIngredient(stacks, amount);
            first = stack;
        }
        if (map.isEmpty())
            return EmiStack.EMPTY;
        if (map.size() == 1)
            return first.copy().setAmount(amount);
        var adapter = (EmiRegistryAdapter<T>) ADAPTERS_BY_CLASS.get(clazz);
        if (adapter == null)
            return new ListEmiIngredient(stacks, amount);
        var keys = (ObjectArrayList<EmiTagKey<T>>) (Object) CACHED_TAGS.get(map.keySet());
        if (keys != null) {
            for (var key : keys)
                for (var value : key.getList())
                    map.remove(value);
        } else {
            keys = new ObjectArrayList<>();
            // keySet is consumed below, so the cache needs an independent, stable key snapshot.
            var original = new ReferenceOpenHashSet<>(map.keySet());
            for (var key : getTags(adapter.getRegistry())) {
                var values = key.getList();
                if (values.size() < 2)
                    continue;
                if (map.keySet().containsAll(values)) {
                    for (var value : values)
                        map.remove(value);
                    keys.add(key);
                }
                if (map.isEmpty())
                    break;
            }
            CACHED_TAGS.put(original, (ObjectArrayList) keys);
        }
        if (keys.isEmpty())
            return new ListEmiIngredient(stacks, amount);
        if (map.isEmpty() && keys.size() == 1)
            return tagIngredient(keys.getFirst(), amount);
        var alternatives = new ObjectArrayList<EmiIngredient>(map.size() + keys.size());
        for (var stack : map.values())
            alternatives.add(stack.copy().setAmount(1));
        for (var key : keys)
            alternatives.add(tagIngredient(key, 1));
        return new ListEmiIngredient(alternatives, amount);
    }

    private static EmiIngredient tagIngredient(EmiTagKey<?> key, long amount) {
        List<?> list = TAG_VALUES.get(key);
        if (list == null || list.isEmpty()) {
            return EmiStack.EMPTY;
        } else if (list.size() == 1) {
            return singleTagStack(key, list.getFirst(), amount);
        } else {
            return new TagEmiIngredient(key, amount);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> EmiStack singleTagStack(EmiTagKey<T> key, Object value, long amount) {
        return stackFromKey(key, (T) value).setAmount(amount);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> List<EmiTagKey<T>> getTags(Registry<T> registry) {
        var tags = SORTED_TAGS.get(registry.key().location());
        return tags == null ? Collections.emptyList() : (List<EmiTagKey<T>>) (List) tags;
    }

    public static void registerTagModels(ResourceManager manager, Consumer<ResourceLocation> consumer) {
        EmiTags.MODELED_TAGS.clear();
        for (ResourceLocation id : EmiPort.findResources(manager, "models/tag", s -> s.endsWith(".json"))) {
            String path = id.getPath();
            path = path.substring(11, path.length() - 5);
            String[] parts = path.split("/");
            if (parts.length > 1) {
                TagKey<?> key = TagKey.create(ResourceKey.createRegistryKey(EmiPort.id("minecraft", parts[0])),
                        EmiPort.id(id.getNamespace(), path.substring(1 + parts[0].length())));
                ResourceLocation mid = EmiPort.id(id.getNamespace(), "tag/" + path);
                EmiTags.MODELED_TAGS.put(key, mid);
                consumer.accept(mid);
            }
        }
        for (ResourceLocation id : EmiPort.findResources(manager, "models/item/tags", s -> s.endsWith(".json"))) {
            String path = id.getPath();
            path = path.substring(0, path.length() - 5);
            String[] parts = path.substring(17).split("/");
            if (id.getNamespace().equals("emi") && parts.length > 1) {
                ResourceLocation mid = new ModelResourceLocation(id.getNamespace(), path.substring(12), "inventory");
                EmiTags.MODELED_TAGS.put(TagKey.create(EmiPort.getItemRegistry().key(),
                        EmiPort.id(parts[0], path.substring(18 + parts[0].length()))), mid);
                consumer.accept(mid);
            }
        }
    }

    public static void reload() {
        EmiIngredientCache.clear();
        EmiTagKey.reload();
        TAGS.clear();
        SORTED_TAGS.clear();
        TAG_VALUES.clear();
        CACHED_TAGS.clear();
        for (Registry<?> registry : ADAPTERS_BY_REGISTRY.keySet()) {
            reloadTags(registry);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static <T> void reloadTags(Registry<T> registry) {
        var hidden = EmiTagKey.of(registry, HIDDEN_FROM_RECIPE_VIEWERS).getSet();
        var rid = registry.key().location();
        var tags = new ObjectArrayList<EmiTagKey<T>>();
        var iterator = registry.getTagNames().iterator();
        while (iterator.hasNext()) {
            var key = EmiTagKey.of(iterator.next());
            if (!exclusions.contains(rid, key.id()) && !hidden.containsAll(key.getList()))
                tags.add(key);
        }
        logUntranslatedTags(tags);
        tags = consolodateTags(tags);
        var adapter = (EmiRegistryAdapter<T>) ADAPTERS_BY_REGISTRY.get(registry);
        for (var key : tags) {
            var original = key.getList();
            var visible = new ObjectArrayList<T>(original.size());
            for (var value : original) {
                if (!EmiHidden.isDisabled(adapter.of(value, EmiPort.emptyExtraData(), 1)))
                    visible.add(value);
            }
            TAG_VALUES.put(key, visible.isEmpty() ? original : visible);
        }
        var byName = tags.clone();
        byName.sort(Comparator.comparing(EmiTagKey::idString));
        TAGS.addAll(byName);
        tags.sort((a, b) -> Integer.compare(b.getList().size(), a.getList().size()));
        SORTED_TAGS.put(rid, (ObjectArrayList) tags);
    }

    @SuppressWarnings("unchecked")
    private static <T> EmiStack stackFromKey(EmiTagKey<T> key, T t) {
        EmiRegistryAdapter<T> adapter = (EmiRegistryAdapter<T>) ADAPTERS_BY_REGISTRY.get(key.registry());
        if (adapter != null) {
            return adapter.of(t, EmiPort.emptyExtraData(), 1);
        }
        throw new UnsupportedOperationException("Unsupported tag registry " + key);
    }

    private static <T> void logUntranslatedTags(List<EmiTagKey<T>> tags) {
        if (EmiConfig.logUntranslatedTags) {
            var untranslated = new ObjectArrayList<String>(tags.size());
            for (EmiTagKey<T> tag : tags) {
                if (!tag.hasTranslation()) {
                    untranslated.add(tag.id().toString());
                }
            }
            if (!untranslated.isEmpty()) {
                untranslated.sort(Comparator.naturalOrder());
                for (String tag : untranslated) {
                    EmiReloadLog.warn("Untranslated tag #" + tag);
                }
                EmiReloadLog.info(
                        " Tag warning can be disabled in the config, EMI docs describe how to add a translation or exclude tags.");
            }
        }
    }

    private static <T> ObjectArrayList<EmiTagKey<T>> consolodateTags(ObjectArrayList<EmiTagKey<T>> tags) {
        var map = new O2OOpenCacheHashMap<Set<T>, EmiTagKey<T>>(tags.size());
        for (var key : tags) {
            var values = key.getSet();
            var original = map.get(values);
            map.put(values, original == null ? key : betterTag(key, original));
        }
        return new ObjectArrayList<>(map.values());
    }

    private static <T> EmiTagKey<T> betterTag(EmiTagKey<T> a, EmiTagKey<T> b) {
        if (a.hasTranslation() != b.hasTranslation()) {
            return a.hasTranslation() ? a : b;
        }
        if (a.hasCustomModel() != b.hasCustomModel()) {
            return a.hasCustomModel() ? a : b;
        }
        String an = a.id().getNamespace();
        String bn = b.id().getNamespace();
        if (!an.equals(bn)) {
            if (an.equals("minecraft")) {
                return a;
            } else if (bn.equals("minecraft")) {
                return b;
            } else if (an.equals("forge")) {
                return a;
            } else if (bn.equals("forge")) {
                return b;
            }
        }
        return a.idString().length() <= b.idString().length() ? a : b;
    }
}
