package dev.emi.emi.registry;

import com.google.common.collect.Lists;
import dev.emi.emi.EmiPort;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiRegistryAdapter;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.IndexSource;
import dev.emi.emi.data.EmiAlias;
import dev.emi.emi.data.EmiData;
import dev.emi.emi.data.IndexStackData;
import dev.emi.emi.runtime.EmiHidden;
import dev.emi.emi.runtime.EmiLog;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import com.gto.fastcollection.fastutil.O2IOpenCacheHashMap;
import com.gto.fastcollection.fastutil.O2IOpenCustomCacheHashMap;
import com.gto.fastcollection.fastutil.OpenCacheHashSet;
import com.gto.fastcollection.fastutil.OpenCustomCacheHashSet;

public class EmiStackList {
    private static final TagKey<Item> ITEM_HIDDEN = TagKey.create(EmiPort.getItemRegistry().key(),
            EmiTags.HIDDEN_FROM_RECIPE_VIEWERS);
    private static final TagKey<Block> BLOCK_HIDDEN = TagKey.create(EmiPort.getBlockRegistry().key(),
            EmiTags.HIDDEN_FROM_RECIPE_VIEWERS);
    private static final TagKey<Fluid> FLUID_HIDDEN = TagKey.create(EmiPort.getFluidRegistry().key(),
            EmiTags.HIDDEN_FROM_RECIPE_VIEWERS);
    public static List<EmiAlias.Baked> registryAliases = Lists.newArrayList();
    public static List<Predicate<EmiStack>> invalidators = Lists.newArrayList();
    public static List<EmiStack> stacks = List.of();
    public static List<EmiStack> filteredStacks = List.of();
    private static final O2IOpenCustomCacheHashMap<EmiStack> strictIndices = new O2IOpenCustomCacheHashMap<>(
            new StrictHashStrategy());
    private static final O2IOpenCacheHashMap<Object> keyIndices = new O2IOpenCacheHashMap<>();

    public static void clear() {
        invalidators.clear();
        registryAliases.clear();
        stacks = List.of();
        strictIndices.clear();
        keyIndices.clear();
    }

    public static void reload() {
        Minecraft client = Minecraft.getInstance();
        CreativeModeTab.ItemDisplayParameters context = new CreativeModeTab.ItemDisplayParameters(
                client.player.connection.enabledFeatures(), false, client.level.registryAccess());
        var groups = new ObjectArrayList<IndexGroup>();
        var namespaceGroups = new LinkedHashMap<String, IndexGroup>();
        for (Item item : EmiPort.getItemRegistry()) {
            String itemName = "null";
            try {
                itemName = item.toString();
                EmiStack stack = EmiStack.of(item);
                namespaceGroups.computeIfAbsent(stack.getId().getNamespace(), (k) -> new IndexGroup()).stacks
                        .add(stack);
            } catch (Exception e) {
                EmiLog.error("Item " + itemName
                        + " threw while EMI was attempting to construct the index, items may be missing.", e);
            }
        }
        if (EmiConfig.indexSource != IndexSource.REGISTERED) {
            // There is an unwritten convention that ItemGroup.updateEntries is only invoked on the main thread
            long groupReloadStart = System.currentTimeMillis();
            EmiLog.info("Reloading item groups on client thread...");
            Reference2ReferenceOpenHashMap<CreativeModeTab, Collection<ItemStack>> itemGroupToStacksMap = client
                    .submit(() -> {
                        var map = new Reference2ReferenceOpenHashMap<CreativeModeTab, Collection<ItemStack>>();
                        Consumer<CreativeModeTab> itemGroupConsumer = group -> {
                            String groupName = "null";
                            try {
                                groupName = group.getDisplayName().toString();
                                group.buildContents(context);
                                map.put(group, group.getSearchTabDisplayItems());
                            } catch (Exception e) {
                                EmiLog.error("Creative item group " + groupName
                                        + " threw while EMI was attempting to construct the index, items may be missing.",
                                        e);
                            }
                        };
                        List<CreativeModeTab> itemGroups = CreativeModeTabs.allTabs();
                        // Category item groups must be updated before non-category ones, otherwise the search group
                        // will
                        // read outdated item lists
                        for (var group : itemGroups)
                            if (group.getType() == CreativeModeTab.Type.CATEGORY)
                                itemGroupConsumer.accept(group);
                        for (var group : itemGroups)
                            if (group.getType() != CreativeModeTab.Type.CATEGORY)
                                itemGroupConsumer.accept(group);
                        return map;
                    }).join();
            EmiLog.info("Reloading item groups on client thread took " + (System.currentTimeMillis() - groupReloadStart)
                    + "ms");
            for (CreativeModeTab group : CreativeModeTabs.allTabs()) {
                String groupName = "null";
                try {
                    groupName = group.getDisplayName().getString();
                    var usedNamespaces = new O2IOpenCacheHashMap<String>();
                    IndexGroup ig = new IndexGroup();
                    Collection<ItemStack> searchStacks = itemGroupToStacksMap.get(group);
                    for (ItemStack stack : searchStacks) {
                        EmiStack es = EmiStack.of(stack);
                        String namespace = es.getId().getNamespace();
                        usedNamespaces.addTo(namespace, 1);
                        ig.stacks.add(es);
                    }
                    if (EmiConfig.indexSource == IndexSource.CREATIVE) {
                        for (String namespace : usedNamespaces.keySet()) {
                            IndexGroup ng = namespaceGroups.get(namespace);
                            if (ng != null) {
                                if (usedNamespaces.getInt(namespace) * 3 >= searchStacks.size()
                                        || usedNamespaces.getInt(namespace) * 3 >= ng.stacks.size()) {
                                    ng.suppressedBy.add(ig);
                                }
                            }
                        }
                    }
                    groups.add(ig);
                } catch (Exception e) {
                    EmiLog.error(
                            "Creative item group " + groupName
                                    + " threw while EMI was attempting to construct the index, items may be missing.",
                            e);
                }
            }
        }
        groups.addAll(namespaceGroups.values());
        IndexGroup fluidGroup = new IndexGroup();
        for (Fluid fluid : EmiPort.getFluidRegistry()) {
            String fluidName = null;
            try {
                fluidName = fluid.toString();
                if (fluid.isSource(fluid.defaultFluidState())
                        || (fluid instanceof FlowingFluid ff && ff.getSource() == Fluids.EMPTY)) {
                    EmiStack fs = EmiStack.of(fluid);
                    fluidGroup.stacks.add(fs);
                }
            } catch (Exception e) {
                EmiLog.error("Fluid  " + fluidName
                        + " threw while EMI was attempting to construct the index, stack may be missing.", e);
            }
        }
        groups.add(fluidGroup);

        int capacity = 0;
        for (var group : groups)
            capacity += group.stacks.size();
        var added = new OpenCustomCacheHashSet<EmiStack>(capacity, new StrictHashStrategy());

        stacks = new ObjectArrayList<>(capacity);
        for (IndexGroup group : groups) {
            if (group.shouldDisplay()) {
                for (EmiStack stack : group.stacks) {
                    if (added.add(stack)) {
                        stacks.add(stack);
                    }
                }
            }
        }
    }

