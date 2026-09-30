package appeng.mixins;

import com.gto.fastcollection.cache.WeakValueHashCache;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.nbt.CompoundTag;
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
        if (key != null) {
            return key;
        }
        key = new AEFluidKey((Fluid) (Object) this, null);
        synchronized (this) {
            if (ae2$fluidKey == null) {
                ae2$fluidKey = key;
                return key;
            }
            return ae2$fluidKey;
        }
    }

    @Override
    public WeakValueHashCache<CompoundTag, AEFluidKey> ae2$getTagAEKeyCache() {
        var cache = ae2$cache;
        if (cache != null) {
            return cache;
        }
        cache = new WeakValueHashCache<>(t -> new AEFluidKey((Fluid) (Object) this, t));
        synchronized (this) {
            if (ae2$cache == null) {
                ae2$cache = cache;
                return cache;
            }
            return ae2$cache;
        }
    }
}
