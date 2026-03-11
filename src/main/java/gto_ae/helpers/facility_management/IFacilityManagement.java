package gto_ae.helpers.facility_management;

import java.util.Set;

import appeng.api.stacks.AEKey;
import appeng.util.ConfigInventory;
import appeng.util.inv.InternalInventoryHost;

import gto_ae.api.util.DirectionalGlobalPos;

public interface IFacilityManagement extends InternalInventoryHost {
    AEKey getFilter();

    void setFilter(AEKey newFilter);

    AEKey getFacilityItem();

    void setFacilityItem(AEKey clazz);

    IFacilityManagementHost getHost();

    ConfigInventory getIoFilterInv();

    ConfigInventory getFacilityIconFilterInv();

    void saveView(Set<DirectionalGlobalPos> positions);

    Set<DirectionalGlobalPos> getSavedView();
}
