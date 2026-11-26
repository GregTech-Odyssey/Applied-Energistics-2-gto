package com.ultramega.ae2insertexportcard.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class InsertExportCardTickerTest {
    @Test
    void itemImportConsumesOnlyTheAmountActuallyInserted() {
        assertEquals(4, InsertExportCardTicker.importedItemCount(10, 4));
        assertEquals(4, InsertExportCardTicker.importedItemCount(4, 10));
        assertEquals(0, InsertExportCardTicker.importedItemCount(10, 0));
    }

    @Test
    void fluidImportRequiresAConfiguredMatchingFluid() {
        var water = org.mockito.Mockito.mock(appeng.api.stacks.AEKey.class);
        var lava = org.mockito.Mockito.mock(appeng.api.stacks.AEKey.class);
        var fuzzyMode = appeng.api.config.FuzzyMode.IGNORE_ALL;
        assertTrue(InsertExportCardTicker.matchesFluidFilter(water, water, false, fuzzyMode));
        assertFalse(InsertExportCardTicker.matchesFluidFilter(water, lava, false, fuzzyMode));
        org.mockito.Mockito.when(water.fuzzyEquals(lava, fuzzyMode)).thenReturn(true);
        assertTrue(InsertExportCardTicker.matchesFluidFilter(water, lava, true, fuzzyMode));
    }

    @Test
    void craftingJobsAreScopedToPlayerTerminalAndInventorySlot() {
        var player = UUID.randomUUID();
        var terminal = UUID.randomUUID();
        var key = new InsertExportCardTicker.CraftingJobKey(player, terminal, 2);

        assertNotEquals(key, new InsertExportCardTicker.CraftingJobKey(UUID.randomUUID(), terminal, 2));
        assertNotEquals(key, new InsertExportCardTicker.CraftingJobKey(player, UUID.randomUUID(), 2));
        assertNotEquals(key, new InsertExportCardTicker.CraftingJobKey(player, terminal, 3));
    }
}
