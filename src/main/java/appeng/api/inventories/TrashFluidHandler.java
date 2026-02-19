package appeng.api.inventories;

import org.jetbrains.annotations.NotNull;

import net.minecraftforge.common.util.NonNullSupplier;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class TrashFluidHandler implements IFluidHandler {

    public static final IFluidHandler INSTANCE = new TrashFluidHandler();

    public static final NonNullSupplier<IFluidHandler> SUPPLIER = () -> INSTANCE;

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int i) {
        return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int i) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isFluidValid(int i, @NotNull FluidStack fluidStack) {
        return true;
    }

    @Override
    public int fill(FluidStack fluidStack, FluidAction fluidAction) {
        return fluidStack.getAmount();
    }

    @Override
    public @NotNull FluidStack drain(FluidStack fluidStack, FluidAction fluidAction) {
        return FluidStack.EMPTY;
    }

    @Override
    public @NotNull FluidStack drain(int i, FluidAction fluidAction) {
        return FluidStack.EMPTY;
    }
}
