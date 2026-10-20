package appeng.mixins.emi;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import dev.emi.emi.EmiClient;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.runtime.EmiReloadManager;
import dev.emi.emi.screen.FavoriteGroupSidebar;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {
    @Shadow
    public ClientLevel level;

    @Inject(at = @At("RETURN"), method = "reloadResourcePacks(Z)Ljava/util/concurrent/CompletableFuture;")
    private void reloadResources(boolean force, CallbackInfoReturnable<CompletableFuture<Void>> info) {
        CompletableFuture<Void> future = info.getReturnValue();
        if (future != null) {
            future.thenRunAsync(() -> {
                Minecraft client = Minecraft.getInstance();
                if (client.level != null && client.level.getRecipeManager() != null) {
                    EmiReloadManager.reload();
                }
            }, Executors.newFixedThreadPool(1));
        }
    }

    @Inject(at = @At("RETURN"), method = "tick()V")
    private void emi$favoriteCraftTick(CallbackInfo info) {
        FavoriteGroupSidebar.tick();
    }

    @Inject(at = @At("HEAD"), method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V")
    private void disconnect(CallbackInfo info) {
        EmiLog.info("Disconnecting from server, EMI data cleared");
        EmiReloadManager.clear();
        EmiClient.onServer = false;
    }
}
