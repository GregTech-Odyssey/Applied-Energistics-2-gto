package gto_ae.helpers.facility_management;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/// Server only manager for facility UIDs
/// <p>
/// Client UIDs are managed separately in {@link FrozenMachineStatus} and are not guaranteed to match server UIDs, but they will be consistent for a given client session
/// </p>
public final class FacilityUidManager {
    private static final AtomicInteger UID_COUNTER = new AtomicInteger(0);
    private static final Map<IStatusTracked, Integer> UID_CACHE = new WeakHashMap<>();

    private FacilityUidManager() {
    }

    public static int uidFor(IStatusTracked facility) {
        return UID_CACHE.computeIfAbsent(facility, k -> UID_COUNTER.incrementAndGet());
    }

    public static void reset() {
        UID_COUNTER.set(0);
        UID_CACHE.clear();
    }
}
