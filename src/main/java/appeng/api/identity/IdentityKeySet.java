package appeng.api.identity;

import static it.unimi.dsi.fastutil.HashCommon.arraySize;

import java.util.Collection;
import java.util.function.Consumer;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

public class IdentityKeySet<K extends IdentityKey> extends ReferenceOpenHashSet<K> {

    public IdentityKeySet() {
        super(DEFAULT_INITIAL_SIZE, DEFAULT_LOAD_FACTOR);
    }

    public IdentityKeySet(int expected) {
        super(expected, DEFAULT_LOAD_FACTOR);
    }

    public IdentityKeySet(final int expected, final float f) {
        super(expected, f);
    }

    public IdentityKeySet(Collection<? extends K> c) {
        super(c.size(), DEFAULT_LOAD_FACTOR);
        c.forEach(this::add);
    }

    public final void fastClear() {
        if (size == 0)
            return;
        size = 0;
        containsNull = false;
        key = (K[]) IdentityKey.EMPTY_IDENTITY_KEY_ARRAY;
    }

    @Override
    public final boolean contains(final Object k) {
        if (k instanceof IdentityKey what) {
            return IdentityKey.find(key, mask, what) >= 0;
        }
        return false;
    }

    @Override
    public final boolean add(final K k) {
        if (k == null) {
            return false;
        }
        final Object[] key = this.key;
        final int pos = IdentityKey.find(key, mask, k);
        if (pos >= 0) {
            return false;
        }
        key[-pos - 1] = k;
        if (size++ >= maxFill) {
            rehash(arraySize(size + 1, f));
        }
        return true;
    }

    @Override
    public final boolean remove(final Object k) {
        if (k instanceof IdentityKey what) {
            final int pos = IdentityKey.find(key, mask, what);
            if (pos < 0) {
                return false;
            }
            size--;
            shiftKeys(pos);
            if (n > minN && size < maxFill / 4 && n > DEFAULT_INITIAL_SIZE) {
                rehash(n / 2);
            }
            return true;
        }
        return false;
    }

    @Override
    public final void forEach(final Consumer<? super K> action) {
        int remaining = this.size;
        if (remaining == 0) {
            return;
        }
        final Object[] key = this.key;
        int pos = this.n;
        Object k;
        while (remaining > 0 && pos-- != 0) {
            if ((k = key[pos]) != null) {
                action.accept((K) k);
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

}
