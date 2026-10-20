package appeng.integration.modules.emi;

import dev.emi.emi.runtime.EmiFavorites;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import appeng.api.stacks.AEKey;

public final class EmiFavoriteKeyCache {
    private static final ObjectArrayList<AEKey> KEYS = new ObjectArrayList<>();

    private EmiFavoriteKeyCache() {
    }

    public static ObjectArrayList<AEKey> getKeys() {
        return KEYS;
    }

    public static void recalculate() {
        KEYS.clear();
        for (var favorite : EmiFavorites.favorites) {
            for (var stack : favorite.getStack().getEmiStacks()) {
                var generic = EmiStackHelper.toGenericStack(stack);
                if (generic != null)
                    KEYS.add(generic.what());
            }
        }
    }
}
