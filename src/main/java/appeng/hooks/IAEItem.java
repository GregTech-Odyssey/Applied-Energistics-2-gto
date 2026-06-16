package appeng.hooks;

import com.gto.fastcollection.cache.WeakValueIdentityHashCache;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.InternedTag;

public interface IAEItem extends IUnique {

    AEItemKey ae2$getAEKey();

    WeakValueIdentityHashCache<InternedTag, AEItemKey> ae2$getTagAEKeyCache();
}
