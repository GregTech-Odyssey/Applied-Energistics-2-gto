package appeng.api.stacks;

import static it.unimi.dsi.fastutil.HashCommon.arraySize;

import java.util.Iterator;

import org.jetbrains.annotations.NotNull;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;

public class AEKeyMap<K> extends Reference2LongOpenHashMap<K>
        implements Iterable<Reference2LongMap.Entry<K>> {

    public AEKeyMap() {
        super(DEFAULT_INITIAL_SIZE, DEFAULT_LOAD_FACTOR);
    }

    public AEKeyMap(int size) {
        super(size, DEFAULT_LOAD_FACTOR);
    }

    public AEKeyMap(Reference2LongOpenHashMap<K> map) {
        super(map.size(), DEFAULT_LOAD_FACTOR);
        map.reference2LongEntrySet().fastForEach(e -> put(e.getKey(), e.getLongValue()));
    }

    public AEKeyMap(Object2LongOpenHashMap<K> map) {
        super(map.size(), DEFAULT_LOAD_FACTOR);
        map.object2LongEntrySet().fastForEach(e -> put(e.getKey(), e.getLongValue()));
    }

    @Override
    public @NotNull Iterator<Entry<K>> iterator() {
        return reference2LongEntrySet().fastIterator();
    }

    @Override
    public long put(final K k, final long v) {
        if (k == null)
            return 0;
        int pos;
        K curr;
        if ((curr = key[pos = ((AEKey) k).hashMix() & mask]) != null) {
            do
                if (curr == k) {
                    final long oldValue = value[pos];
                    value[pos] = v;
                    return oldValue;
                }
            while ((curr = key[pos = (pos + 1) & mask]) != null);
        }
        key[pos] = k;
        value[pos] = v;
        if (size++ >= maxFill)
            rehash(arraySize(size + 1, f));
        return 0;
    }

    @Override
    public long addTo(final K k, final long incr) {
        if (k == null)
            return 0;
        int pos;
        K curr;
        if ((curr = key[pos = ((AEKey) k).hashMix() & mask]) != null) {
            do
                if (curr == k) {
                    final long oldValue = value[pos];
                    final long newValue = oldValue + incr;
                    if (newValue < 0 && incr >= 0 && oldValue >= 0) {
                        value[pos] = Long.MAX_VALUE;
                    } else {
                        value[pos] = newValue;
                    }
                    return oldValue;
                }
            while ((curr = key[pos = (pos + 1) & mask]) != null);
        }
        key[pos] = k;
        value[pos] = incr;
        if (size++ >= maxFill)
            rehash(arraySize(size + 1, f));
        return 0;
    }

    @Override
    public long getLong(final Object k) {
        if (k == null)
            return 0;
        K curr;
        int pos;
        if ((curr = key[pos = ((AEKey) k).hashMix() & mask]) == null)
            return defRetValue;
        if (k == curr)
            return value[pos];
        while (true) {
            if ((curr = key[pos = (pos + 1) & mask]) == null)
                return defRetValue;
            if (k == curr)
                return value[pos];
        }
    }

    public void ensureCapacity(int capacity) {
        int needed = (int) Math.min(1073741824L,
                Math.max(2L, HashCommon.nextPowerOfTwo((long) Math.ceil((float) (capacity + size) / this.f))));
        if (needed > this.n) {
            this.rehash(needed);
        }
    }

    public void reset() {
        for (int i = 0, len = value.length; i < len; i++)
            value[i] = 0;
    }
}
