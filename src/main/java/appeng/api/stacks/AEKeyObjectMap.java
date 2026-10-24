package appeng.api.stacks;

import appeng.api.identity.IdentityKeyObjectMap;

public final class AEKeyObjectMap<K extends AEKey, V> extends IdentityKeyObjectMap<K, V> {

    public AEKeyObjectMap() {
        super();
    }

    public AEKeyObjectMap(int expected) {
        super(expected);
    }

}
