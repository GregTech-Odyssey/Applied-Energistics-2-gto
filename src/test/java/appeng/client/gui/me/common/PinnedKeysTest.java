package appeng.client.gui.me.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PinnedKeysTest {

    @BeforeEach
    @AfterEach
    void clearPinnedKeys() {
        PinnedKeys.clearPinnedKeys();
    }

    @Test
    void manualPinsAreNotPrunedWhenCraftingPinsOverflow() {
        var manualA = new TestKey("manual_a");
        var manualB = new TestKey("manual_b");

        PinnedKeys.pinKey(manualA, PinnedKeys.PinReason.MANUAL);
        PinnedKeys.pinKey(manualB, PinnedKeys.PinReason.MANUAL);

        for (int i = 0; i < PinnedKeys.CRAFTING_MAX_PINNED + 1; i++) {
            PinnedKeys.pinKey(new TestKey("craft_" + i), PinnedKeys.PinReason.CRAFTING);
        }

        assertThat(PinnedKeys.getPinnedKeys(PinnedKeys.PinReason.CRAFTING))
                .hasSize(PinnedKeys.CRAFTING_MAX_PINNED);
        assertThat(PinnedKeys.getPinnedKeys(PinnedKeys.PinReason.MANUAL))
                .containsExactlyInAnyOrder(manualA, manualB);
    }

    @Test
    void manualPinPromotesCraftingPin() {
        var key = new TestKey("shared");

        PinnedKeys.pinKey(key, PinnedKeys.PinReason.CRAFTING);
        PinnedKeys.pinKey(key, PinnedKeys.PinReason.MANUAL);

        assertThat(PinnedKeys.getPinInfo(key).reason).isEqualTo(PinnedKeys.PinReason.MANUAL);
        assertThat(PinnedKeys.isPinned(key, PinnedKeys.PinReason.CRAFTING)).isFalse();
    }

    @Test
    void craftingPinDoesNotOverrideManualPin() {
        var key = new TestKey("manual_first");

        PinnedKeys.pinKey(key, PinnedKeys.PinReason.MANUAL);
        PinnedKeys.pinKey(key, PinnedKeys.PinReason.CRAFTING);

        assertThat(PinnedKeys.getPinInfo(key).reason).isEqualTo(PinnedKeys.PinReason.MANUAL);
        assertThat(PinnedKeys.isPinned(key, PinnedKeys.PinReason.MANUAL)).isTrue();
    }

    @Test
    void pinsAreIndexedByReason() {
        var crafting = new TestKey("crafting_indexed");
        var manual = new TestKey("manual_indexed");

        PinnedKeys.pinKey(crafting, PinnedKeys.PinReason.CRAFTING);
        PinnedKeys.pinKey(manual, PinnedKeys.PinReason.MANUAL);

        assertThat(PinnedKeys.getPinned(PinnedKeys.PinReason.CRAFTING))
                .containsOnlyKeys(crafting);
        assertThat(PinnedKeys.getPinned(PinnedKeys.PinReason.MANUAL))
                .containsOnlyKeys(manual);
        assertThat(PinnedKeys.getPinnedByReason())
                .containsKeys(PinnedKeys.PinReason.CRAFTING, PinnedKeys.PinReason.MANUAL);
    }

    @Test
    void promotingPinUpdatesReasonBuckets() {
        var key = new TestKey("promoted");

        PinnedKeys.pinKey(key, PinnedKeys.PinReason.CRAFTING);
        PinnedKeys.pinKey(key, PinnedKeys.PinReason.MANUAL);

        assertThat(PinnedKeys.getPinned(PinnedKeys.PinReason.CRAFTING))
                .doesNotContainKey(key);
        assertThat(PinnedKeys.getPinned(PinnedKeys.PinReason.MANUAL))
                .containsOnlyKeys(key);
    }

    @Test
    void pruningKeepsReasonBucketsInSync() {
        var crafting = new TestKey("crafting_prune");
        var manual = new TestKey("manual_prune");

        PinnedKeys.pinKey(crafting, PinnedKeys.PinReason.CRAFTING);
        PinnedKeys.pinKey(manual, PinnedKeys.PinReason.MANUAL);

        PinnedKeys.getPinInfo(crafting).canPrune = true;
        PinnedKeys.prune();

        assertThat(PinnedKeys.getPinned(PinnedKeys.PinReason.CRAFTING))
                .isEmpty();
        assertThat(PinnedKeys.getPinned(PinnedKeys.PinReason.MANUAL))
                .containsOnlyKeys(manual);
        assertThat(PinnedKeys.getPinInfo(crafting)).isNull();
    }
}
