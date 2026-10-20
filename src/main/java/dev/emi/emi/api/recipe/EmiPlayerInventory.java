package dev.emi.emi.api.recipe;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.registry.EmiStackList;
import dev.emi.emi.runtime.EmiCraftingToolCompat;
import dev.emi.emi.runtime.EmiFavorite;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings({"unchecked", "rawtypes"})
public class EmiPlayerInventory {
	private final Comparison none = Comparison.DEFAULT_COMPARISON;
	private final Comparison strict = EmiPort.compareStrict();
	public Map<EmiStack, EmiStack> inventory = Maps.newHashMap();
	
	@Deprecated
	@ApiStatus.Internal
	public EmiPlayerInventory(Player entity) {
		AbstractContainerScreen<?> screen = EmiApi.getHandledScreen();
		if (screen != null && screen.getMenu() != null) {
			if (screen.getMenu().getCarried() != null) {
				addStack(screen.getMenu().getCarried());
			}
			List<EmiRecipeHandler<?>> handlers = (List) EmiRecipeFiller.getAllHandlers(screen);
			if (!handlers.isEmpty()) {
				if (handlers.get(0) instanceof StandardRecipeHandler standard) {
					List<Slot> slots = standard.getInputSources(screen.getMenu());
					for (Slot slot : slots) {
						if (slot.mayPickup(entity)) {
							addStack(slot.getItem());
						}
					}
					return;
				}
			}
		}

		Inventory pInv = entity.getInventory();
		for (int i = 0; i < pInv.items.size(); i++) {
			addStack(pInv.items.get(i));
		}
	}

	public EmiPlayerInventory(List<EmiStack> stacks) {
		for (EmiStack stack : stacks) {
			addStack(stack);
		}
		AbstractContainerScreen<?> screen = EmiApi.getHandledScreen();
		if (screen != null && screen.getMenu() != null) {
			if (screen.getMenu().getCarried() != null) {
				addStack(screen.getMenu().getCarried());
			}
		}
	}

	public static EmiPlayerInventory of(Player entity) {
		AbstractContainerScreen<?> screen = EmiApi.getHandledScreen();
		if (screen != null) {
			List<EmiRecipeHandler<?>> handlers = (List) EmiRecipeFiller.getAllHandlers(screen);
			if (!handlers.isEmpty()) {
				return handlers.get(0).getInventory((AbstractContainerScreen) screen);
			}
		}
		if (entity == null) {
			return new EmiPlayerInventory(List.of());
		}
		return new EmiPlayerInventory(entity);
	}

	private void addStack(ItemStack is) {
		EmiStack stack = EmiStack.of(is).comparison(c -> EmiCraftingToolCompat.isGtTool(is) ? strict : none);
		addStack(stack);
	}

	private void addStack(EmiStack stack) {
		if (!stack.isEmpty()) {
			if (EmiCraftingToolCompat.isGtTool(stack)) {
				stack = stack.copy().comparison(c -> strict);
			}
			inventory.merge(stack, stack, (a, b) -> a.setAmount(a.getAmount() + b.getAmount()));
		}
	}

	public Predicate<dev.emi.emi.api.recipe.EmiRecipe> getPredicate() {
		AbstractContainerScreen screen = EmiApi.getHandledScreen();
		List<EmiRecipeHandler> handlers = EmiRecipeFiller.getAllHandlers(screen);
		if (!handlers.isEmpty()) {
			EmiCraftContext context = new EmiCraftContext(screen, this, EmiCraftContext.Type.CRAFTABLE);
			return r -> {
				for (int i = 0; i < handlers.size(); i++) {
					EmiRecipeHandler handler = handlers.get(i);
					if (handler.supportsRecipe(r)) {
						return handler.canCraft(r, context);
					}
				}
				return false;
			};
		}
		return null;
	}

	public List<EmiIngredient> getCraftables() {
		Predicate<dev.emi.emi.api.recipe.EmiRecipe> predicate = getPredicate();
		if (predicate == null) {
			return List.of();
		}
		Set<dev.emi.emi.api.recipe.EmiRecipe> set = Sets.newHashSet();
		for (EmiStack stack : inventory.keySet()) {
			set.addAll(EmiApi.getRecipeManager().getRecipesByInput(stack));
		}
		return set.stream().filter(r -> !r.hideCraftable() && predicate.test(r) && r.getOutputs().size() > 0)
			.map(r -> new EmiFavorite.Craftable(r))
			.sorted((a, b) -> {
				int i = Integer.compare(
					EmiStackList.getIndex(a.getStack()),
					EmiStackList.getIndex(b.getStack()));
				if (i != 0) {
					return i;
				}
				return Long.compare(a.getAmount(), b.getAmount());
			}).collect(Collectors.toList());
	}

	public List<Boolean> getCraftAvailability(dev.emi.emi.api.recipe.EmiRecipe recipe) {
		Object2LongMap<EmiStack> used = new Object2LongOpenHashMap<>();
		Map<EmiStack, Long> reservedReusable = new IdentityHashMap<>();
		List<Boolean> states = Lists.newArrayList();
		for (EmiIngredient ingredient : recipe.getInputs()) {
			if (ingredient == null || ingredient.isEmpty()) {
				states.add(true);
				continue;
			}
			states.add(reserveIngredient(ingredient, 1L, used, reservedReusable));
		}
		return states;
	}

