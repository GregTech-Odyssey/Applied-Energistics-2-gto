package gto_ae.hooks.gui;

import net.minecraft.resources.ResourceLocation;

import appeng.client.gui.style.Blitter;

public interface IIcon {
    int TEXTURE_WIDTH = 256;
    int TEXTURE_HEIGHT = 256;

    ResourceLocation getIconAtlas();

    default int getIconAtlasWidth() {
        return IIcon.TEXTURE_WIDTH;
    }

    default int getIconAtlasHeight() {
        return IIcon.TEXTURE_HEIGHT;
    }

    int getIconX();

    int getIconY();

    int getIconWidth();

    int getIconHeight();

    default Blitter getBlitter() {
        return Blitter.texture(getIconAtlas(), getIconAtlasWidth(), getIconAtlasHeight())
                .src(getIconX(), getIconY(), getIconWidth(), getIconHeight());
    }

}
