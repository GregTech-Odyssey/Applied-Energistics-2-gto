package dev.emi.emi.api.stack;

import dev.emi.emi.api.stack.EmiStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.ApiStatus;

/**
 * Represents a type that can be implicitly converted into an EmiStack.
 */
@ApiStatus.Experimental
public interface EmiStackConvertible {
    
    /**
     * @return The default representation of this type as an EmiStack.
     *  If the type has an amount of its own, it will be used.
     *  Otherwise, it'll use the default amount for the stack type.
     */
    @OnlyIn(Dist.CLIENT)
    @ApiStatus.Experimental
    default EmiStack emi() {
        throw new IllegalStateException();
    }

    /**
     * @return The default representation of this type as an EmiStack.
     *  Uses the provided amount.
     */
    @OnlyIn(Dist.CLIENT)
    @ApiStatus.Experimental
    default EmiStack emi(long amount) {
        throw new IllegalStateException();
    }
}
