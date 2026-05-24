package appeng.client.gui.me.common;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

import org.jetbrains.annotations.Nullable;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import appeng.api.stacks.AEKey;

@OnlyIn(Dist.CLIENT)
public final class PinnedKeys {
    // One rows worth of keys
    public static final int CRAFTING_MAX_PINNED = 9;

    // Compares by time the entry was pinned in ascending order
    private static final Comparator<Map.Entry<AEKey, PinInfo>> TIME_COMPARATOR = Comparator
            .comparing(e -> e.getValue().since);

    private static final Map<AEKey, PinInfo> pinned = new Reference2ObjectOpenHashMap<>(CRAFTING_MAX_PINNED);

    private PinnedKeys() {
    }

    public static boolean isEmpty() {
        return pinned.isEmpty();
    }

    public static Set<AEKey> getPinnedKeys() {
        return ImmutableSet.copyOf(pinned.keySet());
    }

    public static Set<AEKey> getPinnedKeys(PinReason reason) {
        var result = ImmutableSet.<AEKey>builder();
        for (var entry : pinned.entrySet()) {
            if (entry.getValue().reason == reason) {
                result.add(entry.getKey());
            }
        }
        return result.build();
    }

    @Nullable
    public static PinInfo getPinInfo(AEKey key) {
        return pinned.get(key);
    }

    public static void clearPinnedKeys() {
        pinned.clear();
    }

    public static void pinKey(AEKey key, PinReason reason) {
        var info = pinned.get(key);
        if (info == null) {
            pinned.put(key, new PinInfo(reason));
        } else if (reason == PinReason.MANUAL) {
            info.reason = PinReason.MANUAL;
            info.canPrune = false;
            info.since = Instant.now();
        } else if (info.reason == PinReason.CRAFTING) {
            info.since = Instant.now();
        }

        pruneCraftingOverflow();
    }

    public static void unpin(AEKey what) {
        pinned.remove(what);
    }

    public static boolean isPinned(AEKey what) {
        return pinned.containsKey(what);
    }

    public static boolean isPinned(AEKey what, PinReason reason) {
        var info = pinned.get(what);
        return info != null && info.reason == reason;
    }

    public static boolean hasPinnedKeys(PinReason reason) {
        for (var info : pinned.values()) {
            if (info.reason == reason) {
                return true;
            }
        }
        return false;
    }

    public static void prune() {
        pinned.values().removeIf(v -> v.canPrune);
    }

    private static void pruneCraftingOverflow() {
        var toRemove = new ArrayList<Map.Entry<AEKey, PinInfo>>();
        for (var entry : pinned.entrySet()) {
            if (entry.getValue().reason == PinReason.CRAFTING) {
                toRemove.add(entry);
            }
        }

        if (toRemove.size() <= CRAFTING_MAX_PINNED) {
            return;
        }

        toRemove.sort(TIME_COMPARATOR);
        var overflow = toRemove.size() - CRAFTING_MAX_PINNED;
        for (int i = 0; i < overflow; i++) {
            pinned.remove(toRemove.get(i).getKey());
        }
    }

    public static class PinInfo {
        // When was it pinned?
        public Instant since;
        // Why was it pinned?
        public PinReason reason;
        // Can it be pruned the next time the UI is opened?
        public boolean canPrune;

        public PinInfo(PinReason reason) {
            this.reason = reason;
            this.since = Instant.now();
        }
    }

    public enum PinReason {
        CRAFTING,
        MANUAL,
    }
}
