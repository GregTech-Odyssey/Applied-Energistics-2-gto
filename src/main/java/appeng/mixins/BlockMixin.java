package appeng.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.world.level.block.Block;

import appeng.hooks.IUnique;

@Mixin(Block.class)
public class BlockMixin implements IUnique {
    @Unique
    private int ae2$uid;

    @Override
    public int ae2$getUid() {
        var id = ae2$uid;
        if (id == 0) {
            ae2$setUid();
            id = ae2$uid;
        }
        return id;
    }

    @Override
    public void ae2$setUid() {
        synchronized (IUnique.LOCK) {
            if (ae2$uid == 0) {
                ae2$uid = IUnique.ID.incrementAndGet();
            }
        }
    }
}
