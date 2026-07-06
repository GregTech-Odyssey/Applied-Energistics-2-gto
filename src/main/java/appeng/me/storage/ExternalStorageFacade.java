package appeng.me.storage;

import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.primitives.Ints;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.core.AELog;
import appeng.core.localization.GuiText;

/**
 * Adapts external platform storage to behave like an {@link MEStorage}.
 */
public abstract class ExternalStorageFacade implements MEStorage {

    @Nullable
    private Runnable changeListener;

    public void setChangeListener(@Nullable Runnable listener) {
        this.changeListener = listener;
    }

    public void setBlockEntity(@Nullable BlockEntity blockEntity) {

    }

    public abstract int getSlots();

    @Nullable
    public abstract GenericStack getStackInSlot(int slot);

    public abstract AEKeyType getKeyType();

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        var inserted = insertExternal(what, Ints.saturatedCast(amount), mode);
        if (inserted > 0 && mode == Actionable.MODULATE) {
            if (this.changeListener != null) {
                this.changeListener.run();
            }
        }
        return inserted;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        var extracted = extractExternal(what, Ints.saturatedCast(amount), mode);
        if (extracted > 0 && mode == Actionable.MODULATE) {
            if (this.changeListener != null) {
                this.changeListener.run();
            }
        }
        return extracted;
    }

    @Override
    public Component getDescription() {
        return GuiText.ExternalStorage.text(AEKeyType.fluids().getDescription());
    }

    protected abstract int insertExternal(AEKey what, int amount, Actionable mode);

    protected abstract int extractExternal(AEKey what, int amount, Actionable mode);

    public abstract boolean containsAnyFuzzy(Set<AEKey> keys);

    public static ExternalStorageFacade of(IFluidHandler handler) {
        return handler instanceof MEStorageFluidHandler meStorageFluidHandler
                ? new FluidHandlerMEFacade(meStorageFluidHandler)
                : new FluidHandlerFacade(handler);
    }

    public static ExternalStorageFacade of(IItemHandler handler) {
        return handler instanceof MEStorageItemHandler meStorageItemHandler
                ? new ItemHandlerMEFacade(meStorageItemHandler)
                : new ItemHandlerFacade(handler);
    }

    public void setExtractableOnly(boolean extractableOnly) {

    }

    public interface MEStorageItemHandler {
        int insertExternal(AEItemKey what, int amount, Actionable mode);

        int extractExternal(AEItemKey what, int amount, Actionable mode);

    }

    public interface MEStorageFluidHandler {
        int insertExternal(AEFluidKey what, int amount, Actionable mode);

        int extractExternal(AEFluidKey what, int amount, Actionable mode);

    }

    private final static class ItemHandlerMEFacade extends ItemHandlerFacade {

        private final MEStorageItemHandler storageHandler;

        public ItemHandlerMEFacade(MEStorageItemHandler handler) {
            super((IItemHandler) handler);
            this.storageHandler = handler;
        }

        @Override
        public int insertExternal(AEKey what, int amount, Actionable mode) {
            if (!(what instanceof AEItemKey itemKey)) {
                return 0;
            }
            return storageHandler.insertExternal(itemKey, amount, mode);
        }

        @Override
        public int extractExternal(AEKey what, int amount, Actionable mode) {
            if (!(what instanceof AEItemKey itemKey)) {
                return 0;
            }
            return storageHandler.extractExternal(itemKey, amount, mode);
        }
    }

    private static class ItemHandlerFacade extends ExternalStorageFacade {
        private final IItemHandler handler;

        public ItemHandlerFacade(IItemHandler handler) {
            this.handler = handler;
        }

        @Override
        public final int getSlots() {
            return handler.getSlots();
        }

        @Nullable
        @Override
        public final GenericStack getStackInSlot(int slot) {
            return GenericStack.fromItemStack(handler.getStackInSlot(slot));
        }

        @Override
        public final AEKeyType getKeyType() {
            return AEKeyType.items();
        }

        @Override
        public int insertExternal(AEKey what, int amount, Actionable mode) {
            if (!(what instanceof AEItemKey itemKey)) {
                return 0;
            }

            ItemStack orgInput = itemKey.toStack(amount);
            ItemStack remaining = orgInput;

            int slotCount = handler.getSlots();
            boolean simulate = mode == Actionable.SIMULATE;

            // This uses a brute force approach and tries to jam it in every slot the inventory exposes.
            for (int i = 0; i < slotCount && !remaining.isEmpty(); i++) {
                remaining = handler.insertItem(i, remaining, simulate);
            }

            // At this point, we still have some items left...
            if (remaining == orgInput) {
                // The stack remained unmodified, target inventory is full
                return 0;
            }

            return amount - remaining.getCount();
        }

        @Override
        public int extractExternal(AEKey what, int amount, Actionable mode) {
            if (!(what instanceof AEItemKey itemKey)) {
                return 0;
            }

            int totalExtracted = 0;

            for (int i = 0; i < handler.getSlots(); i++) {
                int extracted = extractFromHandler(handler, i, itemKey, amount - totalExtracted, mode);
                totalExtracted += extracted;

                // Done?
                if (amount == totalExtracted) {
                    break;
                }
            }

            return totalExtracted;
        }

        /**
         * Extracts as much as possible from a single slot of an item handler, ignoring the usual max stack size
         * restriction.
         */
        private static int extractFromHandler(IItemHandler handler, int slot, AEItemKey itemKey, int maxExtract,
                Actionable actionable) {
            ItemStack stackInInventorySlot = handler.getStackInSlot(slot);
            if (!itemKey.matches(stackInInventorySlot)) {
                return 0;
            }

            return switch (actionable) {
                case SIMULATE -> {
                    // Query amount before the stack potentially gets modified
                    int amountInSlot = stackInInventorySlot.getCount();

                    int extracted = wrapHandlerExtract(handler, slot, maxExtract, true);
                    // Heuristic for simulation: looping in case of simulations is pointless, since the state of the
                    // underlying inventory does not change after a simulated extraction. To still support
                    // inventories that report stacks that are larger than maxStackSize, we use this heuristic
                    if (extracted == itemKey.getMaxStackSize() && maxExtract > itemKey.getMaxStackSize()
                            && amountInSlot > itemKey.getMaxStackSize()) {
                        yield Math.min(amountInSlot, maxExtract);
                    } else {
                        yield extracted;
                    }
                }
                case MODULATE -> {
                    // We have to loop here because according to the docs, the handler shouldn't return a stack with
                    // size > maxSize, even if we request more. So even if it returns a valid stack, it might have more
                    // stuff.
                    int totalExtracted = 0;
                    while (true) {
                        int extracted = wrapHandlerExtract(handler, slot, maxExtract - totalExtracted, false);
                        if (extracted > 0) {
                            totalExtracted += extracted;
                        } else {
                            break;
                        }
                    }
                    yield totalExtracted;
                }
            };
        }

        /**
         * Guards {@link IItemHandler#extractItem(int, int, boolean)} to make sure that we don't extract more than
         * requested.
         */
        private static int wrapHandlerExtract(IItemHandler handler, int slot, int maxExtract, boolean simulate) {
            int extracted = handler.extractItem(slot, maxExtract, simulate).getCount();
            if (extracted > maxExtract) {
                // Something broke. It should never return more than we requested...
                // We're going to silently eat the remainder
                AELog.warn(
                        "Mod that provided item handler %s is broken. Returned %d items while only requesting %d.",
                        handler.getClass().getName(), extracted, maxExtract);
                return maxExtract;
            } else {
                return extracted;
            }
        }

        @Override
        public final boolean containsAnyFuzzy(Set<AEKey> keys) {
            var slots = handler.getSlots();
            for (int i = 0; i < slots; i++) {
                var stack = handler.getStackInSlot(i);
                var item = stack.getItem();
                if (item == Items.AIR)
                    continue;
                if (keys.contains(AEItemKey.of(item)))
                    return true;
            }
            return false;
        }

        @Override
        public final void getAvailableStacks(KeyCounter out) {
            var slots = handler.getSlots();
            for (int i = 0; i < slots; i++) {
                var stack = handler.getStackInSlot(i);
                var count = stack.getCount();
                if (count < 1)
                    continue;
                out.add(AEItemKey.of(stack), count);
            }
        }
    }

    private final static class FluidHandlerMEFacade extends FluidHandlerFacade {

        private final MEStorageFluidHandler storageHandler;

        public FluidHandlerMEFacade(MEStorageFluidHandler handler) {
            super((IFluidHandler) handler);
            this.storageHandler = handler;
        }

        @Override
        public int insertExternal(AEKey what, int amount, Actionable mode) {
            if (!(what instanceof AEFluidKey fluidKey)) {
                return 0;
            }
            return storageHandler.insertExternal(fluidKey, amount, mode);
        }

        @Override
        public int extractExternal(AEKey what, int amount, Actionable mode) {
            if (!(what instanceof AEFluidKey fluidKey)) {
                return 0;
            }
            return storageHandler.extractExternal(fluidKey, amount, mode);
        }
    }

    private static class FluidHandlerFacade extends ExternalStorageFacade {
        private final IFluidHandler handler;

        public FluidHandlerFacade(IFluidHandler handler) {
            this.handler = handler;
        }

        @Override
        public final int getSlots() {
            return handler.getTanks();
        }

        @Nullable
        @Override
        public final GenericStack getStackInSlot(int slot) {
            return GenericStack.fromFluidStack(handler.getFluidInTank(slot));
        }

        @Override
        public final AEKeyType getKeyType() {
            return AEKeyType.fluids();
        }

        @Override
        protected int insertExternal(AEKey what, int amount, Actionable mode) {
            if (!(what instanceof AEFluidKey fluidKey)) {
                return 0;
            }

            return handler.fill(fluidKey.toStack(amount), mode.getFluidAction());
        }

        @Override
        public int extractExternal(AEKey what, int amount, Actionable mode) {
            if (!(what instanceof AEFluidKey fluidKey)) {
                return 0;
            }

            var fluidStack = fluidKey.toStack(amount);

            // Drain the fluid from the tank
            FluidStack gathered = handler.drain(fluidStack, mode.getFluidAction());
            if (gathered.isEmpty()) {
                // If nothing was pulled from the tank, return null
                return 0;
            }

            return gathered.getAmount();
        }

        @Override
        public final boolean containsAnyFuzzy(Set<AEKey> keys) {
            var tanks = handler.getTanks();
            for (int i = 0; i < tanks; i++) {
                var stack = handler.getFluidInTank(i);
                var fluid = stack.getFluid();
                if (fluid == Fluids.EMPTY)
                    continue;
                if (keys.contains(AEFluidKey.of(fluid)))
                    return true;
            }
            return false;
        }

        @Override
        public final void getAvailableStacks(KeyCounter out) {
            var tanks = handler.getTanks();
            for (int i = 0; i < tanks; i++) {
                var stack = handler.getFluidInTank(i);
                var amount = stack.getAmount();
                if (amount < 1)
                    continue;
                out.add(AEFluidKey.of(stack), amount);
            }
        }
    }
}
