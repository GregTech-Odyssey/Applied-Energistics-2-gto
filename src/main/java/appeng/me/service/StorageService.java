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

package appeng.me.service;

import java.util.*;

import com.google.common.base.Preconditions;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import it.unimi.dsi.fastutil.objects.*;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridServiceProvider;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyLongMap;
import appeng.api.stacks.AEKeyObjectMap;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.me.helpers.InterestManager;
import appeng.me.helpers.StackWatcher;
import appeng.me.storage.NetworkStorage;

public class StorageService implements Runnable, IStorageService, IGridServiceProvider {

    /**
     * Tracks the storage service's state for each grid node that provides storage to the network.
     */
    private final Map<IGridNode, ProviderState> nodeProviders = new Reference2ReferenceOpenHashMap<>();
    /**
     * Tracks state for storage providers that are provided by other grid services (i.e. crafting).
     */
    private final List<ProviderState> globalProviders = new ArrayList<>();
    private final Set<UpdateRequester> requesters = new ReferenceOpenHashSet<>();
    private final SetMultimap<AEKey, StackWatcher<IStorageWatcherNode>> interests = Multimaps
            .newSetMultimap(new AEKeyObjectMap<>(), ReferenceOpenHashSet::new);
    private final InterestManager<StackWatcher<IStorageWatcherNode>> interestManager = new InterestManager<>(
            this.interests);
    private final NetworkStorage storage;
    /**
     * Publicly exposed cached available stacks.
     */
    private final KeyCounter cachedAvailableStacks = new KeyCounter();
    /**
     * Private cached amounts, to ensure that we send correct change notifications even if
     * {@link #cachedAvailableStacks} is modified by mistake.
     */
    private final AEKeyLongMap<AEKey> cachedAvailableAmounts = new AEKeyLongMap<>();
    private volatile boolean cachedStacksNeedUpdate = true;
    private boolean watcherUpdate = false;

    /**
     * Tracks the stack watcher associated with a given grid node. Needed to clean up watchers when the node leaves the
     * grid.
     */
    private final Map<IGridNode, StackWatcher<IStorageWatcherNode>> watchers = new IdentityHashMap<>();
    private final Reference2ObjectOpenHashMap<MEStorage, MountContent> mountContents = new Reference2ObjectOpenHashMap<>();
    private final ObjectOpenHashSet<AEKey> pendingWatcherKeys = new ObjectOpenHashSet<>();
    private KeyCounter mountScratch = new KeyCounter();
    private boolean mountsSynced;
    private boolean fullWatcherUpdate = true;
    private boolean trackKeys;
    private int mountEpoch;

    private static final int JOURNAL_MIN_KEYS = 1024;

    private MountContent drainingMount;
    private final java.util.function.ObjLongConsumer<AEKey> journalApplier = (key, amount) -> {
        var content = drainingMount.content;
        long old = content.get(key);
        if (old == amount) {
            return;
        }
        adjustCached(key, amount - old);
        if (amount == 0) {
            content.remove(key);
        } else {
            content.set(key, amount);
        }
    };

    private static final class MountContent {

        KeyCounter content = new KeyCounter();
        long version = -1;
        int epoch;
        boolean journaled;
    }

    public StorageService() {
        this.storage = new NetworkStorage();
    }

    @Override
    public void onServerEndTick(MinecraftServer server) {
        if (watcherUpdate && server.getTickCount() % 10 == 0) {
            updateCachedStacks();
            if (!interestManager.isEmpty()) {
                watcherUpdate();
            }
        } else {
            cachedStacksNeedUpdate = true;
        }
    }

    private void updateCachedStacks() {
        cachedStacksNeedUpdate = false;
        if (!mountsSynced || storage.usesTrackedQuery()) {
            rebuildCachedStacks();
            return;
        }
        trackKeys = !fullWatcherUpdate && !interestManager.isEmpty();
        if (interestManager.isEmpty()) {
            fullWatcherUpdate = true;
        }
        int epoch = ++mountEpoch;
        storage.readMounts(inventory -> refreshMount(inventory, epoch));
        if (mountContents.size() > 0) {
            for (var it = mountContents.values().iterator(); it.hasNext();) {
                var mount = it.next();
                if (mount.epoch == epoch) {
                    continue;
                }
                for (var entry : mount.content) {
                    adjustCached(entry.getKey(), -entry.getLongValue());
                }
                it.remove();
            }
        }
    }

