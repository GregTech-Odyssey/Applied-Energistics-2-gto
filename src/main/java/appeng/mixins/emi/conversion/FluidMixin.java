package appeng.mixins.emi.conversion;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.world.level.material.Fluid;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackConvertible;

@Mixin(Fluid.class)
public class FluidMixin implements EmiStackConvertible {

    @Override
    public EmiStack emi() {
        return EmiStack.of((Fluid) (Object) this);
    }

    @Override
    public EmiStack emi(long amount) {
        return EmiStack.of((Fluid) (Object) this, amount);
    }
}