	public boolean canCraft(dev.emi.emi.api.recipe.EmiRecipe recipe) {
		return canCraft(recipe, 1);
	}

	public boolean canCraft(EmiRecipe recipe, long amount) {
		Object2LongMap<EmiStack> used = new Object2LongOpenHashMap<>();
		Map<EmiStack, Long> reservedReusable = new IdentityHashMap<>();
		for (EmiIngredient ingredient : recipe.getInputs()) {
			if (ingredient == null || ingredient.isEmpty()) {
				continue;
			}
			if (!reserveIngredient(ingredient, Math.max(1L, amount), used, reservedReusable)) {
				return false;
			}
		}
		return true;
	}

	private boolean reserveIngredient(EmiIngredient ingredient, long crafts, Object2LongMap<EmiStack> used,
			Map<EmiStack, Long> reservedReusable) {
		for (EmiStack stack : ingredient.getEmiStacks()) {
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			boolean reusable = EmiCraftingToolCompat.isReusable(stack);
			if (reusable) {
				long requiredInstances = Math.max(1L, stack.getAmount());
				EmiStack identity = findReusableCandidate(stack, crafts, requiredInstances, reservedReusable);
				if (identity != null) {
					reservedReusable.put(identity,
						safeAdd(reservedReusable.getOrDefault(identity, 0L), requiredInstances));
					return true;
				}
				continue;
			}
			long desired = safeMultiply(Math.max(1L, stack.getAmount()), crafts);
			EmiStack identity = findMatching(stack);
			if (identity == null) {
				continue;
			}
			long alreadyUsed = used.getOrDefault(identity, 0L);
			long available = identity.getAmount() - alreadyUsed;
			if (available >= desired) {
				used.put(identity, desired + alreadyUsed);
				return true;
			}
		}
		return false;
	}

	private EmiStack findReusableCandidate(EmiStack expected, long crafts, long requiredInstances,
			Map<EmiStack, Long> reservedReusable) {
		EmiStack best = null;
		long bestUses = -1L;
		for (EmiStack candidate : inventory.values()) {
			if (candidate == null || candidate.isEmpty() || !EmiCraftingToolCompat.matches(expected, candidate)) {
				continue;
			}
			long reserved = reservedReusable.getOrDefault(candidate, 0L);
			if (candidate.getAmount() - reserved < requiredInstances) {
				continue;
			}
			if (EmiCraftingToolCompat.isGtTool(candidate)) {
				long uses = EmiCraftingToolCompat.getSafeCraftingUses(candidate);
				if (uses < crafts) {
					continue;
				}
				if (best == null || uses > bestUses) {
					best = candidate;
					bestUses = uses;
				}
			} else if (best == null) {
				best = candidate;
			}
		}
		return best;
	}

	private static long safeMultiply(long a, long b) {
		if (a <= 0L || b <= 0L) {
			return 0L;
		}
		if (a > Long.MAX_VALUE / b) {
			return Long.MAX_VALUE;
		}
		return a * b;
	}

	private static long safeAdd(long a, long b) {
		if (b > 0L && a > Long.MAX_VALUE - b) {
			return Long.MAX_VALUE;
		}
		return a + b;
	}

	private EmiStack findMatching(EmiStack stack) {
		EmiStack identity = inventory.get(stack);
		if (identity != null) {
			return identity;
		}
		if (!EmiCraftingToolCompat.isGtTool(stack)) {
			return null;
		}
		EmiStack best = null;
		long bestUses = -1L;
		for (EmiStack candidate : inventory.values()) {
			if (!EmiCraftingToolCompat.matches(stack, candidate)) {
				continue;
			}
			long uses = EmiCraftingToolCompat.getSafeCraftingUses(candidate);
			if (best == null || uses > bestUses) {
				best = candidate;
				bestUses = uses;
			}
		}
		return best;
	}

	private long getReusableCraftingUses(EmiStack stack) {
		long total = 0L;
		for (EmiStack candidate : inventory.values()) {
			if (!EmiCraftingToolCompat.matches(stack, candidate)) {
				continue;
			}
			long uses = EmiCraftingToolCompat.getSafeCraftingUses(candidate);
			if (uses == Long.MAX_VALUE) {
				return Long.MAX_VALUE;
			}
			if (uses > 0L) {
				if (total > Long.MAX_VALUE - uses) {
					return Long.MAX_VALUE;
				}
				total += uses;
			}
		}
		return total;
	}

	public boolean isEqual(EmiPlayerInventory other) {
		if (other == null) {
			return false;
		}
		Comparison comparison = Comparison.of((a, b) -> {
			return strict.compare(a, b) && a.getAmount() == b.getAmount();
		});
		if (other.inventory.size() != inventory.size()) {
			return false;
		} else {
			for (EmiStack stack : inventory.keySet()) {
				if (!other.inventory.containsKey(stack) || !other.inventory.get(stack).isEqual(stack, comparison)) {
					return false;
				}
			}
		}
		return true;
	}
}
