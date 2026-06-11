package appeng.api.stacks;

import static it.unimi.dsi.fastutil.HashCommon.arraySize;

import java.util.Iterator;
import java.util.function.ObjLongConsumer;

import org.jetbrains.annotations.NotNull;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;

public class AEKeyMap<K extends AEKey> extends Reference2LongOpenHashMap<K>
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
        Object curr;
        final Object[] key = this.key;
        if ((curr = key[pos = k.mix & mask]) != null) {
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
    public long removeLong(Object k) {
        if (k == null) {
            return 0;
        } else {
            final Object[] key = this.key;
            Object curr;
            int pos;
            if ((curr = key[pos = ((AEKey) k).mix & this.mask]) == null) {
                return 0;
            } else if (k == curr) {
                return this.removeEntry(pos);
            } else {
                while ((curr = key[pos = pos + 1 & this.mask]) != null) {
                    if (k == curr) {
                        return this.removeEntry(pos);
                    }
                }
                return 0;
            }
        }
    }

    @Override
    public long addTo(final K k, final long incr) {
        if (k == null)
            return 0;
        int pos;
        Object curr;
        final Object[] key = this.key;
        if ((curr = key[pos = k.mix & mask]) != null) {
            do
                if (curr == k) {
                    final long oldValue = value[pos];
                    final long newValue = oldValue + incr;
                    if (newValue < 0 && incr > 0 && oldValue > 0) {
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

    public long removeTo(final K k, final long incr) {
        if (k == null)
            return 0;
        int pos;
        Object curr;
        final Object[] key = this.key;
        if ((curr = key[pos = k.mix & mask]) != null) {
            do
                if (curr == k) {
                    final long oldValue = value[pos];
                    final long newValue = oldValue - incr;
                    if (oldValue < 0 && newValue > 0 && incr > 0) {
                        value[pos] = -Long.MAX_VALUE;
                    } else {
                        value[pos] = newValue;
                    }
                    return oldValue;
                }
            while ((curr = key[pos = (pos + 1) & mask]) != null);
        }
        key[pos] = k;
        value[pos] = -incr;
        if (size++ >= maxFill)
            rehash(arraySize(size + 1, f));
        return 0;
    }

    @Override
    public long getLong(final Object k) {
        if (k == null)
            return 0;
        Object curr;
        int pos;
        final Object[] key = this.key;
        if ((curr = key[pos = ((AEKey) k).mix & mask]) == null)
            return 0;
        if (k == curr)
            return value[pos];
        while (true) {
            if ((curr = key[pos = (pos + 1) & mask]) == null)
                return 0;
            if (k == curr)
                return value[pos];
        }
    }

    @Override
    public AEKeyMap<K> clone() {
        return (AEKeyMap<K>) super.clone();
    }

    public long getAmount(final AEKey k) {
        if (k == null)
            return 0;
        Object curr;
        int pos;
        final Object[] key = this.key;
        if ((curr = key[pos = k.mix & mask]) == null)
            return 0;
        if (k == curr)
            return value[pos];
        while (true) {
            if ((curr = key[pos = (pos + 1) & mask]) == null)
                return 0;
            if (k == curr)
                return value[pos];
        }
    }

    public long set(final AEKey k, final long v) {
        if (k == null)
            return 0;
        int pos;
        Object curr;
        final Object[] key = this.key;
        if ((curr = key[pos = k.mix & mask]) != null) {
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

    public long insert(AEKey k, long amount) {
        if (k == null || amount < 1)
            return 0;
        int pos;
        Object curr;
        final Object[] key = this.key;
        if ((curr = key[pos = k.mix & mask]) != null) {
            do
                if (curr == k) {
                    final long oldValue = value[pos];
                    final long newValue = oldValue + amount;
                    if (newValue < 0 && oldValue > 0) {
                        value[pos] = Long.MAX_VALUE;
                        return Long.MAX_VALUE - oldValue;
                    } else {
                        value[pos] = newValue;
                        return amount;
                    }
                }
            while ((curr = key[pos = (pos + 1) & mask]) != null);
        }
        key[pos] = k;
        value[pos] = amount;
        if (size++ >= maxFill)
            rehash(arraySize(size + 1, f));
        return amount;
    }

    public long extract(AEKey k, long amount) {
        if (k == null || amount < 1)
            return 0;
        int pos;
        Object curr;
        final Object[] key = this.key;
        if ((curr = key[pos = k.mix & mask]) != null) {
            do
                if (curr == k) {
                    final long oldValue = value[pos];
                    if (oldValue > amount) {
                        value[pos] = oldValue - amount;
                        return amount;
                    } else {
                        return removeEntry(pos);
                    }
                }
            while ((curr = key[pos = (pos + 1) & mask]) != null);
        }
        return 0;
    }

    public void addAll(AEKeyMap<K> map) {
        this.ensureCapacity(map.size);
        map.fastForEach(this::addTo);
    }

    public void removeAll(AEKeyMap<K> map) {
        this.ensureCapacity(map.size);
        map.fastForEach(this::removeTo);
    }

    public void fastForEach(ObjLongConsumer<? super K> consumer) {
        final Object[] key = this.key;
        final long[] value = this.value;
        int pos = this.n;
        Object k;
        while (pos-- != 0) {
            if ((k = key[pos]) != null) {
                consumer.accept((K) k, value[pos]);
            }
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

    protected long removeEntry(int pos) {
        long oldValue = this.value[pos];
        --this.size;
        this.shiftKeys(pos);
        if (this.n > this.minN && this.size < this.maxFill / 4 && this.n > 16) {
            this.rehash(this.n / 2);
        }

        return oldValue;
    }
}
