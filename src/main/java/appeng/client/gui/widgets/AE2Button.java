package appeng.client.gui.widgets;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.PaletteColor;
import appeng.core.AppEng;

public class AE2Button extends Button {
    protected static final ResourceLocation BUTTON_TEXTURE = AppEng.makeId("textures/gui/sprites/button.png");
    protected static final ResourceLocation BUTTON_DISABLED_TEXTURE = AppEng
            .makeId("textures/gui/sprites/button_disabled.png");
    protected static final ResourceLocation BUTTON_HIGHLIGHTED_TEXTURE = AppEng
            .makeId("textures/gui/sprites/button_highlighted.png");

    private static final int DEFAULT_DISABLED_COLOR = 0xFF413f54;
    private static final int DEFAULT_HOVER_COLOR = 0xFF517497;
    private static final int DEFAULT_NORMAL_COLOR = 0xFFf2f2f2;

    public AE2Button(int pX, int pY, int pWidth, int pHeight, Component component, OnPress onPress) {
        super(pX, pY, pWidth, pHeight, component, onPress, supplier -> Component.empty());
    }

    public AE2Button(Component component, OnPress onPress) {
        super(0, 0, 0, 0, component, onPress, supplier -> Component.empty());
    }

    @Override
    protected void renderWidget(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        pGuiGraphics.setColor(1.0F, 1.0F, 1.0F, this.alpha);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        ResourceLocation texture = !this.active ? BUTTON_DISABLED_TEXTURE
                : (this.isHovered() ? BUTTON_HIGHLIGHTED_TEXTURE : BUTTON_TEXTURE);
        Blitter.blitNineSliced(pGuiGraphics, texture, this.getX(), this.getY(), 0, this.getWidth(), this.getHeight(),
                3, 3, 3, 3, 200, 20);
        pGuiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        int alphaMask = Mth.ceil(this.alpha * 255.0F) << 24;
        if (!this.active) {
            int rgb = resolveColor(minecraft, PaletteColor.BUTTON_TEXT_DISABLED, DEFAULT_DISABLED_COLOR) & 0x00FFFFFF;
            this.renderButtonText(pGuiGraphics, minecraft.font, 2, rgb | alphaMask, -1);
        } else if (this.isHovered()) {
            int rgb = resolveColor(minecraft, PaletteColor.BUTTON_TEXT_HOVER, DEFAULT_HOVER_COLOR) & 0x00FFFFFF;
            this.renderButtonText(pGuiGraphics, minecraft.font, 2, rgb | alphaMask, 0);
        } else {
            int rgb = resolveColor(minecraft, PaletteColor.BUTTON_TEXT, DEFAULT_NORMAL_COLOR) & 0x00FFFFFF;
            this.renderButtonText(pGuiGraphics, minecraft.font, 2, rgb | alphaMask, 1);
        }
    }

    private static int resolveColor(Minecraft minecraft, PaletteColor key, int defaultArgb) {
        if (minecraft.screen instanceof AEBaseScreen<?> aeScreen) {
            return aeScreen.getStyle().getColorOrDefault(key, defaultArgb);
        }
        return defaultArgb;
    }

    public static void renderButtonText(GuiGraphics pGuiGraphics, Font pFont, Component pText, int pMinX, int pMinY,
            int pMaxX, int pMaxY, int yOffset, int pColor) {
        renderButtonText(pGuiGraphics, pFont, pText, (pMinX + pMaxX) / 2, pMinX, pMinY, pMaxX, pMaxY, yOffset, pColor);
    }

    public static void renderButtonText(GuiGraphics pGuiGraphics, Font pFont, Component pText, int pCenterX, int pMinX,
            int pMinY, int pMaxX, int pMaxY, int yOffset, int pColor) {
        int i = pFont.width(pText);
        int j = (pMinY + pMaxY - 9) / 2 + 1;
        int k = pMaxX - pMinX;
        if (i > k) {
            int l = i - k;
            double d0 = (double) Util.getMillis() / 1000.0;
            double d1 = Math.max((double) l * 0.5, 3.0);
            double d2 = Math.sin((Math.PI / 2) * Math.cos((Math.PI * 2) * d0 / d1)) / 2.0 + 0.5;
            double d3 = Mth.lerp(d2, 0.0, (double) l);
            pGuiGraphics.enableScissor(pMinX, pMinY, pMaxX, pMaxY);
            pGuiGraphics.drawString(pFont, pText, pMinX - (int) d3, j, pColor, false);
            pGuiGraphics.disableScissor();
        } else {
            int i1 = Mth.clamp(pCenterX, pMinX + i / 2, pMaxX - i / 2);
            FormattedCharSequence formattedcharsequence = pText.getVisualOrderText();
            pGuiGraphics.drawString(pFont, formattedcharsequence, i1 - pFont.width(formattedcharsequence) / 2,
                    j - yOffset, pColor, false);
        }
    }

    protected void renderButtonText(GuiGraphics pGuiGraphics, Font pFont, int pWidth, int pColor, int yOffset) {
        int i = this.getX() + pWidth;
        int j = this.getX() + this.getWidth() - pWidth;
        renderButtonText(pGuiGraphics, pFont, this.getMessage(), i, this.getY(), j, this.getY() + this.getHeight(),
                yOffset, pColor);
    }
}
