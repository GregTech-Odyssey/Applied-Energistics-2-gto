package gto_ae.client.gui.widgets;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

import appeng.client.Point;
import appeng.client.gui.*;
import appeng.client.gui.style.Blitter;

public class AESlotWidget implements ICompositeWidget {

    private static final Blitter SLOT = Icon.SLOT_BACKGROUND.getBlitter();
    private final Slot slot;
    private final AEBaseScreen<?> screen;
    private final List<Component> tooltips = new ArrayList<>();

    public AESlotWidget(Slot slot, AEBaseScreen<?> screen) {
        this.slot = slot;
        this.screen = screen;
    }

    @Override
    public void drawBackgroundLayer(GuiGraphics guiGraphics, Rect2i bounds, Point mouse) {
        SLOT.dest(slot.x + screen.getGuiLeft() - 1, slot.y + screen.getGuiTop() - 1).blit(guiGraphics);
    }

    @Override
    public void setPosition(Point position) {
        slot.x = position.getX();
        slot.y = position.getY();
    }

    @Override
    public void setSize(int width, int height) {
    }

    @Override
    public @Nullable Tooltip getTooltip(int mouseX, int mouseY) {
        return new Tooltip(tooltips);
    }

    public void setTooltip(List<Component> tooltips) {
        this.tooltips.clear();
        this.tooltips.addAll(tooltips);
    }

    public void setTooltip(Component... tooltips) {
        this.tooltips.clear();
        this.tooltips.addAll(List.of(tooltips));
    }

    @Override
    public Rect2i getBounds() {
        return new Rect2i(slot.x, slot.y, 18, 18);
    }

}
