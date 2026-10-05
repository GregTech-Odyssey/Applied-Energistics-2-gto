package appeng.api.storage;

import java.lang.ref.WeakReference;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullConsumer;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.capabilities.Capabilities;
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

    private static final int RECHECK_HITS = 64;

    private Tier tier = Tier.NONE;
    @Nullable
    private Object raw;
    @Nullable
    private BlockEntity noStorage;
    @Nullable
    private Direction noStorageSide;
    private int noStorageHits;
    @Nullable
    private BlockEntity handlerBe;
    @Nullable
    private Direction handlerSide;
    @Nullable
    private AEKeyType handlerType;
    @Nullable
    private LazyOptional<?> handlerOptional;
    @Nullable
    private Object handler;
    private Tier handlerTier = Tier.NONE;
    private boolean handlerTrusted;
    private int handlerHits;
    @Nullable
    private Invalidation invalidation;
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
        if (noStorage != null) {
            noStorage = null;
        }
        if (handlerBe != null) {
            handlerBe = null;
            forgetHandler();
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
        if (storage == null) {
            return null;
        }
        if (storage instanceof KeyTypedStorage typed) {
            if (type == null) {
                return hit(Tier.STORAGE, storage);
            }
            var typedStorage = typed.forKeyType(type);
            return typedStorage == null ? none() : hit(Tier.STORAGE, typedStorage);
        }
        return access != StorageAccess.EXTRACT ? hit(Tier.STORAGE, storage) : null;
    }

    private void forgetEpoch() {
        if (epochHost != null) {
            epochHost = null;
            epochRaw = null;
        }
    }

    private Tier handlerOf(BlockEntity be, @Nullable Direction targetSide, AEKeyType type) {
        if (be == handlerBe && targetSide == handlerSide && type == handlerType && --handlerHits > 0) {
            var handler = this.handler;
            if (handler == null) {
                return none();
            }
            if (handlerTrusted) {
                return hit(handlerTier, handler);
            }
        }
        return queryHandler(be, targetSide, type);
    }

    private Tier queryHandler(BlockEntity be, @Nullable Direction targetSide, AEKeyType type) {
        LazyOptional<?> optional;
        if (type == AEKeyTypes.ITEMS) {
            optional = be.getCapability(ForgeCapabilities.ITEM_HANDLER, targetSide);
        } else if (type == AEKeyTypes.FLUIDS) {
            optional = be.getCapability(ForgeCapabilities.FLUID_HANDLER, targetSide);
        } else {
            return none();
        }
        if (be != handlerBe || targetSide != handlerSide || type != handlerType) {
            handlerBe = be;
            handlerSide = targetSide;
            handlerType = type;
            forgetHandler();
        }
        handlerHits = RECHECK_HITS;
        var handler = optional.orElse(null);
        if (handler == null) {
            forgetHandler();
            return none();
        }
        if (optional != handlerOptional) {
            handlerOptional = optional;
            handlerTrusted = false;
        } else if (!handlerTrusted) {
            trust(optional);
        }
        if (this.handler != handler) {
            this.handler = handler;
            handlerTier = handler instanceof ExternalStorageFacade.DirectKeyHandler ? Tier.DIRECT : Tier.FORGE;
        }
        return hit(handlerTier, handler);
    }

    private void forgetHandler() {
        if (handlerOptional != null) {
            handlerOptional = null;
        }
        if (handler != null) {
            handler = null;
        }
        handlerTrusted = false;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void trust(LazyOptional<?> optional) {
        var invalidation = this.invalidation;
        if (invalidation == null) {
            this.invalidation = invalidation = new Invalidation(this);
        }
        handlerTrusted = true;
        optional.addListener((NonNullConsumer) invalidation);
    }

    @Nullable
    private MEStorage storageOf(BlockEntity be, @Nullable Direction targetSide) {
        if (be == noStorage && targetSide == noStorageSide && --noStorageHits > 0) {
            return null;
        }
        return storageCapability(be, targetSide);
    }

    @Nullable
    private MEStorage storageCapability(BlockEntity be, @Nullable Direction targetSide) {
        var storage = be.getCapability(Capabilities.STORAGE, targetSide).orElse(null);
        if (storage == null) {
            noStorage = be;
            noStorageSide = targetSide;
            noStorageHits = RECHECK_HITS;
        } else if (noStorage != null) {
            noStorage = null;
        }
        return storage;
    }

    private static final class Invalidation extends WeakReference<StorageTargetResolver>
            implements NonNullConsumer<LazyOptional<?>> {

        private Invalidation(StorageTargetResolver resolver) {
            super(resolver);
        }

        @Override
        public void accept(LazyOptional<?> optional) {
            var resolver = get();
            if (resolver != null && resolver.handlerOptional == optional) {
                resolver.handlerTrusted = false;
            }
        }
    }
}
