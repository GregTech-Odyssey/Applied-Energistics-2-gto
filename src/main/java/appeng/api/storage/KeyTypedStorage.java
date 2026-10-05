package appeng.api.storage;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;

public interface KeyTypedStorage extends MEStorage {

    boolean supportsKeyType(AEKeyType type);

    @Nullable
    MEStorage forKeyType(AEKeyType type);

    default boolean containsAny(Set<AEKey> primaryKeys) {
        var counter = new KeyCounter();
        getAvailableStacks(counter);
        for (var entry : counter) {
            if (primaryKeys.contains(entry.getKey().dropSecondary())) {
                return true;
            }
        }
        return false;
    }

    default boolean isEmpty() {
        var counter = new KeyCounter();
        getAvailableStacks(counter);
        return counter.isEmpty();
    }

    default void getAvailableStacks(KeyCounter out, boolean extractableOnly) {
        getAvailableStacks(out);
    }
}
