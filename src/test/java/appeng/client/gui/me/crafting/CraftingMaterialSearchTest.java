package appeng.client.gui.me.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import appeng.menu.me.crafting.CraftingStatusEntry;

class CraftingMaterialSearchTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void searchesItemAndFluidNamesWithoutChangingPlanQuantities() {
        var bedrock = key(AEItemKey.class, "Bedrock Dust");
        var fluid = key(AEFluidKey.class, "Liquid Bedrock");
        var iron = key(AEItemKey.class, "Iron Ingot");
        var itemEntry = new CraftingPlanSummaryEntry(bedrock, 7, 13, 21, 0);
        var fluidEntry = new CraftingPlanSummaryEntry(fluid, 1000, 2000, 3000, 0);
        var entries = List.of(itemEntry, new CraftingPlanSummaryEntry(iron, 0, 1, 0, 0), fluidEntry);
        var search = new CraftingMaterialSearch<CraftingPlanSummaryEntry>(CraftingPlanSummaryEntry::getWhat);

        search.setSearchTerm("  BEDROCK  ");

        assertEquals(List.of(itemEntry, fluidEntry), search.filter(entries));
        assertEquals(7, itemEntry.getMissingAmount());
        assertEquals(13, itemEntry.getStoredAmount());
        assertEquals(21, itemEntry.getCraftAmount());
        assertEquals(3000, fluidEntry.getCraftAmount());
        assertEquals(3, entries.size());
    }

    @Test
    void clearingAnUnmatchedQueryRestoresTheCompleteSource() {
        var entries = List.of(key(AEItemKey.class, "Bedrock"), key(AEFluidKey.class, "Water"));
        var search = new CraftingMaterialSearch<AEKey>(key -> key);
        search.setSearchTerm("missing");
        assertTrue(search.filter(entries).isEmpty());

        search.setSearchTerm("");

        assertSame(entries, search.filter(entries));
    }

    @Test
    void refreshesVisibleStatusEntriesWhenQuantitiesOrSelectedCpuChange() {
        var bedrock = key(AEItemKey.class, "Bedrock");
        var water = key(AEFluidKey.class, "Water");
        var original = new CraftingStatusEntry(1, bedrock, 5, 10, 20);
        var updated = new CraftingStatusEntry(1, bedrock, 2, 3, 4);
        var search = new CraftingMaterialSearch<CraftingStatusEntry>(CraftingStatusEntry::getWhat);
        search.setSearchTerm("bedrock");
        assertSame(original, search.filter(List.of(original)).getFirst());

        assertSame(updated, search.filter(List.of(updated)).getFirst());
        assertTrue(search.filter(List.of(new CraftingStatusEntry(1, water, 1, 2, 3))).isEmpty());
        assertSame(updated, search.filter(List.of(updated)).getFirst());
        assertTrue(search.filter(List.of()).isEmpty());
    }

    @Test
    void reusesVisibleListAndNamesUntilTheSourceOrQueryChanges() {
        var material = key(AEItemKey.class, "Bedrock Dust");
        var entries = List.<AEKey>of(material);
        var search = new CraftingMaterialSearch<AEKey>(key -> key);
        search.setSearchTerm("bedrock");
        var visible = search.filter(entries);

        assertSame(visible, search.filter(entries));
        search.setSearchTerm("dust");
        assertEquals(entries, search.filter(entries));
        verify(material, times(1)).getDisplayName();
    }

    @Test
    void supportsChineseNamesAndUsesLocaleIndependentCaseFolding() {
        var originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var iron = key(AEItemKey.class, "IRON INGOT");
            var bedrock = key(AEItemKey.class, "基岩粉");
            var entries = List.<AEKey>of(iron, bedrock);
            var search = new CraftingMaterialSearch<AEKey>(key -> key);
            search.setSearchTerm("iron");
            assertEquals(List.of(iron), search.filter(entries));

            search.setSearchTerm("基岩");
            assertEquals(List.of(bedrock), search.filter(entries));
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    private static <K extends AEKey> K key(Class<K> type, String name) {
        var key = mock(type);
        when(key.getDisplayName()).thenReturn(Component.literal(name));
        return key;
    }
}
