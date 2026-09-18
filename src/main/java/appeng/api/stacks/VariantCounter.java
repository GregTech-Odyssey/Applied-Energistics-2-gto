package appeng.api.stacks;

import java.util.Collections;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongLists;
import it.unimi.dsi.fastutil.objects.Object2LongAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import appeng.api.config.FuzzyMode;

/**
 * Tallies a negative or positive amount for sub-variants of a {@link AEKey}. The implementation is chosen based on the
 * number of variants a counter holds, so that it only allocates the memory it actually needs.
 */
sealed interface VariantCounter permits VariantCounter.Single, VariantCounter.Unordered,
        VariantCounter.Fuzzy {

    /**
     * Tallies the given amount for the given key.
     * <p>
     * The given counter is modified in place whenever its implementation is able to hold the result, which is also why
     * the caller has to use the returned counter instead of the given one.
     *
     * @param counter The counter to add to, or {@code null} if there is no counter for this key yet.
     * @return The resulting counter. This may be an instance of a different implementation than the given counter.
     */
    static VariantCounter add(@Nullable VariantCounter counter, AEKey key, long amount) {
        switch (counter) {
            case Single single -> {
                if (single.key == key) {
                    return new Single(key, single.count + amount);
                } else {
                    return grow(single.key, single.count, key, amount);
                }
            }
            case Unordered unordered -> {
                unordered.records.addTo(key, amount);
                return unordered;
            }
            case Fuzzy fuzzy -> {
                fuzzy.records.addTo(key, amount);
                return fuzzy;
            }
            case null, default -> {
                return new Single(key, amount);
            }
        }
    }

    /**
     * Replaces a counter that only held a single variant with a counter that is able to hold multiple variants.
     */
    private static VariantCounter grow(AEKey existingKey, long existingAmount, AEKey key, long amount) {
        // Whether the key supports fuzzy range searches decides which map implementation is needed
        if (existingKey.getFuzzySearchMaxValue() <= 0) {
            var records = new Object2LongOpenHashMap<AEKey>(2);
            records.put(existingKey, existingAmount);
            records.put(key, amount);
            return new Unordered(records);
        } else {
            var records = new Object2LongAVLTreeMap<AEKey>(FuzzySearch.COMPARATOR);
            records.put(existingKey, existingAmount);
            records.put(key, amount);
            return new Fuzzy(records);
        }
    }

    /**
     * @return True if any of the held variants is in the same fuzzy partition as the given filter. The tallied amounts
     *         are not taken into account.
     */
    boolean containsFuzzy(AEKey filter, FuzzyMode fuzzy);

    /**
     * @return The keys of all variants that are in the same fuzzy partition as the given filter.
     */
    Set<AEKey> findFuzzyKey(AEKey filter, FuzzyMode fuzzy);

    /**
     * @return The amounts of all variants that are in the same fuzzy partition as the given filter.
     */
    LongCollection findFuzzyValue(AEKey filter, FuzzyMode fuzzy);

    /**
     * @return All variants that are in the same fuzzy partition as the given filter.
     */
    Set<Object2LongMap.Entry<AEKey>> findFuzzy(AEKey filter, FuzzyMode fuzzy);

    /**
     * Holds a single variant, which is both the most common and the most memory-efficient state.
     */
    final class Single implements VariantCounter {

        private final AEKey key;
        private final long count;

        Single(AEKey key, long count) {
            this.key = key;
            this.count = count;
        }

        @Override
        public boolean containsFuzzy(AEKey filter, FuzzyMode fuzzy) {
            return key.fuzzyModeEquals(filter, fuzzy);
        }

        @Override
        public Set<AEKey> findFuzzyKey(AEKey filter, FuzzyMode fuzzy) {
            return containsFuzzy(filter, fuzzy) ? Collections.singleton(key) : Collections.emptySet();
        }

        @Override
        public LongCollection findFuzzyValue(AEKey filter, FuzzyMode fuzzy) {
            return containsFuzzy(filter, fuzzy) ? LongLists.singleton(count) : LongLists.EMPTY_LIST;
        }

        @Override
        public Set<Object2LongMap.Entry<AEKey>> findFuzzy(AEKey filter, FuzzyMode fuzzy) {
            return containsFuzzy(filter, fuzzy) ? Collections.singleton(new KeyCounter.Entry(count, key))
                    : Collections.emptySet();
        }
    }

    /**
     * Holds multiple variants of keys that do not support fuzzy range searches, in which case a fuzzy search simply
     * matches all variants, and an unordered map is sufficient.
     */
    final class Unordered implements VariantCounter {

        private final Object2LongOpenHashMap<AEKey> records;

        Unordered(Object2LongOpenHashMap<AEKey> records) {
            this.records = records;
        }

        @Override
        public boolean containsFuzzy(AEKey filter, FuzzyMode fuzzy) {
            return !records.isEmpty();
        }

        @Override
        public Set<AEKey> findFuzzyKey(AEKey filter, FuzzyMode fuzzy) {
            return records.keySet();
        }

        @Override
        public LongCollection findFuzzyValue(AEKey filter, FuzzyMode fuzzy) {
            return records.values();
        }

        @Override
        public Set<Object2LongMap.Entry<AEKey>> findFuzzy(AEKey filter, FuzzyMode fuzzy) {
            return records.object2LongEntrySet();
        }
    }

    /**
     * Holds multiple variants of keys that support fuzzy range searches, such as the damage values of a tool, and
     * therefore needs to keep them ordered by their fuzzy search value.
     */
    final class Fuzzy implements VariantCounter {

        private final Object2LongAVLTreeMap<AEKey> records;

        Fuzzy(Object2LongAVLTreeMap<AEKey> records) {
            this.records = records;
        }

        @Override
        public boolean containsFuzzy(AEKey filter, FuzzyMode fuzzy) {
            return !FuzzySearch.findFuzzy(records, filter, fuzzy).isEmpty();
        }

        @Override
        public Set<AEKey> findFuzzyKey(AEKey filter, FuzzyMode fuzzy) {
            return FuzzySearch.findFuzzy(records, filter, fuzzy).keySet();
        }

        @Override
        public LongCollection findFuzzyValue(AEKey filter, FuzzyMode fuzzy) {
            return FuzzySearch.findFuzzy(records, filter, fuzzy).values();
        }

        @Override
        public Set<Object2LongMap.Entry<AEKey>> findFuzzy(AEKey filter, FuzzyMode fuzzy) {
            return FuzzySearch.findFuzzy(records, filter, fuzzy).object2LongEntrySet();
        }
    }
}
