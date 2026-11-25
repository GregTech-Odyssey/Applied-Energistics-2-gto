package appeng.api.storage;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;

public interface MEStorageHost {

    @Nullable
    MEStorage getMEStorage(@Nullable Direction side);

    /**
     * Resolves the storage exposed on {@code side} for the requested {@code access} mode. {@link StorageAccess#FULL}
     * returns the storage used for full network/storage-bus mounting.
     */
    @Nullable
    default MEStorage getMEStorage(@Nullable Direction side, StorageAccess access) {
        return getMEStorage(side);
    }

    @Nullable
    default MEStorage getAnyMEStorage() {
        return getMEStorage(null);
    }

    default int storageEpoch() {
        return -1;
    }
}
