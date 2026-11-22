package appeng.client.render;

import java.util.List;

import com.mojang.datafixers.util.Either;

import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.RenderTooltipEvent;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.crafting.pattern.EncodedPatternItem;

public class PatternTooltipComponent implements TooltipComponent, ClientTooltipComponent {
    private static final int TEXT_OFFSET = 12;

    private final AEKey what;
    private final List<FormattedCharSequence> lines;
    private final int width;

    private PatternTooltipComponent(AEKey what, Component text, Font font, int maxWidth) {
        this.what = what;
        this.lines = font.split(text, Math.max(1, maxWidth - TEXT_OFFSET));
        int textWidth = 0;
        for (int i = 0; i < lines.size(); i++) {
            textWidth = Math.max(textWidth, font.width(lines.get(i)));
        }
        this.width = TEXT_OFFSET + textWidth;
    }

    public static void gatherComponents(RenderTooltipEvent.GatherComponents event) {
        if (!(event.getItemStack().getItem() instanceof EncodedPatternItem)) {
            return;
        }

        var minecraft = Minecraft.getInstance();
        var font = ForgeHooksClient.getTooltipFont(event.getItemStack(), minecraft.font);
        var window = minecraft.getWindow();
        // EMI gathers components with unbounded screen dimensions.
        int screenWidth = Math.min(event.getScreenWidth(), window.getGuiScaledWidth());
        int mouseX = (int) (minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth());
        int maxWidth = mouseX > screenWidth / 2 ? mouseX - 20 : screenWidth - mouseX - 16;
        if (event.getMaxWidth() > 0) {
            maxWidth = Math.min(maxWidth, event.getMaxWidth());
        }

        var elements = event.getTooltipElements();
        for (int i = 0; i < elements.size(); i++) {
            var text = elements.get(i).map(value -> value, value -> null);
            if (text instanceof Component component
                    && component.getContents() instanceof EncodedPatternItem.MaterialTooltip material) {
                elements.set(i, Either.right(new PatternTooltipComponent(material.what(), component, font, maxWidth)));
            }
        }
    }

    @Override
    public int getHeight() {
        return lines.size() * 10;
    }

    @Override
    public int getWidth(Font font) {
        return width;
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource buffers) {
        for (int i = 0; i < lines.size(); i++) {
            font.drawInBatch(lines.get(i), x + TEXT_OFFSET, y + i * 10, -1, true, matrix, buffers,
                    Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        }
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(0.625F, 0.625F, 1.0F);
        AEKeyRendering.drawInGui(Minecraft.getInstance(), guiGraphics, 0, 0, what);
        pose.popPose();
    }
}
