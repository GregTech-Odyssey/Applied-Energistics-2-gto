package appeng.mixins.emi;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.screen.EmiScreenManager;

@Mixin(MouseHandler.class)
public class MouseMixin {
    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    private double xpos, ypos;
    @Shadow
    private int activeButton = -1;

    @Inject(at = @At(value = "INVOKE", ordinal = 0, target = "net/minecraft/client/gui/screens/Screen.wrapScreenError(Ljava/lang/Runnable;Ljava/lang/String;Ljava/lang/String;)V"), method = "onPress(JIII)V", cancellable = true)
    private void onMouseDown(long window, int button, int action, int mods, CallbackInfo info) {
        try {
            Screen screen = minecraft.screen;
            if (screen instanceof AbstractContainerScreen<?> hs) {
                double mx = this.xpos * minecraft.getWindow().getGuiScaledWidth()
                        / minecraft.getWindow().getScreenWidth();
                double my = this.ypos * minecraft.getWindow().getGuiScaledHeight()
                        / minecraft.getWindow().getScreenHeight();
                if (EmiScreenManager.mouseClicked(mx, my, button)) {
                    info.cancel();
                }
            }
        } catch (Exception e) {
            EmiLog.error("Error while handling mouse press", e);
        }
    }

    @Inject(at = @At(value = "INVOKE", ordinal = 1, target = "net/minecraft/client/gui/screens/Screen.wrapScreenError(Ljava/lang/Runnable;Ljava/lang/String;Ljava/lang/String;)V"), method = "onPress(JIII)V", cancellable = true)
    private void onMouseUp(long window, int button, int action, int mods, CallbackInfo info) {
        try {
            Screen screen = minecraft.screen;
            if (screen instanceof AbstractContainerScreen<?> hs) {
                double mx = this.xpos * minecraft.getWindow().getGuiScaledWidth()
                        / minecraft.getWindow().getScreenWidth();
                double my = this.ypos * minecraft.getWindow().getGuiScaledHeight()
                        / minecraft.getWindow().getScreenHeight();
                if (EmiScreenManager.mouseReleased(mx, my, button)) {
                    info.cancel();
                }
            }
        } catch (Exception e) {
            EmiLog.error("Error while handling mouse release", e);
        }
    }

    @Inject(at = @At(value = "INVOKE", ordinal = 1, target = "net/minecraft/client/gui/screens/Screen.wrapScreenError(Ljava/lang/Runnable;Ljava/lang/String;Ljava/lang/String;)V"), method = "onMove(JDD)V", cancellable = true)
    private void onMouseDragged(long window, double xpos, double ypos, CallbackInfo info) {
        try {
            Screen screen = minecraft.screen;
            if (screen instanceof AbstractContainerScreen<?> hs) {
                double mx = this.xpos * minecraft.getWindow().getGuiScaledWidth()
                        / minecraft.getWindow().getScreenWidth();
                double my = this.ypos * minecraft.getWindow().getGuiScaledHeight()
                        / minecraft.getWindow().getScreenHeight();
                double dx = (xpos - this.xpos) * minecraft.getWindow().getGuiScaledWidth()
                        / minecraft.getWindow().getScreenWidth();
                double dy = (ypos - this.ypos) * minecraft.getWindow().getGuiScaledHeight()
                        / minecraft.getWindow().getScreenHeight();
                EmiScreenManager.mouseDragged(mx, my, activeButton, dx, dy);
            }
        } catch (Exception e) {
            EmiLog.error("Error while handling mouse drag", e);
        }
    }

    @Inject(at = @At(value = "INVOKE", target = "net/minecraft/client/gui/screens/Screen.mouseScrolled(DDD)Z"), method = "onScroll(JDD)V", cancellable = true)
    private void onMouseScrolled(long window, double horizontal, double vertical, CallbackInfo info) {
        try {
            Screen screen = minecraft.screen;
            if (screen instanceof AbstractContainerScreen<?> hs) {
                double amount = (minecraft.options.discreteMouseScroll().get() ? Math.signum(vertical) : vertical)
                        * minecraft.options.mouseWheelSensitivity().get();
                double mx = xpos * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
                double my = ypos * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
                if (EmiScreenManager.mouseScrolled(mx, my, amount)) {
                    info.cancel();
                }
            }
        } catch (Exception e) {
            EmiLog.error("Error while handling mouse scroll", e);
        }
    }
}
