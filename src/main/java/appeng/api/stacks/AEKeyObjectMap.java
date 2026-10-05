package appeng.api.stacks;

import static it.unimi.dsi.fastutil.HashCommon.arraySize;

import it.unimi.dsi.fastutil.objects.Reference2ObjectFunction;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

public final class AEKeyObjectMap<K extends AEKey, V> extends Reference2ObjectOpenHashMap<K, V> {

    public AEKeyObjectMap() {
        super();
    }

    public AEKeyObjectMap(int expected) {
        super(expected);
    }

    @Override
    public V get(final Object k) {
        if (k instanceof AEKey what) {
            final int pos = AEKeyHash.find(key, mask, what);
            return pos >= 0 ? value[pos] : defRetValue;
        }
        return defRetValue;
    }

    @Override
    public V getOrDefault(final Object k, final V defaultValue) {
        if (k instanceof AEKey what) {
            final int pos = AEKeyHash.find(key, mask, what);
            return pos >= 0 ? value[pos] : defaultValue;
        }
        return defaultValue;
    }

    @Override
    public boolean containsKey(final Object k) {
        if (k instanceof AEKey what) {
            return AEKeyHash.find(key, mask, what) >= 0;
        }
        return false;
    }

    @Override
    public V put(final K k, final V v) {
        if (k == null) {
            return super.put(null, v);
        }
        final int pos = AEKeyHash.find(key, mask, k);
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
    public V remove(final Object k) {
        if (k instanceof AEKey what) {
            final int pos = AEKeyHash.find(key, mask, what);
            return pos >= 0 ? removeAt(pos) : defRetValue;
        }
        return defRetValue;
    }

    @Override
    public V putIfAbsent(final K k, final V v) {
        if (k == null) {
            return super.putIfAbsent(null, v);
        }
        final int pos = AEKeyHash.find(key, mask, k);
        if (pos >= 0) {
            return value[pos];
        }
        insertAt(-pos - 1, k, v);
        return defRetValue;
    }

    @Override
    public V computeIfAbsent(final K k, final Reference2ObjectFunction<? super K, ? extends V> mappingFunction) {
        if (k == null) {
            return super.computeIfAbsent(null, mappingFunction);
        }
        final int pos = AEKeyHash.find(key, mask, k);
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

    private void insertAt(final int pos, final K k, final V v) {
        final Object[] key = this.key;
        key[pos] = k;
        value[pos] = v;
        if (size++ >= maxFill) {
            rehash(arraySize(size + 1, f));
        }
    }

    private V removeAt(final int pos) {
        final V oldValue = value[pos];
        size--;
        shiftKeys(pos);
        if (n > minN && size < maxFill / 4 && n > DEFAULT_INITIAL_SIZE) {
            rehash(n / 2);
        }
        return oldValue;
    }
}
