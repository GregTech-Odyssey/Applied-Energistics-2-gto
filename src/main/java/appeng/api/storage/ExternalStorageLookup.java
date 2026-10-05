package appeng.api.storage;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.config.Actionable;
import appeng.api.inventories.PlatformInventoryWrapper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.StorageTargetResolver.Tier;
import appeng.capabilities.Capabilities;
import appeng.me.storage.CompositeStorage;
import appeng.me.storage.ExternalStorageFacade;
import appeng.me.storage.ExtractableOnlyStorage;
import appeng.parts.automation.StackWorldBehaviors;
import appeng.util.BlockApiCache;

/**
 * One consumer face: resolves the adjacent target through {@link StorageTargetResolver} and reuses the AE view built
 * for a raw target for as long as the raw target keeps its identity.
 */
public final class ExternalStorageLookup {

    private static final int RECHECK_HITS = 64;

    private final BlockApiCache<MEStorage> blockEntity;
    private final ServerLevel level;
    private final BlockPos pos;
    private final Direction side;
    private final Memo items = new Memo();
    private final Memo fluids = new Memo();
    private final Memo all = new Memo();
    @Nullable
    private Memo others;
    @Nullable
    private OtherStrategies otherStrategies;
    @Nullable
    private CompositeStorage composite;
    private Tier tier = Tier.NONE;
    @Nullable
    private Object raw;
    private boolean extractableOnly;
    @Nullable
    private Runnable changeListener;

    private ExternalStorageLookup(ServerLevel level, BlockPos pos, Direction side) {
        this.blockEntity = BlockApiCache.create(Capabilities.STORAGE, level, pos);
        this.level = level;
        this.pos = pos;
        this.side = side;
    }

    public static ExternalStorageLookup create(ServerLevel level, BlockPos pos, Direction side) {
        return new ExternalStorageLookup(level, pos, side);
    }

    @Nullable
    public BlockEntity getBlockEntity() {
        return blockEntity.getBlockEntity();
    }

    @Nullable
    public MEStorage find(AEKeyType type, StorageAccess access) {
        var be = blockEntity.getBlockEntity();
        var memo = memoOf(type);
        var resolver = memo.resolver;
        var tier = resolver.resolve(be, side, type, access);
        var raw = resolver.raw();
        remember(tier, raw);
        return view(memo, tier, raw, be, type);
    }

    public ItemStack insert(ItemStack stack) {
        if (stack.isEmpty()) {
            return stack;
        }
        var storage = find(AEKeyTypes.ITEMS, StorageAccess.INSERT);
        if (storage == null) {
            return stack;
        }
        if (tier != Tier.STORAGE && raw instanceof IItemHandler handler) {
            return new PlatformInventoryWrapper(handler).addItems(stack);
        }
        var count = stack.getCount();
        var inserted = storage.insert(AEItemKey.of(stack), count, Actionable.MODULATE, IActionSource.empty());
        if (inserted <= 0) {
            return stack;
        }
        return inserted >= count ? ItemStack.EMPTY : stack.copyWithCount(count - (int) inserted);
    }

    @Nullable
    public MEStorage findAll(StorageAccess access) {
        var be = blockEntity.getBlockEntity();
        var all = this.all;
        var resolver = all.resolver;
        if (resolver.resolveAll(be, side, access) == Tier.STORAGE) {
            var raw = resolver.raw();
            remember(Tier.STORAGE, raw);
            return view(all, Tier.STORAGE, raw, be, null);
        }
        all.clear();
        return composite(be);
    }

    public void refresh() {
        items.resolver.forget();
        fluids.resolver.forget();
        all.resolver.forget();
        if (others != null) {
            others.resolver.forget();
        }
        if (otherStrategies != null) {
            otherStrategies.forget();
        }
    }

    public Tier tier() {
        return tier;
    }

    @Nullable
    public Object raw() {
        return raw;
    }

    public void configure(boolean extractableOnly, @Nullable Runnable changeListener) {
        var listenerChanged = this.changeListener != changeListener;
        this.changeListener = changeListener;
        if (this.extractableOnly != extractableOnly) {
            this.extractableOnly = extractableOnly;
            items.clear();
            fluids.clear();
            all.clear();
            if (others != null) {
                others.clear();
            }
            if (otherStrategies != null) {
                otherStrategies.forget();
            }
            return;
        }
        if (listenerChanged) {
            items.listen(changeListener);
            fluids.listen(changeListener);
            if (otherStrategies != null) {
                otherStrategies.forget();
            }
        }
    }

    @Nullable
    public static MEStorage resolve(@Nullable BlockEntity be, @Nullable Direction side, @Nullable AEKeyType type,
            StorageAccess access) {
        var resolver = new StorageTargetResolver();
        if (type != null) {
            return switch (resolver.resolve(be, side, type, access)) {
                case NONE -> null;
                case STORAGE -> (MEStorage) resolver.raw();
                case DIRECT, FORGE -> facade(resolver.raw(), be, type);
            };
        }
        if (resolver.resolveAll(be, side, access) == Tier.STORAGE) {
            return (MEStorage) resolver.raw();
        }
        var items = resolver.resolveHandler(be, side, AEKeyTypes.ITEMS) == Tier.NONE ? null
                : facade(resolver.raw(), be, AEKeyTypes.ITEMS);
        var fluids = resolver.resolveHandler(be, side, AEKeyTypes.FLUIDS) == Tier.NONE ? null
                : facade(resolver.raw(), be, AEKeyTypes.FLUIDS);
        if (items == null) {
            return fluids;
        }
        return fluids == null ? items : new CompositeStorage(items, fluids);
    }

