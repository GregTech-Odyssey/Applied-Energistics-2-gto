package appeng.integration.modules.emi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import dev.emi.emi.runtime.EmiFavorites;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyIntMap;

public final class EmiFavoriteKeyCache {
    private static final ObjectArrayList<AEKey> KEYS = new ObjectArrayList<>();
    private static final AEKeyIntMap<AEKey> KEY_INDEX = new AEKeyIntMap<>();

    private EmiFavoriteKeyCache() {
    }

    public static @NotNull @UnmodifiableView ObjectArrayList<@NotNull AEKey> getKeys() {
        return KEYS;
    }

    public static boolean contains(@Nullable AEKey key) {
        return KEY_INDEX.containsKey(key);
    }

    public static void recalculate() {
        KEYS.clear();
        KEY_INDEX.clear();
        for (var favorite : EmiFavorites.favorites) {
            for (var stack : favorite.getStack().getEmiStacks()) {
                var key = EmiStackHelper.toAEKey(stack);
                if (key != null) {
                    KEYS.add(key);
                    KEY_INDEX.put(key, 0);
                }
            }
        }
    }
}
