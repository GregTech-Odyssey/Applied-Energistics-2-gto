package com.ultramega.ae2insertexportcard.screen;

import java.util.Collections;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;

import org.jetbrains.annotations.NotNull;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.ITooltip;

public class UpgradeItemButton extends Button implements ITooltip {
    private final ResourceLocation texture;
    public static final ResourceLocation TEXTURE_STATES = Icon.TEXTURE;

    public UpgradeItemButton(OnPress onPress, ResourceLocation texture) {
        super(0, 0, 16, 16, Component.empty(), onPress, Button.DEFAULT_NARRATION);
        this.texture = texture;
    }

    public void setVisibility(final boolean vis) {
        visible = vis;
        active = vis;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, final int mouseX, final int mouseY, float partial) {
        if (!visible) {
            return;
        }
        int yOffset = isHovered() ? 1 : 0;
        Icon bgIcon = isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                : isFocused() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND;
        bgIcon.getBlitter()
                .dest(getX() - 1, getY() + yOffset, 18, 20)
                .zOffset(2)
                .blit(graphics);

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 3);
        RenderSystem.enableBlend();
        if (active) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            RenderSystem.setShaderColor(0.5f, 0.5f, 0.5f, 1.0f);
        }
        graphics.blit(texture, getX(), getY() + 1 + yOffset, 16, 16, 0, 0, 512, 512, 512, 512);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        graphics.pose().popPose();
    }

    @Override
    public @NotNull List<Component> getTooltipMessage() {
        return Collections.singletonList(getMessage());
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(getX(), getY(), 16, 16);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return visible;
    }
}
