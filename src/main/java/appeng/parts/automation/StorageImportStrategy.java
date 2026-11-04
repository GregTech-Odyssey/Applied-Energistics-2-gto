package appeng.parts.automation;

import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import appeng.api.behaviors.StackImportStrategy;
import appeng.api.behaviors.StackTransferContext;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.*;
import appeng.core.AELog;

import com.gto.ae.helpers.facility_management.ThroughputCounter;

/**
 * Strategy for efficiently importing stacks from external storage into an internal
 * {@link appeng.api.storage.MEStorage}.
 */
public class StorageImportStrategy implements StackImportStrategy {
    private final ExternalStorageLookup lookup;
    private final AEKeyType keyType;

    public StorageImportStrategy(AEKeyType keyType, ServerLevel level, BlockPos fromPos, Direction fromSide) {
        this.lookup = ExternalStorageLookup.create(level, fromPos, fromSide);
        this.keyType = keyType;
    }

    @Override
    public boolean transfer(StackTransferContext context) {
        var keyType = this.keyType;
        if (!context.isKeyTypeEnabled(keyType)) {
            return false;
        }

        var lookup = this.lookup;
        var adjacentStorage = lookup.find(keyType, StorageAccess.EXTRACT);
        if (adjacentStorage == null) {
            return false;
        }

        var inv = context.getInternalStorage().getInventory();
        var source = context.getActionSource();
        var inverted = context.isInverted();
        var stats = context.getStats();
        var amountPerOperation = keyType.getAmountPerOperation();
        long remaining = (long) context.getOperationsRemaining() * amountPerOperation;
        if (lookup.tier() == StorageTargetResolver.Tier.STORAGE && adjacentStorage instanceof KeyTypedStorage typed) {
            typed.forEachKey((k, v) -> transfer(inv, source, inverted, stats, k, v, context, adjacentStorage,
                    new AtomicLong(remaining), amountPerOperation), true);
        } else {
            adjacentStorage.forEachKey((k, v) -> transfer(inv, source, inverted, stats, k, v, context, adjacentStorage,
                    new AtomicLong(remaining), amountPerOperation));
        }
        return false;
    }

    private boolean transfer(MEStorage inv, IActionSource source, boolean inverted, ThroughputCounter stats, AEKey what,
            long amount, StackTransferContext context, MEStorage adjacentStorage, AtomicLong remaining,
            int amountPerOperation) {
        var r = remaining.get();
        if (r <= 0) {
            return true;
        }
        if (context.isInFilter(what) == inverted) {
            return false;
        }
        amount = inv.insert(what, amount, Actionable.SIMULATE, source);
        if (amount <= 0) {
            return false;
        }
        var extracted = adjacentStorage.extract(what, amount, Actionable.MODULATE, source);
        if (extracted <= 0) {
            return false;
        }
        var inserted = inv.insert(what, extracted, Actionable.MODULATE, source);
        if (inserted < extracted) {
            var leftover = extracted - inserted;
            leftover -= adjacentStorage.insert(what, leftover, Actionable.MODULATE, source);
            if (leftover > 0) {
                AELog.warn("Extracted %dx%s from adjacent storage and voided it because network refused insert",
                        leftover, what);
            }
        }
        if (inserted > 0) {
            stats.add(what, inserted);
            context.reduceOperationsRemaining(Math.max(1, inserted / amountPerOperation));
            r -= inserted;
            if (r <= 0)
                return true;
            remaining.set(r);
        }
        return false;
    }

    public static StackImportStrategy createItem(ServerLevel level, BlockPos fromPos, Direction fromSide) {
        return new StorageImportStrategy(AEKeyTypes.ITEMS, level, fromPos, fromSide);
    }

    public static StackImportStrategy createFluid(ServerLevel level, BlockPos fromPos, Direction fromSide) {
        return new StorageImportStrategy(AEKeyTypes.FLUIDS, level, fromPos, fromSide);
    }
}
