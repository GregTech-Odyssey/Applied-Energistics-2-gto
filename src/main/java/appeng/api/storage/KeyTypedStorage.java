package appeng.api.storage;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.stacks.KeyVisitor;

public interface KeyTypedStorage extends MEStorage {

    boolean supportsKeyType(AEKeyType type);

    @Nullable
    MEStorage forKeyType(AEKeyType type);

    default boolean containsAny(Set<AEKey> primaryKeys) {
        return forEachKey((k, v) -> primaryKeys.contains(k.dropSecondary()));
    }

    default boolean isEmpty() {
        return forEachKey((k, v) -> v > 0);
    }

    default boolean forEachKey(KeyVisitor visitor, boolean extractableOnly) {
        return forEachKey(visitor);
    }

    default void getAvailableStacks(KeyCounter out, boolean extractableOnly) {
        getAvailableStacks(out);
    }
}
