package dev.emi.emi.api;

import dev.emi.emi.EmiClient;
import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiFillAction;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.registry.EmiRecipeFiller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @deprecated Migrate to {@link StandardRecipeHandler}
 */
@Deprecated
public interface EmiRecipeHandler<T extends AbstractContainerMenu> extends StandardRecipeHandler<T> {
	public static final Component NOT_ENOUGH_INGREDIENTS = EmiPort.translatable("emi.not_enough_ingredients");
	
	/**
	 * @return The slots for the recipe handler to source ingredients from.
	 * Typically this should include the player's inventory, and crafting slots.
	 */
	List<Slot> getInputSources(T handler);

	default List<Slot> getInputSources(AbstractContainerScreen<T> screen) {
		return getInputSources(screen.getMenu());
	}

	/**
	 * @return The slots where inputs should be placed to perform crafting.
	 */
	List<Slot> getCraftingSlots(T handler);

	default List<Slot> getCraftingSlots(AbstractContainerScreen<T> screen) {
		return getCraftingSlots(screen.getMenu());
	}

	/**
	 * @return The slots where inputs should be placed to perform crafting for a particular context.
	 */
	default List<Slot> getCraftingSlots(EmiRecipe recipe, AbstractContainerScreen<T> screen) {
		return getCraftingSlots(screen.getMenu());
	}
	
	@SuppressWarnings("unchecked")
	default List<Slot> getCraftingSlots(EmiRecipe recipe, T handler) {
		AbstractContainerScreen<?> hs = EmiApi.getHandledScreen();
		if (hs != null && hs.getMenu() == handler) {
			return getCraftingSlots(recipe, (AbstractContainerScreen<T>) hs);
		}
		return List.of();
	}

	/**
	 * @return The output slot for recipe handlers that support instant interaction, like crafting tables.
	 * For handlers that have processing time, or where this concept is otherwise inapplicable, null.
	 */
	default @Nullable Slot getOutputSlot(T handler) {
		return null;
	}

	/**
	 * @return Whether this handler is applicable for the provided recipe.
	 */
	boolean supportsRecipe(EmiRecipe recipe);

	/**
	 * @return Whether the provided recipe is only conscious of the handler when the handler is applicable.
	 * Effectively, whether to not display the plus fill button outside of applicable screens.
	 */
	default boolean onlyDisplayWhenApplicable(EmiRecipe recipe) {
		return false;
	}

	@Override
	default boolean alwaysDisplaySupport(EmiRecipe recipe) {
		return !onlyDisplayWhenApplicable(recipe);
	}

	@Override
	default boolean canCraft(EmiRecipe recipe, EmiCraftContext<T> context) {
		return canCraft(recipe, context.getInventory(), context.getScreen());
	}

	default boolean canCraft(EmiRecipe recipe, EmiPlayerInventory inventory, AbstractContainerScreen<T> screen) {
		return inventory.canCraft(recipe);
	}

	default Component getInvalidReason(EmiRecipe recipe, EmiPlayerInventory inventory, AbstractContainerScreen<T> screen) {
		return NOT_ENOUGH_INGREDIENTS;
	}

	default boolean performFill(EmiRecipe recipe, AbstractContainerScreen<T> screen, dev.emi.emi.api.EmiFillAction action, int amount) {
		List<ItemStack> stacks = EmiRecipeFiller.getStacks(this, recipe, screen, amount);
		if (stacks != null) {
			stacks = mutateFill(recipe, screen, stacks);
			if (stacks != null) {
				Minecraft.getInstance().setScreen(screen);
				if (!EmiClient.onServer) {
					return EmiRecipeFiller.clientFill(this, recipe, screen, stacks, switch (action) {
						case FILL -> EmiCraftContext.Destination.NONE;
						case QUICK_MOVE -> EmiCraftContext.Destination.INVENTORY;
						case CURSOR -> EmiCraftContext.Destination.CURSOR;
					});
				} else {
					EmiClient.sendFillRecipe(this, screen, screen.getMenu().containerId, action.id, stacks, recipe);
				}
				return true;
			}
		}
		return false;
	}

	@Override
	default boolean craft(EmiRecipe recipe, EmiCraftContext<T> context) {
		return performFill(recipe, context.getScreen(), switch (context.getDestination()) {
			case NONE -> dev.emi.emi.api.EmiFillAction.FILL;
			case INVENTORY -> dev.emi.emi.api.EmiFillAction.QUICK_MOVE;
			case CURSOR -> EmiFillAction.CURSOR;
		}, context.getAmount());
	}

	@Deprecated
	default List<ItemStack> mutateFill(EmiRecipe recipe, AbstractContainerScreen<T> screen, List<ItemStack> stacks) {
		return stacks;
	}
}
