package appeng.api.inventories;

import org.jetbrains.annotations.NotNull;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.NonNullSupplier;
import net.minecraftforge.items.IItemHandler;

public class TrashItemHandler implements IItemHandler {

    public static final IItemHandler INSTANCE = new TrashItemHandler();

    public static final NonNullSupplier<IItemHandler> SUPPLIER = () -> INSTANCE;

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int i) {
        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull ItemStack insertItem(int i, @NotNull ItemStack itemStack, boolean b) {
        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull ItemStack extractItem(int i, int i1, boolean b) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int i) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isItemValid(int i, @NotNull ItemStack itemStack) {
        return true;
    }
}
