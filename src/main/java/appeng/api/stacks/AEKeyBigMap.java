package appeng.api.stacks;

import static it.unimi.dsi.fastutil.HashCommon.arraySize;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.function.BiConsumer;
import java.util.function.ObjLongConsumer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

public final class AEKeyBigMap<K extends AEKey> extends Reference2ReferenceOpenHashMap<K, BigInteger>
        implements Iterable<Reference2ReferenceMap.Entry<K, BigInteger>> {

    @UnmodifiableView
    public static final AEKeyBigMap<AEKey> EMPTY = new AEKeyBigMap<>(0);

    public AEKeyBigMap() {
        super(DEFAULT_INITIAL_SIZE, DEFAULT_LOAD_FACTOR);
    }

    public AEKeyBigMap(int size) {
        super(size, DEFAULT_LOAD_FACTOR);
    }

    public AEKeyBigMap(AEKeyLongMap<K> map) {
        super(map.size(), DEFAULT_LOAD_FACTOR);
        map.fastForEach((k, v) -> set(k, BigInteger.valueOf(v)));
    }

    public AEKeyBigMap(AEKeyBigMap<K> map) {
        super(map.size, DEFAULT_LOAD_FACTOR);
        map.fastForEach(this::set);
    }

    public AEKeyBigMap(Reference2ReferenceOpenHashMap<K, BigInteger> map) {
        super(map.size(), DEFAULT_LOAD_FACTOR);
        map.reference2ReferenceEntrySet().fastForEach(e -> set(e.getKey(), e.getValue()));
    }

    @Override
    public @NotNull Iterator<Entry<K, BigInteger>> iterator() {
        return reference2ReferenceEntrySet().fastIterator();
    }

    @Override
    public BigInteger put(final K k, final BigInteger v) {
        if (k == null) {
            return BigInteger.ZERO;
        }
        final int pos = AEKeyHash.find(key, mask, k);
        if (pos >= 0) {
            final Object[] value = this.value;
            final BigInteger oldValue = (BigInteger) value[pos];
            value[pos] = v;
            return oldValue;
        }
        insertAt(-pos - 1, k, v);
        return BigInteger.ZERO;
    }

    @Override
    public BigInteger remove(final Object k) {
        if (!(k instanceof AEKey what)) {
            return BigInteger.ZERO;
        }
        final int pos = AEKeyHash.find(key, mask, what);
        return pos >= 0 ? removeAt(pos) : BigInteger.ZERO;
    }

    public BigInteger addTo(final AEKey k, final BigInteger incr) {
        if (k == null) {
            return BigInteger.ZERO;
        }
        final int pos = AEKeyHash.find(key, mask, k);
        if (pos >= 0) {
            final Object[] value = this.value;
            final BigInteger oldValue = (BigInteger) value[pos];
            value[pos] = oldValue.add(incr);
            return oldValue;
        }
        insertAt(-pos - 1, k, incr);
        return BigInteger.ZERO;
    }

    @Override
    public BigInteger get(final Object k) {
        if (!(k instanceof AEKey what)) {
            return BigInteger.ZERO;
        }
        final int pos = AEKeyHash.find(key, mask, what);
        final Object[] value = this.value;
        return pos >= 0 ? (BigInteger) value[pos] : BigInteger.ZERO;
    }

    @Override
    public boolean containsKey(final Object k) {
        return k instanceof AEKey what && AEKeyHash.find(key, mask, what) >= 0;
    }

    @Override
    public AEKeyBigMap<K> clone() {
        return (AEKeyBigMap<K>) super.clone();
    }

    public boolean contains(final AEKey k) {
        return k != null && AEKeyHash.find(key, mask, k) >= 0;
    }

    public long getLongAmount(final AEKey k) {
        if (k == null) {
            return 0;
        }
        final int pos = AEKeyHash.find(key, mask, k);
        final Object[] value = this.value;
        return pos >= 0 ? saturateToLong((BigInteger) value[pos]) : 0;
    }

    public BigInteger getAmount(final AEKey k) {
        if (k == null) {
            return BigInteger.ZERO;
        }
        final int pos = AEKeyHash.find(key, mask, k);
        final Object[] value = this.value;
        return pos >= 0 ? (BigInteger) value[pos] : BigInteger.ZERO;
    }

    public void set(final AEKey k, final BigInteger v) {
        if (k == null) {
            return;
        }
        final int pos = AEKeyHash.find(key, mask, k);
        if (pos >= 0) {
            final Object[] value = this.value;
            value[pos] = v;
        } else {
            insertAt(-pos - 1, k, v);
        }
    }

    public void insert(final AEKey k, final BigInteger amount) {
        if (k == null || amount.signum() <= 0) {
            return;
        }
        final int pos = AEKeyHash.find(key, mask, k);
        if (pos >= 0) {
            final Object[] value = this.value;
            value[pos] = ((BigInteger) value[pos]).add(amount);
        } else {
            insertAt(-pos - 1, k, amount);
        }
    }

    public BigInteger insert(final AEKey k, final BigInteger amount, final BigInteger limit) {
        if (k == null || amount.signum() <= 0 || limit.signum() <= 0) {
            return BigInteger.ZERO;
        }
        final int pos = AEKeyHash.find(key, mask, k);
        if (pos >= 0) {
            final Object[] value = this.value;
            final BigInteger oldValue = (BigInteger) value[pos];
            if (oldValue.compareTo(limit) >= 0) {
                return BigInteger.ZERO;
            }
            final BigInteger newValue = oldValue.add(amount);
            if (newValue.compareTo(limit) > 0) {
                value[pos] = limit;
                return limit.subtract(oldValue);
            }
            value[pos] = newValue;
            return amount;
        }
        final BigInteger toInsert = amount.compareTo(limit) > 0 ? limit : amount;
        insertAt(-pos - 1, k, toInsert);
        return toInsert;
    }

    public BigInteger extract(final AEKey k, final BigInteger amount) {
        if (k == null || amount.signum() <= 0) {
            return BigInteger.ZERO;
        }
        final int pos = AEKeyHash.find(key, mask, k);
        if (pos < 0) {
            return BigInteger.ZERO;
        }
        final Object[] value = this.value;
        final BigInteger oldValue = (BigInteger) value[pos];
        if (oldValue.compareTo(amount) > 0) {
            value[pos] = oldValue.subtract(amount);
            return amount;
        }
        return removeAt(pos);
    }

    public long extractLong(final AEKey k, final long amount) {
        if (k == null || amount < 1) {
            return 0;
        }
        final int pos = AEKeyHash.find(key, mask, k);
        if (pos < 0) {
            return 0;
        }
        final Object[] value = this.value;
        final var bigAmount = BigInteger.valueOf(amount);
        final BigInteger oldValue = (BigInteger) value[pos];
        if (oldValue.compareTo(bigAmount) > 0) {
            value[pos] = oldValue.subtract(bigAmount);
            return amount;
        }
        return saturateToLong(removeAt(pos));
    }

    public void putAll(AEKeyLongMap<K> map) {
        this.ensureCapacity(map.size());
        map.fastForEach((k, v) -> set(k, BigInteger.valueOf(v)));
    }

    public void putAll(AEKeyBigMap<K> map) {
        this.ensureCapacity(map.size);
        map.fastForEach(this::set);
    }

    public void addAll(AEKeyBigMap<K> map) {
        this.ensureCapacity(map.size);
        map.fastForEach(this::addTo);
    }

    public void fastForEach(final BiConsumer<? super K, BigInteger> consumer) {
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
                consumer.accept((K) k, (BigInteger) value[pos]);
                remaining--;
            }
        }
    }

    public void fastForEachLong(final ObjLongConsumer<? super K> consumer) {
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
                consumer.accept((K) k, saturateToLong((BigInteger) value[pos]));
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
        final Object[] value = this.value;
        for (int i = 0, len = value.length; i < len; i++) {
            value[i] = BigInteger.ZERO;
        }
    }

    private void insertAt(final int pos, final AEKey k, final BigInteger v) {
        final Object[] key = this.key;
        final Object[] value = this.value;
        key[pos] = k;
        value[pos] = v;
        if (size++ >= maxFill) {
            rehash(arraySize(size + 1, f));
        }
    }

    private BigInteger removeAt(final int pos) {
        final Object[] value = this.value;
        final BigInteger oldValue = (BigInteger) value[pos];
        --this.size;
        this.shiftKeys(pos);
        if (this.n > this.minN && this.size < this.maxFill / 4 && this.n > DEFAULT_INITIAL_SIZE) {
            this.rehash(this.n / 2);
        }
        return oldValue;
    }

    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

    public static long saturateToLong(BigInteger value) {
        if (value == null) {
            return 0L;
        }
        int bitLength = value.bitLength();
        if (bitLength < 63) {
            return value.longValue();
        } else if (bitLength > 63) {
            return Long.MAX_VALUE;
        }
        return value.compareTo(MAX_LONG) > 0 ? Long.MAX_VALUE : value.longValue();
    }
}
