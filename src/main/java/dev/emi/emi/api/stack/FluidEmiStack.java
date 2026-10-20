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
import appeng.api.stacks.AEFluidKey;

@ApiStatus.Internal
public class FluidEmiStack extends dev.emi.emi.api.stack.EmiStack {
    private final AEFluidKey fluid;

    public FluidEmiStack(Fluid fluid) {
        this.fluid = AEFluidKey.of(fluid);
        this.amount = 0;
    }

    public FluidEmiStack(Fluid fluid, @Nullable CompoundTag nbt) {
        this(fluid, nbt, 0);
    }

    public FluidEmiStack(Fluid fluid, @Nullable CompoundTag nbt, long amount) {
        this.fluid = AEFluidKey.of(fluid, nbt);
        this.amount = amount;
    }

    public FluidEmiStack(AEFluidKey fluid, long amount) {
        this.fluid = fluid;
        this.amount = amount;
    }

    @Override
    public EmiStack copy() {
        EmiStack e = new FluidEmiStack(fluid, amount);
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
        return fluid.getTag();
    }

    @Override
    public Object getKey() {
        return fluid.getFluid();
    }

    @Override
    public AEFluidKey getAEKey() {
        return fluid;
    }

    @Override
    public ResourceLocation getId() {
        return fluid.getId();
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
        return EmiForgePlatform.getFluidTooltip(fluid.getFluid(), getNbt());
    }

    @Override
    public List<ClientTooltipComponent> getTooltip() {
        List<ClientTooltipComponent> list = Lists.newArrayList();
        List<Component> text = getTooltipText();
        if (!text.isEmpty()) {
            list.add(new EmiTextTooltipWrapper(this, EmiPort.ordered(text.get(0))));
        }
        for (int i = 1; i < text.size(); i++)
            list.add(EmiTooltipComponents.of(text.get(i)));
        if (amount > 1) {
            list.add(EmiTooltipComponents.getAmount(this));
        }
        String namespace = EmiPort.getFluidRegistry().getKey(fluid.getFluid()).getNamespace();
        EmiTooltipComponents.appendModName(list, namespace);
        list.addAll(super.getTooltip());
        return list;
    }

    @Override
    public Component getName() {
        return EmiForgePlatform.getFluidName(fluid.getFluid(), getNbt());
    }

    static class FluidEntry {
    }
}
