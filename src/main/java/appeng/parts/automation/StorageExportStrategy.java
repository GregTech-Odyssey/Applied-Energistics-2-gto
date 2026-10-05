package appeng.parts.automation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import appeng.api.behaviors.StackExportStrategy;
import appeng.api.behaviors.StackTransferContext;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.ExternalStorageLookup;
import appeng.api.storage.StorageAccess;

public class StorageExportStrategy implements StackExportStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(StorageExportStrategy.class);
    private final ExternalStorageLookup lookup;
    private final AEKeyType keyType;

    protected StorageExportStrategy(AEKeyType keyType, ServerLevel level, BlockPos fromPos, Direction fromSide) {
        this.lookup = ExternalStorageLookup.create(level, fromPos, fromSide);
        this.keyType = keyType;
    }

    @Override
    public long transfer(StackTransferContext context, AEKey what, long amount) {
        if (what.getType() != keyType) {
            return 0;
        }

        var adjacentStorage = lookup.find(keyType, StorageAccess.INSERT);
        if (adjacentStorage == null) {
            return 0;
        }

        var inv = context.getInternalStorage().getInventory();
        var source = context.getActionSource();
        var extracted = inv.extract(what, amount, Actionable.SIMULATE, source);
        if (extracted <= 0) {
            return 0;
        }
        var accepted = adjacentStorage.insert(what, extracted, Actionable.SIMULATE, source);
        if (accepted <= 0) {
            return 0;
        }
        extracted = inv.extract(what, accepted, Actionable.MODULATE, source);
        if (extracted <= 0) {
            return 0;
        }
        var inserted = adjacentStorage.insert(what, extracted, Actionable.MODULATE, source);
        if (inserted < extracted) {
            var leftover = extracted - inserted;
            leftover -= inv.insert(what, leftover, Actionable.MODULATE, source);
            if (leftover > 0) {
                LOGGER.error("Storage export: adjacent block unexpectedly refused insert, voided {}x{}", leftover,
                        what);
            }
        }
        context.getStats().remove(what, inserted);
        return inserted;
    }

    @Override
    public long push(AEKey what, long amount, Actionable mode) {
        if (what.getType() != keyType) {
            return 0;
        }

        var adjacentStorage = lookup.find(keyType, StorageAccess.INSERT);
        if (adjacentStorage == null) {
            return 0;
        }

        return adjacentStorage.insert(what, amount, mode, IActionSource.empty());
    }

    public static StackExportStrategy createItem(ServerLevel level, BlockPos fromPos, Direction fromSide) {
        return new StorageExportStrategy(AEKeyTypes.ITEMS, level, fromPos, fromSide);
    }

    public static StackExportStrategy createFluid(ServerLevel level, BlockPos fromPos, Direction fromSide) {
        return new StorageExportStrategy(AEKeyTypes.FLUIDS, level, fromPos, fromSide);
    }
}
