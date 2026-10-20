package dev.emi.emi.widget;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.planner.PlannerText;
import dev.emi.emi.planner.ProductionPlanner;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.widget.RecipeButtonWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

import java.util.ArrayList;
import java.util.List;

public class RecipePlannerButtonWidget extends RecipeButtonWidget {

	public RecipePlannerButtonWidget(int x, int y, EmiRecipe recipe) {
		super(x, y, 0, 0, recipe);
	}

	@Override
	public void render(GuiGraphics raw, int mouseX, int mouseY, float delta) {
		EmiDrawContext context = EmiDrawContext.wrap(raw);
		boolean hovered = getBounds().contains(mouseX, mouseY);
		boolean replacing = ProductionPlanner.canReplacePendingWith(recipe);
		int bg = hovered ? 0xFF4A4A54 : 0xFF303038;
		int border = replacing ? (hovered ? 0xFFFFFFAA : 0xFFD8B84C) : (hovered ? 0xFFE0E0E0 : 0xFF808088);
		context.fill(x, y, 12, 12, bg);
		context.fill(x, y, 12, 1, border);
		context.fill(x, y + 11, 12, 1, border);
		context.fill(x, y, 1, 12, border);
		context.fill(x + 11, y, 1, 12, border);
		if (replacing) {
			context.drawCenteredText(EmiPort.literal("R"), x + 6, y + 2, 0xFFFFDD66);
		} else {
			context.fill(x + 5, y + 2, 2, 8, 0xFF66FF99);
			context.fill(x + 2, y + 5, 8, 2, 0xFF66FF99);
		}
	}

	@Override
	public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
		List<ClientTooltipComponent> tooltip = new ArrayList<>();
		boolean pendingReplace = ProductionPlanner.hasPendingRecipeReplacement();
		boolean replacing = ProductionPlanner.canReplacePendingWith(recipe);
		if (recipe.getId() == null) {
			tooltip.add(ClientTooltipComponent.create(EmiPort.ordered(EmiPort.literal(PlannerText.tr("recipe_planner.title", "Production Planner")))));
			tooltip.add(ClientTooltipComponent.create(EmiPort.ordered(EmiPort.literal(PlannerText.tr("recipe_planner.no_stable_id", "This recipe has no stable ID and cannot be saved")))));
		} else if (replacing) {
			tooltip.add(ClientTooltipComponent.create(EmiPort.ordered(EmiPort.literal(PlannerText.tr("recipe_planner.replace_title", "Replace recipe in Production Planner")))));
			tooltip.add(ClientTooltipComponent.create(EmiPort.ordered(EmiPort.literal(PlannerText.tr("recipe_planner.replace_lmb", "LMB - Replace the selected Planner row with this recipe")))));
			tooltip.add(ClientTooltipComponent.create(EmiPort.ordered(EmiPort.literal(PlannerText.tr("recipe_planner.replace_preserve", "Preserves group and compatible MACH / PAR / VOLT / CFG settings")))));
			tooltip.add(ClientTooltipComponent.create(EmiPort.ordered(EmiPort.literal(PlannerText.tr("recipe_planner.replace_cancel", "RMB - Cancel replacement and return to Planner")))));
		} else {
			tooltip.add(ClientTooltipComponent.create(EmiPort.ordered(EmiPort.literal(PlannerText.tr("recipe_planner.title", "Production Planner")))));
			tooltip.add(ClientTooltipComponent.create(EmiPort.ordered(EmiPort.literal(PlannerText.tr("recipe_planner.add_lmb", "LMB - Add to active line (AUTO when recipe duration is detected)")))));
			tooltip.add(ClientTooltipComponent.create(EmiPort.ordered(EmiPort.literal(PlannerText.tr("recipe_planner.open_rmb", "RMB - Open planner without adding")))));
			if (pendingReplace) {
				String output = ProductionPlanner.pendingRecipeReplacementOutputName();
				tooltip.add(ClientTooltipComponent.create(EmiPort.ordered(EmiPort.literal(PlannerText.tr("recipe_planner.replace_mismatch", "Replace mode: this recipe does not produce %s", output)))));
			}
		}
		return tooltip;
	}

	@Override
	public boolean mouseClicked(int mouseX, int mouseY, int button) {
		if (recipe.getId() == null) {
			return false;
		}
		if (button == 1) {
			ProductionPlanner.cancelPendingRecipeReplacement();
			playButtonSound();
			EmiApi.viewProductionPlanner();
			return true;
		}
		if (button == 0 && ProductionPlanner.canReplacePendingWith(recipe)) {
			if (ProductionPlanner.replacePendingRecipe(recipe)) {
				playButtonSound();
				EmiApi.viewProductionPlanner();
				return true;
			}
			return false;
		}
		if (button == 0) {
			ProductionPlanner.cancelPendingRecipeReplacement();
			if (ProductionPlanner.addRecipe(recipe)) {
				playButtonSound();
				EmiApi.viewProductionPlanner();
				return true;
			}
		}
		return false;
	}
}
