package appeng.api.stacks;

import java.math.BigInteger;
import java.util.function.ObjLongConsumer;

import org.jetbrains.annotations.UnmodifiableView;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

import appeng.api.identity.IdentityKey;
import appeng.api.identity.IdentityKeyObjectMap;

public final class AEKeyBigMap<K extends AEKey> extends IdentityKeyObjectMap<K, BigInteger> {

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
        map.forEach(this::set);
    }

    public AEKeyBigMap(Reference2ReferenceOpenHashMap<K, BigInteger> map) {
        super(map.size(), DEFAULT_LOAD_FACTOR);
        map.reference2ReferenceEntrySet().fastForEach(e -> set(e.getKey(), e.getValue()));
    }

    public BigInteger addTo(final AEKey k, final BigInteger incr) {
        if (k == null) {
            return BigInteger.ZERO;
        }
        final int pos = IdentityKey.find(key, mask, k);
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
    public AEKeyBigMap<K> clone() {
        return (AEKeyBigMap<K>) super.clone();
    }

    public long getLongAmount(final AEKey k) {
        if (k == null) {
            return 0;
        }
        final int pos = IdentityKey.find(key, mask, k);
        final Object[] value = this.value;
        return pos >= 0 ? saturateToLong((BigInteger) value[pos]) : 0;
    }

    public BigInteger getAmount(final AEKey k) {
        if (k == null) {
            return BigInteger.ZERO;
        }
        final int pos = IdentityKey.find(key, mask, k);
        final Object[] value = this.value;
        return pos >= 0 ? (BigInteger) value[pos] : BigInteger.ZERO;
    }

    public void insert(final AEKey k, final BigInteger amount) {
        if (k == null || amount.signum() <= 0) {
            return;
        }
        final int pos = IdentityKey.find(key, mask, k);
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
        final int pos = IdentityKey.find(key, mask, k);
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
        final int pos = IdentityKey.find(key, mask, k);
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
        final int pos = IdentityKey.find(key, mask, k);
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

    public void addAll(AEKeyBigMap<K> map) {
        this.ensureCapacity(map.size);
        map.forEach(this::addTo);
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

    public void reset() {
        final Object[] value = this.value;
        for (int i = 0, len = value.length; i < len; i++) {
            value[i] = BigInteger.ZERO;
        }
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
