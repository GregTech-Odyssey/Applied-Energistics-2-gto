package appeng.mixins;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.storage.BlockEntityTargets;

@Mixin(BlockEntity.class)
public class BlockEntityMixin implements BlockEntityTargets.Holder {
    @Nullable
    private BlockEntityTargets ae2$targets;

    @Override
    @Nullable
    public BlockEntityTargets ae2$getTargets() {
        return ae2$targets;
    }

    @Override
    public void ae2$setTargets(BlockEntityTargets targets) {
        ae2$targets = targets;
    }
}
