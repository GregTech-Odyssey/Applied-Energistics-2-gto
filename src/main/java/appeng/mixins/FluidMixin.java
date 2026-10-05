package appeng.mixins;

import com.gto.fastcollection.cache.WeakValueHashCache;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;

import appeng.api.stacks.AEFluidKey;
import appeng.hooks.IAEFluid;
import appeng.hooks.IUnique;

@Mixin(Fluid.class)
public class FluidMixin implements IAEFluid {
    @Unique
    private int ae2$uid;
    @Unique
    private volatile AEFluidKey ae2$fluidKey;
    @Unique
    private volatile AEFluidKey ae2$sourceKey;
    @Unique
    private volatile WeakValueHashCache<CompoundTag, AEFluidKey> ae2$cache;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onConstructed(CallbackInfo ci) {
        ae2$uid = IUnique.ID.getAndIncrement();
    }

    @Override
    public int ae2$getUid() {
        return ae2$uid;
    }

    @Override
    public AEFluidKey ae2$getAEKey() {
        var key = ae2$fluidKey;
        return key != null ? key : ae2$createAEKey();
    }

    @Unique
    private AEFluidKey ae2$createAEKey() {
        var key = new AEFluidKey((Fluid) (Object) this, null);
        synchronized (this) {
            var existing = ae2$fluidKey;
            if (existing != null) {
                return existing;
            }
            ae2$fluidKey = key;
            return key;
        }
    }

    @Override
    public AEFluidKey ae2$getSourceAEKey() {
        var key = ae2$sourceKey;
        return key != null ? key : ae2$initSourceAEKey();
    }

    @Unique
    private AEFluidKey ae2$initSourceAEKey() {
        Object self = this;
        var key = self instanceof FlowingFluid flowing ? ((IAEFluid) flowing.getSource()).ae2$getAEKey()
                : ae2$getAEKey();
        synchronized (this) {
            var existing = ae2$sourceKey;
            if (existing != null) {
                return existing;
            }
            ae2$sourceKey = key;
            return key;
        }
    }

    @Override
    public WeakValueHashCache<CompoundTag, AEFluidKey> ae2$getTagAEKeyCache() {
        var cache = ae2$cache;
        return cache != null ? cache : ae2$createTagAEKeyCache();
    }

    @Unique
    private WeakValueHashCache<CompoundTag, AEFluidKey> ae2$createTagAEKeyCache() {
        var cache = new WeakValueHashCache<CompoundTag, AEFluidKey>(t -> new AEFluidKey((Fluid) (Object) this, t));
        synchronized (this) {
            var existing = ae2$cache;
            if (existing != null) {
                return existing;
            }
            ae2$cache = cache;
            return cache;
        }
    }
}
