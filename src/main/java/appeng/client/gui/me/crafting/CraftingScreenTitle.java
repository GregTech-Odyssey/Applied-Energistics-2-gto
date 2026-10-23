package appeng.client.gui.me.crafting;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.network.chat.Component;

/**
 * Keeps long crafting titles and their ETA or byte count inside the space beside the search field.
 */
final class CraftingScreenTitle extends StringWidget {
    CraftingScreenTitle(Font font, int color) {
        super(Component.empty(), font);
        setColor(color);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int overflow = getFont().width(getMessage()) - getWidth();
        int textX = getX();
        if (overflow > 0) {
            // Use Vanilla's scrolling motion with AE's unshadowed panel text.
            double time = Util.getMillis() / 1000.0;
            double duration = Math.max(overflow * 0.5, 3.0);
            double progress = Math.sin(Math.PI / 2 * Math.cos(Math.PI * 2 * time / duration)) / 2 + 0.5;
            textX -= (int) (progress * overflow);
            guiGraphics.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
        }
        guiGraphics.drawString(getFont(), getMessage(), textX, getY(), getColor(), false);
        if (overflow > 0) {
            guiGraphics.disableScissor();
        }
    }
}
