package appeng.client.gui.me.crafting;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

class CraftingScreenTitleTest {
    @Test
    void longTitlesStayClippedToTheAreaBeforeTheSearchField() {
        var font = mock(Font.class);
        var graphics = mock(GuiGraphics.class);
        var message = Component.literal("Crafting Plan - 26,553 Bytes Used");
        when(font.width(message)).thenReturn(180);
        var title = new CraftingScreenTitle(font, 0x404040);
        title.setPosition(8, 7);
        title.setWidth(97);
        title.setHeight(9);
        title.setMessage(message);

        title.renderWidget(graphics, 0, 0, 0);

        verify(graphics).enableScissor(8, 7, 105, 16);
        verify(graphics).disableScissor();
        verify(graphics).drawString(eq(font), eq(message), anyInt(), eq(7), eq(0x404040), eq(false));
        verify(graphics, never()).drawString(eq(font), eq(message), anyInt(), anyInt(), anyInt());
    }

    @Test
    void shortTitlesRemainLeftAligned() {
        var font = mock(Font.class);
        var graphics = mock(GuiGraphics.class);
        var message = Component.literal("合成状态");
        when(font.width(message)).thenReturn(40);
        var title = new CraftingScreenTitle(font, 0x404040);
        title.setPosition(8, 7);
        title.setWidth(97);
        title.setHeight(9);
        title.setMessage(message);

        title.renderWidget(graphics, 0, 0, 0);

        verify(graphics).drawString(font, message, 8, 7, 0x404040, false);
        verify(graphics, never()).drawString(eq(font), eq(message), anyInt(), anyInt(), anyInt());
        verify(graphics, never()).enableScissor(anyInt(), anyInt(), anyInt(), anyInt());
    }
}
