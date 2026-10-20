package appeng.mixins.emi.accessor;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(ShovelItem.class)
public interface ShovelItemAccessor {

    @Accessor("FLATTENABLES")
    static Map<Block, BlockState> getPathStates() {
        throw new UnsupportedOperationException();
    }
}
