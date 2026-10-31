package appeng.hooks;

import net.minecraft.nbt.CompoundTag;

import appeng.api.stacks.AEItemKey;

import com.gto.fastcollection.cache.WeakValueHashCache;

public interface IAEItem extends IUnique {

    AEItemKey ae2$getAEKey();

    AEItemKey ae2$getDefaultAEKey();

    WeakValueHashCache<CompoundTag, AEItemKey> ae2$getTagAEKeyCache();
}
