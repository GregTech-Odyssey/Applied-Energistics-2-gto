package gto_ae.compat.extendedae;

import gto_ae.helpers.facility_management.ThroughputCounter;

import appeng.api.behaviors.StackTransferContext;

public interface EAEStackTransferContext extends StackTransferContext {
    void gto$ae$setStats(ThroughputCounter gto$ae$stats);
}
