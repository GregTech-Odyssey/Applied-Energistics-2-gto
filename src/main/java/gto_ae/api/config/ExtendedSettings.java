package gto_ae.api.config;

import static appeng.api.config.Settings.register;

import appeng.api.config.*;

import gto_ae.helpers.facility_management.IO;
import gto_ae.helpers.facility_management.WorkingStatus;
import gto_ae.menu.ShowMolecularAssembler;

public final class ExtendedSettings {
    public static final Setting<WorkingStatus> WORKING_STATUS_SETTING = register("fmt_working_status",
            WorkingStatus.class);
    public static final Setting<WorkingStatus> CPU_WORKING_STATUS_SETTING = register("cpu_working_status",
            WorkingStatus.IDLE, WorkingStatus.WORKING, WorkingStatus.NONE);
    public static final Setting<YesNo> HAS_CPU_TASK = register("fmt_has_cpu_task", YesNo.class);
    public static final Setting<IO> FILTER_MODE = register("fmt_io_filter_mode", IO.class);
    public static final Setting<ShowMolecularAssembler> TERMINAL_SHOW_MOLECULAR_ASSEMBLERS = Settings.register(
            "show_molecular_assemblers",
            ShowMolecularAssembler.ALL,
            ShowMolecularAssembler.ONLY_MOLECULAR_ASSEMBLER,
            ShowMolecularAssembler.EXPECT_MOLECULAR_ASSEMBLER);

}
