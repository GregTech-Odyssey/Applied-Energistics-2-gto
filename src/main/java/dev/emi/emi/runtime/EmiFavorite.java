package dev.emi.emi.runtime;

import com.google.common.collect.Lists;
import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.render.EmiTooltipComponents;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.FluidEmiStack;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.HelpLevel;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.screen.MicroTextRenderer;
import dev.emi.emi.screen.StackBatcher.Batchable;
import dev.emi.emi.screen.tooltip.RecipeTooltipComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EmiFavorite implements EmiIngredient, Batchable {
	public enum Role {
		ITEM,
		RESULT,
		INGREDIENT,
		CATALYST
	}

	protected EmiIngredient stack;
	protected @Nullable EmiRecipe recipe;
	protected final @Nullable ResourceLocation recipeId;
	protected final Role role;
	private long nextRecipeResolveAttemptNanos;

	public EmiFavorite(EmiIngredient stack, @Nullable EmiRecipe recipe) {
		this(stack, recipe, recipe == null ? Role.ITEM : Role.RESULT);
	}

	public EmiFavorite(EmiIngredient stack, @Nullable EmiRecipe recipe, Role role) {
		this(stack, recipe, recipe == null ? null : recipe.getId(), role);
	}

	EmiFavorite(EmiIngredient stack, @Nullable EmiRecipe recipe, @Nullable ResourceLocation recipeId, Role role) {
		this.recipe = recipe;
		this.recipeId = recipe != null && recipe.getId() != null ? recipe.getId() : recipeId;
		this.role = this.recipeId == null ? Role.ITEM : role;
		this.stack = this.role == Role.CATALYST ? stack.copy().setAmount(1) : stack;
	}

	public EmiIngredient getStack() {
		return stack;
	}

	public Role getRole() {
		return role;
	}

	@Override
	public EmiIngredient copy() {
		return new EmiFavorite(stack, getRecipe(), recipeId, role);
	}

	@Override
	public long getAmount() {
		return stack.getAmount();
	}

	@Override
	public EmiIngredient setAmount(long amount) {
		stack = stack.copy().setAmount(role == Role.CATALYST ? 1 : amount);
		return this;
	}

	@Override
	public float getChance() {
		return 1;
	}

	@Override
	public EmiIngredient setChance(float chance) {
		return this;
	}

	public @Nullable EmiRecipe getRecipe() {
		resolveRecipeReference();
		return recipe;
	}

	boolean resolveRecipeReference() {
		if (recipe != null || recipeId == null) {
			return false;
		}
		long now = System.nanoTime();
		if (now < nextRecipeResolveAttemptNanos) {
			return false;
		}
		nextRecipeResolveAttemptNanos = now + 250_000_000L;
		try {
			EmiRecipe resolved = EmiApi.getRecipeManager().getRecipe(recipeId);
			if (resolved != null) {
				recipe = resolved;
				return true;
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	boolean hasUnresolvedRecipeReference() {
		return recipe == null && recipeId != null;
	}

	public @Nullable ResourceLocation getRecipeId() {
		if (recipeId != null) {
			return recipeId;
		}
		return recipe == null ? null : recipe.getId();
	}

	@Override
	public List<EmiStack> getEmiStacks() {
		return stack.getEmiStacks();
	}

	@Override
	public void render(GuiGraphics raw, int x, int y, float delta, int flags) {
		dev.emi.emi.runtime.EmiDrawContext context = dev.emi.emi.runtime.EmiDrawContext.wrap(raw);
		EmiRecipe resolvedRecipe = getRecipe();
		boolean recipeFavorite = getRecipeId() != null;
		boolean grouped = recipeFavorite && EmiFavoriteGroups.groupFor(this) != null;
		int stackFlags = flags;
		boolean groupedFluid = grouped && !stack.getEmiStacks().isEmpty() && stack.getEmiStacks().get(0) instanceof FluidEmiStack;
		boolean compactGroupedItem = grouped && !groupedFluid && getAmount() >= 100;
		boolean zeroGrouped = grouped && getAmount() == 0L;
		if ((recipeFavorite && !grouped) || groupedFluid || compactGroupedItem || zeroGrouped) {
			stackFlags &= ~EmiIngredient.RENDER_AMOUNT;
		}
		stack.render(context.raw(), x, y, delta, stackFlags);
		if (grouped && getAmount() > 0L && (flags & EmiIngredient.RENDER_AMOUNT) != 0 && getAmount() != 1) {
			if (groupedFluid) {
				MicroTextRenderer.renderBookmarkFluidAmount(context, getAmount(), x, y);
			} else if (compactGroupedItem) {
				MicroTextRenderer.renderBookmarkItemAmount(context, getAmount(), x, y);
			}
		}
		if (recipeFavorite && !grouped && getAmount() != 1 && !stack.getEmiStacks().isEmpty()) {
			boolean volume = stack.getEmiStacks().get(0) instanceof FluidEmiStack;
			MicroTextRenderer.render(context, getAmount(), volume, 16, x + 17, y + 17);
		}
		if ((flags & EmiIngredient.RENDER_INGREDIENT) > 0 && recipeFavorite && role == Role.RESULT) {
			if (resolvedRecipe != null) {
				renderRecipeHandlerIcon(context, resolvedRecipe, x, y, delta);
			} else if (!grouped) {
				EmiRenderHelper.renderRecipeFavorite(stack, context, x, y);
			}
		}
	}

	private static void renderRecipeHandlerIcon(dev.emi.emi.runtime.EmiDrawContext context, EmiRecipe recipe, int x, int y, float delta) {
		if (recipe.getCategory() == null || recipe.getCategory() == VanillaEmiRecipeCategories.CRAFTING) {
			return;
		}
		float scale = 0.45f;
		int size = 8;
		int left = x + 16 - size;
		context.fill(left, y, size, size, 0xB0000000);
		context.push();
		context.resetColor();
		context.enableDepthTest();
		context.matrices().translate(x + 16 - 16 * scale, y, 410);
		context.matrices().scale(scale, scale, 1);
		recipe.getCategory().renderSimplified(context.raw(), 0, 0, delta);
		context.pop();
		context.resetColor();
	}

	@Override
	public List<ClientTooltipComponent> getTooltip() {
		List<ClientTooltipComponent> list = Lists.newArrayList();
		list.addAll(stack.getTooltip());
		EmiRecipe resolvedRecipe = getRecipe();
		if (resolvedRecipe != null && EmiFavoriteGroups.groupFor(this) == null) {
			list.add(new RecipeTooltipComponent(resolvedRecipe, true));
		}
		return list;
	}

	public boolean strictEquals(EmiIngredient other) {
		List<EmiStack> as = this.getEmiStacks();
		List<EmiStack> bs = other.getEmiStacks();
		if (as.size() != bs.size()) {
			return false;
		}
		for (int i = 0; i < as.size(); i++) {
			if (!as.get(i).isEqual(bs.get(i), EmiPort.compareStrict())) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof EmiIngredient ingredient && EmiIngredient.areEqual(this, ingredient);
	}

	@Override
	public boolean isSideLit() {
		return stack instanceof Batchable b && b.isSideLit();
	}

	@Override
	public boolean isUnbatchable() {
		return !(stack instanceof Batchable b) || b.isUnbatchable();
	}

	@Override
	public void setUnbatchable() {
		if (stack instanceof Batchable b) {
			b.setUnbatchable();
		}
	}

	@Override
	public void renderForBatch(MultiBufferSource vcp, GuiGraphics raw, int x, int y, int z, float delta) {
		if (stack instanceof Batchable b) {
			b.renderForBatch(vcp, raw, x, y, z, delta);
		}
	}

	public static class Craftable extends EmiFavorite {

		public Craftable(EmiRecipe recipe) {
			super(recipe.getOutputs().isEmpty() ? EmiStack.EMPTY : recipe.getOutputs().get(0), recipe, Role.RESULT);
		}

		@Override
		public void render(GuiGraphics raw, int x, int y, float delta, int flags) {
			super.render(raw, x, y, delta, flags & (~EmiIngredient.RENDER_INGREDIENT));
		}
	}

	public static class Synthetic extends EmiFavorite {
		public final long batches;
		public final long amount;
		public final int state;
		public final long total;

		public Synthetic(EmiRecipe recipe, long batches, long amount, long total, int state) {
			super(recipe.getOutputs().get(0), recipe, Role.RESULT);
			this.batches = batches;
			this.amount = amount;
			this.total = total;
			this.state = state;
		}

		public Synthetic(EmiIngredient ingredient, long needed, long total) {
			super(ingredient, null, Role.ITEM);
			this.batches = needed;
			this.amount = needed;
			this.total = total;
			this.state = -1;
		}

		@Override
		public void render(GuiGraphics raw, int x, int y, float delta, int flags) {
			dev.emi.emi.runtime.EmiDrawContext context = EmiDrawContext.wrap(raw);
			int color = 0x915900;
			if (state == 1) {
				color = 0x790091;
			} else if (state == 2) {
				color = 0x00918e;
			} else if (state == -1) {
				color = 0x911300;
			}
			stack.render(context.raw(), x, y, delta, flags & (~EmiIngredient.RENDER_AMOUNT));
			MicroTextRenderer.render(context, amount, stack.getEmiStacks().get(0) instanceof FluidEmiStack, 18, x + 17, y + 17, color);
		}

		@Override
		public List<ClientTooltipComponent> getTooltip() {
			List<ClientTooltipComponent> list = Lists.newArrayList();
			list.addAll(super.getTooltip());

			long diff = total - amount;
			list.add(EmiTooltipComponents.of(EmiPort.translatable("tooltip.emi.synfav.remaining", EmiRenderHelper.getAmountText(stack, amount)).withStyle(ChatFormatting.GRAY)));
			list.add(EmiTooltipComponents.of(EmiPort.translatable("tooltip.emi.synfav.obtained", EmiRenderHelper.getAmountText(stack, diff), EmiRenderHelper.getAmountText(stack, total)).withStyle(ChatFormatting.GRAY)));
			if (batches != amount) {
				list.add(EmiTooltipComponents.of(EmiPort.translatable("tooltip.emi.synfav.batches_remaining", batches).withStyle(ChatFormatting.GRAY)));
			}

			if (state == -1) {
				return list;
			}

			Component craftKey = null;

			if (EmiConfig.helpLevel.has(HelpLevel.NORMAL) && EmiRecipeFiller.getFirstValidHandler(recipe, EmiApi.getHandledScreen()) != null) {
				if (EmiConfig.craftAllToInventory.isBound()) {
					craftKey = EmiConfig.craftAllToInventory.getBindText();
				} else if (EmiConfig.craftAll.isBound()) {
					craftKey = EmiConfig.craftAll.getBindText();
				}
			}
			if (state == 0) {
				list.add(ClientTooltipComponent.create(EmiPort.translatable("tooltip.emi.synfav.uncraftable").getVisualOrderText()));
			} else if (state == 1) {
				list.add(ClientTooltipComponent.create(EmiPort.translatable("tooltip.emi.synfav.partially_craftable").getVisualOrderText()));
				if (craftKey != null) {
					list.add(ClientTooltipComponent.create(EmiPort.translatable("tooltip.emi.synfav.craft_some", craftKey).getVisualOrderText()));
				}
			} else if (state == 2) {
				list.add(ClientTooltipComponent.create(EmiPort.translatable("tooltip.emi.synfav.fully_craftable", batches).getVisualOrderText()));
				if (craftKey != null) {
					list.add(ClientTooltipComponent.create(EmiPort.translatable("tooltip.emi.synfav.craft_all", craftKey, batches).getVisualOrderText()));
				}
			}
			return list;
		}

		@Override
		public boolean isUnbatchable() {
			return true;
		}
	}
}
