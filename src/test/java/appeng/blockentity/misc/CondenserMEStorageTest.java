package appeng.blockentity.misc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import appeng.api.config.CondenserOutput;
import appeng.api.config.Settings;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlockEntities;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.BaseActionSource;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class CondenserMEStorageTest {
    CondenserBlockEntity be = new CondenserBlockEntity(AEBlockEntities.CONDENSER, BlockPos.ZERO,
            AEBlocks.CONDENSER.block().defaultBlockState());
    MEStorage inv = be.getMEStorage();

    @Test
    void testSingularityProductionAndPriority() {
        assertThat(inv).isNotNull();
        assertThat(inv).isInstanceOf(CondenserMEStorage.class);
        assertThat(inv.getAvailableStacks()).isEmpty();

        CondenserOutput.SINGULARITY.requiredPower = 256000;

        be.getConfigManager().putSetting(Settings.CONDENSER_OUTPUT, CondenserOutput.SINGULARITY);
        be.getInternalInventory().setItemDirect(2, AEItems.CELL_COMPONENT_64K.stack());
        be.addPower(99999999999.0);

        var singularity = AEItemKey.of(AEItems.SINGULARITY.asItem());
        assertThat(inv.getAvailableStacks())
                .extracting(Map.Entry::getKey)
                .containsOnly(singularity);
        assertThat(inv.getAvailableStacks().get(singularity)).isEqualTo(2);
        assertThat(inv.isPreferredStorageFor(singularity, new BaseActionSource())).isFalse();
    }
}
