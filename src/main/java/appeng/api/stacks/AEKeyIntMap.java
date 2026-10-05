package appeng.api.stacks;

import static it.unimi.dsi.fastutil.HashCommon.arraySize;

import java.util.function.ToIntFunction;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

public final class AEKeyIntMap<K extends AEKey> extends Reference2IntOpenHashMap<K> {

    public AEKeyIntMap() {
        super();
    }

    public AEKeyIntMap(int expected) {
        super(expected);
    }

    @Override
    public int getInt(final Object k) {
        if (k instanceof AEKey what) {
            final int pos = AEKeyHash.find(key, mask, what);
            return pos >= 0 ? value[pos] : defRetValue;
        }
        return super.getInt(k);
    }

    @Override
    public int getOrDefault(final Object k, final int defaultValue) {
        if (k instanceof AEKey what) {
            final int pos = AEKeyHash.find(key, mask, what);
            return pos >= 0 ? value[pos] : defaultValue;
        }
        return super.getOrDefault(k, defaultValue);
    }

    @Override
    public boolean containsKey(final Object k) {
        if (k instanceof AEKey what) {
            return AEKeyHash.find(key, mask, what) >= 0;
        }
        return super.containsKey(k);
    }

    @Override
    public int put(final K k, final int v) {
        if (k == null) {
            return super.put(null, v);
        }
        final int pos = AEKeyHash.find(key, mask, k);
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
    public int addTo(final K k, final int incr) {
        if (k == null) {
            return super.addTo(null, incr);
        }
        final int pos = AEKeyHash.find(key, mask, k);
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

    @Override
    public int removeInt(final Object k) {
        if (k instanceof AEKey what) {
            final int pos = AEKeyHash.find(key, mask, what);
            return pos >= 0 ? removeAt(pos) : defRetValue;
        }
        return super.removeInt(k);
    }

    @Override
    public int putIfAbsent(final K k, final int v) {
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
    public int computeIfAbsent(final K k, final ToIntFunction<? super K> mappingFunction) {
        if (k == null) {
            return super.computeIfAbsent(null, mappingFunction);
        }
        final int pos = AEKeyHash.find(key, mask, k);
        if (pos >= 0) {
            return value[pos];
        }
        final int newValue = mappingFunction.applyAsInt(k);
        insertAt(-pos - 1, k, newValue);
        return newValue;
    }

    private void insertAt(final int pos, final K k, final int v) {
        final Object[] key = this.key;
        key[pos] = k;
        value[pos] = v;
        if (size++ >= maxFill) {
            rehash(arraySize(size + 1, f));
        }
    }

    private int removeAt(final int pos) {
        final int oldValue = value[pos];
        size--;
        shiftKeys(pos);
        if (n > minN && size < maxFill / 4 && n > DEFAULT_INITIAL_SIZE) {
            rehash(n / 2);
        }
        return oldValue;
    }
}
