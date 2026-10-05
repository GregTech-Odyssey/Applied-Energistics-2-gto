package appeng.api.storage;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;

public interface MEStorageHost {

    @Nullable
    MEStorage getMEStorage(@Nullable Direction side);

    @Nullable
    default MEStorage getAnyMEStorage() {
        return getMEStorage(null);
    }

    default int storageEpoch() {
        return -1;
    }
}
