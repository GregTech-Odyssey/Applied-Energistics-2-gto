package dev.emi.emi.jemi.impl.extras;

import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.gui.widgets.ITextWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import mezz.jei.api.gui.widgets.IRecipeWidgetTooltipCallback;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.emi.emi.jemi.impl.JemiTooltipBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.FormattedCharSequence;
import java.util.Collection;

import java.util.List;

public class JemiTextWidget extends JemiPlaceable<ITextWidget> implements ITextWidget {
    public int color = 0xffffffff;
    public boolean shadow = true;
    public int spacing = 0;
    public HorizontalAlignment horizontal = HorizontalAlignment.LEFT;
    public VerticalAlignment vertical = VerticalAlignment.TOP;
    public List<FormattedText> text;
    private Font font;
    private IRecipeWidgetTooltipCallback tooltip;

    public JemiTextWidget(List<FormattedText> text, int width, int height) {
        super(width, height);
        this.text = text;
    }

    @Override
    public ITextWidget setFont(Font font) {
        this.font = font;
        return this;
    }

    @Override
    public ITextWidget setPosition(int xPos, int yPos) {
        super.setPosition(xPos, yPos);
        return this;
    }

    @Override
    public ITextWidget setPosition(int areaX, int areaY, int areaWidth, int areaHeight,
            HorizontalAlignment horizontalAlignment, VerticalAlignment verticalAlignment) {
        super.setPosition(areaX, areaY, areaWidth, areaHeight, horizontalAlignment, verticalAlignment);
        return this;
    }

    @Override
    public ITextWidget setTooltip(FormattedText tooltip) {
        this.tooltip = builder -> builder.add(tooltip);
        return this;
    }

    @Override
    public ITextWidget setTooltip(Collection<? extends FormattedText> tooltip) {
        var snapshot = new ObjectArrayList<FormattedText>(tooltip);
        this.tooltip = builder -> builder.addAll(snapshot);
        return this;
    }

    @Override
    public ITextWidget setTooltip(TooltipComponent tooltip) {
        this.tooltip = builder -> builder.add(tooltip);
        return this;
    }

    @Override
    public ITextWidget setTooltip(IRecipeWidgetTooltipCallback tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public void addWidgets(WidgetHolder holder) {
        var actualFont = font == null ? Minecraft.getInstance().font : font;
        var lines = new ObjectArrayList<FormattedCharSequence>(text.size());
        for (var part : text) lines.addAll(actualFont.split(part, width));
        int lineStep = Math.max(1, actualFont.lineHeight + spacing);
        int visibleLines = Math.min(lines.size(), Math.max(0, (height + spacing) / lineStep));
        int textHeight = visibleLines == 0 ? 0 : visibleLines * lineStep - spacing;
        int yOffset = vertical.getYPos(height, textHeight);
        holder.addDrawable(x, y, width, height, (draw, mouseX, mouseY, delta) -> {
            for (int i = 0; i < visibleLines; i++) {
                var line = lines.get(i);
                draw.drawString(actualFont, line, horizontal.getXPos(width, actualFont.width(line)),
                        yOffset + i * lineStep, color, shadow);
            }
        }).tooltip((mouseX, mouseY) -> {
            var builder = new JemiTooltipBuilder();
            if (visibleLines < lines.size()) builder.addAll(text);
            if (tooltip != null) tooltip.onTooltip(builder);
            return builder.tooltip;
        });
    }

    @Override
    public ITextWidget setColor(int color) {
        this.color = color;
        return this;
    }

    @Override
    public ITextWidget setLineSpacing(int spacing) {
        this.spacing = spacing;
        return this;
    }

    @Override
    public ITextWidget setShadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    @Override
    public ITextWidget setTextAlignment(HorizontalAlignment horizontalAlignment) {
        this.horizontal = horizontalAlignment;
        return this;
    }

    @Override
    public ITextWidget setTextAlignment(VerticalAlignment verticalAlignment) {
        this.vertical = verticalAlignment;
        return this;
    }
    
}
