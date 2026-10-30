package appeng.api.stacks;

/**
 * 126 位非负整数：{@code 值 = hi·2^63 + lo}，{@code hi}、{@code lo} 都在 {@code [0, 2^63)}。
 * <p>
 * 可变、运算只用 long，供 {@link AEKeyBigMap} 这类高频累加的数量表就地读写：插入/取出不产生任何对象。 越界一律饱和到 {@code 2^126-1}（对游戏内数量等价于无限），读出时超出
 * {@code long} 按 {@link Long#MAX_VALUE} 计。
 * <p>
 * {@link #toByteArray()} / {@link #fromByteArray(byte[])} 按 {@code BigInteger.toByteArray()} 的补码大端表示 （至少 1 字节，最高位为 1
 * 时前置一个 {@code 0x00}），与之前用 BigInteger 写出的存档逐字节一致，旧档免迁移。
 */
public final class U126 {

    public static final long LIMB_MAX = Long.MAX_VALUE;
    private static final double TWO_63 = 9.223372036854775808E18;

    private long hi;
    private long lo;

    public U126() {
    }

    public U126(long amount) {
        this.lo = amount;
    }

    public long hi() {
        return hi;
    }

    public long lo() {
        return lo;
    }

    public boolean isZero() {
        return (hi | lo) == 0;
    }

    /** 是否已超过 {@link Long#MAX_VALUE}。 */
    public boolean isAboveLong() {
        return hi != 0;
    }

    /** 是否大于 {@code amount}（{@code amount} 非负）。 */
    public boolean isAbove(long amount) {
        return hi != 0 || lo > amount;
    }

    /** 饱和到 long。 */
    public long toLong() {
        return hi == 0 ? lo : Long.MAX_VALUE;
    }

    public double doubleValue() {
        return hi * TWO_63 + lo;
    }

    public int compareTo(U126 other) {
        return hi != other.hi ? Long.compare(hi, other.hi) : Long.compare(lo, other.lo);
    }

    /** 设为 {@code long} 范围里的值。 */
    public void setValue(long amount) {
        hi = 0;
        lo = amount;
    }

    public U126 copyOf() {
        final U126 copy = new U126();
        copy.hi = hi;
        copy.lo = lo;
        return copy;
    }

    public void saturate() {
        hi = LIMB_MAX;
        lo = LIMB_MAX;
    }

    /** 就地加，返回实际加入量（到 2^126-1 饱和，正常情况等于 {@code amount}）。 */
    public long add(long amount) {
        if (amount < 1) {
            return 0;
        }
        final long newLo = lo + amount;
        if (newLo >= 0) {
            // 两段都小于 2^63，和没有越过 2^63 就没有进位
            lo = newLo;
            return amount;
        }
        final long newHi = hi + 1;
        if (newHi < 0) {
            // 越过 2^126，饱和
            final long space = LIMB_MAX - lo;
            saturate();
            return space;
        }
        hi = newHi;
        lo = newLo & LIMB_MAX;
        return amount;
    }

    /** 就地加上另一个 126 位值，越界饱和。 */
    public void add(U126 other) {
        final long newLo = lo + other.lo;
        lo = newLo & LIMB_MAX;
        final long newHi = hi + other.hi + (newLo < 0 ? 1 : 0);
        if (newHi < 0) {
            saturate();
        } else {
            hi = newHi;
        }
    }

    /** 就地减，返回实际取出量。 */
    public long subtract(long amount) {
        if (amount < 1) {
            return 0;
        }
        if (hi == 0) {
            if (amount >= lo) {
                final long taken = lo;
                lo = 0;
                return taken;
            }
            lo -= amount;
            return amount;
        }
        // hi > 0 时数量必然大于 amount，够减
        final long newLo = lo - amount;
        if (newLo >= 0) {
            lo = newLo;
        } else {
            hi--;
            lo = newLo & LIMB_MAX;
        }
        return amount;
    }

    /**
     * 补码大端字节，与 {@code BigInteger.toByteArray()} 完全一致；用于写盘时直接落字节，不再经过 BigInteger。
     */
    public byte[] toByteArray() {
        if (isZero()) {
            return new byte[] { 0 };
        }
        final byte[] tmp = new byte[16];
        long upper = hi;
        long lower = lo;
        int length = 0;
        while ((upper | lower) != 0) {
            tmp[15 - length] = (byte) lower;
            lower = (lower >>> 8) | ((upper & 0xFF) << 55);
            upper >>>= 8;
            length++;
        }
        // 最高位为 1 时要补一个 0x00，否则读出来是负数
        final boolean sign = (tmp[16 - length] & 0x80) != 0;
        final int total = length + (sign ? 1 : 0);
        final byte[] out = new byte[total];
        System.arraycopy(tmp, 16 - length, out, total - length, length);
        return out;
    }

    /** 读 {@link #toByteArray()} 写出的字节；超过 126 位时饱和。 */
    public void fromByteArray(byte[] bytes) {
        long upper = 0;
        long lower = 0;
        for (byte b : bytes) {
            if ((upper >>> 55) != 0) {
                saturate();
                return;
            }
            upper = (upper << 8) + (lower >>> 55);
            if (upper < 0) {
                saturate();
                return;
            }
            lower = ((lower << 8) & LIMB_MAX) | (b & 0xFF);
        }
        hi = upper;
        lo = lower;
    }

    /** 解析十进制字符串（旧 NBT 存档用），非数字字符跳过，超出 2^126-1 饱和。 */
    public void parseDecimal(String text) {
        hi = 0;
        lo = 0;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c < '0' || c > '9') {
                continue;
            }
            multiplyBy10AndAdd(c - '0');
        }
    }

    /** {@code this = this * 10 + digit}，越界饱和。 */
    private void multiplyBy10AndAdd(long digit) {
        // 10 * lo 拆成 2^64 高位 + 低 64 位
        final long loTimes = lo * 10;
        final long loHigh = Math.multiplyHigh(lo, 10);
        // 10 * hi 一旦越过 2^64，结果必然超出 126 位
        final long hiTimes = hi * 10;
        final long hiHigh = Math.multiplyHigh(hi, 10);
        if (hiHigh != 0) {
            saturate();
            return;
        }
        final long low = (loTimes & LIMB_MAX) + digit;
        final long lowCarry = low < 0 ? 1 : 0;
        // 2^64 = 2·2^63，所以 10*lo 的 64 位高位要乘 2 才能加到 2^63 段
        final long addend = (loHigh << 1) + (loTimes >>> 63) + lowCarry;
        if (Long.compareUnsigned(hiTimes, (1L << 63) - addend) >= 0) {
            saturate();
            return;
        }
        hi = hiTimes + addend;
        lo = low & LIMB_MAX;
    }
}
