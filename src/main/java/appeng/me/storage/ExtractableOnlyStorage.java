package appeng.me.storage;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.KeyTypedStorage;
import appeng.api.storage.MEStorage;

public final class ExtractableOnlyStorage implements MEStorage {

    private final KeyTypedStorage storage;

    public ExtractableOnlyStorage(KeyTypedStorage storage) {
        this.storage = storage;
    }

    public KeyTypedStorage storage() {
        return storage;
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return storage.isPreferredStorageFor(what, source);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return storage.insert(what, amount, mode, source);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return storage.extract(what, amount, mode, source);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        storage.getAvailableStacks(out, true);
    }

    @Override
    public Component getDescription() {
        return storage.getDescription();
    }

    @Nullable
    @Override
    public Object getResourceIdentity() {
        return storage.getResourceIdentity();
    }
}
