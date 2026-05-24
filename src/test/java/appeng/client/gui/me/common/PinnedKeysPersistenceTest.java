package appeng.client.gui.me.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.world.item.Items;

import appeng.api.stacks.AEItemKey;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class PinnedKeysPersistenceTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        PinnedKeys.setClientCacheOverrideForTests(tempDir.resolve("pinned_keys.json").toFile());
        PinnedKeys.clearPinnedKeys();
    }

    @AfterEach
    void tearDown() {
        PinnedKeys.clearPinnedKeys();
        PinnedKeys.setClientCacheOverrideForTests(null);
    }

    @Test
    void reloadRestoresManualPinsFromClientCache() {
        var manual = AEItemKey.of(Items.STONE);
        var crafting = AEItemKey.of(Items.DIRT);

        PinnedKeys.pinKey(manual, PinnedKeys.PinReason.MANUAL);
        PinnedKeys.pinKey(crafting, PinnedKeys.PinReason.CRAFTING);

        assertThat(tempDir.resolve("pinned_keys.json")).exists();

        PinnedKeys.clearPinnedKeys();
        PinnedKeys.reloadClientCache();

        assertThat(PinnedKeys.isPinned(manual, PinnedKeys.PinReason.MANUAL)).isTrue();
        assertThat(PinnedKeys.isPinned(crafting, PinnedKeys.PinReason.CRAFTING)).isFalse();
    }
}
