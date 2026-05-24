/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
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

package appeng.client.gui.me.common;

import java.time.Instant;
import java.util.*;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.Ingredient;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;

import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.client.gui.me.search.RepoSearch;
import appeng.client.gui.widgets.IScrollSource;
import appeng.client.gui.widgets.ISortSource;
import appeng.core.AELog;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.IClientRepo;
import appeng.util.prioritylist.IPartitionList;

/**
 * For showing the network content of a storage channel, this class will maintain a client-side copy of the current
 * server-side storage, which is continuously synchronized to the client while it is open.
 */
public class Repo implements IClientRepo {

    public static final Comparator<GridInventoryEntry> AMOUNT_ASC = Comparator
            .comparingDouble((GridInventoryEntry entry) -> ((double) entry.getStoredAmount())
                    / ((double) entry.getWhat().getAmountPerUnit()));

    public static final Comparator<GridInventoryEntry> AMOUNT_DESC = AMOUNT_ASC.reversed();

    private static final Comparator<GridInventoryEntry> PINNED_ROW_COMPARATOR = Comparator.comparing(entry -> {
        var pinInfo = PinnedKeys.getPinInfo(entry.getWhat());
        return pinInfo != null ? pinInfo.since : Instant.MAX;
    });

    private int rowSize = 9;

    private boolean hasPower;

    private final BiMap<Long, GridInventoryEntry> entries = HashBiMap.create();
    private final Reference2ObjectMap<AEKey, GridInventoryEntry> byKey = new Reference2ObjectOpenHashMap<>();
    private final ReferenceSet<AEKey> craftableKeys = new ReferenceOpenHashSet<>();
    private final ArrayList<GridInventoryEntry> view = new ArrayList<>();
    private final ArrayList<GridInventoryEntry> craftingPinnedRow = new ArrayList<>();
    private final ArrayList<GridInventoryEntry> manualPinnedView = new ArrayList<>();
    /**
     * Entries by item ID to speed up ingredient matching.
     */
    private final Int2ObjectOpenHashMap<List<GridInventoryEntry>> entriesByItemId = new Int2ObjectOpenHashMap<>();
    private boolean entriesByItemIdNeedsUpdate = true;
    private final RepoSearch search = new RepoSearch();
    private IPartitionList partitionList;
    private Runnable updateViewListener;

    private final IScrollSource src;
    private final ISortSource sortSrc;
    private boolean paused;
    private boolean showManualPinnedRow = true;

    public Repo(IScrollSource src, ISortSource sortSrc) {
        this.src = src;
        this.sortSrc = sortSrc;
    }

    public void setPartitionList(IPartitionList partitionList) {
        if (partitionList != this.partitionList) {
            this.partitionList = partitionList;
            this.updateView();
        }
    }

    @Override
    public final void handleUpdate(boolean fullUpdate, List<GridInventoryEntry> entries) {
        if (fullUpdate) {
            clear();
        }

        for (var entry : entries) {
            handleUpdate(entry);
        }

        updateView();
    }

    private void handleUpdate(GridInventoryEntry serverEntry) {
        entriesByItemIdNeedsUpdate = true;

        var localEntry = entries.get(serverEntry.getSerial());
        if (localEntry == null) {
            // First time we're seeing this serial -> create new entry
            if (serverEntry.getWhat() == null) {
                AELog.warn("First time seeing serial %s, but incomplete info received", serverEntry.getSerial());
                return;
            }
            if (serverEntry.isMeaningful()) {
                entries.put(serverEntry.getSerial(), serverEntry);
                byKey.put(serverEntry.getWhat(), serverEntry);
                putCraftable(serverEntry);
            }
            return;
        }

        // Update the local entry
        if (!serverEntry.isMeaningful()) {
            entries.remove(serverEntry.getSerial());
            byKey.remove(localEntry.getWhat());
            craftableKeys.remove(localEntry.getWhat());
        } else if (serverEntry.getWhat() == null) {
            entries.put(serverEntry.getSerial(), new GridInventoryEntry(
                    serverEntry.getSerial(),
                    localEntry.getWhat(),
                    serverEntry.getStoredAmount(),
                    serverEntry.getRequestableAmount(),
                    serverEntry.isCraftable()));
            byKey.put(localEntry.getWhat(), entries.get(serverEntry.getSerial()));
            putCraftable(serverEntry);
        } else {
            entries.put(serverEntry.getSerial(), serverEntry);
            byKey.put(serverEntry.getWhat(), serverEntry);
            putCraftable(serverEntry);
        }
    }

