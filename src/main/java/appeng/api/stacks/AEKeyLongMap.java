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

public final class AEKeyLongMap<K extends AEKey> extends Reference2LongOpenHashMap<K>
        implements Iterable<Reference2LongMap.Entry<K>> {

    @UnmodifiableView
    public static final AEKeyLongMap<AEKey> EMPTY = new AEKeyLongMap<>(0);

    public AEKeyLongMap() {
        super(DEFAULT_INITIAL_SIZE, DEFAULT_LOAD_FACTOR);
    }

    public AEKeyLongMap(int size) {
        super(size, DEFAULT_LOAD_FACTOR);
    }

    public AEKeyLongMap(AEKeyBigMap<K> map) {
        super(map.size(), DEFAULT_LOAD_FACTOR);
        map.fastForEachLong(this::set);
    }

    public AEKeyLongMap(AEKeyLongMap<K> map) {
        super(map.size, DEFAULT_LOAD_FACTOR);
        map.fastForEach(this::set);
    }

    public AEKeyLongMap(Reference2LongOpenHashMap<K> map) {
        super(map.size(), DEFAULT_LOAD_FACTOR);
        map.reference2LongEntrySet().fastForEach(e -> set(e.getKey(), e.getLongValue()));
    }

    public AEKeyLongMap(Object2LongOpenHashMap<K> map) {
        super(map.size(), DEFAULT_LOAD_FACTOR);
        map.object2LongEntrySet().fastForEach(e -> set(e.getKey(), e.getLongValue()));
    }

    @SuppressWarnings("all")
    @UnmodifiableView
    public static <T extends AEKey> AEKeyLongMap<T> empty() {
        return (AEKeyLongMap<T>) EMPTY;
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
        final int pos = AEKeyHash.find(key, mask, k);
        if (pos >= 0) {
            final long[] value = this.value;
            final long oldValue = value[pos];
            value[pos] = v;
            return oldValue;
        }
        insertAt(-pos - 1, k, v);
        return 0;
    }

    @Override
    public long removeLong(final Object k) {
        if (!(k instanceof AEKey what)) {
            return 0;
        }
        final int pos = AEKeyHash.find(key, mask, what);
        return pos >= 0 ? removeAt(pos) : 0;
    }

    @Override
    public long addTo(final K k, final long incr) {
        if (k == null) {
            return 0;
        }
        final int pos = AEKeyHash.find(key, mask, k);
        if (pos >= 0) {
            final long[] value = this.value;
            final long oldValue = value[pos];
            final long newValue = oldValue + incr;
            value[pos] = newValue < 0 && incr > 0 && oldValue > 0 ? Long.MAX_VALUE : newValue;
            return oldValue;
        }
        insertAt(-pos - 1, k, incr);
        return 0;
    }

    public long removeTo(final AEKey k, final long incr) {
        if (k == null) {
            return 0;
        }
        final int pos = AEKeyHash.find(key, mask, k);
        if (pos >= 0) {
            final long[] value = this.value;
            final long oldValue = value[pos];
            final long newValue = oldValue - incr;
            value[pos] = oldValue < 0 && newValue > 0 && incr > 0 ? -Long.MAX_VALUE : newValue;
            return oldValue;
        }
        insertAt(-pos - 1, k, -incr);
        return 0;
    }

    @Override
    public long getLong(final Object k) {
        if (!(k instanceof AEKey what)) {
            return 0;
        }
        final int pos = AEKeyHash.find(key, mask, what);
        return pos >= 0 ? value[pos] : 0;
    }

    @Override
    public long getOrDefault(final Object k, final long defaultValue) {
        if (!(k instanceof AEKey what)) {
            return defaultValue;
        }
        final int pos = AEKeyHash.find(key, mask, what);
        return pos >= 0 ? value[pos] : defaultValue;
    }

    @Override
    public boolean containsKey(final Object k) {
        return k instanceof AEKey what && AEKeyHash.find(key, mask, what) >= 0;
    }

    @Override
    public AEKeyLongMap<K> clone() {
        return (AEKeyLongMap<K>) super.clone();
    }

    public boolean contains(final AEKey k) {
        return k != null && AEKeyHash.find(key, mask, k) >= 0;
    }

    public long getAmount(final AEKey k) {
        if (k == null) {
            return 0;
        }
        final int pos = AEKeyHash.find(key, mask, k);
        return pos >= 0 ? value[pos] : 0;
    }

    public void set(final AEKey k, final long v) {
        if (k == null) {
            return;
        }
        final int pos = AEKeyHash.find(key, mask, k);
        if (pos >= 0) {
            value[pos] = v;
        } else {
            insertAt(-pos - 1, k, v);
        }
    }

    public void insert(final AEKey k, final long amount) {
        if (k == null || amount < 1) {
            return;
        }
        final int pos = AEKeyHash.find(key, mask, k);
        if (pos >= 0) {
            final long[] value = this.value;
            final long oldValue = value[pos];
            final long newValue = oldValue + amount;
            value[pos] = newValue < 0 && oldValue > 0 ? Long.MAX_VALUE : newValue;
        } else {
            insertAt(-pos - 1, k, amount);
        }
    }

    public long insert(final AEKey k, final long amount, final long limit) {
        if (k == null || amount < 1 || limit < 1) {
            return 0;
        }
        final int pos = AEKeyHash.find(key, mask, k);
        if (pos >= 0) {
            final long[] value = this.value;
            final long oldValue = value[pos];
            if (oldValue >= limit) {
                return 0;
            }
            final long newValue = oldValue + amount;
            if (newValue > limit || newValue < 0) {
                value[pos] = limit;
                return limit - oldValue;
            }
            value[pos] = newValue;
            return amount;
        }
        final long toInsert = Math.min(amount, limit);
        insertAt(-pos - 1, k, toInsert);
        return toInsert;
    }

    public long extract(final AEKey k, final long amount) {
        if (k == null || amount < 1) {
            return 0;
        }
        final int pos = AEKeyHash.find(key, mask, k);
        if (pos < 0) {
            return 0;
        }
        final long[] value = this.value;
        final long oldValue = value[pos];
        if (oldValue > amount) {
            value[pos] = oldValue - amount;
            return amount;
        }
        return removeAt(pos);
    }

    public long extract(final AEKey k, final long amount, final long limit) {
        if (k == null || amount < 1) {
            return 0;
        }
        final int pos = AEKeyHash.find(key, mask, k);
        if (pos < 0) {
            return 0;
        }
        final long[] value = this.value;
        final long oldValue = value[pos];
        final long extractable = oldValue - limit;
        if (extractable <= 0) {
            return 0;
        }
        if (amount < extractable) {
            value[pos] = oldValue - amount;
            return amount;
        }
        if (limit == 0) {
            return removeAt(pos);
        }
        value[pos] = limit;
        return extractable;
    }

    public void putAll(AEKeyLongMap<K> map) {
        this.ensureCapacity(map.size);
        map.fastForEach(this::set);
    }

    public void addAll(AEKeyLongMap<K> map) {
        this.ensureCapacity(map.size);
        map.fastForEach(this::addTo);
    }

    public void removeAll(AEKeyLongMap<K> map) {
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
        final long[] value = this.value;
        for (int i = 0, len = value.length; i < len; i++) {
            value[i] = 0;
        }
    }

    private void insertAt(final int pos, final AEKey k, final long v) {
        final Object[] key = this.key;
        key[pos] = k;
        value[pos] = v;
        if (size++ >= maxFill) {
            rehash(arraySize(size + 1, f));
        }
    }

    private long removeAt(final int pos) {
        final long oldValue = this.value[pos];
        --this.size;
        this.shiftKeys(pos);
        if (this.n > this.minN && this.size < this.maxFill / 4 && this.n > DEFAULT_INITIAL_SIZE) {
            this.rehash(this.n / 2);
        }
        return oldValue;
    }
}
