package gto_ae.api.config;

import static appeng.api.config.Settings.register;
import static appeng.client.gui.widgets.SettingToggleButton.registerApp;

import gto_ae.client.gui.IconsExtended;
import gto_ae.core.localization.ExtendedLangs;
import gto_ae.helpers.facility_management.IO;
import gto_ae.helpers.facility_management.WorkingStatus;

import appeng.api.config.*;

public final class ExtendedSettings {
    public static final Setting<WorkingStatus> WORKING_STATUS_SETTING = register("fmt_working_status",
            WorkingStatus.class);
    public static final Setting<YesNo> HAS_CPU_TASK = register("fmt_has_cpu_task", YesNo.class);
    public static final Setting<IO> FILTER_MODE = register("fmt_io_filter_mode", IO.class);

    static {
        registerApp(IconsExtended.WORLING_STATUS_IDLE, WORKING_STATUS_SETTING, WorkingStatus.IDLE,
                ExtendedLangs.WorkingStatusFilterModeTooltip,
                ExtendedLangs.WorkingStatusFilterModeOnlyIdleTooltip);
        registerApp(IconsExtended.WORLING_STATUS_WORKING, WORKING_STATUS_SETTING, WorkingStatus.WORKING,
                ExtendedLangs.WorkingStatusFilterModeTooltip,
                ExtendedLangs.WorkingStatusFilterModeOnlyWorkingTooltip);
        registerApp(IconsExtended.WORLING_STATUS_BUSY, WORKING_STATUS_SETTING, WorkingStatus.BUSY,
                ExtendedLangs.WorkingStatusFilterModeTooltip,
                ExtendedLangs.WorkingStatusFilterModeOnlyBusyTooltip);
        registerApp(IconsExtended.WORLING_STATUS_WORKING_OR_BUSY, WORKING_STATUS_SETTING,
                WorkingStatus.WORKING_OR_BUSY,
                ExtendedLangs.WorkingStatusFilterModeTooltip,
                ExtendedLangs.WorkingStatusFilterModeWorkingOrBusyTooltip);
        registerApp(IconsExtended.WORLING_STATUS_NONE, WORKING_STATUS_SETTING, WorkingStatus.NONE,
                ExtendedLangs.WorkingStatusFilterModeTooltip,
                ExtendedLangs.WorkingStatusFilterModeNoneTooltip);

        registerApp(IconsExtended.HAS_CPU_WORKS, HAS_CPU_TASK, YesNo.YES,
                ExtendedLangs.CpuFilterModeTooltip,
                ExtendedLangs.CpuFilterModeOnlyOneOrMoreTooltip);
        registerApp(IconsExtended.HAS_NO_CPU_WORKS, HAS_CPU_TASK, YesNo.NO,
                ExtendedLangs.CpuFilterModeTooltip,
                ExtendedLangs.CpuFilterModeOnlyZeroTooltip);
        registerApp(IconsExtended.CPU_WORKS_BOTH, HAS_CPU_TASK, YesNo.UNDECIDED,
                ExtendedLangs.CpuFilterModeTooltip,
                ExtendedLangs.CpuFilterModeNoneTooltip);

        registerApp(IconsExtended.FILTER_INPUT_ONLY, FILTER_MODE, IO.IN,
                ExtendedLangs.FilterModeTooltip,
                ExtendedLangs.FilterModeOnlyInputTooltip);
        registerApp(IconsExtended.FILTER_OUTPUT_ONLY, FILTER_MODE, IO.OUT,
                ExtendedLangs.FilterModeTooltip,
                ExtendedLangs.FilterModeOnlyOutputTooltip);
        registerApp(IconsExtended.FILTER_BOTH, FILTER_MODE, IO.BOTH,
                ExtendedLangs.FilterModeTooltip,
                ExtendedLangs.FilterModeInputAndOutputTooltip);
        registerApp(IconsExtended.FILTER_NONE, FILTER_MODE, IO.NONE,
                ExtendedLangs.FilterModeTooltip,
                ExtendedLangs.FilterModeNoneTooltip);

    }
}