    private void putCraftable(GridInventoryEntry entry) {
        if (entry.isCraftable()) {
            craftableKeys.add(entry.getWhat());
        } else {
            craftableKeys.remove(entry.getWhat());
        }
    }

    public final void updateView() {
        // While the view is paused, we try to only append to the view list in order to avoid mis-clicks by the
        // player due to items shifting under their mouse cursor.
        if (isPaused()) {
            // First pass -> detect and update
            var visibleSerials = new LongOpenHashSet(this.view.size());
            updateEntriesWhilePaused(craftingPinnedRow, visibleSerials);
            updateEntriesWhilePaused(manualPinnedView, visibleSerials);
            updateEntriesWhilePaused(view, visibleSerials);

            var craftingPinnedRowFreeSlots = getFreeSlots(craftingPinnedRow);
            var manualPinnedFreeSlots = getFreeSlots(manualPinnedView);
            var viewFreeSlots = getFreeSlots(view);

            var entriesToAdd = new ArrayList<GridInventoryEntry>();

            // Determine what to do with server entries that are not currently being shown
            for (var serverEntry : entries.values()) {
                if (visibleSerials.contains(serverEntry.getSerial())) {
                    continue; // Entry is already visible
                }

                // First, try to find an empty/meaningless slot in the view that is visually indistinguishable
                // and fill it
                if (takeOverSlotOccupiedByRemovedItem(serverEntry, craftingPinnedRowFreeSlots, craftingPinnedRow)
                        || takeOverSlotOccupiedByRemovedItem(serverEntry, manualPinnedFreeSlots, manualPinnedView)
                        || takeOverSlotOccupiedByRemovedItem(serverEntry, viewFreeSlots, view)) {
                    continue;
                }

                // if we couldn't take over an existing slot, just append it
                entriesToAdd.add(serverEntry);
            }

            addEntriesToView(entriesToAdd);
        } else {
            rebuildVisibleRows();
        }

        // Don't re-sort while being paused
        if (!isPaused()) {
            sortVisibleRows();
        }

        if (this.updateViewListener != null) {
            this.updateViewListener.run();
        }
    }

    public final void rebuildView() {
        rebuildVisibleRows();
        sortVisibleRows();

        if (this.updateViewListener != null) {
            this.updateViewListener.run();
        }
    }

    private void rebuildVisibleRows() {
        this.view.clear();
        this.craftingPinnedRow.clear();
        this.manualPinnedView.clear();

        this.view.ensureCapacity(this.entries.size());
        this.craftingPinnedRow.ensureCapacity(rowSize);
        this.manualPinnedView.ensureCapacity(this.entries.size());

        addEntriesToView(this.entries.values());
    }

    private void sortVisibleRows() {
        // Sort older entries first in the pinned rows
        craftingPinnedRow.sort(PINNED_ROW_COMPARATOR);
        manualPinnedView.sort(PINNED_ROW_COMPARATOR);

        var sortOrder = this.sortSrc.getSortBy();
        var sortDir = this.sortSrc.getSortDir();

        this.view.sort(getComparator(sortOrder, sortDir));
    }

    private void addEntriesToView(Collection<GridInventoryEntry> entries) {
        var viewMode = this.sortSrc.getSortDisplay();
        var typeFilter = this.sortSrc.getTypeFilter().getFilter();

        for (var entry : entries) {
            if (PinnedKeys.isPinned(entry.getWhat(), PinnedKeys.PinReason.CRAFTING)) {
                craftingPinnedRow.add(entry);
                continue;
            }

            if (showManualPinnedRow && PinnedKeys.isPinned(entry.getWhat(), PinnedKeys.PinReason.MANUAL)) {
                manualPinnedView.add(entry);
                continue;
            }

            // Pinned keys ignore all filters & search
            if (this.partitionList != null && !this.partitionList.isListed(entry.getWhat())) {
                continue;
            }

            if (viewMode == ViewItems.CRAFTABLE && !entry.isCraftable()) {
                continue;
            }

            if (viewMode == ViewItems.STORED && entry.getStoredAmount() == 0) {
                continue;
            }

            if (!typeFilter.matches(entry.getWhat())) {
                continue;
            }

            if (search.matches(entry)) {
                this.view.add(entry);
            }
        }

        // Manual pinned entries can outlive their backing grid entry, so represent missing ones with a fake entry.
        if (showManualPinnedRow) {
            for (var pinnedKey : PinnedKeys.getPinnedKeys(PinnedKeys.PinReason.MANUAL)) {
                if (manualPinnedView.stream().noneMatch(r -> pinnedKey.equals(r.getWhat()))) {
                    this.manualPinnedView.add(new GridInventoryEntry(
                            -1, pinnedKey, 0, 0, false));
                }
            }
        }
    }

