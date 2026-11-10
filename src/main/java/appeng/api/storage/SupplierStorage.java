package appeng.api.storage;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.stacks.KeyVisitor;

/**
 * Delegates all calls to a {@link MEStorage} returned by a supplier such that the underlying storage can change
 * dynamically. If the supplier returns a null value, this storage will appear empty and read-only.
 */
public final class SupplierStorage implements MEStorage {
    private final Supplier<@Nullable MEStorage> supplier;

    public SupplierStorage(Supplier<@Nullable MEStorage> supplier) {
        this.supplier = supplier;
    }

    @Nullable
    public Object getResourceIdentity() {
        var delegate = supplier.get();
        return delegate == null ? null : delegate.getResourceIdentity();
    }

    @Override
    public boolean supportsKeyType(AEKeyType<?> type) {
        var delegate = supplier.get();
        return delegate != null && delegate.supportsKeyType(type);
    }

    @Override
    public int operationTime() {
        var delegate = supplier.get();
        return delegate == null ? 0 : delegate.operationTime();
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        var delegate = supplier.get();
        return delegate == null ? 0 : delegate.insert(what, amount, mode, source);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        var delegate = supplier.get();
        return delegate == null ? 0 : delegate.extract(what, amount, mode, source);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        var delegate = supplier.get();
        if (delegate != null) {
            delegate.getAvailableStacks(out);
        }
    }

    @Override
    public boolean forEachKey(KeyVisitor visitor) {
        var delegate = supplier.get();
        if (delegate != null) {
            return delegate.forEachKey(visitor);
        }
        return false;
    }

    @Override
    public Component getDescription() {
        var delegate = supplier.get();
        if (delegate != null) {
            return delegate.getDescription();
        }
        return Component.empty();
    }

    @Override
    public KeyCounter getAvailableStacks() {
        var delegate = supplier.get();
        if (delegate != null) {
            return delegate.getAvailableStacks();
        }
        return KeyCounter.empty();
    }
}
