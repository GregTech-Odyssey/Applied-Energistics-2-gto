package appeng.mixins.emi.conversion;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.world.item.ItemStack;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackConvertible;

@Mixin(ItemStack.class)
public class ItemStackMixin implements EmiStackConvertible {

    @Override
    public EmiStack emi() {
        return EmiStack.of((ItemStack) (Object) this);
    }

    @Override
    public EmiStack emi(long amount) {
        return EmiStack.of((ItemStack) (Object) this, amount);
    }
}
