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
                unordered.addTo(key, amount);
                return unordered;
            }
            case Fuzzy fuzzy -> {
                fuzzy.addTo(key, amount);
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
            var records = new Unordered();
            records.put(existingKey, existingAmount);
            records.put(key, amount);
            return records;
        } else {
            var records = new Fuzzy();
            records.put(existingKey, existingAmount);
            records.put(key, amount);
            return records;
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
    final class Single implements VariantCounter, Object2LongMap.Entry<AEKey> {

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
            return containsFuzzy(filter, fuzzy) ? Collections.singleton(this)
                    : Collections.emptySet();
        }

        @Override
        public long getLongValue() {
            return count;
        }

        @Override
        public long setValue(long value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AEKey getKey() {
            return key;
        }
    }

    /**
     * Holds multiple variants of keys that do not support fuzzy range searches, in which case a fuzzy search simply
     * matches all variants, and an unordered map is sufficient.
     */
    final class Unordered extends Object2LongOpenHashMap<AEKey> implements VariantCounter {

        Unordered() {
            super(2);
        }

        @Override
        public boolean containsFuzzy(AEKey filter, FuzzyMode fuzzy) {
            return !super.isEmpty();
        }

        @Override
        public Set<AEKey> findFuzzyKey(AEKey filter, FuzzyMode fuzzy) {
            return super.keySet();
        }

        @Override
        public LongCollection findFuzzyValue(AEKey filter, FuzzyMode fuzzy) {
            return super.values();
        }

        @Override
        public Set<Object2LongMap.Entry<AEKey>> findFuzzy(AEKey filter, FuzzyMode fuzzy) {
            return super.object2LongEntrySet();
        }
    }

    /**
     * Holds multiple variants of keys that support fuzzy range searches, such as the damage values of a tool, and
     * therefore needs to keep them ordered by their fuzzy search value.
     */
    final class Fuzzy extends Object2LongAVLTreeMap<AEKey> implements VariantCounter {

        Fuzzy() {
            super(FuzzySearch.COMPARATOR);
        }

        @Override
        public boolean containsFuzzy(AEKey filter, FuzzyMode fuzzy) {
            return !FuzzySearch.findFuzzy(this, filter, fuzzy).isEmpty();
        }

        @Override
        public Set<AEKey> findFuzzyKey(AEKey filter, FuzzyMode fuzzy) {
            return FuzzySearch.findFuzzy(this, filter, fuzzy).keySet();
        }

        @Override
        public LongCollection findFuzzyValue(AEKey filter, FuzzyMode fuzzy) {
            return FuzzySearch.findFuzzy(this, filter, fuzzy).values();
        }

        @Override
        public Set<Object2LongMap.Entry<AEKey>> findFuzzy(AEKey filter, FuzzyMode fuzzy) {
            return FuzzySearch.findFuzzy(this, filter, fuzzy).object2LongEntrySet();
        }
    }
}
