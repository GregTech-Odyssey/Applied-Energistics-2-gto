package dev.emi.emi.screen.widget;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.planner.PlannerText;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.widget.SizedButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

import java.util.List;

public class ProductionPlannerButtonWidget extends SizedButtonWidget {

    public ProductionPlannerButtonWidget(int x, int y) {
        super(x, y, 20, 20, 0, 0, () -> true, widget -> EmiApi.viewProductionPlanner());
    }

    @Override
    public void renderWidget(GuiGraphics raw, int mouseX, int mouseY, float delta) {
        EmiDrawContext context = EmiDrawContext.wrap(raw);
        this.active = true;
        boolean hovered = this.isMouseOver(mouseX, mouseY);
        int background = hovered ? 0xFF686873 : 0xFF505058;
        int border = hovered ? 0xFFFFFFFF : 0xFFB8B8C0;
        context.fill(this.x, this.y, this.width, this.height, background);
        context.fill(this.x, this.y, this.width, 1, border);
        context.fill(this.x, this.y + this.height - 1, this.width, 1, border);
        context.fill(this.x, this.y, 1, this.height, border);
        context.fill(this.x + this.width - 1, this.y, 1, this.height, border);
        context.fill(this.x + 4, this.y + 5, 4, 4, 0xFF8ED6A4);
        context.fill(this.x + 12, this.y + 5, 4, 4, 0xFF8ED6A4);
        context.fill(this.x + 8, this.y + 12, 4, 4, 0xFF8ED6A4);
        context.fill(this.x + 8, this.y + 6, 4, 2, 0xFF8ED6A4);
        context.fill(this.x + 9, this.y + 8, 2, 4, 0xFF8ED6A4);
        if (hovered) {
            context.push();
            context.disableDepthTest();
            Minecraft client = Minecraft.getInstance();
            EmiRenderHelper.drawTooltip(client.screen, context, List.of(
                ClientTooltipComponent.create(EmiPort.ordered(EmiPort.literal(PlannerText.tr("recipe_planner.title", "Production Planner")))),
                ClientTooltipComponent.create(EmiPort.ordered(EmiPort.literal(PlannerText.tr("recipe_planner.open_lines", "Open production lines"))))
            ), mouseX, mouseY);
            context.pop();
        }
    }
}
