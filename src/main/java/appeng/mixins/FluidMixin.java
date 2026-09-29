package appeng.mixins;

import com.gto.fastcollection.cache.WeakValueHashCache;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

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
    private volatile AEFluidKey ae2$itemKey;
    @Unique
    private volatile WeakValueHashCache<CompoundTag, AEFluidKey> ae2$cache;

    @Override
    public int ae2$getUid() {
        var id = ae2$uid;
        if (id == 0) {
            ae2$setUid();
            id = ae2$uid;
        }
        return id;
    }

    @Override
    public void ae2$setUid() {
        synchronized (IUnique.LOCK) {
            if (ae2$uid == 0) {
                ae2$uid = IUnique.ID.incrementAndGet();
            }
        }
    }

    @Override
    public AEFluidKey ae2$getAEKey() {
        var key = ae2$itemKey;
        if (key == null) {
            synchronized (IUnique.LOCK) {
                key = ae2$itemKey;
                if (key == null) {
                    ae2$itemKey = key = new AEFluidKey((Fluid) (Object) this, null);
                }
            }
        }
        return key;
    }

    @Override
    public WeakValueHashCache<CompoundTag, AEFluidKey> ae2$getTagAEKeyCache() {
        var cache = this.ae2$cache;
        if (cache == null) {
            synchronized (IUnique.LOCK) {
                cache = this.ae2$cache;
                if (cache == null) {
                    cache = this.ae2$cache = new WeakValueHashCache<>(t -> new AEFluidKey((Fluid) (Object) this, t));
                }
            }
        }
        return cache;
    }
}
