package dev.emi.emi.api.stack;

import com.google.common.collect.Lists;
import dev.emi.emi.EmiPort;
import dev.emi.emi.api.render.EmiRender;
import dev.emi.emi.api.render.EmiTooltipComponents;
import dev.emi.emi.forge.EmiForgePlatform;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.tooltip.EmiTextTooltipWrapper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

@ApiStatus.Internal
public class FluidEmiStack extends dev.emi.emi.api.stack.EmiStack {
	private final Fluid fluid;
	private final CompoundTag nbt;

	public FluidEmiStack(Fluid fluid) {
		this(fluid, null);
	}

	public FluidEmiStack(Fluid fluid, @Nullable CompoundTag nbt) {
		this(fluid, nbt, 0);
	}

	public FluidEmiStack(Fluid fluid, @Nullable CompoundTag nbt, long amount) {
		this.fluid = fluid;
		this.nbt = nbt;
		this.amount = amount;
	}

	@Override
	public dev.emi.emi.api.stack.EmiStack copy() {
		EmiStack e = new FluidEmiStack(fluid, nbt, amount);
		e.setChance(chance);
		e.setRemainder(getRemainder().copy());
		e.comparison = comparison;
		return e;
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public CompoundTag getNbt() {
		return nbt;
	}

	@Override
	public Object getKey() {
		return fluid;
	}

	@Override
	public ResourceLocation getId() {
		return EmiPort.getFluidRegistry().getKey(fluid);
	}

	@Override
	public void render(GuiGraphics raw, int x, int y, float delta, int flags) {
		EmiDrawContext context = EmiDrawContext.wrap(raw);
		if ((flags & RENDER_ICON) != 0) {
			context.push();
			context.matrices().translate(0, 0, 100);
			EmiForgePlatform.renderFluid(this, context.matrices(), x, y, delta);
			context.pop();
		}
		if ((flags & RENDER_REMAINDER) != 0) {
			EmiRender.renderRemainderIcon(this, context.raw(), x, y);
		}
	}

	@Override
	public List<Component> getTooltipText() {
		return EmiForgePlatform.getFluidTooltip(fluid, nbt);
	}

	@Override
	public List<ClientTooltipComponent> getTooltip() {
		List<ClientTooltipComponent> list = Lists.newArrayList();
		List<Component> text = getTooltipText();
		if (!text.isEmpty()) {
			list.add(new EmiTextTooltipWrapper(this, EmiPort.ordered(text.get(0))));
		}
		list.addAll(text.stream().skip(1).map(EmiTooltipComponents::of).collect(Collectors.toList()));
		if (amount > 1) {
			list.add(EmiTooltipComponents.getAmount(this));
		}
		String namespace = EmiPort.getFluidRegistry().getKey(fluid).getNamespace();
		EmiTooltipComponents.appendModName(list, namespace);
		list.addAll(super.getTooltip());
		return list;
	}

	@Override
	public Component getName() {
		return EmiForgePlatform.getFluidName(fluid, nbt);
	}

	static class FluidEntry {
	}
}
