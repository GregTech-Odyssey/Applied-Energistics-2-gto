package gto_ae.helpers.facility_management;

import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import gto_ae.api.util.DirectionalGlobalPos;

import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.util.ConfigInventory;

public class FacilityManagement implements IFacilityManagement {

    private final IFacilityManagementHost host;
    private IO filterMode = IO.NONE;
    @Nullable
    private WorkingStatus workingStatus = null;
    private Set<DirectionalGlobalPos> savedView = Set.of();

    private final ConfigInventory ioFilterInv = ConfigInventory.configTypes(1, this::saveChanges);
    private final ConfigInventory facilityFilterInv = ConfigInventory.configTypes(1, this::saveChanges);

    private boolean isLoading = false;

    public FacilityManagement(IFacilityManagementHost host) {
        this.host = host;
    }

    @Override
    public AEKey getFilter() {
        return ioFilterInv.getKey(0);
    }

    @Override
    public void setFilter(AEKey newFilter) {
        ioFilterInv.setStack(0, newFilter == null ? null : new GenericStack(newFilter, 1));
    }

    @Override
    public AEKey getFacilityItem() {
        return facilityFilterInv.getKey(0);
    }

    @Override
    public void setFacilityItem(AEKey key) {
        facilityFilterInv.setStack(0, key == null ? null : new GenericStack(key, 1));
    }

    @Override
    public IFacilityManagementHost getHost() {
        return host;
    }

    @Override
    public void onChangeInventory(InternalInventory inv, int slot) {
        saveChanges();
    }

    @Override
    public void saveChanges() {
        // Do not re-save while we're loading since it could overwrite the NBT with incomplete data
        if (!isLoading) {
            host.markForSave();
        }
    }

    @Override
    public ConfigInventory getIoFilterInv() {
        return ioFilterInv;
    }

    @Override
    public ConfigInventory getFacilityIconFilterInv() {
        return facilityFilterInv;
    }

    @Override
    public boolean isClientSide() {
        return host.getLevel().isClientSide();
    }

    public void serializeNBT(CompoundTag tag) {
        ioFilterInv.writeToChildTag(tag, "ioFilterInv");
        facilityFilterInv.writeToChildTag(tag, "facilityFilterInv");
        tag.putString("filterMode", filterMode.name());
        if (workingStatus == null) {
            tag.putBoolean("hasWorkingStatus", false);
        } else {
            tag.putBoolean("hasWorkingStatus", true);
            tag.putString("workingStatus", workingStatus.name());
        }
        writeSavedView(tag);
    }

    public void deserializeNBT(CompoundTag tag) {
        isLoading = true;
        try {
            ioFilterInv.readFromChildTag(tag, "ioFilterInv");
            facilityFilterInv.readFromChildTag(tag, "facilityFilterInv");
            filterMode = IO.valueOf(tag.getString("filterMode"));
            if (tag.getBoolean("hasWorkingStatus")) {
                workingStatus = WorkingStatus.valueOf(tag.getString("workingStatus"));
            } else {
                workingStatus = null;
            }
            readSavedView(tag);
        } finally {
            isLoading = false;
        }
    }

    @Override
    public void saveView(Set<DirectionalGlobalPos> positions) {
        if (!isClientSide()) {
            this.savedView = positions;
            getHost().markForSave();
        }
    }

    @Override
    public Set<DirectionalGlobalPos> getSavedView() {
        return savedView;
    }

    private void writeSavedView(CompoundTag tag) {

        ListTag list = new ListTag();
        for (DirectionalGlobalPos pos : savedView) {
            CompoundTag posTag = new CompoundTag();
            DirectionalGlobalPos.writeToTag(posTag, "p", pos);
            list.add(posTag);
        }
        tag.put("savedView", list);
    }

    private void readSavedView(CompoundTag tag) {
        savedView = new HashSet<>();
        ListTag list = tag.getList("savedView", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag posTag = list.getCompound(i);
            DirectionalGlobalPos pos = DirectionalGlobalPos.readFromTag(posTag, "p");
            savedView.add(pos);
        }
    }
}