    private void updateEntriesWhilePaused(List<GridInventoryEntry> shownEntries, LongSet visibleSerials) {
        for (int i = 0; i < shownEntries.size(); i++) {
            var entry = shownEntries.get(i);

            // Update entries with data from server, which doesn't move them
            var serverEntry = entries.get(entry.getSerial());
            if (serverEntry == null) {
                // The server has removed the entry. Let's replace it with an entry that is not meaningful, but shows
                // amount 0
                entry = new GridInventoryEntry(
                        entry.getSerial(),
                        entry.getWhat(),
                        0,
                        0,
                        false);
            } else {
                entry = serverEntry;
            }

            visibleSerials.add(entry.getSerial());
            shownEntries.set(i, entry);
        }
    }

    /**
     * Computes free slot indices by AEKey. Used to replace removed items by items that are visually indistinguishable.
     */
    private Map<AEKey, IntList> getFreeSlots(List<GridInventoryEntry> slots) {
        Map<AEKey, IntList> freeSlots = new HashMap<>();

        for (int i = 0; i < slots.size(); ++i) {
            var entry = slots.get(i);
            if (!entries.containsKey(entry.getSerial())) {
                freeSlots.computeIfAbsent(entry.getWhat(), k -> new IntArrayList()).add(i);
            }
        }

        // Reverse list, so we can pop from the end of the list
        for (var list : freeSlots.values()) {
            Collections.reverse(list);
        }

        return freeSlots;
    }

    private static boolean takeOverSlotOccupiedByRemovedItem(GridInventoryEntry serverEntry,
            Map<AEKey, IntList> freeSlots, List<GridInventoryEntry> slots) {
        IntList freeSlotIndices = freeSlots.get(serverEntry.getWhat());
        if (freeSlotIndices == null) {
            return false;
        }

        int i = freeSlotIndices.removeInt(freeSlotIndices.size() - 1);
        if (freeSlotIndices.size() == 0) {
            freeSlots.remove(serverEntry.getWhat());
        }

        slots.set(i, serverEntry);
        return true;
    }

    private Comparator<? super GridInventoryEntry> getComparator(SortOrder sortOrder, SortDir sortDir) {
        if (sortOrder == SortOrder.AMOUNT) {
            return sortDir == SortDir.ASCENDING ? AMOUNT_ASC : AMOUNT_DESC;
        }

        return Comparator.comparing(GridInventoryEntry::getWhat, getKeyComparator(sortOrder, sortDir));
    }

    public List<GridInventoryEntry> getPinnedEntries(PinnedKeys.PinReason reason) {
        return Collections.unmodifiableList(reason == PinnedKeys.PinReason.CRAFTING ? this.craftingPinnedRow
                : this.manualPinnedView);
    }

    @Nullable
    public PinnedKeys.PinReason getPinnedRowReason(int idx) {
        if (!craftingPinnedRow.isEmpty()) {
            if (idx < this.rowSize) {
                return PinnedKeys.PinReason.CRAFTING;
            }
            idx -= this.rowSize;
        }

        var manualSlots = getVisibleManualPinnedRowCount() * this.rowSize;
        if (idx < manualSlots) {
            return PinnedKeys.PinReason.MANUAL;
        }

        return null;
    }

    public int getPinnedRowCount() {
        return (!craftingPinnedRow.isEmpty() ? 1 : 0) + getVisibleManualPinnedRowCount();
    }

    public int getTotalDisplayRows() {
        return getPinnedRowCount() + (this.view.size() + this.rowSize - 1) / this.rowSize;
    }

    public int getSyntheticPinnedEntryCount() {
        if (!showManualPinnedRow) {
            return 0;
        }

        int count = 0;
        for (var entry : manualPinnedView) {
            if (entry.getSerial() == -1) {
                count++;
            }
        }
        return count;
    }

    public boolean isShowManualPinnedRow() {
        return showManualPinnedRow;
    }

    public void setShowManualPinnedRow(boolean showManualPinnedRow) {
        this.showManualPinnedRow = showManualPinnedRow;
    }

