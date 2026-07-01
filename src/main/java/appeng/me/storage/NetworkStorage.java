/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.me.storage;

import java.util.*;

import com.google.common.base.Preconditions;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.core.localization.GuiText;

/**
 * Manages all available {@link MEStorage} on the network.
 */
public class NetworkStorage implements MEStorage {

    // This flag prevents both concurrent modifications of the mounted storage while
    // they're being iterated, and recursive extract/insert/list operations.
    private boolean mountsInUse;
    private boolean getInUse;

    private final ObjectArrayList<MountOperation> priorityInventory;
    private final Reference2ReferenceOpenHashMap<MEStorage, Object> storages;
    private final ReferenceOpenHashSet<Object> identities;

    // Queued mount/unmount operations that occurred while an insert/extract was ongoing
    // Is only non-null if something is queued
    @Nullable
    private ArrayList<QueuedOperation> queuedOperations;

    public NetworkStorage() {
        this.priorityInventory = new ObjectArrayList<>();
        this.storages = new Reference2ReferenceOpenHashMap<>();
        this.storages.defaultReturnValue(NetworkStorage.class);
        this.identities = new ReferenceOpenHashSet<>();
    }

    public void mount(int priority, MEStorage inventory) {
        var operation = new MountOperation(priority, inventory);
        if (mountsInUse) {
            if (queuedOperations == null) {
                queuedOperations = new ArrayList<>();
            }
            queuedOperations.add(operation);
        } else {
            if (storages.containsKey(inventory)) {
                return;
            }
            var identity = inventory.getResourceIdentity();
            if (identity != null) {
                if (identity instanceof Set<?> set) {
                    for (var i : set) {
                        if (identities.contains(i)) {
                            return;
                        }
                    }
                    identities.addAll(set);
                } else {
                    if (identities.contains(identity)) {
                        return;
                    }
                    identities.add(identity);
                }
            }
            storages.put(inventory, identity);
            priorityInventory.add(operation);
            priorityInventory.sort(MountOperation.PRIORITY_SORTER);
            inventory.onMount(this);
        }
    }

    public void unmount(MEStorage inventory) {
        if (mountsInUse) {
            if (queuedOperations == null) {
                queuedOperations = new ArrayList<>();
            }
            queuedOperations.add(new UnmountOperation(inventory));
        } else {
            var identity = storages.remove(inventory);
            if (identity != NetworkStorage.class) {
                if (identity != null) {
                    if (identity instanceof Set<?> set) {
                        identities.removeAll(set);
                    } else {
                        identities.remove(identity);
                    }
                }
                priorityInventory.removeIf(obj -> obj.storage == inventory);
                inventory.onUnmount(this);
            }
        }
    }

    @Nullable
    @Override
    public Object getResourceIdentity() {
        return identities;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable type, IActionSource src) {
        if (mountsInUse) {
            return 0;
        }
        var remaining = amount;
        this.mountsInUse = true;
        try {
            var ii = priorityInventory.listIterator(0);
            while (ii.hasNext() && remaining > 0) {
                var inv = ii.next().storage;
                if (isQueuedForRemoval(inv))
                    continue;
                remaining -= inv.insert(what, remaining, type, src);
            }
        } finally {
            this.mountsInUse = false;
        }
        flushQueuedOperations();
        return amount - remaining;
    }

    private void flushQueuedOperations() {
        Preconditions.checkState(!this.mountsInUse);
        var queuedOperations = this.queuedOperations;
        if (queuedOperations != null) {
            this.queuedOperations = null;
            for (var op : queuedOperations) {
                if (op instanceof MountOperation(int priority, MEStorage storage1)) {
                    mount(priority, storage1);
                } else if (op instanceof UnmountOperation(MEStorage storage)) {
                    unmount(storage);
                } else {
                    throw new IllegalStateException("Unknown operation: " + op);
                }
            }
        }
    }

    private boolean isQueuedForRemoval(MEStorage inv) {
        if (queuedOperations != null) {
            for (var queuedOperation : queuedOperations) {
                if (queuedOperation instanceof UnmountOperation(MEStorage storage) && storage == inv) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (mountsInUse) {
            return 0;
        }
        var extracted = 0L;
        this.mountsInUse = true;
        try {
            for (int i = priorityInventory.size() - 1; i >= 0 && extracted < amount; i--) {
                var inv = priorityInventory.get(i).storage;
                if (isQueuedForRemoval(inv)) {
                    continue;
                }
                extracted += inv.extract(what, amount - extracted, mode, source);
            }
        } finally {
            this.mountsInUse = false;
        }
        flushQueuedOperations();
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (getInUse) {
            return;
        }
        getInUse = true;
        try {
            if (priorityInventory.isEmpty()) {
                return;
            }
            priorityInventory.forEach(entry -> entry.storage.getAvailableStacks(out));
        } finally {
            getInUse = false;
        }
    }

    @Override
    public Component getDescription() {
        return GuiText.MENetworkStorage.text();
    }

    sealed interface QueuedOperation permits MountOperation, UnmountOperation {
    }

    private record MountOperation(int priority, MEStorage storage) implements QueuedOperation {
        public static final Comparator<MountOperation> PRIORITY_SORTER = (a, b) -> Integer.compare(b.priority,
                a.priority);

    }

    private record UnmountOperation(MEStorage storage) implements QueuedOperation {
    }
}
