package appeng.hooks;

import com.gto.fastcollection.cache.WeakValueIdentityHashCache;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.InternedTag;

public interface IAEFluid extends IUnique {

    AEFluidKey ae2$getAEKey();

    WeakValueIdentityHashCache<InternedTag, AEFluidKey> ae2$getTagAEKeyCache();

}
