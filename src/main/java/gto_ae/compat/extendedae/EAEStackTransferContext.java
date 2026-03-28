package gto_ae.compat.extendedae;

import appeng.api.behaviors.StackTransferContext;

import gto_ae.helpers.facility_management.ThroughputCounter;

public interface EAEStackTransferContext extends StackTransferContext {
    void gto$ae$setStats(ThroughputCounter gto$ae$stats);
}