    private void rebuildCachedStacks() {
        mountContents.clear();
        pendingWatcherKeys.clear();
        fullWatcherUpdate = true;
        cachedAvailableStacks.clear();
        if (storage.usesTrackedQuery()) {
            mountsSynced = false;
            storage.getAvailableStacks(cachedAvailableStacks);
        } else {
            int epoch = ++mountEpoch;
            storage.readMounts(inventory -> {
                var mount = new MountContent();
                mount.epoch = epoch;
                long version = inventory.getChangeVersion();
                inventory.getAvailableStacks(mount.content);
                mount.version = version;
                mount.journaled = armJournal(inventory, version, mount.content.size());
                mountContents.put(inventory, mount);
                cachedAvailableStacks.addAll(mount.content);
            });
            mountsSynced = true;
        }
        cachedAvailableStacks.removeZeros();
        cachedAvailableStacks.removeEmptySubmaps();
    }

    private void refreshMount(MEStorage inventory, int epoch) {
        var mount = mountContents.get(inventory);
        if (mount == null) {
            mount = new MountContent();
            mount.epoch = epoch;
            long version = inventory.getChangeVersion();
            inventory.getAvailableStacks(mount.content);
            mount.version = version;
            mount.journaled = armJournal(inventory, version, mount.content.size());
            mountContents.put(inventory, mount);
            for (var entry : mount.content) {
                adjustCached(entry.getKey(), entry.getLongValue());
            }
            return;
        }
        mount.epoch = epoch;
        long version = inventory.getChangeVersion();
        if (version >= 0 && version == mount.version) {
            return;
        }
        if (mount.journaled) {
            drainingMount = mount;
            boolean drained = inventory.drainChanges(this, journalApplier);
            drainingMount = null;
            if (drained) {
                mount.version = version;
                return;
            }
        }
        var fresh = mountScratch;
        fresh.clear();
        inventory.getAvailableStacks(fresh);
        var old = mount.content;
        for (var entry : old) {
            var key = entry.getKey();
            adjustCached(key, fresh.get(key) - entry.getLongValue());
        }
        for (var entry : fresh) {
            var key = entry.getKey();
            if (!old.contains(key)) {
                adjustCached(key, entry.getLongValue());
            }
        }
        old.clear();
        mountScratch = old;
        mount.content = fresh;
        mount.version = version;
        mount.journaled = armJournal(inventory, version, mount.content.size());
    }

    private boolean armJournal(MEStorage inventory, long version, int size) {
        if (version < 0 || size < JOURNAL_MIN_KEYS) {
            return false;
        }
        inventory.drainChanges(this, MEStorage.IGNORE_CHANGES);
        return true;
    }

    private void adjustCached(AEKey key, long delta) {
        if (delta == 0) {
            return;
        }
        long amount = cachedAvailableStacks.get(key) + delta;
        if (amount == 0) {
            cachedAvailableStacks.remove(key);
        } else {
            cachedAvailableStacks.set(key, amount);
        }
        if (trackKeys) {
            pendingWatcherKeys.add(key);
        }
    }

    private void watcherUpdate() {
        if (!fullWatcherUpdate) {
            for (var what : pendingWatcherKeys) {
                var newAmount = cachedAvailableStacks.get(what);
                if (newAmount != cachedAvailableAmounts.getAmount(what)) {
                    postWatcherUpdate(what, newAmount);
                    if (newAmount == 0) {
                        cachedAvailableAmounts.removeLong(what);
                    } else {
                        cachedAvailableAmounts.set(what, newAmount);
                    }
                }
            }
            pendingWatcherKeys.clear();
            return;
        }
        fullWatcherUpdate = false;
        pendingWatcherKeys.clear();
        for (var it = cachedAvailableAmounts.iterator(); it.hasNext();) {
            var entry = it.next();
            var what = entry.getKey();
            var newAmount = cachedAvailableStacks.get(what);
            if (newAmount != entry.getLongValue()) {
                postWatcherUpdate(what, newAmount);
                if (newAmount == 0) {
                    it.remove();
                } else {
                    entry.setValue(newAmount);
                }
            }
        }

        cachedAvailableStacks.forEach(entry -> {
            var what = entry.getKey();
            var newAmount = entry.getLongValue();
            if (newAmount != cachedAvailableAmounts.getAmount(what)) {
                postWatcherUpdate(what, newAmount);
                cachedAvailableAmounts.set(what, newAmount);
            }
        });
    }

    private void postWatcherUpdate(AEKey what, long newAmount) {
        for (var watcher : interestManager.get(what)) {
            watcher.getHost().onStackChange(what, newAmount);
        }
        for (var watcher : interestManager.getAllStacksWatchers()) {
            watcher.getHost().onStackChange(what, newAmount);
        }
    }

