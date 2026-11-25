package de.mari_023.ae2wtlib.terminal;

import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import appeng.client.gui.widgets.ITooltip;

public class IconButton extends Button implements ITooltip {
    private final Icon icon;
    private final Icon bg;
    @Nullable
    private List<Component> tooltip;

    public IconButton(OnPress onPress, Icon icon) {
        this(onPress, icon, Icon.BUTTON_BACKGROUND);
    }

    public IconButton(OnPress onPress, Icon icon, Icon bg) {
        super(0, 0, bg.width(), bg.height(), Component.empty(), onPress, Button.DEFAULT_NARRATION);
        this.icon = icon;
        this.bg = bg;
    }

    public void setVisibility(boolean vis) {
        visible = vis;
        active = vis;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
        if (!visible) {
            return;
        }

        int yOffset = isHovered() ? 1 : 0;
        Icon activeBg = bg;
        if (bg == Icon.BUTTON_BACKGROUND) {
            activeBg = isHovered() ? Icon.BUTTON_BACKGROUND_HOVER
                    : isFocused() ? Icon.BUTTON_BACKGROUND_FOCUS : Icon.BUTTON_BACKGROUND;
        }
        activeBg.getBlitter().dest(getX(), getY() + yOffset, activeBg.width(), activeBg.height()).blit(guiGraphics);

        Icon fg = getIcon();
        int iconX = getX() + (activeBg.width() - fg.width()) / 2;
        int iconY = getY() + (activeBg.height() - 3 - fg.height()) / 2 + yOffset;
        var blitter = fg.getBlitter().dest(iconX, iconY);
        if (!active) {
            blitter.opacity(0.5f);
        }
        blitter.blit(guiGraphics);
    }

    protected Icon getIcon() {
        return icon;
    }

    @Override
    public List<Component> getTooltipMessage() {
        if (tooltip == null) {
            return Collections.singletonList(getMessage());
        }
        return tooltip;
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(getX(), getY(), bg.width(), bg.height());
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return visible;
    }

    @Contract("_ -> this")
    public IconButton withTooltip(Component message) {
        super.setMessage(message);
        return this;
    }

    @Contract("_ -> this")
    public IconButton withTooltip(List<Component> tooltip) {
        this.tooltip = tooltip;
        return this;
    }
}
