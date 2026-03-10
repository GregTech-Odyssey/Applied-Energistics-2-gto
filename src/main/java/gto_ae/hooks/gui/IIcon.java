package gto_ae.hooks.gui;

import net.minecraft.resources.ResourceLocation;

import appeng.client.gui.style.Blitter;

public interface IIcon {
    int TEXTURE_WIDTH = 256;
    int TEXTURE_HEIGHT = 256;

    ResourceLocation getIconTexture();

    default int getIconTextureWidth() {
        return IIcon.TEXTURE_WIDTH;
    }

    default int getIconTextureHeight() {
        return IIcon.TEXTURE_HEIGHT;
    }

    int getIconX();

    int getIconY();

    int getIconWidth();

    int getIconHeight();

    default Blitter getBlitter() {
        return Blitter.texture(getIconTexture(), getIconTextureWidth(), getIconTextureHeight())
                .src(getIconX(), getIconY(), getIconWidth(), getIconHeight());
    }

}
