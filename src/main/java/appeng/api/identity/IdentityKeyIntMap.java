package appeng.api.identity;

import static it.unimi.dsi.fastutil.HashCommon.arraySize;

import java.util.Iterator;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.*;

public class IdentityKeyIntMap<K extends IdentityKey> extends Reference2IntOpenHashMap<K>
        implements Iterable<Reference2IntMap.Entry<K>> {

    public IdentityKeyIntMap() {
        super(DEFAULT_INITIAL_SIZE, DEFAULT_LOAD_FACTOR);
    }

    public IdentityKeyIntMap(int expected) {
        super(expected, DEFAULT_LOAD_FACTOR);
    }

    public IdentityKeyIntMap(final int expected, final float f) {
        super(expected, f);
    }

    public IdentityKeyIntMap(IdentityKeyIntMap<K> map) {
        super(map.size(), DEFAULT_LOAD_FACTOR);
        map.fastForEach(this::set);
    }

    public IdentityKeyIntMap(Reference2IntOpenHashMap<K> map) {
        super(map.size(), DEFAULT_LOAD_FACTOR);
        map.reference2IntEntrySet().fastForEach(e -> set(e.getKey(), e.getIntValue()));
    }

    public IdentityKeyIntMap(Object2IntOpenHashMap<K> map) {
        super(map.size(), DEFAULT_LOAD_FACTOR);
        map.object2IntEntrySet().fastForEach(e -> set(e.getKey(), e.getIntValue()));
    }

    public final void fastClear() {
        if (size == 0)
            return;
        size = 0;
        containsNullKey = false;
        key = (K[]) IdentityKey.EMPTY_IDENTITY_KEY_ARRAY;
        value = ArrayUtils.EMPTY_INT_ARRAY;
    }

    @Override
    public final @NotNull Iterator<Reference2IntMap.Entry<K>> iterator() {
        return reference2IntEntrySet().fastIterator();
    }

    @Override
    public final int getInt(final Object k) {
        if (k instanceof IdentityKey what) {
            final int pos = IdentityKey.find(key, mask, what);
            return pos >= 0 ? value[pos] : defRetValue;
        }
        return defRetValue;
    }

    @Override
    public final int getOrDefault(final Object k, final int defaultValue) {
        if (k instanceof IdentityKey what) {
            final int pos = IdentityKey.find(key, mask, what);
            return pos >= 0 ? value[pos] : defaultValue;
        }
        return defaultValue;
    }

    @Override
    public final boolean containsKey(final Object k) {
        if (k instanceof IdentityKey what) {
            return IdentityKey.find(key, mask, what) >= 0;
        }
        return false;
    }

    @Override
    public final int put(final K k, final int v) {
        if (k == null) {
            return super.put(null, v);
        }
        final int pos = IdentityKey.find(key, mask, k);
        if (pos >= 0) {
            final int[] value = this.value;
            final int oldValue = value[pos];
            value[pos] = v;
            return oldValue;
        }
        insertAt(-pos - 1, k, v);
        return defRetValue;
    }

    @Override
    public final int addTo(final K k, final int incr) {
        if (k == null) {
            return super.addTo(null, incr);
        }
        final int pos = IdentityKey.find(key, mask, k);
        if (pos >= 0) {
            final int[] value = this.value;
            final int oldValue = value[pos];
            value[pos] = oldValue + incr;
            return oldValue;
        }
        final int defRetValue = this.defRetValue;
        insertAt(-pos - 1, k, defRetValue + incr);
        return defRetValue;
    }

    public final int removeTo(final IdentityKey k, final int incr) {
        if (k == null) {
            return 0;
        }
        final int pos = IdentityKey.find(key, mask, k);
        if (pos >= 0) {
            final int[] value = this.value;
            final int oldValue = value[pos];
            final int newValue = oldValue - incr;
            value[pos] = oldValue < 0 && newValue > 0 && incr > 0 ? -Integer.MAX_VALUE : newValue;
            return oldValue;
        }
        insertAt(-pos - 1, k, -incr);
        return 0;
    }

    @Override
    public final int removeInt(final Object k) {
        if (k instanceof IdentityKey what) {
            final int pos = IdentityKey.find(key, mask, what);
            return pos >= 0 ? removeAt(pos) : defRetValue;
        }
        return defRetValue;
    }

    @Override
    public final int putIfAbsent(final K k, final int v) {
        if (k == null) {
            return super.putIfAbsent(null, v);
        }
        final int pos = IdentityKey.find(key, mask, k);
        if (pos >= 0) {
            return value[pos];
        }
        insertAt(-pos - 1, k, v);
        return defRetValue;
    }

    @Override
    public final int computeIfAbsent(final K k, final ToIntFunction<? super K> mappingFunction) {
        if (k == null) {
            return super.computeIfAbsent(null, mappingFunction);
        }
        final int pos = IdentityKey.find(key, mask, k);
        if (pos >= 0) {
            return value[pos];
        }
        final int newValue = mappingFunction.applyAsInt(k);
        insertAt(-pos - 1, k, newValue);
        return newValue;
    }

    public final boolean contains(final IdentityKey k) {
        return k != null && IdentityKey.find(key, mask, k) >= 0;
    }

    public final int getAmount(final IdentityKey k) {
        if (k == null) {
            return 0;
        }
        final int pos = IdentityKey.find(key, mask, k);
        return pos >= 0 ? value[pos] : 0;
    }

    public final void set(final IdentityKey k, final int v) {
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

    public final void insert(final IdentityKey k, final int amount) {
        if (k == null || amount < 1) {
            return;
        }
        final int pos = IdentityKey.find(key, mask, k);
        if (pos >= 0) {
            final int[] value = this.value;
            final int oldValue = value[pos];
            final int newValue = oldValue + amount;
            value[pos] = newValue < 0 && oldValue > 0 ? Integer.MAX_VALUE : newValue;
        } else {
            insertAt(-pos - 1, k, amount);
        }
    }

    public final int insert(final IdentityKey k, final int amount, final int limit) {
        if (k == null || amount < 1 || limit < 1) {
            return 0;
        }
        final int pos = IdentityKey.find(key, mask, k);
        if (pos >= 0) {
            final int[] value = this.value;
            final int oldValue = value[pos];
            if (oldValue >= limit) {
                return 0;
            }
            final int newValue = oldValue + amount;
            if (newValue > limit || newValue < 0) {
                value[pos] = limit;
                return limit - oldValue;
            }
            value[pos] = newValue;
            return amount;
        }
        final int toInsert = Math.min(amount, limit);
        insertAt(-pos - 1, k, toInsert);
        return toInsert;
    }

    public final int extract(final IdentityKey k, final int amount) {
        if (k == null || amount < 1) {
            return 0;
        }
        final int pos = IdentityKey.find(key, mask, k);
        if (pos < 0) {
            return 0;
        }
        final int[] value = this.value;
        final int oldValue = value[pos];
        if (oldValue > amount) {
            value[pos] = oldValue - amount;
            return amount;
        }
        return removeAt(pos);
    }

    public final int extract(final IdentityKey k, final int amount, final int limit) {
        if (k == null || amount < 1) {
            return 0;
        }
        final int pos = IdentityKey.find(key, mask, k);
        if (pos < 0) {
            return 0;
        }
        final int[] value = this.value;
        final int oldValue = value[pos];
        final int extractable = oldValue - limit;
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

    public final void putAll(IdentityKeyIntMap<K> map) {
        this.ensureCapacity(map.size);
        map.fastForEach(this::set);
    }

    public final void addAll(IdentityKeyIntMap<K> map) {
        this.ensureCapacity(map.size);
        map.fastForEach(this::addTo);
    }

    public final void removeAll(IdentityKeyIntMap<K> map) {
        this.ensureCapacity(map.size);
        map.fastForEach(this::removeTo);
    }

    public final void fastForEach(final ObjIntConsumer<? super K> consumer) {
        int remaining = this.size;
        if (remaining == 0) {
            return;
        }
        final Object[] key = this.key;
        final int[] value = this.value;
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
        final int[] value = this.value;
        for (int i = 0, len = value.length; i < len; i++) {
            value[i] = 0;
        }
    }

    protected final void insertAt(final int pos, final IdentityKey k, final int v) {
        final Object[] key = this.key;
        key[pos] = k;
        value[pos] = v;
        if (size++ >= maxFill) {
            rehash(arraySize(size + 1, f));
        }
    }

    protected final int removeAt(final int pos) {
        final int oldValue = value[pos];
        size--;
        shiftKeys(pos);
        if (n > minN && size < maxFill / 4 && n > DEFAULT_INITIAL_SIZE) {
            rehash(n / 2);
        }
        return oldValue;
    }
}
