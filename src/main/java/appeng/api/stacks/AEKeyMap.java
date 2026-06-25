package appeng.api.stacks;

import static it.unimi.dsi.fastutil.HashCommon.arraySize;

import java.util.Iterator;
import java.util.function.ObjLongConsumer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;

public final class AEKeyMap<K extends AEKey> extends Reference2LongOpenHashMap<K>
        implements Iterable<Reference2LongMap.Entry<K>> {

    @UnmodifiableView
    public static final AEKeyMap<AEKey> EMPTY = new AEKeyMap<>(0);

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
        if (k == null) {
            return 0;
        }
        final Object[] key = this.key;
        final long[] value = this.value;
        final int mask = this.mask;
        int pos;
        Object curr;
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
        if (size++ >= maxFill) {
            rehash(arraySize(size + 1, f));
        }
        return 0;
    }

    @Override
    public long removeLong(final Object k) {
        if (k == null) {
            return 0;
        }
        final Object[] key = this.key;
        final int mask = this.mask;
        Object curr;
        int pos;
        if ((curr = key[pos = ((AEKey) k).mix & mask]) == null) {
            return 0;
        } else if (k == curr) {
            return this.removeEntry(pos);
        } else {
            while ((curr = key[pos = pos + 1 & mask]) != null) {
                if (k == curr) {
                    return this.removeEntry(pos);
                }
            }
            return 0;
        }

    }

    @Override
    public long addTo(final K k, final long incr) {
        if (k == null) {
            return 0;
        }
        final Object[] key = this.key;
        final long[] value = this.value;
        final int mask = this.mask;
        int pos;
        Object curr;
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
        if (size++ >= maxFill) {
            rehash(arraySize(size + 1, f));
        }
        return 0;

    }

    public long removeTo(final AEKey k, final long incr) {
        if (k == null) {
            return 0;
        }
        final Object[] key = this.key;
        final long[] value = this.value;
        final int mask = this.mask;
        int pos;
        Object curr;
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
        if (size++ >= maxFill) {
            rehash(arraySize(size + 1, f));
        }
        return 0;
    }

    @Override
    public long getLong(final Object k) {
        if (k == null) {
            return 0;
        }
        final Object[] key = this.key;
        final int mask = this.mask;
        Object curr;
        int pos;
        if ((curr = key[pos = ((AEKey) k).mix & mask]) == null) {
            return 0;
        } else if (k == curr) {
            return value[pos];
        } else {
            while ((curr = key[pos = (pos + 1) & mask]) != null) {
                if (k == curr) {
                    return value[pos];
                }
            }
            return 0;
        }
    }

    @Override
    public AEKeyMap<K> clone() {
        return (AEKeyMap<K>) super.clone();
    }

    public long getAmount(final AEKey k) {
        if (k == null) {
            return 0;
        }
        final Object[] key = this.key;
        final int mask = this.mask;
        Object curr;
        int pos;
        if ((curr = key[pos = k.mix & mask]) == null) {
            return 0;
        } else if (k == curr) {
            return value[pos];
        } else {
            while ((curr = key[pos = (pos + 1) & mask]) != null) {
                if (k == curr) {
                    return value[pos];
                }
            }
            return 0;
        }
    }

    public long set(final AEKey k, final long v) {
        if (k == null) {
            return 0;
        }
        final Object[] key = this.key;
        final long[] value = this.value;
        final int mask = this.mask;
        int pos;
        Object curr;
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
        if (size++ >= maxFill) {
            rehash(arraySize(size + 1, f));
        }
        return 0;
    }

    public long insert(final AEKey k, final long amount) {
        if (k == null || amount < 1) {
            return 0;
        }
        final Object[] key = this.key;
        final long[] value = this.value;
        final int mask = this.mask;
        int pos;
        Object curr;
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
        if (size++ >= maxFill) {
            rehash(arraySize(size + 1, f));
        }
        return amount;
    }

    public long insert(final AEKey k, final long amount, final long limit) {
        if (k == null || amount < 1 || limit < 1) {
            return 0;
        }
        final Object[] key = this.key;
        final long[] value = this.value;
        final int mask = this.mask;
        int pos;
        Object curr;
        if ((curr = key[pos = k.mix & mask]) != null) {
            do
                if (curr == k) {
                    final long oldValue = value[pos];
                    if (oldValue >= limit) {
                        return 0;
                    }
                    final long newValue = oldValue + amount;
                    if (newValue > limit || newValue < 0) {
                        value[pos] = limit;
                        return limit - oldValue;
                    } else {
                        value[pos] = newValue;
                        return amount;
                    }
                }
            while ((curr = key[pos = (pos + 1) & mask]) != null);
        }
        final long toInsert = Math.min(amount, limit);
        key[pos] = k;
        value[pos] = toInsert;
        if (size++ >= maxFill) {
            rehash(arraySize(size + 1, f));
        }
        return toInsert;
    }

    public long extract(final AEKey k, final long amount) {
        if (k == null || amount < 1) {
            return 0;
        }
        final Object[] key = this.key;
        final int mask = this.mask;
        int pos;
        Object curr;
        if ((curr = key[pos = k.mix & mask]) != null) {
            do
                if (curr == k) {
                    final long[] value = this.value;
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

    public void putAll(AEKeyMap<K> map) {
        this.ensureCapacity(map.size);
        map.fastForEach(this::put);
    }

    public void addAll(AEKeyMap<K> map) {
        this.ensureCapacity(map.size);
        map.fastForEach(this::addTo);
    }

    public void removeAll(AEKeyMap<K> map) {
        this.ensureCapacity(map.size);
        map.fastForEach(this::removeTo);
    }

    public void fastForEach(final ObjLongConsumer<? super K> consumer) {
        int remaining = this.size;
        if (remaining == 0) {
            return;
        }
        final Object[] key = this.key;
        final long[] value = this.value;
        int pos = this.n;
        Object k;
        while (remaining > 0 && pos-- != 0) {
            if ((k = key[pos]) != null) {
                consumer.accept((K) k, value[pos]);
                remaining--;
            }
        }
    }

    public void ensureCapacity(final int capacity) {
        if (capacity < 2) {
            return;
        }
        final int needed = (int) Math.clamp(
                HashCommon.nextPowerOfTwo((long) Math.ceil((float) (capacity + size) / this.f)),
                2L, 1073741824L);
        if (needed > this.n) {
            this.rehash(needed);
        }
    }

    public void reset() {
        for (int i = 0, len = value.length; i < len; i++)
            value[i] = 0;
    }

    private long removeEntry(final int pos) {
        final long oldValue = this.value[pos];
        --this.size;
        this.shiftKeys(pos);
        if (this.n > this.minN && this.size < this.maxFill / 4 && this.n > 16) {
            this.rehash(this.n / 2);
        }

        return oldValue;
    }
}