    @Nullable
    private CompositeStorage composite(@Nullable BlockEntity be) {
        var items = this.items;
        var itemTier = items.resolver.resolveHandler(be, side, AEKeyTypes.ITEMS);
        var itemRaw = items.resolver.raw();
        var itemView = view(items, itemTier, itemRaw, be, AEKeyTypes.ITEMS);
        var fluids = this.fluids;
        var fluidTier = fluids.resolver.resolveHandler(be, side, AEKeyTypes.FLUIDS);
        var fluidRaw = fluids.resolver.raw();
        var fluidView = view(fluids, fluidTier, fluidRaw, be, AEKeyTypes.FLUIDS);
        if (itemView != null) {
            remember(itemTier, itemRaw);
        } else {
            remember(fluidTier, fluidRaw);
        }
        var strategies = otherStrategies();
        var composite = this.composite;
        if (composite == null) {
            this.composite = composite = new CompositeStorage(strategies.types);
        }
        composite.setStorages(itemView, fluidView);
        var found = itemView != null || fluidView != null;
        if (strategies.types.length > 0) {
            found |= strategies.update(be, composite, extractableOnly, changeListener);
        }
        return found ? composite : null;
    }

    private OtherStrategies otherStrategies() {
        var strategies = this.otherStrategies;
        if (strategies == null) {
            this.otherStrategies = strategies = new OtherStrategies(level, pos, side);
        }
        return strategies;
    }

    private void remember(Tier tier, @Nullable Object raw) {
        if (this.raw != raw) {
            this.raw = raw;
        }
        if (this.tier != tier) {
            this.tier = tier;
        }
    }

    private Memo memoOf(AEKeyType type) {
        if (type == AEKeyTypes.ITEMS) {
            return items;
        }
        if (type == AEKeyTypes.FLUIDS) {
            return fluids;
        }
        var others = this.others;
        if (others == null) {
            this.others = others = new Memo();
        }
        return others;
    }

    @Nullable
    private MEStorage view(Memo memo, Tier tier, @Nullable Object raw, @Nullable BlockEntity be,
            @Nullable AEKeyType type) {
        if (tier == Tier.NONE) {
            memo.clear();
            return null;
        }
        if (memo.raw == raw && memo.tier == tier) {
            return memo.view;
        }
        return rebuild(memo, tier, raw, be, type);
    }

    private MEStorage rebuild(Memo memo, Tier tier, Object raw, @Nullable BlockEntity be,
            @Nullable AEKeyType type) {
        MEStorage view;
        if (tier == Tier.STORAGE) {
            view = extractableOnly && raw instanceof KeyTypedStorage typed ? new ExtractableOnlyStorage(typed)
                    : (MEStorage) raw;
        } else {
            var facade = facade(raw, be, type);
            facade.setExtractableOnly(extractableOnly);
            facade.setChangeListener(changeListener);
            view = facade;
        }
        memo.tier = tier;
        memo.raw = raw;
        memo.view = view;
        return view;
    }

    private static ExternalStorageFacade facade(Object raw, @Nullable BlockEntity be, AEKeyType type) {
        var facade = type == AEKeyTypes.ITEMS ? ExternalStorageFacade.of((IItemHandler) raw)
                : ExternalStorageFacade.of((IFluidHandler) raw);
        facade.setBlockEntity(be);
        return facade;
    }

    private static final class Memo {
        private final StorageTargetResolver resolver = new StorageTargetResolver();
        private Tier tier = Tier.NONE;
        @Nullable
        private Object raw;
        @Nullable
        private MEStorage view;

        private void clear() {
            if (tier != Tier.NONE) {
                tier = Tier.NONE;
                raw = null;
                view = null;
            }
        }

        private void listen(@Nullable Runnable changeListener) {
            if (view instanceof ExternalStorageFacade facade) {
                facade.setChangeListener(changeListener);
            }
        }
    }

    private static final class OtherStrategies {
        private final AEKeyType[] types;
        private final ExternalStorageStrategy[] strategies;
        @Nullable
        private BlockEntity be;
        private int hits;
        private boolean found;

        private OtherStrategies(ServerLevel level, BlockPos pos, Direction side) {
            var created = StackWorldBehaviors.createExtraExternalStorageStrategies(level, pos, side);
            this.types = created.keySet().toArray(new AEKeyType[0]);
            this.strategies = new ExternalStorageStrategy[types.length];
            for (int i = 0; i < types.length; i++) {
                strategies[i] = created.get(types[i]);
            }
        }

        private boolean update(@Nullable BlockEntity be, CompositeStorage composite, boolean extractableOnly,
                @Nullable Runnable changeListener) {
            if (be != null && be == this.be && --hits > 0) {
                return found;
            }
            if (this.be != be) {
                this.be = be;
            }
            hits = RECHECK_HITS;
            var found = false;
            var listener = changeListener != null ? changeListener : NO_LISTENER;
            for (int i = 0; i < strategies.length; i++) {
                var wrapper = be == null ? null : strategies[i].createWrapper(extractableOnly, listener);
                composite.setOther(i, wrapper);
                found |= wrapper != null;
            }
            this.found = found;
            return found;
        }

        private void forget() {
            be = null;
        }
    }

    private static final Runnable NO_LISTENER = () -> {
    };
}
