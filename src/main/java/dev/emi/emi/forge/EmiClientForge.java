package dev.emi.emi.forge;

import java.util.Arrays;

import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.ForgeRenderTypes;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import dev.emi.emi.EmiClient;
import dev.emi.emi.EmiPort;
import dev.emi.emi.data.EmiData;
import dev.emi.emi.network.EmiNetwork;
import dev.emi.emi.registry.EmiTags;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiReloadManager;
import dev.emi.emi.screen.ConfigScreen;
import dev.emi.emi.screen.EmiScreenBase;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.StackBatcher;

@Mod.EventBusSubscriber(modid = "emi", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class EmiClientForge {
    @SubscribeEvent
    public static void clientInit(FMLClientSetupEvent event) {
        StackBatcher.EXTRA_RENDER_LAYERS.addAll(Arrays.stream(ForgeRenderTypes.values()).map(f -> f.get()).toList());
        EmiClient.init();
        EmiNetwork.initClient(packet -> EmiPacketHandler.CHANNEL.sendToServer(packet));
        MinecraftForge.EVENT_BUS.addListener(EmiClientForge::recipesReloaded);
        MinecraftForge.EVENT_BUS.addListener(EmiClientForge::tagsReloaded);
        MinecraftForge.EVENT_BUS.addListener(EmiClientForge::renderScreenBackground);
        MinecraftForge.EVENT_BUS.addListener(EmiClientForge::renderScreenForeground);
        MinecraftForge.EVENT_BUS.addListener(EmiClientForge::postRenderScreen);
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((client, last) -> new ConfigScreen(last)));
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        Minecraft client = Minecraft.getInstance();
        EmiTags.registerTagModels(client.getResourceManager(), event::register);
    }

    @SubscribeEvent
    public static void registerResourceReloaders(RegisterClientReloadListenersEvent event) {
        EmiData.init(event::registerReloadListener);
    }

    public static void recipesReloaded(RecipesUpdatedEvent event) {
        EmiReloadManager.reloadRecipes();
    }

    public static void tagsReloaded(TagsUpdatedEvent event) {
        EmiReloadManager.reloadTags();
    }

    public static void renderScreenForeground(ContainerScreenEvent.Render.Foreground event) {
        EmiDrawContext context = EmiDrawContext.wrap(event.getGuiGraphics());
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        EmiScreenBase base = EmiScreenBase.of(screen);
        if (base != null) {
            Minecraft client = Minecraft.getInstance();
            context.push();
            context.matrices().translate(-screen.getGuiLeft(), -screen.getGuiTop(), 0.0);
            EmiPort.setPositionTexShader();
            EmiScreenManager.render(context, event.getMouseX(), event.getMouseY(), client.getFrameTime());
            context.pop();
        }
    }

    public static void renderScreenBackground(ScreenEvent.BackgroundRendered event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        Window window = client.getWindow();
        int mouseX = (int) (client.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth());
        int mouseY = (int) (client.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight());
        EmiScreenManager.drawBackground(EmiDrawContext.wrap(event.getGuiGraphics()), mouseX, mouseY,
                client.getFrameTime());
    }

    public static void postRenderScreen(ScreenEvent.Render.Post event) {
        EmiDrawContext context = EmiDrawContext.wrap(event.getGuiGraphics());
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?>)) {
            return;
        }
        EmiScreenBase base = EmiScreenBase.of(screen);
        if (base != null) {
            Minecraft client = Minecraft.getInstance();
            context.push();
            EmiPort.setPositionTexShader();
            EmiScreenManager.drawForeground(context, event.getMouseX(), event.getMouseY(), client.getFrameTime());
            context.pop();
        }
    }
}
