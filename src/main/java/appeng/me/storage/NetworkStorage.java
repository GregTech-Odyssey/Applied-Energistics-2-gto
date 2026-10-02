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
import java.util.function.Consumer;

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
public final class NetworkStorage implements MEStorage {

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
    public final KeyCounter cache;
    private final KeyCounter queryContent = new KeyCounter();
    private boolean queryContentReady;
    private final ReferenceOpenHashSet<MEStorage> networkLinks = new ReferenceOpenHashSet<>();
    private long topologyVersion = -1;
    private boolean topologyShared;
    private boolean firstMountExclusive;
    long enteredEpoch = -1;
    private Object[] mountTokens = new Object[0];

    public NetworkStorage() {
        this.priorityInventory = new ObjectArrayList<>();
        this.storages = new Reference2ReferenceOpenHashMap<>();
        this.storages.defaultReturnValue(NetworkStorage.class);
        this.identities = new ReferenceOpenHashSet<>();
        this.cache = new KeyCounter();
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
            if (StorageQuery.isNetworkLink(inventory))
                networkLinks.add(inventory);
            StorageQuery.topologyChanged();
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
                networkLinks.remove(inventory);
                StorageQuery.topologyChanged();
                inventory.onUnmount(this);
            }
        }
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
        if (StorageQuery.owned() || (!networkLinks.isEmpty() && sharedTopology())) {
            return extractTracked(what, amount, mode, source);
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

    private long extractTracked(AEKey what, long amount, Actionable mode, IActionSource source) {
        var query = StorageQuery.current();
        var last = priorityInventory.size() - 1;
        if (query != null && query.extractDepth > 0) {
            if (!query.sameExtract(what, mode)) {
                return extractFrom(what, amount, mode, source, null, last);
            }
            if (!query.enter(this)) {
                return 0;
            }
            query.extractDepth++;
            try {
                return extractFrom(what, amount, mode, source, query, last);
            } finally {
                query.extractDepth--;
            }
        }
        if (networkLinks.isEmpty() || !sharedTopology()) {
            return extractFrom(what, amount, mode, source, null, last);
        }
        var extracted = 0L;
        var start = last;
        Object firstToken = null;
        var first = last >= 0 ? priorityInventory.get(last).storage : null;
        if (firstMountExclusive && first != null && !isQueuedForRemoval(first)) {
            this.mountsInUse = true;
            try {
                extracted = first.extract(what, amount, mode, source);
            } finally {
                this.mountsInUse = false;
            }
            if (extracted >= amount) {
                flushQueuedOperations();
                return extracted;
            }
            if (extracted > 0 || StorageQuery.extractsUnfiltered(first)) {
                firstToken = mountToken(last);
            }
            start = last - 1;
        }
        if ((query = StorageQuery.acquire()) == null) {
            return extracted + extractFrom(what, amount - extracted, mode, source, null, start);
        }
        query.beginExtract(what, mode);
        try {
            if (firstToken instanceof NetworkStorage network) {
                query.enter(network);
            } else if (firstToken != null && extracted > 0 && mode == Actionable.SIMULATE) {
                query.markDrained(firstToken);
            }
            return extracted + extractFrom(what, amount - extracted, mode, source, query, start);
        } finally {
            query.endExtract();
            StorageQuery.release();
        }
    }

    private long extractFrom(AEKey what, long amount, Actionable mode, IActionSource source,
            @Nullable StorageQuery query, int start) {
        var extracted = 0L;
        var tracked = query != null && mode == Actionable.SIMULATE;
        this.mountsInUse = true;
        try {
            for (int i = start; i >= 0 && extracted < amount; i--) {
                var inv = priorityInventory.get(i).storage;
                if (isQueuedForRemoval(inv)) {
                    continue;
                }
                if (!tracked) {
                    extracted += inv.extract(what, amount - extracted, mode, source);
                    continue;
                }
                var token = query.hasDrained() ? mountToken(i) : null;
                if (token != null && query.isDrained(token)) {
                    continue;
                }
                var got = inv.extract(what, amount - extracted, mode, source);
                if (got > 0) {
                    extracted += got;
                    if (token == null)
                        token = mountToken(i);
                    if (token != null)
                        query.markDrained(token);
                }
            }
        } finally {
            this.mountsInUse = false;
        }
        flushQueuedOperations();
        return extracted;
    }

    @Nullable
    private Object mountToken(int index) {
        if (topologyVersion != StorageQuery.topologyVersion() || mountTokens.length != priorityInventory.size()) {
            sharedTopology();
        }
        var tokens = mountTokens;
        return index < tokens.length ? tokens[index] : null;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (getInUse) {
            return;
        }
        if (StorageQuery.owned() || (!networkLinks.isEmpty() && sharedTopology())) {
            getAvailableStacksTracked(out);
            return;
        }
        getInUse = true;
        try {
            priorityInventory.forEach(entry -> entry.storage.getAvailableStacks(out));
        } finally {
            getInUse = false;
        }
    }

    private void getAvailableStacksTracked(KeyCounter out) {
        var query = StorageQuery.current();
        if (query != null && query.readDepth > 0) {
            if (query.needsCache(this)) {
                if (!queryContentReady) {
                    queryContent.clear();
                    fill(queryContent, query);
                    queryContentReady = true;
                    query.filled(this);
                }
                out.addAll(queryContent);
            } else {
                fill(out, query);
            }
            return;
        }
        if (networkLinks.isEmpty() || !sharedTopology() || (query = StorageQuery.acquire()) == null) {
            getInUse = true;
            try {
                priorityInventory.forEach(entry -> entry.storage.getAvailableStacks(out));
            } finally {
                getInUse = false;
            }
            return;
        }
        query.readDepth = 1;
        try {
            scanMounts(query);
            fill(out, query);
        } finally {
            query.readDepth = 0;
            query.endRead();
            StorageQuery.release();
        }
    }

    public static void markTopologyChanged() {
        StorageQuery.topologyChanged();
    }

    private boolean sharedTopology() {
        var version = StorageQuery.topologyVersion();
        if (topologyVersion != version || mountTokens.length != priorityInventory.size()) {
            var tokens = new Object[priorityInventory.size()];
            for (int i = 0; i < tokens.length; i++) {
                tokens[i] = StorageQuery.tokenOf(priorityInventory.get(i).storage);
            }
            var reached = StorageQuery.reachCounts(this);
            topologyShared = StorageQuery.anyShared(reached);
            firstMountExclusive = tokens.length > 0
                    && StorageQuery.exclusiveSubtree(tokens[tokens.length - 1], reached);
            mountTokens = tokens;
            topologyVersion = version;
        }
        return topologyShared;
    }

    void forEachMount(Consumer<MEStorage> action) {
        for (var entry : priorityInventory) {
            action.accept(entry.storage);
        }
    }

    private void scanMounts(StorageQuery query) {
        if (!query.scan(this))
            return;
        for (var entry : priorityInventory) {
            var storage = entry.storage;
            if (storage instanceof MEInventoryHandler handler && !handler.allowExtraction)
                continue;
            var token = StorageQuery.tokenOf(storage);
            if (token == null)
                continue;
            query.reach(token, StorageQuery.isTransparent(storage));
            if (token instanceof NetworkStorage nested)
                nested.scanMounts(query);
        }
    }

    private void fill(KeyCounter out, StorageQuery query) {
        getInUse = true;
        try {
            for (var entry : priorityInventory) {
                var storage = entry.storage;
                var token = query.anyShared ? StorageQuery.tokenOf(storage) : null;
                if (token == null || !query.isShared(token)) {
                    storage.getAvailableStacks(out);
                    continue;
                }
                if (query.isFull(token))
                    continue;
                var transparent = StorageQuery.isTransparent(storage);
                if (transparent && !query.hasEmitted(token)) {
                    storage.getAvailableStacks(out);
                    query.markFull(token);
                    continue;
                }
                var temp = query.borrow();
                try {
                    storage.getAvailableStacks(temp);
                    query.addOnce(token, temp, out);
                } finally {
                    query.release(temp);
                }
                if (transparent)
                    query.markFull(token);
            }
        } finally {
            getInUse = false;
        }
    }

    void clearQueryContent() {
        queryContent.clear();
        queryContentReady = false;
    }

    @Override
    public KeyCounter getAvailableStacks() {
        var cache = this.cache;
        cache.clear();
        getAvailableStacks(cache);
        return cache;
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
