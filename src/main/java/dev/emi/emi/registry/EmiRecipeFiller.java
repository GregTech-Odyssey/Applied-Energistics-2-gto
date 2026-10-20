package dev.emi.emi.registry;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.handler.CoercedRecipeHandler;
import appeng.mixins.emi.accessor.CraftingResultSlotAccessor;
import dev.emi.emi.runtime.EmiCraftingToolCompat;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.runtime.EmiSidebars;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class EmiRecipeFiller {
	public static Map<MenuType<?>, List<EmiRecipeHandler<?>>> handlers = Maps.newHashMap();
	public static BiFunction<AbstractContainerMenu, EmiRecipe, EmiRecipeHandler<?>> extraHandlers = (h, r) -> null;

	public static void clear() {
		handlers.clear();
		extraHandlers = (h, r) -> null;
	}

	public static boolean isSupported(EmiRecipe recipe) {
		for (List<EmiRecipeHandler<?>> list : handlers.values()) {
			for (EmiRecipeHandler<?> handler : list) {
				if (handler.supportsRecipe(recipe) && handler.alwaysDisplaySupport(recipe)) {
					return true;
				}
			}
		}
		AbstractContainerScreen<?> hs = EmiApi.getHandledScreen();
		if (hs != null) {
			for (EmiRecipeHandler<?> handler : getAllHandlers(hs)) {
				if (handler.supportsRecipe(recipe)) {
					return true;
				}
			}
			EmiRecipeHandler<?> handler = extraHandlers.apply(hs.getMenu(), recipe);
			if (handler != null && handler.supportsRecipe(recipe)) {
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	public static <T extends AbstractContainerMenu> List<EmiRecipeHandler<T>> getAllHandlers(AbstractContainerScreen<T> screen) {
		if (screen != null) {
			T screenHandler = screen.getMenu();
			MenuType<?> type;
			try {
				type = screenHandler instanceof InventoryMenu ? null : screenHandler.getType();
			} catch (UnsupportedOperationException e) {
				type = null;
			}
			if ((type != null || screenHandler instanceof InventoryMenu) && handlers.containsKey(type)) {
				return (List<EmiRecipeHandler<T>>) (List<?>) handlers.get(type);
			}
			for (Slot slot : screen.getMenu().slots) {
				if (slot instanceof ResultSlot crs) {
					CraftingContainer inv = ((CraftingResultSlotAccessor) crs).getInput();
					if (inv != null && inv.getWidth() > 0 && inv.getHeight() > 0) {
						return List.of(new CoercedRecipeHandler<T>(crs));
					}
				}
			}
		}
		return List.of();
	}

	@SuppressWarnings("unchecked")
	public static <T extends AbstractContainerMenu> @Nullable EmiRecipeHandler<T> getFirstValidHandler(EmiRecipe recipe, AbstractContainerScreen<T> screen) {
		EmiRecipeHandler<T> ret = null;
		for (EmiRecipeHandler<T> handler : getAllHandlers(screen)) {
			if (handler.supportsRecipe(recipe)) {
				ret = handler;
				break;
			}
		}
		if (ret == null || (ret instanceof CoercedRecipeHandler && !(screen instanceof InventoryScreen))) {
			EmiRecipeHandler<T> extra = (EmiRecipeHandler<T>) extraHandlers.apply(screen.getMenu(), recipe);
			if (extra != null) {
				ret = extra;
			}
		}
		return ret;
	}

	public static <T extends AbstractContainerMenu> boolean performFill(EmiRecipe recipe, AbstractContainerScreen<T> screen,
			EmiCraftContext.Type type, EmiCraftContext.Destination destination, int amount) {
		EmiRecipeHandler<T> handler = getFirstValidHandler(recipe, screen);
		if (handler != null && handler.supportsRecipe(recipe)) {
			EmiPlayerInventory inv = handler.getInventory(screen);
			EmiCraftContext<T> context = new EmiCraftContext<T>(screen, inv, type, destination, amount);
			if (handler.canCraft(recipe, context)) {
				EmiSidebars.craft(recipe);
				boolean crafted = handler.craft(recipe, context);
				if (crafted) {
					Minecraft.getInstance().setScreen(screen);
				}
				return crafted;
			}
		}
		return false;
	}

	public static <T extends AbstractContainerMenu> @Nullable List<ItemStack> getStacks(StandardRecipeHandler<T> handler, EmiRecipe recipe, AbstractContainerScreen<T> screen, int amount) {
		try {
			T screenHandler = screen.getMenu();
			if (handler != null) {
				List<Slot> slots = handler.getInputSources(screenHandler);
				List<Slot> craftingSlots = handler.getCraftingSlots(recipe, screenHandler);
				List<EmiIngredient> ingredients = recipe.getInputs();
				List<DiscoveredItem> discovered = Lists.newArrayList();
				Object2IntMap<EmiStack> weightDivider = new Object2IntOpenHashMap<>();
				Map<Integer, Integer> reservedToolSlots = new HashMap<>();
				for (int i = 0; i < ingredients.size(); i++) {
					List<DiscoveredItem> d = Lists.newArrayList();
					EmiIngredient ingredient = ingredients.get(i);
					if (ingredient.isEmpty()) {
						discovered.add(null);
						continue;
					}
					slotLoop:
					for (Slot source : slots) {
						ItemStack ss = source.getItem();
						if (ss == null || ss.isEmpty()) {
							continue;
						}
						EmiStack actual = EmiStack.of(ss);
						EmiStack matched = matchingOption(ingredient, actual);
						if (matched == null) {
							continue;
						}
						if (EmiCraftingToolCompat.isReusable(matched) && EmiCraftingToolCompat.isGtTool(actual)) {
							d.add(new DiscoveredItem(matched, ss, ss.getCount(), (int) ingredient.getAmount(), ss.getMaxStackSize(), source.index));
							continue;
						}
						for (DiscoveredItem di : d) {
							if (!di.gtToolCatalyst() && EmiCraftingToolCompat.matches(ss, di.stack)) {
								di.amount += ss.getCount();
								continue slotLoop;
							}
						}
						d.add(new DiscoveredItem(matched, ss, ss.getCount(), (int) ingredient.getAmount(), ss.getMaxStackSize(), -1));
					}
					DiscoveredItem biggest = null;
					for (DiscoveredItem di : d) {
						if (di.gtToolCatalyst()) {
							int reserved = reservedToolSlots.getOrDefault(di.sourceSlotId, 0);
							if (di.amount - reserved <= 0) {
								continue;
							}
							if (biggest == null || !biggest.gtToolCatalyst()
									|| EmiCraftingToolCompat.getSafeCraftingUses(di.stack) > EmiCraftingToolCompat.getSafeCraftingUses(biggest.stack)) {
								biggest = di;
							}
							continue;
						}
						if (biggest == null || biggest.gtToolCatalyst()) {
							if (biggest == null) {
								biggest = di;
							}
							continue;
						}
						int a = di.amount / (weightDivider.getOrDefault(di.ingredient, 0) + di.consumed);
						int ba = biggest.amount / (weightDivider.getOrDefault(biggest.ingredient, 0) + biggest.consumed);
						if (ba < a) {
							biggest = di;
						}
					}
					if (biggest == null || i >= craftingSlots.size()) {
						return null;
					}
					Slot slot = craftingSlots.get(i);
					if (slot == null) {
						return null;
					}
					if (biggest.gtToolCatalyst()) {
						reservedToolSlots.put(biggest.sourceSlotId, reservedToolSlots.getOrDefault(biggest.sourceSlotId, 0) + 1);
					} else {
						weightDivider.put(biggest.ingredient, weightDivider.getOrDefault(biggest.ingredient, 0) + biggest.consumed);
					}
					biggest.max = Math.min(biggest.max, slot.getMaxStackSize());
					discovered.add(biggest);
				}
				if (discovered.isEmpty()) {
					return null;
				}

				List<DiscoveredItem> unique = Lists.newArrayList();
				outer:
				for (DiscoveredItem di : discovered) {
					if (di == null) {
						continue;
					}
					if (di.gtToolCatalyst()) {
						unique.add(new DiscoveredItem(di.ingredient, di.stack, di.amount, di.consumed, di.max, di.sourceSlotId));
						continue;
					}
					for (DiscoveredItem ui : unique) {
						if (!ui.gtToolCatalyst() && EmiCraftingToolCompat.matches(di.stack, ui.stack)) {
							ui.consumed += di.consumed;
							continue outer;
						}
					}
					unique.add(new DiscoveredItem(di.ingredient, di.stack, di.amount, di.consumed, di.max, di.sourceSlotId));
				}
				int maxAmount = Integer.MAX_VALUE;
				for (DiscoveredItem ui : unique) {
					if (ui.catalyst()) {
						long uses = EmiCraftingToolCompat.getSafeCraftingUses(ui.stack);
						if (uses >= 0L && uses < Long.MAX_VALUE) {
							maxAmount = Math.min(maxAmount, (int) Math.min(Integer.MAX_VALUE, uses));
						}
					} else {
						maxAmount = Math.min(maxAmount, ui.amount / ui.consumed);
						maxAmount = Math.min(maxAmount, ui.max);
					}
				}
				maxAmount = Math.min(maxAmount, amount + batchesAlreadyPresent(recipe, handler, screen));

				if (maxAmount == 0) {
					return null;
				}

				List<ItemStack> desired = Lists.newArrayList();
				for (int i = 0; i < discovered.size(); i++) {
					DiscoveredItem di = discovered.get(i);
					if (di != null) {
						ItemStack is = di.stack.copy();
						int a = di.catalyst() ? di.consumed : di.consumed * maxAmount;
						is.setCount(a);
						desired.add(is);
					} else {
						desired.add(ItemStack.EMPTY);
					}
				}
				return desired;
			}
		} catch (Exception e) {
			EmiLog.error("Error collecting stacks", e);
		}
		return null;
	}


	private static EmiStack matchingOption(EmiIngredient ingredient, EmiStack actual) {
		if (ingredient == null || actual == null || actual.isEmpty()) {
			return null;
		}
		for (EmiStack option : ingredient.getEmiStacks()) {
			if (option != null && !option.isEmpty() && EmiCraftingToolCompat.matches(option, actual)) {
				return option;
			}
		}
		return null;
	}

	public static <T extends AbstractContainerMenu> int batchesAlreadyPresent(EmiRecipe recipe, StandardRecipeHandler<T> handler, AbstractContainerScreen<T> screen) {
		List<EmiIngredient> inputs = recipe.getInputs();
		List<ItemStack> stacks = Lists.newArrayList();
		Slot output = handler.getOutputSlot(screen.getMenu());
		if (output != null && !output.getItem().isEmpty() && recipe.getOutputs().size() > 0
				&& !ItemStack.matches(output.getItem(), recipe.getOutputs().get(0).getItemStack())) {
			return 0;
		}
		for (Slot slot : handler.getCraftingSlots(recipe, screen.getMenu())) {
			if (slot != null) {
				stacks.add(slot.getItem());
			} else {
				stacks.add(ItemStack.EMPTY);
			}
		}
		long amount = Long.MAX_VALUE;
		outer:
		for (int i = 0; i < inputs.size(); i++) {
			EmiIngredient input = inputs.get(i);
			if (input.isEmpty()) {
				if (stacks.get(i).isEmpty()) {
					continue;
				}
				return 0;
			}
			if (i >= stacks.size()) {
				return 0;
			}
			EmiStack es = EmiStack.of(stacks.get(i));
			for (EmiStack v : input.getEmiStacks()) {
				if (v.isEmpty()) {
					continue;
				}
				if (EmiCraftingToolCompat.matches(v, es)) {
					if (EmiCraftingToolCompat.isReusable(v)) {
						continue outer;
					}
					if (es.getAmount() >= v.getAmount()) {
						amount = Math.min(amount, es.getAmount() / v.getAmount());
						continue outer;
					}
				}
			}
			return 0;
		}
		if (amount < Long.MAX_VALUE && amount > 0) {
			return (int) amount;
		}
		return 0;
	}

	public static <T extends AbstractContainerMenu> boolean clientFill(StandardRecipeHandler<T> handler, EmiRecipe recipe,
			AbstractContainerScreen<T> screen, List<ItemStack> stacks, EmiCraftContext.Destination destination) {
		T screenHandler = screen.getMenu();
		if (handler != null && screenHandler.getCarried().isEmpty()) {
			Minecraft client = Minecraft.getInstance();
			MultiPlayerGameMode manager = client.gameMode;
			Player player = client.player;
			List<Slot> clear = handler.getCraftingSlots(screenHandler);
			for (Slot slot : clear) {
				if (slot != null) {
					manager.handleInventoryMouseClick(screenHandler.containerId, slot.index, 0, ClickType.QUICK_MOVE, player);
				}
			}
			List<Slot> inputs = handler.getInputSources(screenHandler);
			List<Slot> slots = handler.getCraftingSlots(recipe, screenHandler);
			outer:
			for (int i = 0; i < stacks.size(); i++) {
				ItemStack stack = stacks.get(i);
				if (stack.isEmpty()) {
					continue;
				}
				if (i >= slots.size()) {
					return false;
				}
				Slot crafting = slots.get(i);
				if (crafting == null) {
					return false;
				}
				int needed = stack.getCount();
				for (int pass = 0; pass < 2 && needed > 0; pass++) {
					for (Slot input : inputs) {
						if (slots.contains(input)) {
							continue;
						}
						ItemStack is = input.getItem().copy();
						boolean exact = ItemStack.isSameItemSameTags(stack, is);
						if ((pass == 0 && !exact) || (pass == 1 && (exact || !EmiCraftingToolCompat.matches(stack, is)))) {
							continue;
						}
						manager.handleInventoryMouseClick(screenHandler.containerId, input.index, 0, ClickType.PICKUP, player);
						if (is.getCount() <= needed) {
							needed -= is.getCount();
							manager.handleInventoryMouseClick(screenHandler.containerId, crafting.index, 0, ClickType.PICKUP, player);
						} else {
							while (needed > 0) {
								manager.handleInventoryMouseClick(screenHandler.containerId, crafting.index, 1, ClickType.PICKUP, player);
								needed--;
							}
							manager.handleInventoryMouseClick(screenHandler.containerId, input.index, 0, ClickType.PICKUP, player);
						}
						if (needed == 0) {
							continue outer;
						}
					}
				}
				return false;
			}
			Slot slot = handler.getOutputSlot(screenHandler);
			if (slot != null) {
				if (destination == EmiCraftContext.Destination.CURSOR) {
					manager.handleInventoryMouseClick(screenHandler.containerId, slot.index, 0, ClickType.PICKUP, player);
				} else if (destination == EmiCraftContext.Destination.INVENTORY) {
					manager.handleInventoryMouseClick(screenHandler.containerId, slot.index, 0, ClickType.QUICK_MOVE, player);
				}
			}
			return true;
		}
		return false;
	}

	private static class DiscoveredItem {
		public EmiStack ingredient;
		public ItemStack stack;
		public int consumed;
		public int amount;
		public int max;
		public int sourceSlotId;

		public DiscoveredItem(EmiStack ingredient, ItemStack stack, int amount, int consumed, int max) {
			this(ingredient, stack, amount, consumed, max, -1);
		}

		public DiscoveredItem(EmiStack ingredient, ItemStack stack, int amount, int consumed, int max, int sourceSlotId) {
			this.ingredient = ingredient;
			this.stack = stack.copy();
			this.amount = amount;
			this.consumed = consumed;
			this.max = max;
			this.sourceSlotId = sourceSlotId;
		}

		public boolean catalyst() {
			return EmiCraftingToolCompat.isReusable(ingredient);
		}

		public boolean gtToolCatalyst() {
			return catalyst() && EmiCraftingToolCompat.isGtTool(stack);
		}
	}
}
