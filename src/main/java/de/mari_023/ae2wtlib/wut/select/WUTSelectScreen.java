package de.mari_023.ae2wtlib.wut.select;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.joml.Matrix4f;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import de.mari_023.ae2wtlib.wut.WUTHandler;

public class WUTSelectScreen extends Screen {
    private static final int SEGMENT_RGB = 0x7D7D7D;
    private static final int SELECTED_SEGMENT_RGB = 0x3E3E3E;

    private final List<String> terminals = new ArrayList<>();

    public WUTSelectScreen(ItemStack terminal) {
        super(Component.translatable("gui.ae2wtlib.wireless_universal_terminal"));
        for (String currentTerminal : WUTHandler.terminalNames) {
            if (WUTHandler.hasTerminal(terminal, currentTerminal)) {
                terminals.add(currentTerminal);
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        int angle = 360 / terminals.size();
        float hWidth = width / 2F;
        float hHeight = height / 2F;
        int selected = (int) AngleHelper.getAngle(mouseX - hWidth, mouseY - hHeight) / angle;

        for (int i = 0; i < terminals.size(); i++) {
            drawSegment(guiGraphics.pose(), i * angle, (i + 1) * angle, hWidth, hHeight, 100, 50,
                    i == selected ? SELECTED_SEGMENT_RGB : SEGMENT_RGB, i == selected);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    public static void drawSegment(PoseStack poseStack, int startingAngle, int endingAngle, float centerX,
            float centerY, int outerRadius, int innerRadius, int rgb, boolean selected) {
        if (selected) {
            outerRadius += 10;
        }
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        poseStack.pushPose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        Matrix4f modelMatrix = poseStack.last().pose();
        bufferBuilder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);

        for (int i = endingAngle; i >= startingAngle; i--) {
            float x = AngleHelper.getX(i, outerRadius);
            float y = AngleHelper.getY(i, outerRadius);
            bufferBuilder.vertex(modelMatrix, centerX + x, centerY - y, 0).color(r, g, b, 255).endVertex();
        }
        for (int i = startingAngle; i <= endingAngle; i++) {
            float x = AngleHelper.getX(i, innerRadius);
            float y = AngleHelper.getY(i, innerRadius);
            bufferBuilder.vertex(modelMatrix, centerX + x, centerY - y, 0).color(r, g, b, 255).endVertex();
        }

        BufferUploader.drawWithShader(bufferBuilder.end());
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
