package appeng.client.gui.me.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import appeng.client.gui.widgets.ISortSource;
import appeng.menu.me.common.GridInventoryEntry;

class RepoTest {

    private Repo repo;

    @BeforeEach
    void setUp() {
        PinnedKeys.clearPinnedKeys();
        repo = new Repo(() -> 0, ISortSource.DEFAULT);
    }

    @AfterEach
    void tearDown() {
        PinnedKeys.clearPinnedKeys();
    }

    @Test
    void manualPinsCanSpanMultipleRows() {
        var crafting = new TestKey("crafting");
        PinnedKeys.pinKey(crafting, PinnedKeys.PinReason.CRAFTING);

        var entries = new ArrayList<GridInventoryEntry>();
        entries.add(new GridInventoryEntry(1, crafting, 1, 0, true));

        for (int i = 0; i < 10; i++) {
            var key = new TestKey("manual_" + i);
            PinnedKeys.pinKey(key, PinnedKeys.PinReason.MANUAL);
            entries.add(new GridInventoryEntry(10 + i, key, 10 - i, 0, false));
        }

        repo.handleUpdate(true, entries);

        assertThat(repo.getPinnedRowCount()).isEqualTo(3);
        assertThat(repo.getTotalDisplayRows()).isEqualTo(3);
        assertThat(repo.getPinnedRowReason(0)).isEqualTo(PinnedKeys.PinReason.CRAFTING);
        assertThat(repo.getPinnedRowReason(9)).isEqualTo(PinnedKeys.PinReason.MANUAL);
        assertThat(repo.getPinnedRowReason(18)).isEqualTo(PinnedKeys.PinReason.MANUAL);
        assertThat(repo.get(18)).isNotNull();
    }

    @Test
    void hiddenManualPinsFallBackToNormalView() {
        var crafting = new TestKey("crafting_hidden");
        PinnedKeys.pinKey(crafting, PinnedKeys.PinReason.CRAFTING);

        var entries = new ArrayList<GridInventoryEntry>();
        entries.add(new GridInventoryEntry(1, crafting, 1, 0, true));

        for (int i = 0; i < 10; i++) {
            var key = new TestKey("hidden_manual_" + i);
            PinnedKeys.pinKey(key, PinnedKeys.PinReason.MANUAL);
            entries.add(new GridInventoryEntry(10 + i, key, 10 - i, 0, false));
        }

        repo.handleUpdate(true, entries);
        repo.setShowManualPinnedRow(false);
        repo.rebuildView();

        assertThat(repo.getPinnedRowCount()).isEqualTo(1);
        assertThat(repo.getTotalDisplayRows()).isEqualTo(3);
        assertThat(repo.getPinnedRowReason(9)).isNull();
        assertThat(repo.get(18)).isNotNull();
        assertThat(repo.getPinnedEntries(PinnedKeys.PinReason.MANUAL)).isEmpty();
    }
}
