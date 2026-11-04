package appeng.api.stacks;

import appeng.api.identity.IdentityKeyIntMap;

public final class AEKeyIntMap<K extends AEKey> extends IdentityKeyIntMap<K> {

    public AEKeyIntMap() {
        super();
    }

    public AEKeyIntMap(int expected) {
        super(expected);
    }

    public boolean forEachKey(KeyVisitor visitor) {
        int remaining = this.size;
        if (remaining == 0) {
            return false;
        }
        final Object[] key = this.key;
        final int[] value = this.value;
        int pos = this.n;
        Object k;
        while (remaining > 0 && pos-- != 0) {
            if ((k = key[pos]) != null) {
                if (visitor.visit((AEKey) k, value[pos])) {
                    return true;
                }
                remaining--;
            }
        }
        return false;
    }

    @Override
    public AEKeyIntMap<K> clone() {
        return (AEKeyIntMap<K>) super.clone();
    }

}
