package appeng.api.stacks;

import java.util.function.ObjLongConsumer;

import org.jetbrains.annotations.UnmodifiableView;

import appeng.api.identity.IdentityKey;
import appeng.api.identity.IdentityKeyObjectMap;

/**
 * 126 位非负整数表（值见 {@link U126}）。
 * <p>
 * 每个键的数量是一个就地修改的 {@link U126}，插入/取出只做 long 运算，不像 {@code BigInteger} 那样每次写入 都要新分配对象。上限 {@code 2^126-1}
 * 对游戏内数量等价于无限，越界按饱和处理；读出时超出 {@code long} 一律按 {@link Long#MAX_VALUE} 计，与旧的 {@code BigInteger} 实现口径一致。
 */
public final class AEKeyBigMap<K extends AEKey> extends IdentityKeyObjectMap<K, U126> {

    // fastutil allocates Object[], so access the array as Object[] before casting its elements.

    @UnmodifiableView
    public static final AEKeyBigMap<AEKey> EMPTY = new AEKeyBigMap<>(0);

    public AEKeyBigMap() {
        super(DEFAULT_INITIAL_SIZE, DEFAULT_LOAD_FACTOR);
    }

    public AEKeyBigMap(int size) {
        super(size, DEFAULT_LOAD_FACTOR);
    }

    @SuppressWarnings("all")
    @UnmodifiableView
    public static <T extends AEKey> AEKeyBigMap<T> empty() {
        return (AEKeyBigMap<T>) EMPTY;
    }

    /** 值是可变的，克隆时给每个键一份独立的 {@link U126}。 */
    @Override
    public AEKeyBigMap<K> clone() {
        final AEKeyBigMap<K> copy = (AEKeyBigMap<K>) super.clone();
        final Object[] values = copy.value;
        for (int i = 0, len = values.length; i < len; i++) {
            if (values[i] != null) {
                values[i] = ((U126) values[i]).copyOf();
            }
        }
        return copy;
    }

    /** 覆盖写入一个键的数量；负数忽略。 */
    public void set(final AEKey k, final long amount) {
        if (k == null || amount < 0) {
            return;
        }
        final int pos = IdentityKey.find(key, mask, k);
        if (pos >= 0) {
            ((U126) ((Object[]) value)[pos]).setValue(amount);
        } else {
            insertAt(-pos - 1, k, new U126(amount));
        }
    }

    /** 只读取一次的数量；返回的是表内实例，不要修改或长期持有。没有该键时返回 null。 */
    public U126 getAmount(final AEKey k) {
        if (k == null) {
            return null;
        }
        final int pos = IdentityKey.find(key, mask, k);
        return pos >= 0 ? (U126) ((Object[]) value)[pos] : null;
    }

    /** 饱和到 long 的数量，没有该键时为 0。 */
    public long getLongAmount(final AEKey k) {
        if (k == null) {
            return 0;
        }
        final int pos = IdentityKey.find(key, mask, k);
        return pos >= 0 ? ((U126) ((Object[]) value)[pos]).toLong() : 0;
    }

    /** 加；返回实际加入量（到 2^126-1 饱和，正常情况等于 {@code amount}）。 */
    public long addTo(final AEKey k, final long amount) {
        if (k == null || amount < 1) {
            return 0;
        }
        final int pos = IdentityKey.find(key, mask, k);
        if (pos >= 0) {
            return ((U126) ((Object[]) value)[pos]).add(amount);
        }
        insertAt(-pos - 1, k, new U126(amount));
        return amount;
    }

    public void insert(final AEKey k, final long amount) {
        addTo(k, amount);
    }

    /** 加到 {@code limit} 为止；返回实际加入量。 */
    public long insert(final AEKey k, final long amount, final long limit) {
        if (k == null || amount < 1 || limit < 1) {
            return 0;
        }
        final int pos = IdentityKey.find(key, mask, k);
        if (pos >= 0) {
            final U126 old = (U126) ((Object[]) value)[pos];
            if (old.isAboveLong()) {
                // 已超过 long，必然已达上限
                return 0;
            }
            final long space = limit - old.lo();
            if (space < 1) {
                return 0;
            }
            if (amount <= space) {
                old.add(amount);
                return amount;
            }
            old.setValue(limit);
            return space;
        }
        final long toInsert = Math.min(amount, limit);
        insertAt(-pos - 1, k, new U126(toInsert));
        return toInsert;
    }

    /** 取；返回实际取出量。数量超过 long 时按 long 饱和返回（与旧实现一致：整条取走）。 */
    public long extractLong(final AEKey k, final long amount) {
        if (k == null || amount < 1) {
            return 0;
        }
        final int pos = IdentityKey.find(key, mask, k);
        if (pos < 0) {
            return 0;
        }
        final U126 old = (U126) ((Object[]) value)[pos];
        if (old.isAbove(amount)) {
            old.subtract(amount);
            return amount;
        }
        final long taken = old.toLong();
        removeAt(pos);
        return taken;
    }

    public void putAll(final AEKeyLongMap<K> map) {
        this.ensureCapacity(map.size());
        map.fastForEach(this::set);
    }

    public void addAll(final AEKeyBigMap<K> map) {
        this.ensureCapacity(map.size);
        map.forEach(this::addAmount);
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
                consumer.accept((K) k, ((U126) value[pos]).toLong());
                remaining--;
            }
        }
    }

    private void addAmount(final AEKey k, final U126 amount) {
        if (k == null || amount.isZero()) {
            return;
        }
        final int pos = IdentityKey.find(key, mask, k);
        if (pos >= 0) {
            ((U126) ((Object[]) value)[pos]).add(amount);
        } else {
            insertAt(-pos - 1, k, amount.copyOf());
        }
    }
}
