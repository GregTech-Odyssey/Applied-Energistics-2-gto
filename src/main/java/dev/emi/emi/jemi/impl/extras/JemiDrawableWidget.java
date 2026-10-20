package dev.emi.emi.jemi.impl.extras;

import java.util.Collection;
import java.util.function.BiConsumer;

import dev.emi.emi.api.widget.WidgetHolder;
import dev.emi.emi.jemi.impl.JemiTooltipBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.gui.widgets.IDrawableWidget;
import mezz.jei.api.gui.widgets.IRecipeWidgetTooltipCallback;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

public class JemiDrawableWidget extends JemiPlaceable<IDrawableWidget> implements IDrawableWidget {
    private final BiConsumer<JemiDrawableWidget, WidgetHolder> constructor;
    private IRecipeWidgetTooltipCallback tooltip;

    public JemiDrawableWidget(int width, int height, BiConsumer<JemiDrawableWidget, WidgetHolder> constructor) {
        super(width, height);
        this.constructor = constructor;
    }

    @Override
    public IDrawableWidget setPosition(int xPos, int yPos) {
        super.setPosition(xPos, yPos);
        return this;
    }

    @Override
    public IDrawableWidget setPosition(int areaX, int areaY, int areaWidth, int areaHeight,
            HorizontalAlignment horizontalAlignment, VerticalAlignment verticalAlignment) {
        super.setPosition(areaX, areaY, areaWidth, areaHeight, horizontalAlignment, verticalAlignment);
        return this;
    }

    @Override
    public IDrawableWidget setTooltip(FormattedText tooltip) {
        this.tooltip = builder -> builder.add(tooltip);
        return this;
    }

    @Override
    public IDrawableWidget setTooltip(Collection<? extends FormattedText> tooltip) {
        var snapshot = new ObjectArrayList<FormattedText>(tooltip);
        this.tooltip = builder -> builder.addAll(snapshot);
        return this;
    }

    @Override
    public IDrawableWidget setTooltip(TooltipComponent tooltip) {
        this.tooltip = builder -> builder.add(tooltip);
        return this;
    }

    @Override
    public IDrawableWidget setTooltip(IRecipeWidgetTooltipCallback tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public void addWidgets(WidgetHolder holder) {
        constructor.accept(this, holder);
        if (tooltip != null) holder.addTooltip((mouseX, mouseY) -> {
            var builder = new JemiTooltipBuilder();
            tooltip.onTooltip(builder);
            return builder.tooltip;
        }, x, y, width, height);
    }
}
