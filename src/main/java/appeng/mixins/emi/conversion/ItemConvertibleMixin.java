package appeng.mixins.emi.conversion;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackConvertible;

@Mixin(ItemLike.class)
public interface ItemConvertibleMixin extends EmiStackConvertible {

    @Override
    default EmiStack emi() {
        return EmiStack.of((Item) (Object) this);
    }

    @Override
    default EmiStack emi(long amount) {
        return EmiStack.of((Item) (Object) this, amount);
    }
}
