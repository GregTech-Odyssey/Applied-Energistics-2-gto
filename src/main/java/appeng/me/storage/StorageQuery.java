package appeng.me.storage;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.parts.storagebus.StorageBusPart;

/**
 * 单次存储查询（列举库存 / 提取）的状态，同一时刻只由一个线程持有：同一份存储经多条路径到达时只计一次。
 */
final class StorageQuery {

    private static final AtomicReference<Thread> OWNER = new AtomicReference<>();
    private static final StorageQuery INSTANCE = new StorageQuery();
    private static int ownerDepth;
    private static final AtomicLong TOPOLOGY = new AtomicLong();

    int readDepth;
    int extractDepth;
    boolean anyShared;
    @Nullable
    private AEKey extractKey;
    @Nullable
    private Actionable extractMode;
    private long extractEpoch;
    private final ReferenceOpenHashSet<Object> full = new ReferenceOpenHashSet<>();

    private final Reference2IntOpenHashMap<Object> reached = new Reference2IntOpenHashMap<>();
    private final ReferenceOpenHashSet<NetworkStorage> scanned = new ReferenceOpenHashSet<>();
    private final ReferenceOpenHashSet<Object> filteredReach = new ReferenceOpenHashSet<>();
    private final Reference2ObjectOpenHashMap<Object, ObjectOpenHashSet<AEKey>> emitted = new Reference2ObjectOpenHashMap<>();
    private final ArrayList<ObjectOpenHashSet<AEKey>> setPool = new ArrayList<>();
    private final ArrayList<KeyCounter> counterPool = new ArrayList<>();
    private final ArrayList<NetworkStorage> filledNetworks = new ArrayList<>();
    private final ReferenceOpenHashSet<Object> drained = new ReferenceOpenHashSet<>();

    static boolean owned() {
        var owner = OWNER.get();
        return owner != null && owner == Thread.currentThread();
    }

    @Nullable
    static StorageQuery current() {
        return owned() ? INSTANCE : null;
    }

    @Nullable
    static StorageQuery acquire() {
        var thread = Thread.currentThread();
        if (OWNER.get() != thread && !OWNER.compareAndSet(null, thread))
            return null;
        ownerDepth++;
        return INSTANCE;
    }

    static void release() {
        if (--ownerDepth == 0)
            OWNER.set(null);
    }

    static long topologyVersion() {
        return TOPOLOGY.get();
    }

    static void topologyChanged() {
        TOPOLOGY.incrementAndGet();
    }

    static Reference2IntOpenHashMap<Object> reachCounts(NetworkStorage root) {
        var reached = new Reference2IntOpenHashMap<Object>();
        var scanned = new ReferenceOpenHashSet<NetworkStorage>();
        var pending = new ArrayList<NetworkStorage>();
        scanned.add(root);
        pending.add(root);
        while (!pending.isEmpty()) {
            pending.remove(pending.size() - 1).forEachMount(storage -> {
                if (storage instanceof MEInventoryHandler handler && !handler.allowExtraction)
                    return;
                var token = tokenOf(storage);
                if (token == null)
                    return;
                reached.addTo(token, 1);
                if (token instanceof NetworkStorage nested && scanned.add(nested))
                    pending.add(nested);
            });
        }
        return reached;
    }

    static boolean anyShared(Reference2IntOpenHashMap<Object> reached) {
        for (var count : reached.values()) {
            if (count > 1)
                return true;
        }
        return false;
    }

    static boolean exclusiveSubtree(@Nullable Object token, Reference2IntOpenHashMap<Object> reached) {
        if (token == null)
            return false;
        if (!(token instanceof NetworkStorage network))
            return true;
        var scanned = new ReferenceOpenHashSet<NetworkStorage>();
        var pending = new ArrayList<NetworkStorage>();
        scanned.add(network);
        pending.add(network);
        var exclusive = new boolean[] { true };
        while (!pending.isEmpty() && exclusive[0]) {
            pending.remove(pending.size() - 1).forEachMount(storage -> {
                if (storage instanceof MEInventoryHandler handler && !handler.allowExtraction)
                    return;
                var inner = tokenOf(storage);
                if (inner == null)
                    return;
                if (inner == network || reached.getInt(inner) > 1) {
                    exclusive[0] = false;
                    return;
                }
                if (inner instanceof NetworkStorage nested && scanned.add(nested))
                    pending.add(nested);
            });
        }
        return exclusive[0];
    }

