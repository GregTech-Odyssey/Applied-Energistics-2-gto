package appeng.client.gui.me.common;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.TagParser;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import appeng.api.stacks.AEKey;
import appeng.core.AELog;
import appeng.core.AppEng;

@OnlyIn(Dist.CLIENT)
public final class PinnedKeys {
    public static final File clientCache = resolveDefaultClientCache();
    // One rows worth of keys
    public static final int CRAFTING_MAX_PINNED = 9;
    private static final String ENTRY_KEY = "key";
    private static final String ENTRY_SINCE = "since";
    private static final String ENTRY_CAN_PRUNE = "canPrune";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().setLenient().create();

    // Compares by time the entry was pinned in ascending order
    private static final Comparator<Map.Entry<AEKey, PinInfo>> TIME_COMPARATOR = Comparator
            .comparing(e -> e.getValue().since);

    private static final Map<AEKey, PinInfo> pinned = new Reference2ObjectOpenHashMap<>(CRAFTING_MAX_PINNED);
    private static final EnumMap<PinReason, Map<AEKey, PinInfo>> pinnedByReason = createPinnedByReason();
    @Nullable
    private static File clientCacheOverride;
    private static boolean initialized;

    private PinnedKeys() {
    }

    private static File resolveDefaultClientCache() {
        try {
            var gameDir = FMLPaths.GAMEDIR.get();
            if (gameDir != null) {
                return new File(gameDir.toFile(), AppEng.MOD_ID + File.separator + "pinned_keys.json");
            }
        } catch (Exception ignored) {
        }

        return new File(AppEng.MOD_ID + File.separator + "pinned_keys.json");
    }

    private static EnumMap<PinReason, Map<AEKey, PinInfo>> createPinnedByReason() {
        var result = new EnumMap<PinReason, Map<AEKey, PinInfo>>(PinReason.class);
        for (var reason : PinReason.values()) {
            result.put(reason, new Reference2ObjectOpenHashMap<>());
        }
        return result;
    }

    public static boolean isEmpty() {
        ensureInitialized();
        return pinned.isEmpty();
    }

    public static Set<AEKey> getPinnedKeys() {
        ensureInitialized();
        return ImmutableSet.copyOf(pinned.keySet());
    }

    public static Map<AEKey, PinInfo> getPinned(PinReason reason) {
        ensureInitialized();
        return Collections.unmodifiableMap(pinnedByReason.get(reason));
    }

    public static Map<PinReason, Map<AEKey, PinInfo>> getPinnedByReason() {
        ensureInitialized();
        var result = new EnumMap<PinReason, Map<AEKey, PinInfo>>(PinReason.class);
        for (var reason : PinReason.values()) {
            result.put(reason, Collections.unmodifiableMap(pinnedByReason.get(reason)));
        }
        return Collections.unmodifiableMap(result);
    }

    public static Set<AEKey> getPinnedKeys(PinReason reason) {
        ensureInitialized();
        return ImmutableSet.copyOf(pinnedByReason.get(reason).keySet());
    }

    @Nullable
    public static PinInfo getPinInfo(AEKey key) {
        ensureInitialized();
        return pinned.get(key);
    }

    public static void clearPinnedKeys() {
        clearPinnedKeysInternal();
        initialized = true;
    }

    public static void reloadClientCache() {
        clearPinnedKeysInternal();
        loadClientCache(getActiveClientCache());
        initialized = true;
    }

    static void setClientCacheOverrideForTests(@Nullable File clientCacheOverride) {
        PinnedKeys.clientCacheOverride = clientCacheOverride;
        initialized = false;
    }

    private static void clearPinnedKeysInternal() {
        pinned.clear();
        for (var groupedPins : pinnedByReason.values()) {
            groupedPins.clear();
        }
    }

    public static void pinKey(AEKey key, PinReason reason) {
        ensureInitialized();

        boolean saveRequired = false;
        var info = pinned.get(key);
        if (info == null) {
            addPin(key, new PinInfo(reason));
            saveRequired = reason.shouldPersistToClientCache();
        } else if (reason == PinReason.MANUAL) {
            movePinToReason(key, info, PinReason.MANUAL);
            info.canPrune = false;
            info.since = Instant.now();
            saveRequired = true;
        } else if (info.reason == reason) {
            info.since = Instant.now();
        }

        pruneCraftingOverflow();
        if (saveRequired) {
            saveClientCache();
        }
    }

    public static void unpin(AEKey what) {
        ensureInitialized();

        var removed = removePin(what);
        if (removed != null && removed.reason.shouldPersistToClientCache()) {
            saveClientCache();
        }
    }

    public static boolean isPinned(AEKey what) {
        ensureInitialized();
        return pinned.containsKey(what);
    }

    public static boolean isPinned(AEKey what, PinReason reason) {
        ensureInitialized();
        var info = pinned.get(what);
        return info != null && info.reason == reason;
    }

    public static boolean hasPinnedKeys(PinReason reason) {
        ensureInitialized();
        return !pinnedByReason.get(reason).isEmpty();
    }

    public static void prune() {
        ensureInitialized();

        var toRemove = new ArrayList<AEKey>();
        boolean saveRequired = false;
        for (var entry : pinned.entrySet()) {
            if (entry.getValue().canPrune) {
                toRemove.add(entry.getKey());
                saveRequired |= entry.getValue().reason.shouldPersistToClientCache();
            }
        }

        for (var key : toRemove) {
            removePin(key);
        }

        if (saveRequired) {
            saveClientCache();
        }
    }

