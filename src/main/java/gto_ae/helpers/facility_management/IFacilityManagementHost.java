package gto_ae.helpers.facility_management;

import net.minecraft.world.level.Level;

import appeng.api.config.YesNo;
import appeng.api.util.IConfigurableObject;

public interface IFacilityManagementHost extends IConfigurableObject {
    FacilityManagement getLogic();

    Level getLevel();

    void markForSave();

    IO getIOModeFilter();

    void setFilterMode(IO newMode);

    WorkingStatus getWorkingFilter();

    void setWorkingFilter(WorkingStatus newStatus);

    YesNo getCraftingJobsFilter();

    void setFilterCraftingJobs(YesNo newValue);
}
