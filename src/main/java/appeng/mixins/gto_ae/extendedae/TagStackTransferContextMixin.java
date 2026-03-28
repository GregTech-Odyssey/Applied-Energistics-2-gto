package appeng.mixins.gto_ae.extendedae;

import com.glodblock.github.extendedae.common.me.taglist.TagStackTransferContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import gto_ae.compat.extendedae.EAEStackTransferContext;
import gto_ae.helpers.facility_management.ThroughputCounter;

@Mixin(TagStackTransferContext.class)
public abstract class TagStackTransferContextMixin implements EAEStackTransferContext {

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