    @SuppressWarnings({ "deprecation", "unchecked" })
    private static <T> boolean isHiddenFromRecipeViewers(T key) {
        if (key instanceof Item i) {
            if (i instanceof BlockItem bi && bi.getBlock().defaultBlockState().is(BLOCK_HIDDEN)) {
                return true;
            } else if (i.builtInRegistryHolder().is(ITEM_HIDDEN)) {
                return true;
            }
        } else if (key instanceof Fluid f) {
            if (f.is(FLUID_HIDDEN)) {
                return true;
            }
        } else {
            EmiRegistryAdapter<T> adapter = (EmiRegistryAdapter<T>) dev.emi.emi.registry.EmiTags.ADAPTERS_BY_CLASS
                    .get(key.getClass());
            if (adapter != null) {
                return adapter.getRegistry().wrapAsHolder(key)
                        .is(TagKey.create(adapter.getRegistry().key(), EmiTags.HIDDEN_FROM_RECIPE_VIEWERS));
            }
        }
        return false;
    }

    public static void bake(List<EmiStack> suppliedStacks) {
        stacks = suppliedStacks;
        removeHiddenStacks(stacks);
        strictIndices.clear();
        keyIndices.clear();
        for (int i = 0; i < stacks.size(); i++) {
            var stack = stacks.get(i);
            strictIndices.put(stack, i);
            keyIndices.put(stack.getKey(), i);
        }
        bakeFiltered();
    }

    private static void removeHiddenStacks(List<EmiStack> stacks) {
        var invalidators = EmiStackList.invalidators;
        stacks.removeIf(s -> {
            try {
                if (s.isEmpty())
                    return true;
                for (Predicate<EmiStack> invalidator : invalidators) {
                    if (invalidator.test(s))
                        return true;
                }
                return isHiddenFromRecipeViewers(s.getKey());
            } catch (Throwable t) {
                EmiLog.error("Stack threw error while baking", t);
                return true;
            }
        });
        for (Supplier<IndexStackData> supplier : EmiData.stackData) {
            IndexStackData data = supplier.get();
            if (!data.removed().isEmpty()) {
                var removed = new OpenCacheHashSet<EmiStack>(data.removed().size());
                for (EmiIngredient ingredient : data.removed()) {
                    for (EmiStack stack : ingredient.getEmiStacks()) {
                        removed.add(stack.copy().comparison(c -> EmiPort.compareStrict()));
                    }
                }
                stacks.removeAll(removed);
            }
            if (!data.filters().isEmpty()) {
                var filters = data.filters();
                stacks.removeIf(s -> {
                    String id = String.valueOf(s.getId());
                    for (IndexStackData.Filter filter : filters) {
                        if (filter.filter().test(id))
                            return true;
                    }
                    return false;
                });
            }
        }
    }

