/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.storage;

import java.util.Objects;
import java.util.function.Consumer;

import com.google.common.base.Preconditions;

import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyLongMap;
import appeng.api.stacks.KeyCounter;
import appeng.hooks.ticking.TickHandler;
import appeng.me.storage.NetworkStorage;

/**
 * AE's Equivalent to IInventory, used to reading contents, and manipulating contents of ME Inventories.
 * <p>
 * Implementations should COMPLETELY ignore stack size limits from an external view point, Meaning that you can inject
 * Integer.MAX_VALUE items and it should work as defined, or be able to extract Integer.MAX_VALUE and have it work as
 * defined, Translations to MC's max stack size are external to the AE API.
 * <p>
 * If you want to request at most a stack of an item, you need to use {@link ItemStack#getMaxStackSize()} before
 * extracting from this inventory.
 * <p>
 * As of 1.20, this is also directly used as a capability / API Lookup for storage busses and pattern providers to
 * request access to another ME network so it can be used as a subnetwork. Can also be offered by addon mods if they
 * want to control their {@code MEStorage}.
 */
public interface MEStorage {

    MEStorage TRASH = new MEStorage() {

        @Override
        public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
            return true;
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            return amount;
        }

        @Override
        public Component getDescription() {
            return Component.empty();
        }
    };

    /**
     * Returns whether this inventory is the preferred storage location for the given stack when being compared to other
     * inventories of the same overall priority.
     * <p/>
     * If for example an inventory already contains some amount of an item, it should be preferred over other
     * inventories that don't when trying to store more of the item.
     *
     * @param source The source trying to find storage for stacks.
     */
    default boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return false;
    }

    /**
     * Store new items, or simulate the addition of new items into the ME Inventory.
     *
     * @param what   what to insert
     * @param amount how much of it to insert. must not be negative
     * @param mode   action type
     * @return returns the number of items inserted.
     */
    default long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return 0;
    }

    /**
     * Extract the specified item from the ME Inventory
     *
     * @param what   what to extract
     * @param amount how much of it to extract (at most)
     * @param mode   simulate, or perform action?
     * @return returns the number of items extracted
     */
    default long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return 0;
    }

    /**
     * request a full report of all available items, storage.
     *
     * @param out The amounts for all available keys will be added to this tally.
     */
    default void getAvailableStacks(KeyCounter out) {
    }

    /**
     * request a full report of all available items, storage.
     *
     * @return a new list of this inventories content
     */
    java.util.function.ObjLongConsumer<AEKey> IGNORE_CHANGES = (key, amount) -> {
    };

    default long getChangeVersion() {
        return -1;
    }

    default boolean drainChanges(Object reader, java.util.function.ObjLongConsumer<AEKey> changed) {
        return false;
    }

    static long nextChangeVersion() {
        return AvailableStacksCache.VERSIONS.incrementAndGet();
    }

    default KeyCounter getAvailableStacks() {
        var result = new KeyCounter();
        getAvailableStacks(result);
        return result;
    }

    /**
     * @return The type of storage represented by this object.
     */
    Component getDescription();

    default void onMount(NetworkStorage parent) {

    }

    default void onUnmount(NetworkStorage parent) {

    }

    @Nullable
    default Object getResourceIdentity() {
        return null;
    }

    @MustBeInvokedByOverriders
    default boolean contains(MEStorage storage, ReferenceOpenHashSet<MEStorage> checked) {
        return storage == this || (checked.add(this) && storage.contains(this, checked));
    }

    static void checkPreconditions(AEKey what, long amount, Actionable mode, IActionSource source) {
        Objects.requireNonNull(what, "Cannot pass a null key");
        Objects.requireNonNull(mode, "Cannot pass a null mode");
        Objects.requireNonNull(source, "Cannot pass a null source");
        Preconditions.checkArgument(amount >= 0, "Cannot pass a negative amount");
    }

    final class AvailableStacksCache {

        static final java.util.concurrent.atomic.AtomicLong VERSIONS = new java.util.concurrent.atomic.AtomicLong();

        private static final int JOURNAL_LIMIT = 4096;

        private long version = VERSIONS.incrementAndGet();
        private AEKey[] journal;
        private AEKey[] journalRecent;
        private it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<AEKey> journalUnique;
        private int journalSize;
        private int journalCap;
        private Object journalReader;
        private boolean journalOverflow = true;
        private long lastTick = -1;
        private boolean tickUpdate = true;
        private boolean needUpdate = true;

        private KeyCounter keyCounter;
        private Consumer<KeyCounter> adder;

        public AvailableStacksCache() {
        }

        public AvailableStacksCache(Consumer<KeyCounter> adder) {
            this.adder = adder;
        }

        public void setAdder(Consumer<KeyCounter> adder) {
            this.adder = adder;
        }

        public void setTickUpdate(boolean tickUpdate) {
            this.tickUpdate = tickUpdate;
        }

        public void markAsDirty() {
            needUpdate = true;
            journalOverflow = true;
        }

        public void markAsDirty(AEKey key) {
            needUpdate = true;
            if (journalOverflow) {
                return;
            }
            var recent = journalRecent;
            int slot = key.mix & (recent.length - 1);
            if (recent[slot] == key) {
                return;
            }
            recent[slot] = key;
            var keys = journal;
            int n = journalSize;
            if (n == keys.length) {
                n = compactJournal();
                if (n >= journalCap) {
                    journalOverflow = true;
                    return;
                }
                if (n > keys.length >> 1) {
                    journal = keys = java.util.Arrays.copyOf(keys, keys.length << 1);
                }
            }
            keys[n] = key;
            journalSize = n + 1;
        }

        private int compactJournal() {
            var keys = journal;
            int n = journalSize;
            var unique = journalUnique;
            if (unique == null) {
                journalUnique = unique = new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<>(n);
            }
            int w = 0;
            for (int i = 0; i < n; i++) {
                var k = keys[i];
                if (unique.add(k)) {
                    keys[w++] = k;
                }
            }
            java.util.Arrays.fill(keys, w, n, null);
            unique.clear();
            journalSize = w;
            return w;
        }

        private void resetJournal() {
            if (journal == null) {
                journal = new AEKey[16];
                journalRecent = new AEKey[256];
            } else {
                java.util.Arrays.fill(journal, 0, journalSize, null);
                java.util.Arrays.fill(journalRecent, null);
            }
            journalSize = 0;
            var counter = keyCounter;
            journalCap = Math.min(JOURNAL_LIMIT, Math.max(128, counter == null ? 0 : counter.size() >> 3));
        }

        public void invalidateCache() {
            needUpdate = true;
            lastTick = 0;
            journalOverflow = true;
        }

        public boolean drainChanges(Object reader, Consumer<AEKey> changed) {
            if (tickUpdate || reader != journalReader || journalOverflow) {
                journalReader = reader;
                resetJournal();
                journalOverflow = false;
                return false;
            }
            if (journalSize > 0) {
                var keys = journal;
                for (int i = 0, n = journalSize; i < n; i++)
                    changed.accept(keys[i]);
                resetJournal();
            }
            return true;
        }

        public long getVersion() {
            return tickUpdate || needUpdate ? -1 : version;
        }

        public boolean updateCache() {
            lastTick = TickHandler.INSTANCE.tickCounter;
            needUpdate = false;
            version = VERSIONS.incrementAndGet();
            AEKeyLongMap<AEKey> tmp;
            var counter = keyCounter;
            if (counter == null) {
                keyCounter = counter = new KeyCounter();
                tmp = AEKeyLongMap.EMPTY;
            } else {
                tmp = counter.getMap().clone();
                counter.clear();
            }
            adder.accept(counter);
            if (tmp.size() != counter.size()) {
                return true;
            }
            for (var entry : counter) {
                var old = tmp.getAmount(entry.getKey());
                if (old == 0 || old != entry.getLongValue()) {
                    return true;
                }
            }
            for (var oldKey : tmp.keySet()) {
                if (!counter.contains(oldKey)) {
                    return true;
                }
            }
            return false;
        }

        public KeyCounter getAvailableStacksCache() {
            var counter = keyCounter;
            if (needUpdate || tickUpdate) {
                var tick = TickHandler.INSTANCE.tickCounter;
                if (tick != lastTick) {
                    needUpdate = false;
                    version = VERSIONS.incrementAndGet();
                    lastTick = tick;
                    if (counter == null) {
                        keyCounter = counter = new KeyCounter();
                    } else {
                        counter.clear();
                    }
                    adder.accept(counter);
                }
            }
            return counter;
        }
    }

}
