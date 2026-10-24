package appeng.api.identity;

import it.unimi.dsi.fastutil.HashCommon;

public class IdentityKey {

    public static final IdentityKey[] EMPTY_IDENTITY_KEY_ARRAY = new IdentityKey[0];

    public final int hashCode = System.identityHashCode(this);
    public final int mix = HashCommon.mix(hashCode);

    @Override
    public final int hashCode() {
        return hashCode;
    }

    public static int find(Object[] key, int mask, IdentityKey k) {
        int pos = k.mix & mask;
        Object curr;
        while ((curr = key[pos]) != null) {
            if (curr == k) {
                return pos;
            }
            pos = (pos + 1) & mask;
        }
        return -pos - 1;
    }
}
