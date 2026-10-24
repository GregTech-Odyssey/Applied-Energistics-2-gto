package appeng.api.stacks;

import java.util.Collection;

import appeng.api.identity.IdentityKeySet;

public final class AEKeySet<K extends AEKey> extends IdentityKeySet<K> {

    public AEKeySet() {
        super();
    }

    public AEKeySet(int expected) {
        super(expected);
    }

    public AEKeySet(Collection<? extends K> c) {
        super(c);
    }
}
