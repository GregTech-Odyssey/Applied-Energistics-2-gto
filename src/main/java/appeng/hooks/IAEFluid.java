package appeng.hooks;

import com.gto.fastcollection.cache.WeakValueHashCache;

import net.minecraft.nbt.CompoundTag;

import appeng.api.stacks.AEFluidKey;

public interface IAEFluid extends IUnique {

    AEFluidKey ae2$getAEKey();

    WeakValueHashCache<CompoundTag, AEFluidKey> ae2$getTagAEKeyCache();

}
