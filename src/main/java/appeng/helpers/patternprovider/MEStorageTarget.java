package appeng.helpers.patternprovider;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.KeyTypedStorage;
import appeng.api.storage.MEStorage;

public final class MEStorageTarget implements PatternProviderTarget {

    private final MEStorage storage;
    private final IActionSource src;

    public MEStorageTarget(MEStorage storage, IActionSource src) {
        this.storage = storage;
        this.src = src;
    }

    public MEStorage storage() {
        return storage;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable type) {
        return storage.insert(what, amount, type, src);
    }

    @Override
    public boolean containsPatternInput(Set<AEKey> patternInputs) {
        return containsAny(patternInputs);
    }

    public boolean containsAny(@Nullable Set<AEKey> primaryKeys) {
        var storage = this.storage;
        if (storage instanceof KeyTypedStorage typed) {
            return primaryKeys == null ? !typed.isEmpty() : typed.containsAny(primaryKeys);
        }
        for (var entry : storage.getAvailableStacks()) {
            if (entry.getLongValue() > 0 && (primaryKeys == null
                    || primaryKeys.contains(entry.getKey().dropSecondary()))) {
                return true;
            }
        }
        return false;
    }
}
