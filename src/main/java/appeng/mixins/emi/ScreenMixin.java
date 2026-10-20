package appeng.mixins.emi;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Style;

import dev.emi.emi.runtime.RecipeShareLink;
import dev.emi.emi.screen.EmiScreenManager;

@Mixin(Screen.class)
public class ScreenMixin {

    @Inject(at = @At("RETURN"), method = "init(Lnet/minecraft/client/Minecraft;II)V")
    private void init(Minecraft client, int width, int height, CallbackInfo info) {
        if ((Object) this instanceof AbstractContainerScreen hs && client.screen == hs) {
            EmiScreenManager.addWidgets(hs);
        }
    }

    @Inject(at = @At("RETURN"), method = "resize(Lnet/minecraft/client/Minecraft;II)V")
    private void resize(Minecraft client, int width, int height, CallbackInfo info) {
        if ((Object) this instanceof AbstractContainerScreen hs && client.screen == hs) {
            EmiScreenManager.addWidgets(hs);
        }
    }

    @Inject(at = @At("HEAD"), method = "handleComponentClicked(Lnet/minecraft/network/chat/Style;)Z", cancellable = true)
    private void handleRecipeShareClick(Style style, CallbackInfoReturnable<Boolean> info) {
        if (RecipeShareLink.handleClick(style)) {
            info.setReturnValue(true);
        }
    }
}
