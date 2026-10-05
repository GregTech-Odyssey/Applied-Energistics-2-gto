package appeng.api.stacks;

final class AEKeyHash {

    private AEKeyHash() {
    }

    static int find(Object[] key, int mask, AEKey k) {
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
