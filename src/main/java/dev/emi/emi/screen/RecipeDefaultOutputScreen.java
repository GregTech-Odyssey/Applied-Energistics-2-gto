package dev.emi.emi.screen;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.bom.BoM.DefaultStatus;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiHistory;
import dev.emi.emi.screen.RecipeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.material.Fluid;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RecipeDefaultOutputScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_GAP = 2;
    private final Screen parent;
    private final EmiRecipe recipe;
    private final List<EmiStack> outputs;
    private final boolean[] selected;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int rowsY;

    public RecipeDefaultOutputScreen(Screen parent, EmiRecipe recipe) {
        super(EmiPort.literal("Choose default recipe outputs"));
        this.parent = parent;
        this.recipe = recipe;
        this.outputs = recipe.getOutputs().stream().filter(stack -> !stack.isEmpty()).toList();
        this.selected = new boolean[outputs.size()];
        boolean anySelected = false;
        for (int i = 0; i < outputs.size(); i++) {
            selected[i] = BoM.getRecipe(outputs.get(i)) == recipe;
            anySelected |= selected[i];
        }
        if (!anySelected && BoM.getRecipeStatus(recipe) == DefaultStatus.EMPTY) {
            Arrays.fill(selected, true);
        }
    }

    @Override
    protected void init() {
        super.init();
        panelWidth = Math.min(PANEL_WIDTH, Math.max(220, width - 32));
        panelHeight = 70 + outputs.size() * (ROW_HEIGHT + ROW_GAP);
        panelHeight = Math.min(panelHeight, height - 24);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        rowsY = panelY + 28;

        int buttonY = panelY + panelHeight - 28;
        int gap = 4;
        int small = 48;
        int large = 72;
        int total = small * 2 + large * 2 + gap * 3;
        int x = panelX + (panelWidth - total) / 2;
        this.addRenderableWidget(EmiPort.newButton(x, buttonY, small, 20, EmiPort.literal("All"), b -> {
            Arrays.fill(selected, true);
        }));
        x += small + gap;
        this.addRenderableWidget(EmiPort.newButton(x, buttonY, small, 20, EmiPort.literal("None"), b -> {
            Arrays.fill(selected, false);
        }));
        x += small + gap;
        this.addRenderableWidget(EmiPort.newButton(x, buttonY, large, 20, EmiPort.literal("Apply"), b -> applySelection()));
        x += large + gap;
        this.addRenderableWidget(EmiPort.newButton(x, buttonY, large, 20, EmiPort.literal("Cancel"), b -> onClose()));
    }

    @Override
    public void render(GuiGraphics raw, int mouseX, int mouseY, float delta) {
        if (parent != null) {
            parent.render(raw, -10000, -10000, delta);
        } else {
            this.renderDirtBackground(raw);
        }

        EmiDrawContext context = EmiDrawContext.wrap(raw);
        context.push();
        context.matrices().translate(0, 0, 1000);
        raw.fill(0, 0, width, height, 0x88000000);
        raw.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF15151B);
        drawBorder(raw, panelX, panelY, panelWidth, panelHeight, 0xFF7B7B84);
        context.drawCenteredTextWithShadow(title, panelX + panelWidth / 2, panelY + 9, 0xFFFFFFFF);

        for (int i = 0; i < outputs.size(); i++) {
            int y = rowsY + i * (ROW_HEIGHT + ROW_GAP);
            if (y + ROW_HEIGHT > panelY + panelHeight - 32) {
                break;
            }
            EmiStack output = outputs.get(i);
            boolean hovered = mouseX >= panelX + 8 && mouseX < panelX + panelWidth - 8
                && mouseY >= y && mouseY < y + ROW_HEIGHT;
            int bg = selected[i] ? 0xFF243428 : 0xFF24242B;
            if (hovered) {
                bg = selected[i] ? 0xFF304836 : 0xFF30303A;
            }
            raw.fill(panelX + 8, y, panelX + panelWidth - 8, y + ROW_HEIGHT, bg);
            drawBorder(raw, panelX + 8, y, panelWidth - 16, ROW_HEIGHT, hovered ? 0xFFD0D0D8 : 0xFF5A5A62);

            int boxX = panelX + 14;
            int boxY = y + 6;
            drawBorder(raw, boxX, boxY, 12, 12, selected[i] ? 0xFF80D890 : 0xFF8A8A92);
            if (selected[i]) {
                raw.fill(boxX + 3, boxY + 3, boxX + 9, boxY + 9, 0xFF63C878);
            }

            output.render(raw, panelX + 34, y + 4, delta, EmiIngredient.RENDER_ICON);
            String amount = formatAmount(output);
            int amountWidth = amount.isEmpty() ? 0 : font.width(amount);
            int maxNameWidth = panelWidth - 82 - amountWidth;
            String name = font.plainSubstrByWidth(output.getName().getString(), Math.max(40, maxNameWidth));
            context.drawTextWithShadow(EmiPort.literal(name), panelX + 56, y + 8, 0xFFFFFFFF);
            if (!amount.isEmpty()) {
                context.drawTextWithShadow(EmiPort.literal(amount), panelX + panelWidth - 16 - amountWidth, y + 8, 0xFFB8B8C0);
            }
        }

        super.render(raw, mouseX, mouseY, delta);
        int hovered = getHoveredRow(mouseX, mouseY);
        if (hovered >= 0) {
            EmiRenderHelper.drawTooltip(this, context, outputs.get(hovered).getTooltip(), mouseX, mouseY);
        }
        context.pop();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int row = getHoveredRow((int) mouseX, (int) mouseY);
            if (row >= 0) {
                selected[row] = !selected[row];
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            applySelection();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode))) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private void applySelection() {
        List<EmiStack> chosen = new ArrayList<>();
        for (int i = 0; i < outputs.size(); i++) {
            if (selected[i]) {
                chosen.add(outputs.get(i));
            }
        }
        BoM.setRecipeOutputs(recipe, chosen);
        if (RecipeScreen.resolve != null) {
            EmiHistory.pop();
        }
        onClose();
    }

    private int getHoveredRow(int mouseX, int mouseY) {
        if (mouseX < panelX + 8 || mouseX >= panelX + panelWidth - 8) {
            return -1;
        }
        for (int i = 0; i < outputs.size(); i++) {
            int y = rowsY + i * (ROW_HEIGHT + ROW_GAP);
            if (y + ROW_HEIGHT > panelY + panelHeight - 32) {
                break;
            }
            if (mouseY >= y && mouseY < y + ROW_HEIGHT) {
                return i;
            }
        }
        return -1;
    }

    private static String formatAmount(EmiStack stack) {
        long amount = stack.getAmount();
        if (stack.getKey() instanceof Fluid) {
            return compact(amount) + " mB";
        }
        if (amount > 1) {
            return "x" + compact(amount);
        }
        return "";
    }

    private static String compact(long amount) {
        long abs = Math.abs(amount);
        if (abs < 1000) {
            return Long.toString(amount);
        }
        String[] suffixes = {"K", "M", "G", "T", "P", "E"};
        double value = amount;
        int suffix = -1;
        while (Math.abs(value) >= 1000 && suffix + 1 < suffixes.length) {
            value /= 1000.0;
            suffix++;
        }
        if (Math.abs(value) >= 100 || Math.rint(value) == value) {
            return String.format(java.util.Locale.ROOT, "%.0f%s", value, suffixes[suffix]);
        }
        return String.format(java.util.Locale.ROOT, "%.1f%s", value, suffixes[suffix]);
    }

    private static void drawBorder(GuiGraphics raw, int x, int y, int width, int height, int color) {
        raw.fill(x, y, x + width, y + 1, color);
        raw.fill(x, y + height - 1, x + width, y + height, color);
        raw.fill(x, y, x + 1, y + height, color);
        raw.fill(x + width - 1, y, x + width, y + height, color);
    }
}
