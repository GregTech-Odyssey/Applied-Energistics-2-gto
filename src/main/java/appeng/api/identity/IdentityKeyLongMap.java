package appeng.api.identity;

import static it.unimi.dsi.fastutil.HashCommon.arraySize;

import java.util.Iterator;
import java.util.function.ObjLongConsumer;

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;

public class IdentityKeyLongMap<K extends IdentityKey> extends Reference2LongOpenHashMap<K>
        implements Iterable<Reference2LongMap.Entry<K>> {

    public IdentityKeyLongMap() {
        super(DEFAULT_INITIAL_SIZE, DEFAULT_LOAD_FACTOR);
    }

    public IdentityKeyLongMap(int expected) {
        super(expected, DEFAULT_LOAD_FACTOR);
    }

    public IdentityKeyLongMap(final int expected, final float f) {
        super(expected, f);
    }

    public IdentityKeyLongMap(IdentityKeyLongMap<K> map) {
        super(map.size, DEFAULT_LOAD_FACTOR);
        map.fastForEach(this::set);
    }

    public IdentityKeyLongMap(IdentityKeyIntMap<K> map) {
        super(map.size(), DEFAULT_LOAD_FACTOR);
        map.fastForEach(this::set);
    }

    public IdentityKeyLongMap(Reference2LongOpenHashMap<K> map) {
        super(map.size(), DEFAULT_LOAD_FACTOR);
        map.reference2LongEntrySet().fastForEach(e -> set(e.getKey(), e.getLongValue()));
    }

    public IdentityKeyLongMap(Object2LongOpenHashMap<K> map) {
        super(map.size(), DEFAULT_LOAD_FACTOR);
        map.object2LongEntrySet().fastForEach(e -> set(e.getKey(), e.getLongValue()));
    }

    public final void fastClear() {
        if (size == 0)
            return;
        size = 0;
        containsNullKey = false;
        key = (K[]) IdentityKey.EMPTY_IDENTITY_KEY_ARRAY;
        value = ArrayUtils.EMPTY_LONG_ARRAY;
    }

    @Override
    public final @NotNull Iterator<Entry<K>> iterator() {
        return reference2LongEntrySet().fastIterator();
    }

    @Override
    public final long put(final K k, final long v) {
        if (k == null) {
            return 0;
        }
        final int pos = IdentityKey.find(key, mask, k);
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
    public final long removeLong(final Object k) {
        if (!(k instanceof IdentityKey what)) {
            return 0;
        }
        final int pos = IdentityKey.find(key, mask, what);
        return pos >= 0 ? removeAt(pos) : 0;
    }

    @Override
    public final long addTo(final K k, final long incr) {
        if (k == null) {
            return 0;
        }
        final int pos = IdentityKey.find(key, mask, k);
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

    public final long removeTo(final IdentityKey k, final long incr) {
        if (k == null) {
            return 0;
        }
        final int pos = IdentityKey.find(key, mask, k);
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
    public final long getLong(final Object k) {
        if (!(k instanceof IdentityKey what)) {
            return 0;
        }
        final int pos = IdentityKey.find(key, mask, what);
        return pos >= 0 ? value[pos] : 0;
    }

    @Override
    public final long getOrDefault(final Object k, final long defaultValue) {
        if (!(k instanceof IdentityKey what)) {
            return defaultValue;
        }
        final int pos = IdentityKey.find(key, mask, what);
        return pos >= 0 ? value[pos] : defaultValue;
    }

    @Override
    public final boolean containsKey(final Object k) {
        return k instanceof IdentityKey what && IdentityKey.find(key, mask, what) >= 0;
    }

    @Override
    public IdentityKeyLongMap<K> clone() {
        return (IdentityKeyLongMap<K>) super.clone();
    }

    public final boolean contains(final IdentityKey k) {
        return k != null && IdentityKey.find(key, mask, k) >= 0;
    }

    public final long getAmount(final IdentityKey k) {
        if (k == null) {
            return 0;
        }
        final int pos = IdentityKey.find(key, mask, k);
        return pos >= 0 ? value[pos] : 0;
    }

    public final void set(final IdentityKey k, final long v) {
        if (k == null) {
            return;
        }
        final int pos = IdentityKey.find(key, mask, k);
        if (pos >= 0) {
            value[pos] = v;
        } else {
            insertAt(-pos - 1, k, v);
        }
    }

    public final void insert(final IdentityKey k, final long amount) {
        if (k == null || amount < 1) {
            return;
        }
        final int pos = IdentityKey.find(key, mask, k);
        if (pos >= 0) {
            final long[] value = this.value;
            final long oldValue = value[pos];
            final long newValue = oldValue + amount;
            value[pos] = newValue < 0 && oldValue > 0 ? Long.MAX_VALUE : newValue;
        } else {
            insertAt(-pos - 1, k, amount);
        }
    }

    public final long insert(final IdentityKey k, final long amount, final long limit) {
        if (k == null || amount < 1 || limit < 1) {
            return 0;
        }
        final int pos = IdentityKey.find(key, mask, k);
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

    public final long extract(final IdentityKey k, final long amount) {
        if (k == null || amount < 1) {
            return 0;
        }
        final int pos = IdentityKey.find(key, mask, k);
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

    public final long extract(final IdentityKey k, final long amount, final long limit) {
        if (k == null || amount < 1) {
            return 0;
        }
        final int pos = IdentityKey.find(key, mask, k);
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

    public final void putAll(IdentityKeyLongMap<K> map) {
        this.ensureCapacity(map.size);
        map.fastForEach(this::set);
    }

    public final void addAll(IdentityKeyLongMap<K> map) {
        this.ensureCapacity(map.size);
        map.fastForEach(this::addTo);
    }

    public final void removeAll(IdentityKeyLongMap<K> map) {
        this.ensureCapacity(map.size);
        map.fastForEach(this::removeTo);
    }

    public final void fastForEach(final ObjLongConsumer<? super K> consumer) {
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

    public final void ensureCapacity(final int capacity) {
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

    public final void reset() {
        final long[] value = this.value;
        for (int i = 0, len = value.length; i < len; i++) {
            value[i] = 0;
        }
    }

    protected final void insertAt(final int pos, final IdentityKey k, final long v) {
        final Object[] key = this.key;
        key[pos] = k;
        value[pos] = v;
        if (size++ >= maxFill) {
            rehash(arraySize(size + 1, f));
        }
    }

    protected final long removeAt(final int pos) {
        final long oldValue = this.value[pos];
        --this.size;
        this.shiftKeys(pos);
        if (this.n > this.minN && this.size < this.maxFill / 4 && this.n > DEFAULT_INITIAL_SIZE) {
            this.rehash(this.n / 2);
        }
        return oldValue;
    }
}
