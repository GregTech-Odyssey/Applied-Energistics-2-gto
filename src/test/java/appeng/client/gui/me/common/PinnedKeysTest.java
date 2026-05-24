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
}
