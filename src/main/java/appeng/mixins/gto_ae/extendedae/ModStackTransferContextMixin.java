package appeng.mixins.gto_ae.extendedae;

import com.glodblock.github.extendedae.common.me.modlist.ModStackTransferContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import gto_ae.compat.extendedae.EAEStackTransferContext;
import gto_ae.helpers.facility_management.ThroughputCounter;

@Mixin(ModStackTransferContext.class)
public abstract class ModStackTransferContextMixin implements EAEStackTransferContext {

    @Unique
    private ThroughputCounter gto$ae$stats;

    @Override
    public ThroughputCounter getStats() {
        return gto$ae$stats;
    }

    @Override
    public void gto$ae$setStats(ThroughputCounter gto$ae$stats) {
        this.gto$ae$stats = gto$ae$stats;
    }
}
