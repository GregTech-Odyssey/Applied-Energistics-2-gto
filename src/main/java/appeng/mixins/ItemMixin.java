package appeng.mixins;

import com.gto.fastcollection.cache.WeakValueHashCache;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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
    @Unique
    private AEItemKey ae2$defaultKey;

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
        return key != null ? key : ae2$createAEKey();
    }

    @Unique
    private AEItemKey ae2$createAEKey() {
        var key = new AEItemKey((Item) (Object) this, null);
        synchronized (this) {
            var existing = ae2$itemKey;
            if (existing != null) {
                return existing;
            }
            ae2$itemKey = key;
            return key;
        }
    }

    @Override
    public AEItemKey ae2$getDefaultAEKey() {
        var key = ae2$defaultKey;
        return key != null ? key : ae2$initDefaultAEKey();
    }

    @Unique
    private AEItemKey ae2$initDefaultAEKey() {
        var tag = new ItemStack((Item) (Object) this).getTag();
        AEItemKey key;
        if (tag == null || tag.isEmpty()) {
            key = ae2$getAEKey();
        } else {
            var cache = ae2$getTagAEKeyCache();
            key = cache.getCache(tag, cache.createFunction());
        }
        ae2$defaultKey = key;
        return key;
    }

    @Override
    public WeakValueHashCache<CompoundTag, AEItemKey> ae2$getTagAEKeyCache() {
        var cache = ae2$cache;
        return cache != null ? cache : ae2$createTagAEKeyCache();
    }

    @Unique
    private WeakValueHashCache<CompoundTag, AEItemKey> ae2$createTagAEKeyCache() {
        var cache = new WeakValueHashCache<CompoundTag, AEItemKey>(t -> new AEItemKey((Item) (Object) this, t));
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