    static boolean isNetworkLink(MEStorage storage) {
        var current = storage;
        while (current instanceof DelegatingMEInventory delegating) {
            var delegate = delegating.getDelegate();
            if (delegate == null)
                return false;
            current = delegate;
        }
        return current instanceof NetworkStorage;
    }

    @Nullable
    static Object tokenOf(MEStorage storage) {
        var current = storage;
        while (current instanceof DelegatingMEInventory delegating) {
            var delegate = delegating.getDelegate();
            if (delegate == null)
                break;
            current = delegate;
        }
        if (current instanceof NetworkStorage)
            return current;
        if (current instanceof CompositeStorage)
            return null;
        var identity = storage.getResourceIdentity();
        return identity instanceof Set<?> ? null : identity;
    }

    static boolean extractsUnfiltered(MEStorage storage) {
        if (storage instanceof NetworkStorage)
            return true;
        var type = storage.getClass();
        if (type != MEInventoryHandler.class && type != StorageBusPart.StorageBusInventory.class)
            return false;
        var handler = (MEInventoryHandler) storage;
        return handler.partitionList.isEmpty() && !(handler.filterOnExtraction && !handler.allowExtraction);
    }

    static boolean isTransparent(MEStorage storage) {
        if (storage instanceof NetworkStorage)
            return true;
        var type = storage.getClass();
        if (type != MEInventoryHandler.class && type != StorageBusPart.StorageBusInventory.class)
            return false;
        var handler = (MEInventoryHandler) storage;
        return handler.allowExtraction && (!handler.filterAvailableContents || handler.partitionList.isEmpty());
    }

    boolean scan(NetworkStorage network) {
        return scanned.add(network);
    }

    void reach(Object token, boolean transparent) {
        if (reached.addTo(token, 1) > 0)
            anyShared = true;
        if (!transparent)
            filteredReach.add(token);
    }

    boolean needsCache(NetworkStorage network) {
        return isShared(network) && filteredReach.contains(network);
    }

    boolean isShared(Object token) {
        return reached.getInt(token) > 1;
    }

    boolean isFull(Object token) {
        return full.contains(token);
    }

    void markFull(Object token) {
        full.add(token);
    }

    boolean hasEmitted(Object token) {
        return emitted.containsKey(token);
    }

    void addOnce(Object token, KeyCounter source, KeyCounter out) {
        var keys = emitted.get(token);
        if (keys == null) {
            keys = setPool.isEmpty() ? new ObjectOpenHashSet<>() : setPool.remove(setPool.size() - 1);
            emitted.put(token, keys);
        }
        for (var entry : source) {
            long amount = entry.getLongValue();
            if (amount > 0 && keys.add(entry.getKey()))
                out.add(entry.getKey(), amount);
        }
    }

    KeyCounter borrow() {
        return counterPool.isEmpty() ? new KeyCounter() : counterPool.remove(counterPool.size() - 1);
    }

    void release(KeyCounter counter) {
        counter.clear();
        counterPool.add(counter);
    }

    void filled(NetworkStorage network) {
        filledNetworks.add(network);
    }

    void endRead() {
        for (var keys : emitted.values()) {
            keys.clear();
            setPool.add(keys);
        }
        emitted.clear();
        full.clear();
        reached.clear();
        scanned.clear();
        filteredReach.clear();
        anyShared = false;
        for (var network : filledNetworks)
            network.clearQueryContent();
        filledNetworks.clear();
    }

    void beginExtract(AEKey what, Actionable mode) {
        extractEpoch++;
        extractDepth = 1;
        extractKey = what;
        extractMode = mode;
    }

    boolean sameExtract(AEKey what, Actionable mode) {
        return extractKey == what && extractMode == mode;
    }

    boolean enter(NetworkStorage network) {
        if (network.enteredEpoch == extractEpoch)
            return false;
        network.enteredEpoch = extractEpoch;
        return true;
    }

    void endExtract() {
        extractDepth = 0;
        drained.clear();
        extractKey = null;
        extractMode = null;
    }

    boolean hasDrained() {
        return !drained.isEmpty();
    }

    boolean isDrained(Object token) {
        return drained.contains(token);
    }

    void markDrained(Object token) {
        drained.add(token);
    }
}