    /**
     * When a node joins the grid, we automatically register provided {@link IStorageProvider} and
     * {@link IStorageWatcherNode}.
     */
    @Override
    public void addNode(IGridNode node, @Nullable CompoundTag savedData) {
        var storageProvider = node.getService(IStorageProvider.class);
        if (storageProvider != null) {
            ProviderState state = new ProviderState(storageProvider);
            this.nodeProviders.put(node, state);
            state.mount();
        }

        var watcher = node.getService(IStorageWatcherNode.class);
        if (watcher != null) {
            var iw = new StackWatcher<>(interestManager, watcher);
            this.watchers.put(node, iw);
            watcher.updateWatcher(iw);
            iw.getListener().add(this);
        }
        if (node.getOwner() instanceof UpdateRequester requester) {
            this.requesters.add(requester);
            requester.getListener().add(this);
        }
        run();
    }

    /**
     * When a node leaves the grid, we automatically unregister the previously registered {@link IStorageProvider} or
     * {@link IStorageWatcherNode}.
     */
    @Override
    public void removeNode(IGridNode node) {
        var watcher = this.watchers.remove(node);
        if (watcher != null) {
            watcher.destroy();
            watcher.getListener().remove(this);
        }

        var providerState = this.nodeProviders.remove(node);
        if (providerState != null) {
            providerState.unmount();
        }
        if (node.getOwner() instanceof UpdateRequester requester) {
            this.requesters.remove(requester);
            requester.getListener().remove(this);
        }
        run();
    }

    @Override
    public MEStorage getInventory() {
        return storage;
    }

    @Override
    public KeyCounter getCachedInventory() {
        if (cachedStacksNeedUpdate) {
            updateCachedStacks();
        }
        return cachedAvailableStacks;
    }

    @Override
    public void addGlobalStorageProvider(IStorageProvider provider) {
        var state = new ProviderState(provider);
        this.globalProviders.add(state);
        state.mount();
    }

    @Override
    public void removeGlobalStorageProvider(IStorageProvider provider) {
        var it = this.globalProviders.iterator();
        while (it.hasNext()) {
            var state = it.next();
            if (state.provider == provider) {
                it.remove();
                state.unmount();
            }
        }
    }

    @Override
    public void refreshNodeStorageProvider(IGridNode node) {
        var state = nodeProviders.get(node);
        if (state == null) {
            throw new IllegalArgumentException("The given node is not part of this grid or has no storage provider.");
        }
        state.update();
    }

    @Override
    public void refreshGlobalStorageProvider(IStorageProvider provider) {
        for (var state : globalProviders) {
            if (state.provider == provider) {
                state.update();
                return;
            }
        }

        throw new IllegalArgumentException("The given node is not part of this grid or has no storage provider.");
    }

    @Override
    public void invalidateCache() {
        mountsSynced = false;
        cachedStacksNeedUpdate = true;
    }

    @Override
    public void run() {
        watcherUpdate = false;
        if (!interestManager.isEmpty()) {
            watcherUpdate = true;
            return;
        }
        for (var requester : requesters) {
            if (requester.isUpdateRequested(this)) {
                watcherUpdate = true;
                return;
            }
        }
    }

    /**
     * A {@link IStorageProvider}-specific mount table facade which allows the provider to easily mount/remount its
     * storage.
     */
    private class ProviderState implements IStorageMounts {
        private final IStorageProvider provider;
        private final ReferenceOpenHashSet<MEStorage> inventories = new ReferenceOpenHashSet<>();
        private boolean mounted;

        public ProviderState(IStorageProvider provider) {
            this.provider = provider;
        }

        /**
         * Performs the first mount operation on this storage provider, which does not assume any of the provider's
         * inventories are currently mounted and need to be removed first.
         */
        private void mount() {
            Preconditions.checkState(!mounted, "Can't mount a provider's inventories when it's already mounted");

            mounted = true;
            provider.mountInventories(this);
        }

        @Override
        public void mount(MEStorage inventory, int priority) {
            Preconditions.checkState(mounted, "Cannot use StorageMounts after the storage has been unmounted.");

            if (!inventories.add(inventory)) {
                throw new IllegalStateException("Cannot mount the same inventory twice.");
            }

            // Mount this inventory into the network storage
            storage.mount(priority, inventory);
        }

        public void update() {
            unmount();
            mount();
        }

        public void unmount() {
            if (!mounted) {
                return;
            }
            mounted = false;

            for (var inventory : inventories) {
                unmount(inventory);
            }
            inventories.clear();
        }

        private void unmount(MEStorage inventory) {
            storage.unmount(inventory);
        }
    }
}
