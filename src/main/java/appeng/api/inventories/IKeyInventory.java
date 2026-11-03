package appeng.api.inventories;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

public interface IKeyInventory extends MEStorage {

    /**
     * Number of addressable slots, including empty slots. This is not the number of stored entries, so
     * {@code size() == 0} is not equivalent to {@link #isEmpty()}.
     */
    int size();

    @Nullable
    AEKey rawKeyAt(int slot);

    long amountAt(int slot);

    long insertRawKey(int slot, AEKey key, long amount, boolean simulate);

    long extractRawKey(int slot, AEKey key, long amount, boolean simulate);

    default long insertRawKey(AEKey key, long amount, boolean simulate) {
        long left = amount;
        int size = size();
        for (int i = 0; i < size && left > 0; i++) {
            if (amountAt(i) > 0 && rawKeyAt(i) == key)
                left -= insertRawKey(i, key, left, simulate);
        }
        for (int i = 0; i < size && left > 0; i++) {
            if (amountAt(i) == 0)
                left -= insertRawKey(i, key, left, simulate);
        }
        return amount - left;
    }

    default long extractRawKey(AEKey key, long amount, boolean simulate) {
        long left = amount;
        int size = size();
        for (int i = 0; i < size && left > 0; i++) {
            if (amountAt(i) > 0 && rawKeyAt(i) == key)
                left -= extractRawKey(i, key, left, simulate);
        }
        return amount - left;
    }

    default long count(AEKey key) {
        long total = 0;
        int size = size();
        for (int i = 0; i < size; i++) {
            if (rawKeyAt(i) == key) {
                long a = amountAt(i);
                total = total + a < 0 ? Long.MAX_VALUE : total + a;
            }
        }
        return total;
    }

    @Override
    default long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return amount > 0 ? insertRawKey(what, amount, mode == Actionable.SIMULATE) : 0;
    }

    @Override
    default long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return amount > 0 ? extractRawKey(what, amount, mode == Actionable.SIMULATE) : 0;
    }

    @Override
    default void getAvailableStacks(KeyCounter out) {
        int size = size();
        for (int i = 0; i < size; i++) {
            long a = amountAt(i);
            if (a > 0)
                out.add(rawKeyAt(i), a);
        }
    }

    default boolean isEmpty() {
        int size = size();
        for (int i = 0; i < size; i++) {
            if (amountAt(i) > 0)
                return false;
        }
        return true;
    }
}
