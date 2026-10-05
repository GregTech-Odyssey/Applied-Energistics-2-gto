package appeng.parts.automation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import appeng.api.behaviors.StackImportStrategy;
import appeng.api.behaviors.StackTransferContext;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.ExternalStorageLookup;
import appeng.api.storage.KeyTypedStorage;
import appeng.api.storage.StorageAccess;
import appeng.api.storage.StorageTargetResolver;
import appeng.core.AELog;

/**
 * Strategy for efficiently importing stacks from external storage into an internal
 * {@link appeng.api.storage.MEStorage}.
 */
public class StorageImportStrategy implements StackImportStrategy {
    private final ExternalStorageLookup lookup;
    private final AEKeyType keyType;
    private final KeyCounter available = new KeyCounter();

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

        var available = this.available;
        available.clear();
        if (lookup.tier() == StorageTargetResolver.Tier.STORAGE && adjacentStorage instanceof KeyTypedStorage typed) {
            typed.getAvailableStacks(available, true);
        } else {
            adjacentStorage.getAvailableStacks(available);
        }
        if (available.isEmpty()) {
            return false;
        }

        var inv = context.getInternalStorage().getInventory();
        var source = context.getActionSource();
        var inverted = context.isInverted();
        var stats = context.getStats();
        for (var entry : available) {
            var what = entry.getKey();
            if (context.isInFilter(what) == inverted) {
                continue;
            }
            var amount = adjacentStorage.extract(what, entry.getLongValue(), Actionable.SIMULATE, source);
            if (amount <= 0) {
                continue;
            }
            amount = inv.insert(what, amount, Actionable.SIMULATE, source);
            if (amount <= 0) {
                continue;
            }
            var extracted = adjacentStorage.extract(what, amount, Actionable.MODULATE, source);
            if (extracted <= 0) {
                continue;
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
                context.reduceOperationsRemaining(1);
            }
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
