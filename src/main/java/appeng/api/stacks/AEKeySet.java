package appeng.api.stacks;

import static it.unimi.dsi.fastutil.HashCommon.arraySize;

import java.util.Collection;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

public final class AEKeySet<K extends AEKey> extends ReferenceOpenHashSet<K> {

    public AEKeySet() {
        super();
    }

    public AEKeySet(int expected) {
        super(expected);
    }

    public AEKeySet(Collection<? extends K> c) {
        super(c);
    }

    @Override
    public boolean contains(final Object k) {
        if (k instanceof AEKey what) {
            return AEKeyHash.find(key, mask, what) >= 0;
        }
        return super.contains(k);
    }

    @Override
    public boolean add(final K k) {
        if (k == null) {
            return super.add(null);
        }
        final Object[] key = this.key;
        final int pos = AEKeyHash.find(key, mask, k);
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
    public boolean remove(final Object k) {
        if (k instanceof AEKey what) {
            final int pos = AEKeyHash.find(key, mask, what);
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
        return super.remove(k);
    }
}