    private static void pruneCraftingOverflow() {
        var toRemove = new ArrayList<>(pinnedByReason.get(PinReason.CRAFTING).entrySet());

        if (toRemove.size() <= CRAFTING_MAX_PINNED) {
            return;
        }

        toRemove.sort(TIME_COMPARATOR);
        var overflow = toRemove.size() - CRAFTING_MAX_PINNED;
        for (int i = 0; i < overflow; i++) {
            removePin(toRemove.get(i).getKey());
        }
    }

    private static void addPin(AEKey key, PinInfo info) {
        pinned.put(key, info);
        pinnedByReason.get(info.reason).put(key, info);
    }

    private static void movePinToReason(AEKey key, PinInfo info, PinReason newReason) {
        if (info.reason == newReason) {
            return;
        }

        pinnedByReason.get(info.reason).remove(key);
        info.reason = newReason;
        pinnedByReason.get(newReason).put(key, info);
    }

    @Nullable
    private static PinInfo removePin(AEKey key) {
        var info = pinned.remove(key);
        if (info != null) {
            pinnedByReason.get(info.reason).remove(key);
        }
        return info;
    }

    private static void ensureInitialized() {
        if (!initialized) {
            reloadClientCache();
        }
    }

    private static File getActiveClientCache() {
        return clientCacheOverride != null ? clientCacheOverride : clientCache;
    }

    private static void saveClientCache() {
        var root = new JsonObject();

        for (var reason : PinReason.values()) {
            if (!reason.shouldPersistToClientCache()) {
                continue;
            }

            var entries = new com.google.gson.JsonArray();
            var pinsForReason = new ArrayList<>(pinnedByReason.get(reason).entrySet());
            pinsForReason.sort(TIME_COMPARATOR);
            for (var entry : pinsForReason) {
                var serializedKey = serializeKey(entry.getKey());
                if (serializedKey == null) {
                    continue;
                }

                var serializedEntry = new JsonObject();
                serializedEntry.addProperty(ENTRY_KEY, serializedKey);
                serializedEntry.addProperty(ENTRY_SINCE, entry.getValue().since.toEpochMilli());
                serializedEntry.addProperty(ENTRY_CAN_PRUNE, entry.getValue().canPrune);
                entries.add(serializedEntry);
            }

            root.add(reason.name(), entries);
        }

        var cacheFile = getActiveClientCache();
        try {
            var parent = cacheFile.toPath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (var writer = Files.newBufferedWriter(cacheFile.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            AELog.warn(e, "Failed to write pinned key client cache.");
        }
    }

    private static void loadClientCache(File cacheFile) {
        if (!cacheFile.isFile()) {
            return;
        }

        try (var reader = Files.newBufferedReader(cacheFile.toPath(), StandardCharsets.UTF_8)) {
            var root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                return;
            }

            for (var reason : PinReason.values()) {
                if (!reason.shouldPersistToClientCache() || !root.has(reason.name())) {
                    continue;
                }

                var entries = root.getAsJsonArray(reason.name());
                if (entries == null) {
                    continue;
                }

                for (var element : entries) {
                    if (!element.isJsonObject()) {
                        continue;
                    }

                    var serializedEntry = element.getAsJsonObject();
                    var key = deserializeKey(serializedEntry);
                    if (key == null) {
                        continue;
                    }

                    addPin(key, deserializePinInfo(reason, serializedEntry));
                }
            }
        } catch (Exception e) {
            AELog.warn(e, "Failed to load pinned key client cache.");
            clearPinnedKeysInternal();
        }
    }

    @Nullable
    private static String serializeKey(AEKey key) {
        try {
            return key.toTagGeneric().toString();
        } catch (Exception e) {
            AELog.warn("Skipping pinned key %s because it could not be serialized to the client cache.", key);
            return null;
        }
    }

    @Nullable
    private static AEKey deserializeKey(JsonObject serializedEntry) {
        if (!serializedEntry.has(ENTRY_KEY)) {
            return null;
        }

        try {
            return AEKey.fromTagGeneric(TagParser.parseTag(serializedEntry.get(ENTRY_KEY).getAsString()));
        } catch (Exception e) {
            AELog.warn("Skipping pinned key client cache entry because it could not be deserialized.");
            return null;
        }
    }

    private static PinInfo deserializePinInfo(PinReason reason, JsonObject serializedEntry) {
        var since = serializedEntry.has(ENTRY_SINCE)
                ? Instant.ofEpochMilli(serializedEntry.get(ENTRY_SINCE).getAsLong())
                : Instant.now();
        var canPrune = serializedEntry.has(ENTRY_CAN_PRUNE)
                && serializedEntry.get(ENTRY_CAN_PRUNE).getAsBoolean();

        return new PinInfo(reason, since, canPrune);
    }

    public static class PinInfo {
        // When was it pinned?
        public Instant since;
        // Why was it pinned?
        public PinReason reason;
        // Can it be pruned the next time the UI is opened?
        public boolean canPrune;

        public PinInfo(PinReason reason) {
            this(reason, Instant.now(), false);
        }

        public PinInfo(PinReason reason, Instant since, boolean canPrune) {
            this.reason = reason;
            this.since = since;
            this.canPrune = canPrune;
        }
    }

    public enum PinReason {
        CRAFTING(false),
        MANUAL(true);

        private final boolean persistToClientCache;

        PinReason(boolean persistToClientCache) {
            this.persistToClientCache = persistToClientCache;
        }

        public boolean shouldPersistToClientCache() {
            return persistToClientCache;
        }
    }
}