    @Nullable
    public final GridInventoryEntry get(int idx) {
        if (!this.craftingPinnedRow.isEmpty()) {
            // The first row of slots is reserved for pinned crafting keys.
            if (idx < this.rowSize) {
                if (idx < this.craftingPinnedRow.size()) {
                    return this.craftingPinnedRow.get(idx);
                }
                return null;
            }
            idx -= this.rowSize;
        }

        var manualSlots = getVisibleManualPinnedRowCount() * this.rowSize;
        if (manualSlots > 0) {
            if (idx < manualSlots) {
                if (idx < this.manualPinnedView.size()) {
                    return this.manualPinnedView.get(idx);
                }
                return null;
            }
            idx -= manualSlots;
        }

        idx += this.src.getCurrentScroll() * this.rowSize;

        if (idx >= this.view.size()) {
            return null;
        }
        return this.view.get(idx);
    }

    public final int size() {
        return this.view.size() + this.craftingPinnedRow.size()
                + (this.showManualPinnedRow ? this.manualPinnedView.size() : 0);
    }

    public final void clear() {
        this.entries.clear();
        this.byKey.clear();
        this.view.clear();
        this.craftingPinnedRow.clear();
        this.manualPinnedView.clear();
        this.entriesByItemId.clear();
        this.entriesByItemIdNeedsUpdate = true;
    }

    public final boolean hasPinnedRow() {
        return getPinnedRowCount() > 0;
    }

    public final boolean hasPower() {
        return this.hasPower;
    }

    public final void setPower(boolean hasPower) {
        this.hasPower = hasPower;
    }

    public final int getRowSize() {
        return this.rowSize;
    }

    public final void setRowSize(int rowSize) {
        this.rowSize = rowSize;
    }

    private int getVisibleManualPinnedRowCount() {
        if (!showManualPinnedRow || manualPinnedView.isEmpty()) {
            return 0;
        }
        return (manualPinnedView.size() + rowSize - 1) / rowSize;
    }

    public final String getSearchString() {
        return this.search.getSearchString();
    }

    public final void setSearchString(String searchString) {
        this.search.setSearchString(searchString);
    }

    private Comparator<AEKey> getKeyComparator(SortOrder sortBy, SortDir sortDir) {
        return KeySorters.getComparator(sortBy, sortDir);
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        if (this.paused != paused) {
            this.paused = paused;
            AELog.debug("Toggling client-repo pause mode to %s", this.paused);
            if (!paused) {
                updateView(); // resort on unpause
            }
        }
    }

    @Override
    public Set<GridInventoryEntry> getAllEntries() {
        return entries.values();
    }

    @Override
    public List<GridInventoryEntry> getByIngredient(Ingredient ingredient) {
        var entries = new ArrayList<GridInventoryEntry>();
        for (int i = 0; i < ingredient.getStackingIds().size(); i++) {
            var itemId = ingredient.getStackingIds().getInt(i);
            for (var entry : getByItemId(itemId)) {
                if (((AEItemKey) entry.getWhat()).matches(ingredient)) {
                    entries.add(entry);
                }
            }
        }
        return entries;
    }

    private Collection<GridInventoryEntry> getByItemId(int itemId) {
        // Build the itemid->entry map if needed
        if (entriesByItemIdNeedsUpdate) {
            rebuildItemIdToEntries();
            entriesByItemIdNeedsUpdate = false;
        }
        return entriesByItemId.getOrDefault(itemId, Collections.emptyList());
    }

    private void rebuildItemIdToEntries() {
        entriesByItemId.clear();
        for (var entry : getAllEntries()) {
            if (entry.getWhat() instanceof AEItemKey itemKey) {
                var itemId = BuiltInRegistries.ITEM.getId(itemKey.getItem());
                var currentList = entriesByItemId.get(itemId);
                if (currentList == null) {
                    // For many items without NBT, this list will only ever have one entry
                    entriesByItemId.put(itemId, List.of(entry));
                } else if (currentList.size() == 1) {
                    // Convert the list from an immutable single-entry list to a mutable normal arraylist
                    var mutableList = new ArrayList<GridInventoryEntry>(10);
                    mutableList.addAll(currentList);
                    mutableList.add(entry);
                    entriesByItemId.put(itemId, mutableList);
                } else {
                    // If it had more than 1 item, it must have been mutable already
                    currentList.add(entry);
                }
            }
        }
    }

    public final void setUpdateViewListener(Runnable updateViewListener) {
        this.updateViewListener = updateViewListener;
    }

    /**
     * Checks if the repo knows that the given key can be crafted.
     */
    public boolean isCraftable(AEKey what) {
        return craftableKeys.contains(what);
    }

    @Override
    public Set<AEKey> getCraftableKeys() {
        return Collections.unmodifiableSet(craftableKeys);
    }

    @Override
    public GridInventoryEntry getByKey(AEKey key) {
        return byKey.get(key);
    }
}
