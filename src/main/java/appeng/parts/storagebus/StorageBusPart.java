/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package appeng.parts.storagebus;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Setting;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.config.YesNo;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.ExternalStorageLookup;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.storage.MEStorageHost;
import appeng.api.storage.StorageAccess;
import appeng.api.storage.StorageTargetResolver;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.core.AppEng;
import appeng.core.definitions.AEItems;
import appeng.core.settings.TickRates;
import appeng.helpers.IConfigInvHost;
import appeng.helpers.IPriorityHost;
import appeng.items.parts.PartModels;
import appeng.me.helpers.MachineSource;
import appeng.me.storage.ITickingMonitor;
import appeng.me.storage.MEInventoryHandler;
import appeng.me.storage.NetworkStorage;
import appeng.me.storage.NullInventory;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.StorageBusMenu;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.automation.UpgradeablePart;
import appeng.util.ConfigInventory;
import appeng.util.Platform;
import appeng.util.SettingsFrom;
import appeng.util.prioritylist.IPartitionList;

public class StorageBusPart extends UpgradeablePart
        implements IGridTickable, IStorageProvider, IPriorityHost, IConfigInvHost {

    public static final ResourceLocation MODEL_BASE = new ResourceLocation(AppEng.MOD_ID, "part/storage_bus_base");

    @PartModels
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE,
            new ResourceLocation(AppEng.MOD_ID, "part/storage_bus_off"));

    @PartModels
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE,
            new ResourceLocation(AppEng.MOD_ID, "part/storage_bus_on"));

    @PartModels
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE,
            new ResourceLocation(AppEng.MOD_ID, "part/storage_bus_has_channel"));

    protected final IActionSource source;
    private final ConfigInventory config = ConfigInventory.configTypes(63, this::onConfigurationChanged);
    /**
     * This is the virtual inventory this storage bus exposes to the network it belongs to. To avoid continuous
     * cell-change notifications, we instead use a handler that will exist as long as this storage bus exists, while
     * changing the underlying inventory.
     */
    private final StorageBusInventory handler = new StorageBusInventory(this);
    @Nullable
    private Component handlerDescription;
    @Nullable
    private ExternalStorageLookup lookup;
    private final Runnable externalChangeListener = this::invalidateOnExternalStorageChange;
    private boolean wasOnline = false;
    private int priority = 0;
    private boolean retargeting;
    private boolean remountPending;

    private PendingUpdateStatus updateStatus = PendingUpdateStatus.FAST_UPDATE;
    private ITickingMonitor monitor = null;

    public StorageBusPart(IPartItem<?> partItem) {
        super(partItem);
        this.getConfigManager().registerSetting(Settings.ACCESS, AccessRestriction.READ_WRITE);
        this.getConfigManager().registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.getConfigManager().registerSetting(Settings.STORAGE_FILTER, StorageFilter.EXTRACTABLE_ONLY);
        this.getConfigManager().registerSetting(Settings.FILTER_ON_EXTRACT, YesNo.YES);
        this.source = new MachineSource(this);
        getMainNode()
                .addService(IStorageProvider.class, this)
                .addService(IGridTickable.class, this);
    }

    @Override
    protected final void onMainNodeStateChanged(IGridNodeListener.State reason) {
        var currentOnline = this.getMainNode().isOnline();
        if (this.wasOnline != currentOnline) {
            this.wasOnline = currentOnline;
            this.getHost().markForUpdate();
            remountStorage();
        }
    }

    private void remountStorage() {
        IStorageProvider.requestUpdate(getMainNode());
    }

    @Override
    public void onSettingChanged(IConfigManager manager, Setting<?> setting) {
        this.onConfigurationChanged();
        this.getHost().markForSave();
    }

    @Override
    public final void upgradesChanged() {
        super.upgradesChanged();
        this.onConfigurationChanged();
    }

    /**
     * Schedule a re-evaluation of the target inventory on the next tick alert the device in case its sleeping.
     */
    private void scheduleUpdate() {
        if (isClientSide()) {
            return;
        }

        this.updateStatus = PendingUpdateStatus.FAST_UPDATE;
        getMainNode().ifPresent((grid, node) -> {
            grid.getTickManager().alertDevice(node);
        });
    }

    @Override
    public void readFromNBT(CompoundTag data) {
        super.readFromNBT(data);
        this.priority = data.getInt("priority");
        config.readFromChildTag(data, "config");
    }

    @Override
    public void writeToNBT(CompoundTag data) {
        super.writeToNBT(data);
        data.putInt("priority", this.priority);
        config.writeToChildTag(data, "config");
    }

    @Override
    public void removeFromWorld() {
        super.removeFromWorld();
        handler.onUnmount(null);
        handler.identity = null;
        handler.track(null);
    }

    @Override
    public final boolean onPartActivate(Player player, InteractionHand hand, Vec3 pos) {
        if (!isClientSide()) {
            openConfigMenu(player);
        }
        return true;
    }

    protected final void openConfigMenu(Player player) {
        MenuOpener.open(getMenuType(), player, MenuLocators.forPart(this));
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(getMenuType(), player, MenuLocators.forPart(this));
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(getPartItem());
    }

    public MenuType<?> getMenuType() {
        return StorageBusMenu.TYPE;
    }

    @Override
    public final void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(3, 3, 15, 13, 13, 16);
        bch.addBox(2, 2, 14, 14, 14, 15);
        bch.addBox(5, 5, 12, 11, 11, 14);
    }

    @Override
    protected final int getUpgradeSlots() {
        return 5;
    }

    @Override
    public final float getCableConnectionLength(AECableType cable) {
        return 4;
    }

    @Override
    public final void onNeighborChanged(BlockGetter level, BlockPos pos, BlockPos neighbor) {
        if (pos.relative(getSide()).equals(neighbor)) {
            var lookup = lookup();
            var te = lookup == null ? null : lookup.getBlockEntity();
            if (te == null) {
                handler.identity = null;
                // In case the TE was destroyed, we have to update the target handler immediately.
                this.updateTarget(false);
            } else {
                handler.identity = te;
                this.scheduleUpdate();
            }
        }
    }

    @Override
    public final TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TickRates.StorageBus, false, true);
    }

    @Override
    public final TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (this.updateStatus != PendingUpdateStatus.NO_UPDATE) {
            this.updateTarget(false);
        }

        if (this.remountPending) {
            this.remountPending = false;
            remountStorage();
        }

        if (this.monitor != null) {
            return this.monitor.onTick();
        }

        return this.updateStatus == PendingUpdateStatus.SLOW_UPDATE ? TickRateModulation.IDLE
                : TickRateModulation.SLEEP;
    }

    /**
     * Used by the menu to configure based on stored contents.
     */
    public MEStorage getInternalHandler() {
        return this.handler.getDelegate();
    }

    private boolean hasRegisteredCellToNetwork() {
        return getMainNode().isOnline()
                && (this.handler.host != null || !(this.handler.getDelegate() instanceof NullInventory));
    }

    public Component getConnectedToDescription() {
        return handlerDescription;
    }

    protected void onConfigurationChanged() {
        if (getMainNode().isReady()) {
            updateTarget(true);
        }
    }

    private void updateTarget(boolean forceFullUpdate) {
        if (isClientSide()) {
            return; // Part is not part of level yet or its client-side
        }
        NetworkStorage.markTopologyChanged();
        var wasRegistered = this.hasRegisteredCellToNetwork();

        MEStorage foundMonitor = null;
        MEStorage foundExternal = null;
        MEStorageHost host = null;

        // If the target position is not ticking, don't search for a target.
        if (Platform.areBlockEntitiesTicking(getLevel(), getBlockEntity().getBlockPos().relative(getSide()))) {
            // In any case we don't need any further update
            this.updateStatus = PendingUpdateStatus.NO_UPDATE;
            var lookup = lookup();
            var be = lookup == null ? null : lookup.getBlockEntity();
            if (be != null) {
                handler.identity = be;
                lookup.refresh();
                lookup.configure(isExtractableOnly(), externalChangeListener);
                var found = lookup.findAll(StorageAccess.FULL);
                if (lookup.tier() == StorageTargetResolver.Tier.STORAGE) {
                    foundMonitor = found;
                } else {
                    foundExternal = found;
                }
                if (be instanceof MEStorageHost h) {
                    host = h;
                }
            } else {
                handler.identity = null;
            }
        } else {
            // Try again in the future...
            this.updateStatus = PendingUpdateStatus.SLOW_UPDATE;
        }
        handler.track(host);

        if (!forceFullUpdate && foundExternal != null && foundExternal == this.handler.getDelegate()) {
            handlerDescription = foundExternal.getDescription();
            return;
        } else if (!forceFullUpdate && foundMonitor == this.handler.getDelegate()) {
            // Monitor didn't change, nothing to do!
            return;
        }

        var wasSleeping = this.monitor == null;
        var wasNetworkLink = this.handler.getDelegate() instanceof NetworkStorage;

        if (foundMonitor != null) {
            if (getMainNode().getGrid().getStorageService().getInventory() == foundMonitor) {
                foundMonitor = null;
            }
        }

        // Update inventory
        MEStorage newInventory;
        if (foundMonitor != null) {
            newInventory = foundMonitor;
            this.checkStorageBusOnInterface();
            handlerDescription = newInventory.getDescription();
        } else if (foundExternal != null) {
            newInventory = foundExternal;
            handlerDescription = newInventory.getDescription();
        } else {
            newInventory = NullInventory.INSTANCE;
            handlerDescription = null;
        }
        this.handler.setDelegate(newInventory);

        // Apply other settings.
        this.handler.setAccessRestriction(this.getConfigManager().getSetting(Settings.ACCESS));
        this.handler.setWhitelist(isUpgradedWith(AEItems.INVERTER_CARD) ? IncludeExclude.BLACKLIST
                : IncludeExclude.WHITELIST);

        this.handler.setPartitionList(createFilter());
        this.handler.setVoidOverflow(this.isUpgradedWith(AEItems.VOID_CARD));

        // Ensure we apply the partition list to the available items.
        boolean filterOnExtract = this.getConfigManager().getSetting(Settings.FILTER_ON_EXTRACT) == YesNo.YES;
        this.handler.setExtractFiltering(filterOnExtract, isExtractableOnly() && filterOnExtract);

        // Let the new inventory react to us ticking.
        if (newInventory instanceof ITickingMonitor tickingMonitor) {
            this.monitor = tickingMonitor;
        } else {
            this.monitor = null;
        }

        // Update sleeping state.
        if (wasSleeping != (this.monitor == null)) {
            getMainNode().ifPresent((grid, node) -> {
                var tm = grid.getTickManager();
                if (this.monitor == null) {
                    tm.sleepDevice(node);
                } else {
                    tm.wakeDevice(node);
                }
            });
        }

        if (wasRegistered != this.hasRegisteredCellToNetwork()
                || wasNetworkLink != (this.handler.getDelegate() instanceof NetworkStorage)) {
            if (retargeting) {
                remountPending = true;
                invalidateOnExternalStorageChange();
            } else {
                remountPending = false;
                remountStorage();
            }
        }
    }

    private void retarget() {
        retargeting = true;
        try {
            updateTarget(false);
        } finally {
            retargeting = false;
        }
    }

    private boolean isExtractableOnly() {
        return this.getConfigManager().getSetting(Settings.STORAGE_FILTER) == StorageFilter.EXTRACTABLE_ONLY;
    }

    @Nullable
    private ExternalStorageLookup lookup() {
        var lookup = this.lookup;
        if (lookup == null && getLevel() instanceof ServerLevel level) {
            var side = getSide();
            this.lookup = lookup = ExternalStorageLookup.create(level,
                    getHost().getBlockEntity().getBlockPos().relative(side), side.getOpposite());
        }
        return lookup;
    }

    private IPartitionList createFilter() {
        var filterBuilder = IPartitionList.builder();
        if (isUpgradedWith(AEItems.FUZZY_CARD)) {
            filterBuilder.fuzzyMode(this.getConfigManager().getSetting(Settings.FUZZY_MODE));
        }

        var slotsToUse = 18 + getInstalledUpgrades(AEItems.CAPACITY_CARD) * 9;
        for (var x = 0; x < config.size() && x < slotsToUse; x++) {
            filterBuilder.add(config.getKey(x));
        }
        return filterBuilder.build();
    }

    private void invalidateOnExternalStorageChange() {
        getMainNode().ifPresent((grid, node) -> {
            grid.getTickManager().alertDevice(node);
        });
    }

    private void checkStorageBusOnInterface() {
    }

    @Override
    public void mountInventories(IStorageMounts mounts) {
        if (this.hasRegisteredCellToNetwork()) {
            mounts.mount(this.handler, priority);
        }
    }

    @Override
    public final int getPriority() {
        return this.priority;
    }

    @Override
    public final void setPriority(int newValue) {
        this.priority = newValue;
        this.getHost().markForSave();
        this.remountStorage();
    }

    /**
     * This inventory forwards to the actual external inventory and allows the inventory to be swapped out underneath.
     */
    public static class StorageBusInventory extends MEInventoryHandler {

        @Nullable
        private Object identity;
        @Nullable
        private final StorageBusPart part;
        @Nullable
        private MEStorageHost host;
        private int epoch;

        public StorageBusInventory(MEStorage inventory) {
            super(inventory);
            this.part = null;
        }

        private StorageBusInventory(StorageBusPart part) {
            super(NullInventory.INSTANCE);
            this.part = part;
        }

        public void setAccessRestriction(AccessRestriction setting) {
            setAllowExtraction(setting.isAllowExtraction());
            setAllowInsertion(setting.isAllowInsertion());
        }

        private void track(@Nullable MEStorageHost host) {
            var epoch = host == null ? -1 : host.storageEpoch();
            if (epoch < 0) {
                if (this.host != null) {
                    this.host = null;
                }
                return;
            }
            this.epoch = epoch;
            if (this.host != host) {
                this.host = host;
            }
        }

        private void checkTarget() {
            var host = this.host;
            if (host != null && part != null) {
                int epoch = host.storageEpoch();
                if (epoch != this.epoch) {
                    this.epoch = epoch;
                    part.retarget();
                }
            }
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            checkTarget();
            return super.insert(what, amount, mode, source);
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            checkTarget();
            return super.extract(what, amount, mode, source);
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            checkTarget();
            super.getAvailableStacks(out);
        }

        @Override
        public boolean isPreferredStorageFor(AEKey input, IActionSource source) {
            checkTarget();
            return super.isPreferredStorageFor(input, source);
        }

        @Override
        public Object getResourceIdentity() {
            var identity = super.getResourceIdentity();
            return identity != null ? identity : this.identity;
        }
    }

    @Override
    public ConfigInventory getConfig() {
        return this.config;
    }

    @Override
    public void importSettings(SettingsFrom mode, CompoundTag input, @Nullable Player player) {
        super.importSettings(mode, input, player);
        config.readFromChildTag(input, "config");
    }

    @Override
    public void exportSettings(SettingsFrom mode, CompoundTag output) {
        super.exportSettings(mode, output);

        if (mode == SettingsFrom.MEMORY_CARD) {
            config.writeToChildTag(output, "config");
        }
    }

    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return MODELS_ON;
        } else {
            return MODELS_OFF;
        }
    }

    private enum PendingUpdateStatus {
        /**
         * Indicates that the storage-bus should reevaluate the block it's attached to on the next tick and update the
         * target inventory - if necessary.
         */
        FAST_UPDATE,
        /**
         * Indicates that the storage bus couldn't find an inventory because it was pointing at an unloaded chunk, and
         * should eventually try again in the future.
         */
        SLOW_UPDATE,
        /**
         * Indicates that no update is required at the moment.
         */
        NO_UPDATE;
    }
}