    public static void bake() {
        stacks.removeIf(s -> {
            try {
                if (s.isEmpty()) {
                    return true;
                }
                for (Predicate<EmiStack> invalidator : invalidators) {
                    if (invalidator.test(s)) {
                        return true;
                    }
                }
                if (isHiddenFromRecipeViewers(s.getKey())) {
                    return true;
                }
                return false;
            } catch (Throwable t) {
                EmiLog.error("Stack threw error while baking", t);
                return true;
            }
        });
        for (Supplier<IndexStackData> supplier : EmiData.stackData) {
            IndexStackData ssd = supplier.get();
            if (!ssd.removed().isEmpty()) {
                var removed = new OpenCacheHashSet<EmiStack>(ssd.removed().size());
                for (EmiIngredient invalidator : ssd.removed()) {
                    for (EmiStack stack : invalidator.getEmiStacks()) {
                        removed.add(stack.copy().comparison(c -> EmiPort.compareStrict()));
                    }
                }
                stacks.removeAll(removed);
            }
            if (!ssd.filters().isEmpty()) {
                stacks.removeIf(s -> {
                    String id = "" + s.getId();
                    for (IndexStackData.Filter filter : ssd.filters()) {
                        if (filter.filter().test(id)) {
                            return true;
                        }
                    }
                    return false;
                });
            }
            for (IndexStackData.Added added : ssd.added()) {
                if (added.added().isEmpty()) {
                    continue;
                }
                if (added.after().isEmpty()) {
                    stacks.add(added.added().getEmiStacks().get(0));
                } else {
                    int i = stacks.indexOf(added.after());
                    if (i == -1) {
                        i = stacks.size() - 1;
                    }
                    stacks.add(i + 1, added.added().getEmiStacks().get(0));
                }
            }
        }
        stacks.removeIf(stack -> !isValidStack(stack));
        strictIndices.clear();
        keyIndices.clear();
        for (int i = 0; i < stacks.size(); i++) {
            EmiStack stack = stacks.get(i);
            strictIndices.put(stack, i);
            keyIndices.put(stack.getKey(), i);
        }
        bakeFiltered();
    }

    private static boolean isValidStack(EmiStack stack) {

        String name = "Unknown";
        String id = "unknown";
        try {
            if (stack.isEmpty()) {
                return false;
            }
            name = stack.toString();
            id = stack.getId().toString();
            if (name != null && stack.getKey() != null && stack.getName() != null) {
                return true;
            }
            EmiLog.warn("Hiding stack " + name + " with id " + id + " from index due to returning dangerous values");
            return false;
        } catch (Throwable t) {
            EmiLog.error("Hiding stack " + name + " with id " + id + " from index due to throwing errors", t);
            return false;
        }
    }

    public static void bakeFiltered() {
        var filtered = new ObjectArrayList<EmiStack>(stacks.size());
        for (var stack : stacks)
            if (!EmiHidden.isDisabled(stack) && !EmiHidden.isHidden(stack))
                filtered.add(stack);
        filteredStacks = filtered;
    }

    public static int getIndex(EmiIngredient ingredient) {
        EmiStack stack = ingredient.getEmiStacks().get(0);
        int ret = strictIndices.getOrDefault(stack, Integer.MAX_VALUE);
        if (ret == Integer.MAX_VALUE) {
            ret = keyIndices.getOrDefault(stack.getKey(), ret);
        }
        return ret;
    }

    public static class IndexGroup {
        public List<EmiStack> stacks = new ObjectArrayList<>();
        public Set<IndexGroup> suppressedBy = new ReferenceOpenHashSet<>();

        public boolean shouldDisplay() {
            for (IndexGroup suppressor : suppressedBy) {
                if (suppressor.shouldDisplay()) {
                    return false;
                }
            }
            return true;
        }
    }

    public static class StrictHashStrategy implements Hash.Strategy<EmiStack> {

        @Override
        public boolean equals(EmiStack a, EmiStack b) {
            if (a == b) {
                return true;
            } else if (a == null || b == null) {
                return false;
            } else if (a.isEmpty() && b.isEmpty()) {
                return true;
            }
            return a.isEqual(b, EmiPort.compareStrict());
        }

        @Override
        public int hashCode(EmiStack stack) {
            if (stack != null) {
                CompoundTag nbtCompound = stack.getNbt();
                int i = 31 + stack.getKey().hashCode();
                return 31 * i + (nbtCompound == null ? 0 : nbtCompound.hashCode());
            }
            return 0;
        }
    }

    public static class ComparisonHashStrategy implements Hash.Strategy<EmiStack> {

        @Override
        public boolean equals(EmiStack a, EmiStack b) {
            if (a == b) {
                return true;
            } else if (a == null || b == null) {
                return false;
            } else if (a.isEmpty() && b.isEmpty()) {
                return true;
            }
            return a.isEqual(b, EmiComparisonDefaults.get(a.getKey()));
        }

        @Override
        public int hashCode(EmiStack stack) {
            if (stack != null) {
                int i = 31 + stack.getKey().hashCode();
                return 31 * i + EmiComparisonDefaults.get(stack.getKey()).getHash(stack);
            }
            return 0;
        }
    }
}
