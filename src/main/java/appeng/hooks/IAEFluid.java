package appeng.hooks;

import net.minecraft.nbt.CompoundTag;

import appeng.api.stacks.AEFluidKey;

import com.gto.fastcollection.cache.WeakValueHashCache;

public interface IAEFluid extends IUnique {

    AEFluidKey ae2$getAEKey();

    AEFluidKey ae2$getSourceAEKey();

    WeakValueHashCache<CompoundTag, AEFluidKey> ae2$getTagAEKeyCache();

}
