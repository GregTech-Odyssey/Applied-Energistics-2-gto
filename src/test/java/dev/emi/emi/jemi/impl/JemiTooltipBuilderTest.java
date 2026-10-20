package dev.emi.emi.jemi.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mojang.datafixers.util.Either;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

class JemiTooltipBuilderTest {
    @Test
    void mutableJeiLinesRemainVisibleThroughTheEmiProjection() {
        var builder = new JemiTooltipBuilder();
        var first = Component.literal("first");
        var second = Component.literal("second");
        builder.add(first);
        builder.getLines().set(0, Either.left(second));
        assertEquals(1, builder.tooltip.size());
        assertSame(second, builder.toLegacyToComponents().getFirst());
        builder.removeAll(List.of(second));
        assertEquals(0, builder.tooltip.size());
        builder.add(first);
        builder.clear();
        assertEquals(0, builder.getLines().size());
        assertEquals(0, builder.tooltip.size());
    }

    @Test
    void formattedTextThatIsNotAComponentIsNotDropped() {
        var builder = new JemiTooltipBuilder();
        builder.add(FormattedText.of("formatted"));
        assertEquals("formatted", builder.toLegacyToComponents().getFirst().getString());
        assertEquals(1, builder.tooltip.size());
    }
}
