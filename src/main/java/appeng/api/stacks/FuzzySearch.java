package appeng.api.stacks;

import java.util.Comparator;
import java.util.SortedMap;

import com.google.common.annotations.VisibleForTesting;

import net.minecraft.world.item.ItemStack;

import appeng.api.config.FuzzyMode;

final class FuzzySearch {
    @VisibleForTesting
    static final KeyComparator COMPARATOR = new KeyComparator();

    private FuzzySearch() {
    }

    @SuppressWarnings({ "unchecked" })
    public static <T extends SortedMap<K, V>, K, V> T findFuzzy(T map, AEKey key, FuzzyMode fuzzy) {
        var lowerBound = makeLowerBound(key, fuzzy);
        var upperBound = makeUpperBound(key, fuzzy);
        // We can use lower/upper bound in this map for queries because our comparator (see below) specifically
        // supports dealing with it
        return (T) map.subMap((K) lowerBound, (K) upperBound);
    }

    private static class KeyComparator implements Comparator<Object> {
        @Override
        public int compare(Object a, Object b) {
            // Either argument can either be a damage bound or a shared item stack
            // Since we never put damage bounds into the map as keys, only one
            // of the two arguments can possibly be a bound
            Integer boundA = null;
            AEKey stackA = null;
            int fuzzyOrderB;
            if (a instanceof Integer integer) {
                boundA = integer;
                fuzzyOrderB = boundA;
            } else {
                stackA = (AEKey) a;
                fuzzyOrderB = stackA.getFuzzySearchValue();
            }
            Integer boundB = null;
            AEKey stackB = null;
            int fuzzyOrderA;
            if (b instanceof Integer integer) {
                boundB = integer;
                fuzzyOrderA = boundB;
            } else {
                stackB = (AEKey) b;
                fuzzyOrderA = stackB.getFuzzySearchValue();
            }

            // When either argument is a damage bound, we just compare the damage values because it is used
            // only to get a certain damage range out of the map.
            if (boundA != null || boundB != null) {
                return Integer.compare(fuzzyOrderA, fuzzyOrderB);
            }

            if (stackA == stackB) {
                return 0;
            }

            // Damaged items are sorted before undamaged items
            final var fuzzyOrder = Integer.compare(fuzzyOrderA, fuzzyOrderB);
            if (fuzzyOrder != 0) {
                return fuzzyOrder;
            }

            // As a final tie breaker, order by the hash code of the key
            // While this will order seemingly at random, we only need the order of
            // damage values to be predictable, while still having to satisfy the
            // complete order requirements of the sorted map
            // (We hope there won't be hash collisions... the probability is very low anyway)
            return Long.compare(stackA.hashCode(), stackB.hashCode());
        }
    }

    /**
     * Minecraft reverses the damage values. So anything with a damage of 0 is undamaged and increases the more damaged
     * the item is.
     * <p>
     * Further the used subMap follows [MAX_DAMAGE, MIN_DAMAGE), so to include undamaged items, we have to start with a
     * lower damage value than 0, while it is fine to use {@link ItemStack#getMaxDamage()} for the upper bound.
     */
    private static final int MIN_DAMAGE_VALUE = -1;

    /*
     * Keep in mind that the stack order is from most damaged to least damaged, so this lower bound will actually be a
     * higher number than the upper bound.
     */
    static Integer makeLowerBound(AEKey key, FuzzyMode fuzzy) {
        var maxValue = key.getFuzzySearchMaxValue();
        int damage;
        if (fuzzy == FuzzyMode.IGNORE_ALL) {
            damage = maxValue;
        } else {
            var breakpoint = fuzzy.calculateBreakPoint(maxValue);
            damage = key.getFuzzySearchValue() <= breakpoint ? breakpoint : maxValue;
        }

        return damage;
    }

    /*
     * Keep in mind that the stack order is from most damaged to least damaged, so this upper bound will actually be a
     * lower number than the lower bound. It also is exclusive.
     */
    static Integer makeUpperBound(AEKey key, FuzzyMode fuzzy) {
        var maxValue = key.getFuzzySearchMaxValue();
        int damage;
        if (fuzzy == FuzzyMode.IGNORE_ALL) {
            damage = MIN_DAMAGE_VALUE;
        } else {
            final var breakpoint = fuzzy.calculateBreakPoint(maxValue);
            damage = key.getFuzzySearchValue() <= breakpoint ? MIN_DAMAGE_VALUE : breakpoint;
        }

        return damage;
    }
}
