package appeng.mixins;

import com.gto.fastcollection.cache.WeakValueHashCache;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

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
    private AEItemKey ae2$itemKey;
    @Unique
    private WeakValueHashCache<CompoundTag, AEItemKey> ae2$cache;

    @Override
    public int ae2$getUid() {
        var id = ae2$uid;
        if (id == 0) {
            ae2$uid = id = IUnique.ID.incrementAndGet();
        }
        return id;
    }

    @Override
    public AEItemKey ae2$getAEKey() {
        var key = ae2$itemKey;
        if (key == null) {
            ae2$itemKey = key = new AEItemKey((Item) (Object) this, null);
        }
        return key;
    }

    @Override
    public WeakValueHashCache<CompoundTag, AEItemKey> ae2$getTagAEKeyCache() {
        var cache = this.ae2$cache;
        if (cache == null) {
            cache = this.ae2$cache = new WeakValueHashCache<>(t -> new AEItemKey((Item) (Object) this, t));
        }
        return cache;
    }
}
