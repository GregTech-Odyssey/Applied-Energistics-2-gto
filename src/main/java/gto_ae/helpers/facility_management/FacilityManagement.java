package gto_ae.helpers.facility_management;

import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.util.ConfigInventory;

import gto_ae.api.util.DirectionalGlobalPos;

public class FacilityManagement implements IFacilityManagement {

    private static final String TAG_IO_FILTER_INV = "ioFilterInv";
    private static final String TAG_FACILITY_FILTER_INV = "facilityFilterInv";
    private static final String TAG_FILTER_MODE = "filterMode";
    private static final String TAG_HAS_WORKING_STATUS = "hasWorkingStatus";
    private static final String TAG_WORKING_STATUS = "workingStatus";
    private static final String TAG_SAVED_VIEW = "savedView";
    private static final String TAG_SAVED_VIEW_POS = "p";

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
        ioFilterInv.writeToChildTag(tag, TAG_IO_FILTER_INV);
        facilityFilterInv.writeToChildTag(tag, TAG_FACILITY_FILTER_INV);
        tag.putString(TAG_FILTER_MODE, filterMode.name());
        if (workingStatus == null) {
            tag.putBoolean(TAG_HAS_WORKING_STATUS, false);
        } else {
            tag.putBoolean(TAG_HAS_WORKING_STATUS, true);
            tag.putString(TAG_WORKING_STATUS, workingStatus.name());
        }
        writeSavedView(tag);
    }

    public void deserializeNBT(CompoundTag tag) {
        isLoading = true;
        try {
            ioFilterInv.readFromChildTag(tag, TAG_IO_FILTER_INV);
            facilityFilterInv.readFromChildTag(tag, TAG_FACILITY_FILTER_INV);
            filterMode = readFilterMode(tag);
            if (tag.getBoolean(TAG_HAS_WORKING_STATUS)) {
                workingStatus = readWorkingStatus(tag);
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
            DirectionalGlobalPos.writeToTag(posTag, TAG_SAVED_VIEW_POS, pos);
            list.add(posTag);
        }
        tag.put(TAG_SAVED_VIEW, list);
    }

    private void readSavedView(CompoundTag tag) {
        savedView = new HashSet<>();
        ListTag list = tag.getList(TAG_SAVED_VIEW, 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag posTag = list.getCompound(i);
            DirectionalGlobalPos pos = DirectionalGlobalPos.readFromTag(posTag, TAG_SAVED_VIEW_POS);
            savedView.add(pos);
        }
    }

    private static IO readFilterMode(CompoundTag tag) {
        try {
            return IO.valueOf(tag.getString(TAG_FILTER_MODE));
        } catch (IllegalArgumentException ignored) {
            return IO.NONE;
        }
    }

    @Nullable
    private static WorkingStatus readWorkingStatus(CompoundTag tag) {
        try {
            return WorkingStatus.valueOf(tag.getString(TAG_WORKING_STATUS));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
