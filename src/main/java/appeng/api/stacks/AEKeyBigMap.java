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

    public AEKeyBigMap(AEKeyMap<K> map) {
        super(map.size(), DEFAULT_LOAD_FACTOR);
        map.fastForEach((k, v) -> set(k, BigInteger.valueOf(v)));
    }

    public AEKeyBigMap(AEKeyBigMap<K> map) {
        super(map.size, DEFAULT_LOAD_FACTOR);
        map.fastForEach(this::put);
    }

    public AEKeyBigMap(Reference2ReferenceOpenHashMap<K, BigInteger> map) {
        super(map.size(), DEFAULT_LOAD_FACTOR);
        map.reference2ReferenceEntrySet().fastForEach(e -> put(e.getKey(), e.getValue()));
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
        final Object[] key = this.key;
        final int mask = this.mask;
        int pos;
        Object curr;
        if ((curr = key[pos = k.mix & mask]) != null) {
            do
                if (curr == k) {
                    final BigInteger oldValue = value[pos];
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
        return BigInteger.ZERO;
    }

    @Override
    public BigInteger remove(final Object k) {
        if (k == null) {
            return BigInteger.ZERO;
        }
        final Object[] key = this.key;
        final int mask = this.mask;
        Object curr;
        int pos;
        if ((curr = key[pos = ((AEKey) k).mix & mask]) == null) {
            return BigInteger.ZERO;
        } else if (k == curr) {
            return this.removeEntry(pos);
        } else {
            while ((curr = key[pos = pos + 1 & mask]) != null) {
                if (k == curr) {
                    return this.removeEntry(pos);
                }
            }
            return BigInteger.ZERO;
        }

    }

    public BigInteger addTo(final AEKey k, final BigInteger incr) {
        if (k == null) {
            return BigInteger.ZERO;
        }
        final Object[] key = this.key;
        final int mask = this.mask;
        int pos;
        Object curr;
        if ((curr = key[pos = k.mix & mask]) != null) {
            do
                if (curr == k) {
                    final BigInteger oldValue = value[pos];
                    final BigInteger newValue = oldValue.add(incr);
                    value[pos] = newValue;
                    return oldValue;
                }
            while ((curr = key[pos = (pos + 1) & mask]) != null);
        }
        key[pos] = k;
        value[pos] = incr;
        if (size++ >= maxFill) {
            rehash(arraySize(size + 1, f));
        }
        return BigInteger.ZERO;
    }

    @Override
    public BigInteger get(final Object k) {
        if (k == null) {
            return BigInteger.ZERO;
        }
        final Object[] key = this.key;
        final int mask = this.mask;
        Object curr;
        int pos;
        if ((curr = key[pos = ((AEKey) k).mix & mask]) == null) {
            return BigInteger.ZERO;
        } else if (k == curr) {
            return value[pos];
        } else {
            while ((curr = key[pos = (pos + 1) & mask]) != null) {
                if (k == curr) {
                    return value[pos];
                }
            }
            return BigInteger.ZERO;
        }
    }

    @Override
    public boolean containsKey(final Object k) {
        if (k == null) {
            return false;
        }
        final Object[] key = this.key;
        final int mask = this.mask;
        Object curr;
        int pos;
        if ((curr = key[pos = ((AEKey) k).mix & mask]) == null) {
            return false;
        } else if (k == curr) {
            return true;
        } else {
            while ((curr = key[pos = (pos + 1) & mask]) != null) {
                if (k == curr) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public AEKeyBigMap<K> clone() {
        return (AEKeyBigMap<K>) super.clone();
    }

    public boolean contains(final AEKey k) {
        if (k == null) {
            return false;
        }
        final Object[] key = this.key;
        final int mask = this.mask;
        Object curr;
        int pos;
        if ((curr = key[pos = k.mix & mask]) == null) {
            return false;
        } else if (k == curr) {
            return true;
        } else {
            while ((curr = key[pos = (pos + 1) & mask]) != null) {
                if (k == curr) {
                    return true;
                }
            }
            return false;
        }
    }

    public long getLongAmount(final AEKey k) {
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
            return saturateToLong(value[pos]);
        } else {
            while ((curr = key[pos = (pos + 1) & mask]) != null) {
                if (k == curr) {
                    return saturateToLong(value[pos]);
                }
            }
            return 0;
        }
    }

    public BigInteger getAmount(final AEKey k) {
        if (k == null) {
            return BigInteger.ZERO;
        }
        final Object[] key = this.key;
        final int mask = this.mask;
        Object curr;
        int pos;
        if ((curr = key[pos = k.mix & mask]) == null) {
            return BigInteger.ZERO;
        } else if (k == curr) {
            return value[pos];
        } else {
            while ((curr = key[pos = (pos + 1) & mask]) != null) {
                if (k == curr) {
                    return value[pos];
                }
            }
            return BigInteger.ZERO;
        }
    }

    public void set(final AEKey k, final BigInteger v) {
        if (k == null) {
            return;
        }
        final Object[] key = this.key;
        final int mask = this.mask;
        int pos;
        Object curr;
        if ((curr = key[pos = k.mix & mask]) != null) {
            do
                if (curr == k) {
                    value[pos] = v;
                    return;
                }
            while ((curr = key[pos = (pos + 1) & mask]) != null);
        }
        key[pos] = k;
        value[pos] = v;
        if (size++ >= maxFill) {
            rehash(arraySize(size + 1, f));
        }
    }

    public void insert(final AEKey k, final BigInteger amount) {
        if (k == null || amount.signum() <= 0) {
            return;
        }
        final Object[] key = this.key;
        final int mask = this.mask;
        int pos;
        Object curr;
        if ((curr = key[pos = k.mix & mask]) != null) {
            do
                if (curr == k) {
                    final BigInteger oldValue = value[pos];
                    final BigInteger newValue = oldValue.add(amount);
                    value[pos] = newValue;
                    return;
                }
            while ((curr = key[pos = (pos + 1) & mask]) != null);
        }
        key[pos] = k;
        value[pos] = amount;
        if (size++ >= maxFill) {
            rehash(arraySize(size + 1, f));
        }
    }

    public BigInteger insert(final AEKey k, final BigInteger amount, final BigInteger limit) {
        if (k == null || amount.signum() <= 0 || limit.signum() <= 0) {
            return BigInteger.ZERO;
        }
        final Object[] key = this.key;
        final int mask = this.mask;
        int pos;
        Object curr;
        if ((curr = key[pos = k.mix & mask]) != null) {
            do
                if (curr == k) {
                    final BigInteger oldValue = value[pos];
                    if (oldValue.compareTo(limit) >= 0) {
                        return BigInteger.ZERO;
                    }
                    final BigInteger newValue = oldValue.add(amount);
                    if (newValue.compareTo(limit) > 0) {
                        value[pos] = limit;
                        return limit.subtract(oldValue);
                    } else {
                        value[pos] = newValue;
                        return amount;
                    }
                }
            while ((curr = key[pos = (pos + 1) & mask]) != null);
        }
        final BigInteger toInsert = amount.compareTo(limit) > 0 ? limit : amount;
        key[pos] = k;
        value[pos] = toInsert;
        if (size++ >= maxFill) {
            rehash(arraySize(size + 1, f));
        }
        return toInsert;
    }

    public BigInteger extract(final AEKey k, final BigInteger amount) {
        if (k == null || amount.signum() <= 0) {
            return BigInteger.ZERO;
        }
        final Object[] key = this.key;
        final int mask = this.mask;
        int pos;
        Object curr;
        if ((curr = key[pos = k.mix & mask]) != null) {
            do
                if (curr == k) {
                    final BigInteger oldValue = value[pos];
                    if (oldValue.compareTo(amount) > 0) {
                        value[pos] = oldValue.subtract(amount);
                        return amount;
                    } else {
                        return removeEntry(pos);
                    }
                }
            while ((curr = key[pos = (pos + 1) & mask]) != null);
        }
        return BigInteger.ZERO;
    }

    public void putAll(AEKeyMap<K> map) {
        this.ensureCapacity(map.size());
        map.fastForEach((k, v) -> set(k, BigInteger.valueOf(v)));
    }

    public void putAll(AEKeyBigMap<K> map) {
        this.ensureCapacity(map.size);
        map.fastForEach(this::put);
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
        for (int i = 0, len = value.length; i < len; i++) {
            value[i] = BigInteger.ZERO;
        }
    }

    private BigInteger removeEntry(final int pos) {
        final BigInteger oldValue = this.value[pos];
        --this.size;
        this.shiftKeys(pos);
        if (this.n > this.minN && this.size < this.maxFill / 4 && this.n > 16) {
            this.rehash(this.n / 2);
        }

        return oldValue;
    }

    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

    public static long saturateToLong(BigInteger value) {
        if (value == null)
            return 0L;
        int bitLength = value.bitLength();
        if (bitLength < 63) {
            return value.longValue();
        } else if (bitLength > 63) {
            return Long.MAX_VALUE;
        }
        return value.compareTo(MAX_LONG) > 0 ? Long.MAX_VALUE : value.longValue();
    }
}
