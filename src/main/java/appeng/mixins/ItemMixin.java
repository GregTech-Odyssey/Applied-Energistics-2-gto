package appeng.mixins;

import com.gto.fastcollection.cache.WeakValueHashCache;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;

import appeng.api.stacks.AEItemKey;
import appeng.hooks.IAEItem;
import appeng.hooks.IUnique;

@Mixin(Item.class)
public class ItemMixin implements IAEItem {
    @Unique
    private int ae2$uid;
    @Unique
    private volatile AEItemKey ae2$itemKey;
    @Unique
    private volatile WeakValueHashCache<CompoundTag, AEItemKey> ae2$cache;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onConstructed(CallbackInfo ci) {
        ae2$uid = IUnique.ID.getAndIncrement();
    }

    @Override
    public int ae2$getUid() {
        return ae2$uid;
    }

    @Override
    public AEItemKey ae2$getAEKey() {
        var key = ae2$itemKey;
        if (key != null) {
            return key;
        }
        key = new AEItemKey((Item) (Object) this, null);
        synchronized (this) {
            if (ae2$itemKey == null) {
                ae2$itemKey = key;
                return key;
            }
            return ae2$itemKey;
        }
    }

    @Override
    public WeakValueHashCache<CompoundTag, AEItemKey> ae2$getTagAEKeyCache() {
        var cache = ae2$cache;
        if (cache != null) {
            return cache;
        }
        cache = new WeakValueHashCache<>(t -> new AEItemKey((Item) (Object) this, t));
        synchronized (this) {
            if (ae2$cache == null) {
                ae2$cache = cache;
                return cache;
            }
            return ae2$cache;
        }
    }
}
