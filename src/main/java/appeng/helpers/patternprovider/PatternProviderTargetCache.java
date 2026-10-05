package appeng.helpers.patternprovider;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.ExternalStorageLookup;
import appeng.api.storage.StorageAccess;

public final class PatternProviderTargetCache {
    private final ExternalStorageLookup lookup;
    private final IActionSource src;
    @Nullable
    private MEStorageTarget target;

    public PatternProviderTargetCache(ServerLevel l, BlockPos pos, Direction direction, IActionSource src) {
        this.lookup = ExternalStorageLookup.create(l, pos, direction);
        this.src = src;
    }

    public void refresh() {
        lookup.refresh();
    }

    @Nullable
    public BlockEntity targetBlockEntity() {
        return lookup.getBlockEntity();
    }

    @Nullable
    public MEStorageTarget find() {
        var storage = lookup.findAll(StorageAccess.FULL);
        if (storage == null) {
            return null;
        }
        var target = this.target;
        if (target == null || target.storage() != storage) {
            this.target = target = new MEStorageTarget(storage, src);
        }
        return target;
    }
}
