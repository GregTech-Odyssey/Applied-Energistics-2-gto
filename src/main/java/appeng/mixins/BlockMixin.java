package appeng.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.level.block.Block;

import appeng.hooks.IUnique;

@Mixin(Block.class)
public class BlockMixin implements IUnique {
    @Unique
    private int ae2$uid;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onConstructed(CallbackInfo ci) {
        ae2$uid = IUnique.ID.getAndIncrement();
    }

    @Override
    public int ae2$getUid() {
        return ae2$uid;
    }
}
