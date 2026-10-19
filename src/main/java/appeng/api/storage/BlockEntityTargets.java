package appeng.api.storage;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.WeakReference;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullConsumer;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.capabilities.Capabilities;
import appeng.me.storage.ExternalStorageFacade;

/**
 * Capability lookups of one block entity, shared by every {@link StorageTargetResolver} that targets it. A result is
 * trusted once the provider returns the same {@link LazyOptional} twice, until it is invalidated or rechecked.
 */
public final class BlockEntityTargets {

    public interface Holder {
        @Nullable
        BlockEntityTargets ae2$getTargets();

        void ae2$setTargets(BlockEntityTargets targets);
    }

    private static final int RECHECK_HITS = 64;
    private static final int SIDES = 7;
    private static final int STORAGE = 0;
    private static final int ITEMS = SIDES;
    private static final int FLUIDS = SIDES * 2;

    @Nullable
    private static final VarHandle FIELD = field();

    private final Entry[] entries = new Entry[SIDES * 3];

    private BlockEntityTargets() {
    }

    static Entry storage(BlockEntity be, @Nullable Direction side) {
        return of(be).entry(be, STORAGE, side, null, Capabilities.STORAGE);
    }

    @Nullable
    static Entry handler(BlockEntity be, @Nullable Direction side, AEKeyType type) {
        if (type == AEKeyTypes.ITEMS) {
            return of(be).entry(be, ITEMS, side, type, ForgeCapabilities.ITEM_HANDLER);
        }
        if (type == AEKeyTypes.FLUIDS) {
            return of(be).entry(be, FLUIDS, side, type, ForgeCapabilities.FLUID_HANDLER);
        }
        return null;
    }

    static ExternalStorageFacade facade(BlockEntity be, @Nullable Direction side, AEKeyType type, Object raw) {
        var entry = handler(be, side, type);
        var facade = entry.facade;
        if (facade == null || entry.facadeRaw != raw) {
            facade = type == AEKeyTypes.ITEMS ? ExternalStorageFacade.of((IItemHandler) raw)
                    : ExternalStorageFacade.of((IFluidHandler) raw);
            facade.setBlockEntity(be);
            entry.facade = facade;
            entry.facadeRaw = raw;
        }
        return facade;
    }

    @Nullable
    private static VarHandle field() {
        try {
            return MethodHandles.privateLookupIn(BlockEntity.class, MethodHandles.lookup())
                    .findVarHandle(BlockEntity.class, "ae2$targets", BlockEntityTargets.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static BlockEntityTargets of(BlockEntity be) {
        var field = FIELD;
        if (field != null) {
            var targets = (BlockEntityTargets) field.get(be);
            if (targets == null) {
                field.set(be, targets = new BlockEntityTargets());
            }
            return targets;
        }
        var holder = (Holder) be;
        var targets = holder.ae2$getTargets();
        if (targets == null) {
            holder.ae2$setTargets(targets = new BlockEntityTargets());
        }
        return targets;
    }

    public static boolean direct() {
        return FIELD != null;
    }

    private Entry entry(BlockEntity be, int kind, @Nullable Direction side, @Nullable AEKeyType type,
            Capability<?> capability) {
        var index = kind + (side == null ? SIDES - 1 : side.ordinal());
        var entry = entries[index];
        if (entry == null) {
            entries[index] = entry = new Entry(be, side, type, capability);
        }
        return entry;
    }

    static final class Entry {
        private final BlockEntity owner;
        @Nullable
        private final Direction side;
        @Nullable
        private final AEKeyType type;
        private final Capability<?> capability;
        @Nullable
        private LazyOptional<?> optional;
        @Nullable
        private LazyOptional<?> listened;
        @Nullable
        private Object value;
        private boolean trusted;
        private int hits;
        @Nullable
        private ExternalStorageFacade facade;
        @Nullable
        private Object facadeRaw;

        private Entry(BlockEntity owner, @Nullable Direction side, @Nullable AEKeyType type,
                Capability<?> capability) {
            this.owner = owner;
            this.side = side;
            this.type = type;
            this.capability = capability;
        }

        boolean matches(BlockEntity be, @Nullable Direction side, @Nullable AEKeyType type) {
            return owner == be && this.side == side && this.type == type;
        }

        @Nullable
        Object get() {
            if (--hits > 0 && (trusted || value == null)) {
                return value;
            }
            var optional = owner.getCapability(capability, side);
            hits = RECHECK_HITS;
            var value = optional.orElse(null);
            if (value == null) {
                this.optional = null;
                this.value = null;
                trusted = false;
                return null;
            }
            if (optional != this.optional) {
                this.optional = optional;
                trusted = false;
            } else if (!trusted) {
                trusted = true;
                if (listened != optional) {
                    listened = optional;
                    listen(optional);
                }
            }
            this.value = value;
            return value;
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private void listen(LazyOptional<?> optional) {
            optional.addListener((NonNullConsumer) new Invalidation(this));
        }

        private void invalidate(LazyOptional<?> optional) {
            if (optional == this.optional) {
                trusted = false;
                hits = 0;
            }
        }
    }

    private static final class Invalidation extends WeakReference<Entry> implements NonNullConsumer<LazyOptional<?>> {
        private Invalidation(Entry entry) {
            super(entry);
        }

        @Override
        public void accept(LazyOptional<?> optional) {
            var entry = get();
            if (entry != null) {
                entry.invalidate(optional);
            }
        }
    }
}
