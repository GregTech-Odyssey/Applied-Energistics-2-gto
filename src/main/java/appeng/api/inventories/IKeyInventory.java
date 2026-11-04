package appeng.api.inventories;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.stacks.KeyVisitor;
import appeng.api.storage.MEStorage;
import appeng.me.storage.ExternalStorageFacade;

public interface IKeyInventory extends MEStorage, ExternalStorageFacade.DirectKeyHandler {

    @Override
    default long insertKey(AEKey what, long amount, Actionable mode) {
        return this.insert(what, amount, mode, IActionSource.empty());
    }

    @Override
    default long extractKey(AEKey what, long amount, Actionable mode) {
        return this.extract(what, amount, mode, IActionSource.empty());
    }

    @Override
    default void getAvailableStacks(KeyCounter out) {
        int size = getKeySlots();
        for (int i = 0; i < size; i++) {
            long a = getAmountInSlot(i);
            if (a > 0) {
                out.add(getKeyInSlot(i), a);
            }
        }
    }

    @Override
    default boolean forEachKey(KeyVisitor visitor) {
        int size = getKeySlots();
        for (int i = 0; i < size; i++) {
            long a = getAmountInSlot(i);
            if (a > 0) {
                if (visitor.visit(getKeyInSlot(i), a)) {
                    return true;
                }
            }
        }
        return false;
    }

    default boolean isEmpty() {
        int size = getKeySlots();
        for (int i = 0; i < size; i++) {
            if (getAmountInSlot(i) > 0)
                return false;
        }
        return true;
    }
}
