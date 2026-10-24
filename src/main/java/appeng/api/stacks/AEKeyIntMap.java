package appeng.api.stacks;

import appeng.api.identity.IdentityKeyIntMap;

public final class AEKeyIntMap<K extends AEKey> extends IdentityKeyIntMap<K> {

    public AEKeyIntMap() {
        super();
    }

    public AEKeyIntMap(int expected) {
        super(expected);
    }

}
