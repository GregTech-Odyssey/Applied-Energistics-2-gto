package appeng.hooks;

import com.gto.fastcollection.cache.WeakValueHashCache;

import net.minecraft.nbt.CompoundTag;

import appeng.api.stacks.AEItemKey;

public interface IAEItem extends IUnique {

    AEItemKey ae2$getAEKey();

    WeakValueHashCache<CompoundTag, AEItemKey> ae2$getTagAEKeyCache();
}
