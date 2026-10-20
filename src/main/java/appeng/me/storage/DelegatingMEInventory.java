package appeng.me.storage;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

/**
 * Convenient base class for wrapping another {@link MEStorage} and forwarding <strong>all</strong> methods to the base
 * inventory.
 * <p/>
 * If no delegate is set, it will act like a {@link NullInventory}.
 */
public class DelegatingMEInventory implements MEStorage {
    private MEStorage delegate;
    private long delegateStamp = MEStorage.nextChangeVersion();
    private long drainedStamp;

    public DelegatingMEInventory(MEStorage delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public MEStorage getDelegate() {
        return delegate;
    }

    public void setDelegate(MEStorage delegate) {
        this.delegate = delegate;
        this.delegateStamp = MEStorage.nextChangeVersion();
    }

    @Override
    public boolean drainChanges(Object reader, java.util.function.ObjLongConsumer<AEKey> changed) {
        if (delegate == null)
            return false;
        if (drainedStamp != delegateStamp) {
            drainedStamp = delegateStamp;
            delegate.drainChanges(reader, IGNORE_CHANGES);
            return false;
        }
        return delegate.drainChanges(reader, changed);
    }

    @Override
    public long getChangeVersion() {
        if (delegate == null)
            return delegateStamp;
        long v = delegate.getChangeVersion();
        return v < 0 ? -1 : v ^ (delegateStamp * 0x9E3779B97F4A7C15L);
    }

    @Nullable
    public Object getResourceIdentity() {
        return delegate == null ? null : delegate.getResourceIdentity();
    }

    @Override
    public boolean isPreferredStorageFor(AEKey input, IActionSource source) {
        return delegate.isPreferredStorageFor(input, source);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return delegate.insert(what, amount, mode, source);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return delegate.extract(what, amount, mode, source);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        delegate.getAvailableStacks(out);
    }

    @Override
    public KeyCounter getAvailableStacks() {
        return delegate.getAvailableStacks();
    }

    @Override
    public Component getDescription() {
        return delegate.getDescription();
    }
}
