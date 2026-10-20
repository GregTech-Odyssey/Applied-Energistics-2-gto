package appeng.mixins.emi;

import java.util.List;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.state.BlockState;

import dev.emi.emi.EmiClient;

@Mixin(HoeItem.class)
public class HoeItemMixin {

    @Inject(at = @At("RETURN"), method = "changeIntoState")
    private static void createTillAction(BlockState result, CallbackInfoReturnable<Consumer<UseOnContext>> info) {
        EmiClient.HOE_ACTIONS.put(info.getReturnValue(), List.of(result.getBlock()));
    }

    @Inject(at = @At("RETURN"), method = "changeIntoStateAndDropItem")
    private static void createTillAndDropAction(BlockState result, ItemLike droppedItem,
            CallbackInfoReturnable<Consumer<UseOnContext>> info) {
        EmiClient.HOE_ACTIONS.put(info.getReturnValue(), List.of(droppedItem, result.getBlock()));
    }
}
