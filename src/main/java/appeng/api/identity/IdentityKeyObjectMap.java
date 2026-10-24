package appeng.api.identity;

import static it.unimi.dsi.fastutil.HashCommon.arraySize;

import java.util.Iterator;
import java.util.Map;
import java.util.function.BiConsumer;

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.*;

public class IdentityKeyObjectMap<K extends IdentityKey, V> extends Reference2ObjectOpenHashMap<K, V>
        implements Iterable<Reference2ObjectMap.Entry<K, V>> {

    public IdentityKeyObjectMap() {
        super(DEFAULT_INITIAL_SIZE, DEFAULT_LOAD_FACTOR);
    }

    public IdentityKeyObjectMap(int expected) {
        super(expected, DEFAULT_LOAD_FACTOR);
    }

    public IdentityKeyObjectMap(final int expected, final float f) {
        super(expected, f);
    }

    public IdentityKeyObjectMap(Map<K, V> map) {
        super(map.size(), DEFAULT_LOAD_FACTOR);
        map.forEach(this::set);
    }

    public final void fastClear() {
        if (size == 0)
            return;
        size = 0;
        containsNullKey = false;
        key = (K[]) IdentityKey.EMPTY_IDENTITY_KEY_ARRAY;
        value = (V[]) ArrayUtils.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public final @NotNull Iterator<Reference2ObjectMap.Entry<K, V>> iterator() {
        return reference2ObjectEntrySet().fastIterator();
    }

    @Override
    public final V get(final Object k) {
        if (k instanceof IdentityKey what) {
            final int pos = IdentityKey.find(key, mask, what);
            return pos >= 0 ? value[pos] : defRetValue;
        }
        return defRetValue;
    }

    @Override
    public final V getOrDefault(final Object k, final V defaultValue) {
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
    public final V put(final K k, final V v) {
        if (k == null) {
            return super.put(null, v);
        }
        final int pos = IdentityKey.find(key, mask, k);
        if (pos >= 0) {
            final V[] value = this.value;
            final V oldValue = value[pos];
            value[pos] = v;
            return oldValue;
        }
        insertAt(-pos - 1, k, v);
        return defRetValue;
    }

    @Override
    public final V remove(final Object k) {
        if (k instanceof IdentityKey what) {
            final int pos = IdentityKey.find(key, mask, what);
            return pos >= 0 ? removeAt(pos) : defRetValue;
        }
        return defRetValue;
    }

    @Override
    public final V putIfAbsent(final K k, final V v) {
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
    public final V computeIfAbsent(final K k, final Reference2ObjectFunction<? super K, ? extends V> mappingFunction) {
        if (k == null) {
            return super.computeIfAbsent(null, mappingFunction);
        }
        final int pos = IdentityKey.find(key, mask, k);
        if (pos >= 0) {
            return value[pos];
        }
        if (!mappingFunction.containsKey(k)) {
            return defRetValue;
        }
        final V newValue = mappingFunction.get(k);
        insertAt(-pos - 1, k, newValue);
        return newValue;
    }

    @Override
    public final void forEach(BiConsumer<? super K, ? super V> action) {
        int remaining = this.size;
        if (remaining == 0) {
            return;
        }
        final Object[] key = this.key;
        final Object[] value = this.value;
        int pos = this.n;
        Object k;
        while (remaining > 0 && pos-- != 0) {
            if ((k = key[pos]) != null) {
                action.accept((K) k, (V) value[pos]);
                remaining--;
            }
        }
    }

    @Override
    public IdentityKeyObjectMap<K, V> clone() {
        return (IdentityKeyObjectMap<K, V>) super.clone();
    }

    public final boolean contains(final IdentityKey k) {
        return k != null && IdentityKey.find(key, mask, k) >= 0;
    }

    public final void set(final IdentityKey k, final V v) {
        if (k == null) {
            return;
        }
        final int pos = IdentityKey.find(key, mask, k);
        if (pos >= 0) {
            final Object[] value = this.value;
            value[pos] = v;
        } else {
            insertAt(-pos - 1, k, v);
        }
    }

    public final void putAll(IdentityKeyObjectMap<K, V> map) {
        this.ensureCapacity(map.size);
        map.forEach(this::set);
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

    public final void reset(V v) {
        final Object[] value = this.value;
        for (int i = 0, len = value.length; i < len; i++) {
            value[i] = v;
        }
    }

    protected final void insertAt(final int pos, final IdentityKey k, final V v) {
        final Object[] key = this.key;
        key[pos] = k;
        value[pos] = v;
        if (size++ >= maxFill) {
            rehash(arraySize(size + 1, f));
        }
    }

    protected final V removeAt(final int pos) {
        final V oldValue = value[pos];
        size--;
        shiftKeys(pos);
        if (n > minN && size < maxFill / 4 && n > DEFAULT_INITIAL_SIZE) {
            rehash(n / 2);
        }
        return oldValue;
    }
}
