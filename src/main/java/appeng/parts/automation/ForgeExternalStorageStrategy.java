package appeng.parts.automation;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.ExternalStorageLookup;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageAccess;

public class ForgeExternalStorageStrategy implements ExternalStorageStrategy {
    private final ExternalStorageLookup lookup;
    private final AEKeyType keyType;

    public ForgeExternalStorageStrategy(AEKeyType keyType, ServerLevel level, BlockPos fromPos, Direction fromSide) {
        this.lookup = ExternalStorageLookup.create(level, fromPos, fromSide);
        this.keyType = keyType;
    }

    @Nullable
    @Override
    public MEStorage createWrapper(boolean extractableOnly, Runnable injectOrExtractCallback) {
        var lookup = this.lookup;
        lookup.configure(extractableOnly, injectOrExtractCallback);
        return lookup.find(keyType, StorageAccess.FULL);
    }

    public static ExternalStorageStrategy createItem(ServerLevel level, BlockPos fromPos, Direction fromSide) {
        return new ForgeExternalStorageStrategy(AEKeyTypes.ITEMS, level, fromPos, fromSide);
    }

    public static ExternalStorageStrategy createFluid(ServerLevel level, BlockPos fromPos, Direction fromSide) {
        return new ForgeExternalStorageStrategy(AEKeyTypes.FLUIDS, level, fromPos, fromSide);
    }
}
