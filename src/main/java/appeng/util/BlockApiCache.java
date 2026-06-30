package appeng.util;

import java.lang.ref.WeakReference;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;

public class BlockApiCache<C> {
    private final ServerLevel level;
    private final BlockPos fromPos;
    private final Capability<C> capability;
    private WeakReference<BlockEntity> blockEntity;

    private BlockApiCache(Capability<C> capability, ServerLevel level, BlockPos fromPos) {
        this.capability = capability;
        this.level = level;
        this.fromPos = fromPos;
    }

    public static <C> BlockApiCache<C> create(Capability<C> capability, ServerLevel level, BlockPos fromPos) {
        return new BlockApiCache<>(capability, level, fromPos);
    }

    @Nullable
    public BlockEntity getBlockEntity() {
        var be = blockEntity != null ? blockEntity.get() : null;
        if (be == null || be.isRemoved()) {
            be = level.getBlockEntity(fromPos);
            if (be == null) {
                blockEntity = null;
                return null;
            } else {
                blockEntity = new WeakReference<>(be);
            }
        }
        return be;
    }

    public void clear() {
        blockEntity = null;
    }

    @Nullable
    public C find(Direction fromSide) {
        var be = getBlockEntity();
        if (be == null) {
            return null;
        }
        return be.getCapability(capability, fromSide).orElse(null);
    }
}
