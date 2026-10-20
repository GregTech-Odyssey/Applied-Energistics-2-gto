package dev.emi.emi.jemi.impl.extras;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.gui.widgets.ITextWidget;

class JemiTextWidgetTest {
    @Test
    void supportsCurrentJeiCovariantPositionMethods() {
        var widget = new JemiTextWidget(List.of(Component.literal("text")), 20, 10);
        ITextWidget api = widget;
        assertSame(api, api.setPosition(4, 7));
        assertEquals(4, widget.x);
        assertEquals(7, widget.y);
        assertSame(api, api.setPosition(10, 20, 100, 50, HorizontalAlignment.RIGHT, VerticalAlignment.BOTTOM));
        assertEquals(90, widget.x);
        assertEquals(60, widget.y);
    }

    @Test
    void extrasRegisterTextWidgetsAndPreserveTheirConfiguredTooltip() {
        var extras = new JemiRecipeExtrasBuilder(null);
        var font = mock(Font.class);
        var text = Component.literal("text");
        when(font.split(any(FormattedText.class), anyInt())).thenReturn(List.of(text.getVisualOrderText()));
        var api = extras.addText(List.of(text), 80, 20).setFont(font).setLineSpacing(1)
                .setPosition(3, 5).setTooltip(Component.literal("configured tooltip"));
        assertEquals(1, extras.widgets.size());
        var holder = new TestHolder();
        extras.widgets.getFirst().addWidgets(holder);
        assertEquals(1, holder.widgets.size());
        assertEquals(3, holder.widgets.getFirst().getBounds().x());
        assertEquals(1, holder.widgets.getFirst().getTooltip(4, 6).size());
        assertSame(api, api.setTooltip(builder -> builder.add(Component.literal("callback"))));
        assertEquals(1, holder.widgets.getFirst().getTooltip(4, 6).size());
    }

    private static class TestHolder implements WidgetHolder {
        final ObjectArrayList<Widget> widgets = new ObjectArrayList<>();

        @Override
        public int getWidth() { return 160; }

        @Override
        public int getHeight() { return 100; }

        @Override
        public <T extends Widget> T add(T widget) {
            widgets.add(widget);
            return widget;
        }
    }
}
