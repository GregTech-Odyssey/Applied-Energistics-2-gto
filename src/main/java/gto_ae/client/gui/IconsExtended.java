package gto_ae.client.gui;

import net.minecraft.resources.ResourceLocation;

import appeng.core.AppEng;

import gto_ae.hooks.gui.IIcon;

public enum IconsExtended implements IIcon {
    WORLING_STATUS_IDLE(0, 0, 16, 16),
    WORLING_STATUS_WORKING(16, 0, 16, 16),
    WORLING_STATUS_BUSY(32, 0, 16, 16),
    WORLING_STATUS_NONE(0, 16, 16, 16),
    WORLING_STATUS_WORKING_OR_BUSY(16, 16, 16, 16),

    OPEN_GUI(32, 16, 16, 16),

    HAS_NO_CPU_WORKS(48, 0, 16, 16),
    HAS_CPU_WORKS(64, 0, 16, 16),
    CPU_WORKS_BOTH(48, 16, 16, 16),

    FILTER_BOTH(80, 0, 16, 16),
    FILTER_NONE(96, 0, 16, 16),
    FILTER_INPUT_ONLY(112, 0, 16, 16),
    FILTER_OUTPUT_ONLY(128, 0, 16, 16),

    VIEW_LOCKED(64, 16, 16, 16),
    VIEW_UNLOCKED(80, 16, 16, 16),

    ENCODING_TO_INVENTORY(96, 16, 16, 16),

    BLOCKING_MODE_CONTAIN(144, 0, 16, 16),
    BLOCKING_MODE_NON_CONTAIN(160, 0, 16, 16),
    BLOCKING_MODE_ALL(176, 0, 16, 16),
    BLOCKING_MODE_PARALLEL(192, 0, 16, 16),

    SLOT_BG_FILTER_SEARCH(240, 0, 16, 16),
    SLOT_BG_CONFIG(240, 16, 16, 16),
    ;

    private final int x, y, width, height;
    public static final ResourceLocation TEXTURE = new ResourceLocation(AppEng.MOD_ID,
            "textures/guis/states_iicon.png");

    IconsExtended(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Override
    public ResourceLocation getIconTexture() {
        return TEXTURE;
    }

    @Override
    public int getIconX() {
        return x;
    }

    @Override
    public int getIconY() {
        return y;
    }

    @Override
    public int getIconWidth() {
        return width;
    }

    @Override
    public int getIconHeight() {
        return height;
    }
}
