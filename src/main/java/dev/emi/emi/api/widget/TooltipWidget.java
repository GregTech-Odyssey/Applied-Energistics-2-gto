package dev.emi.emi.api.widget;

import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

import java.util.List;
import java.util.function.BiFunction;

public class TooltipWidget extends Widget {
    private final dev.emi.emi.api.widget.Bounds bounds;
    private final BiFunction<Integer, Integer, List<ClientTooltipComponent>> tooltipSupplier;

    public TooltipWidget(BiFunction<Integer, Integer, List<ClientTooltipComponent>> tooltipSupplier, int x, int y, int width, int height) {
        this.bounds = new Bounds(x, y, width, height);
        this.tooltipSupplier = tooltipSupplier;
    }

    @Override
    public Bounds getBounds() {
        return bounds;
    }

    @Override
    public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
        return tooltipSupplier.apply(mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics draw, int mouseX, int mouseY, float delta) {
    }
}
