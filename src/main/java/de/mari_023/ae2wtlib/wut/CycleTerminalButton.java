package de.mari_023.ae2wtlib.wut;

import java.util.Collections;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;

import org.jetbrains.annotations.NotNull;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import de.mari_023.ae2wtlib.AE2wtlib;
import de.mari_023.ae2wtlib.TextConstants;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.ITooltip;

public class CycleTerminalButton extends Button implements ITooltip {

    public static final ResourceLocation TEXTURE_STATES = Icon.TEXTURE;
    public static final ResourceLocation nextTerminal = new ResourceLocation(AE2wtlib.MOD_NAME,
            "textures/item/wireless_universal_terminal.png");

    public CycleTerminalButton(OnPress onPress) {
        super(0, 0, 16, 16, Component.empty(), onPress, Button.DEFAULT_NARRATION);
        visible = true;
        active = true;
    }

    @Override
    public @NotNull List<Component> getTooltipMessage() {
        return Collections.singletonList(TextConstants.CYCLE_TOOLTIP);
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(getX(), getY(), 16, 16);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return true;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
        if (!visible) {
            return;
        }
        int yOffset = isHovered() ? 1 : 0;
        Icon bgIcon = isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                : isFocused() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND;
        bgIcon.getBlitter()
                .dest(getX() - 1, getY() + yOffset, 18, 20)
                .zOffset(2)
                .blit(guiGraphics);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 3);
        RenderSystem.enableBlend();
        if (active) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            RenderSystem.setShaderColor(0.5f, 0.5f, 0.5f, 1.0f);
        }
        guiGraphics.blit(nextTerminal, getX() + 1, getY() + 2 + yOffset, 14, 14, 0, 0, 512, 512, 512, 512);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        guiGraphics.pose().popPose();
    }
}
