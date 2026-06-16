package appeng.mixins;

import com.gto.fastcollection.cache.WeakValueIdentityHashCache;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.world.level.material.Fluid;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.InternedTag;
import appeng.hooks.IAEFluid;
import appeng.hooks.IUnique;

@Mixin(Fluid.class)
public class FluidMixin implements IAEFluid {
    @Unique
    private int ae2$uid;
    @Unique
    private AEFluidKey ae2$itemKey;
    @Unique
    private WeakValueIdentityHashCache<InternedTag, AEFluidKey> ae2$cache;

    @Override
    public int ae2$getUid() {
        var id = ae2$uid;
        if (id == 0) {
            ae2$uid = id = IUnique.ID.incrementAndGet();
        }
        return id;
    }

    @Override
    public AEFluidKey ae2$getAEKey() {
        var key = ae2$itemKey;
        if (key == null) {
            ae2$itemKey = key = new AEFluidKey((Fluid) (Object) this, InternedTag.EMPTY);
        }
        return key;
    }

    @Override
    public WeakValueIdentityHashCache<InternedTag, AEFluidKey> ae2$getTagAEKeyCache() {
        var cache = this.ae2$cache;
        if (cache == null) {
            cache = this.ae2$cache = new WeakValueIdentityHashCache<>();
        }
        return cache;
    }
}
