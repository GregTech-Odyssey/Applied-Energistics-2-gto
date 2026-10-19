package appeng.api.storage;

import appeng.me.storage.NetworkStorage;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.stacks.AEKeyType;
import appeng.me.storage.ExternalStorageFacade;

/**
 * The only implementation of the target order: MEStorage, then {@link ExternalStorageFacade.DirectKeyHandler}, then
 * Forge. It reports the hit tier and the raw object; consumers build and cache their own views by raw identity.
 */
public final class StorageTargetResolver {

    public enum Tier {
        NONE,
        STORAGE,
        DIRECT,
        FORGE
    }

    private Tier tier = Tier.NONE;
    @Nullable
    private Object raw;
    @Nullable
    private BlockEntityTargets.Entry storageEntry;
    @Nullable
    private BlockEntityTargets.Entry handlerEntry;
    @Nullable
    private MEStorageHost epochHost;
    @Nullable
    private Direction epochSide;
    @Nullable
    private AEKeyType epochType;
    @Nullable
    private StorageAccess epochAccess;
    private int epoch;
    @Nullable
    private Tier epochTier;
    @Nullable
    private Object epochRaw;

    public Tier resolve(@Nullable BlockEntity be, @Nullable Direction targetSide, AEKeyType type,
            StorageAccess access) {
        if (be == null || be.isRemoved()) {
            return gone();
        }
        Tier tier;
        if (be instanceof MEStorageHost host) {
            tier = current(host, targetSide, type, access) ? reuse() : hostTier(host, targetSide, type, access);
        } else {
            tier = storageTier(storageOf(be, targetSide), type, access);
        }
        return tier != null ? tier : handlerOf(be, targetSide, type);
    }

    public Tier resolveAll(@Nullable BlockEntity be, @Nullable Direction targetSide, StorageAccess access) {
        if (be == null || be.isRemoved()) {
            return gone();
        }
        Tier tier;
        if (be instanceof MEStorageHost host) {
            tier = current(host, targetSide, null, access) ? reuse() : hostTier(host, targetSide, null, access);
        } else {
            tier = storageTier(storageOf(be, targetSide), null, access);
        }
        return tier != null ? tier : none();
    }

    public Tier resolveHandler(@Nullable BlockEntity be, @Nullable Direction targetSide, AEKeyType type) {
        if (be == null || be.isRemoved()) {
            return gone();
        }
        return handlerOf(be, targetSide, type);
    }

    public void forget() {
        if (storageEntry != null) {
            storageEntry = null;
        }
        if (handlerEntry != null) {
            handlerEntry = null;
        }
        forgetEpoch();
    }

    public Tier tier() {
        return tier;
    }

    @Nullable
    public Object raw() {
        return raw;
    }

    private Tier hit(Tier tier, Object raw) {
        if (this.raw != raw) {
            this.raw = raw;
        }
        if (this.tier != tier) {
            this.tier = tier;
        }
        return tier;
    }

    private Tier none() {
        if (tier != Tier.NONE) {
            tier = Tier.NONE;
            raw = null;
        }
        return Tier.NONE;
    }

    private Tier gone() {
        forget();
        return none();
    }

    private boolean current(MEStorageHost host, @Nullable Direction side, @Nullable AEKeyType type,
            StorageAccess access) {
        return host == epochHost && side == epochSide && type == epochType && access == epochAccess
                && host.storageEpoch() == epoch;
    }

    @Nullable
    private Tier reuse() {
        var tier = epochTier;
        if (tier == Tier.STORAGE) {
            return hit(Tier.STORAGE, epochRaw);
        }
        return tier == null ? null : none();
    }

    @Nullable
    private Tier hostTier(MEStorageHost host, @Nullable Direction side, @Nullable AEKeyType type,
            StorageAccess access) {
        var tier = storageTier(host.getMEStorage(side), type, access);
        var epoch = host.storageEpoch();
        if (epoch < 0) {
            forgetEpoch();
            return tier;
        }
        if (epochHost != host) {
            epochHost = host;
        }
        if (epochSide != side) {
            epochSide = side;
        }
        if (epochType != type) {
            epochType = type;
        }
        if (epochAccess != access) {
            epochAccess = access;
        }
        this.epoch = epoch;
        if (epochTier != tier) {
            epochTier = tier;
        }
        var raw = tier == Tier.STORAGE ? this.raw : null;
        if (epochRaw != raw) {
            epochRaw = raw;
        }
        return tier;
    }

    @Nullable
    private Tier storageTier(@Nullable MEStorage storage, @Nullable AEKeyType type, StorageAccess access) {
        switch (storage) {
            case null -> {
                return null;
            }
            case KeyTypedStorage typed -> {
                if (type == null) {
                    return hit(Tier.STORAGE, storage);
                }
                var typedStorage = typed.forKeyType(type);
                return typedStorage == null ? none() : hit(Tier.STORAGE, typedStorage);
            }
            case NetworkStorage ignored when access == StorageAccess.EXTRACT -> {
                return null;
            }
            default -> {
                return  hit(Tier.STORAGE, storage);
            }
        }
    }

    private void forgetEpoch() {
        if (epochHost != null) {
            epochHost = null;
            epochRaw = null;
        }
    }

    private Tier handlerOf(BlockEntity be, @Nullable Direction targetSide, AEKeyType type) {
        var entry = handlerEntry;
        if (entry == null || !entry.matches(be, targetSide, type)) {
            entry = BlockEntityTargets.handler(be, targetSide, type);
            if (entry == null) {
                return none();
            }
            handlerEntry = entry;
        }
        var handler = entry.get();
        if (handler == null) {
            return none();
        }
        return hit(handler instanceof ExternalStorageFacade.DirectKeyHandler ? Tier.DIRECT : Tier.FORGE, handler);
    }

    @Nullable
    private MEStorage storageOf(BlockEntity be, @Nullable Direction targetSide) {
        var entry = storageEntry;
        if (entry == null || !entry.matches(be, targetSide, null)) {
            storageEntry = entry = BlockEntityTargets.storage(be, targetSide);
        }
        return (MEStorage) entry.get();
    }
}
