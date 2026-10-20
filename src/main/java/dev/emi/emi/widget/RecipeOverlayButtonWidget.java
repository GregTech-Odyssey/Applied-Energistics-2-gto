package dev.emi.emi.widget;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.input.EmiInput;
import dev.emi.emi.planner.PlannerText;
import dev.emi.emi.runtime.RecipeFavoriteActions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class RecipeOverlayButtonWidget extends Widget {
	private final int x;
	private final int y;
	private final EmiRecipe recipe;

	public RecipeOverlayButtonWidget(int x, int y, EmiRecipe recipe) {
		this.x = x;
		this.y = y;
		this.recipe = recipe;
	}

	@Override
	public Bounds getBounds() {
		return new Bounds(x, y, 12, 12);
	}

	@Override
	public void render(GuiGraphics draw, int mouseX, int mouseY, float delta) {
		boolean hovered = getBounds().contains(mouseX, mouseY);
		boolean active = RecipeFavoriteActions.isSaved(recipe);
		int border = hovered ? 0xFFB0B0B0 : 0xFF707070;
		draw.fill(x, y, x + 12, y + 12, border);
		draw.fill(x + 1, y + 1, x + 11, y + 11, 0xFF252525);
		int color = active ? 0xFFFF55FF : 0xFFDADADA;
		draw.fill(x + 5, y + 2, x + 7, y + 10, color);
		draw.fill(x + 2, y + 5, x + 10, y + 7, color);
	}

	@Override
	public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
		List<ClientTooltipComponent> list = new ArrayList<>();
		list.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr("recipe_overlay.title", "Recipe Overlay")).withStyle(ChatFormatting.WHITE).getVisualOrderText()));
		list.add(ClientTooltipComponent.create(EmiPort.literal("[alt]").withStyle(ChatFormatting.YELLOW).getVisualOrderText()));
		if (EmiInput.isAltDown()) {
			list.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr("recipe_overlay.save_recipe", "SHIFT + A - Save with Recipe")).withStyle(ChatFormatting.YELLOW).getVisualOrderText()));
			list.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr("recipe_overlay.save_recipe_count", "CTRL + SHIFT + A - Save with Recipe & Count")).withStyle(ChatFormatting.YELLOW).getVisualOrderText()));
			list.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr("recipe_overlay.share_recipe", "CTRL + SHIFT + L - Share Recipe Link to Chat")).withStyle(ChatFormatting.YELLOW).getVisualOrderText()));
		}
		return list;
	}

	@Override
	public boolean mouseClicked(int mouseX, int mouseY, int button) {
		if (button != 0) {
			return false;
		}
		RecipeFavoriteActions.toggleWithRecipe(recipe, EmiInput.isControlDown());
		playSound();
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
		boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
		if (shift && control && keyCode == GLFW.GLFW_KEY_L) {
			RecipeFavoriteActions.sendRecipeToChat(recipe);
			return true;
		}
		if (shift && keyCode == GLFW.GLFW_KEY_A) {
			RecipeFavoriteActions.toggleWithRecipe(recipe, control);
			playSound();
			return true;
		}
		return false;
	}

	private void playSound() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
	}
}
