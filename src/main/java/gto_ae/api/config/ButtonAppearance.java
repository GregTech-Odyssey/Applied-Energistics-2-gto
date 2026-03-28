package gto_ae.api.config;

import static appeng.client.gui.widgets.SettingToggleButton.registerApp;
import static gto_ae.api.config.ExtendedSettings.*;

import appeng.api.config.*;
import appeng.client.gui.Icon;
import appeng.core.definitions.AEParts;
import appeng.core.localization.ButtonToolTips;

import gto_ae.client.gui.IconsExtended;
import gto_ae.core.localization.ExtendedLangs;
import gto_ae.helpers.facility_management.IO;
import gto_ae.helpers.facility_management.WorkingStatus;
import gto_ae.menu.ShowMolecularAssembler;

public final class ButtonAppearance {

    public static void init() {

        // ==== Basic AE2 Settings ====
        registerApp(Icon.CONDENSER_OUTPUT_TRASH, Settings.CONDENSER_OUTPUT, CondenserOutput.TRASH,
                ButtonToolTips.CondenserOutput,
                ButtonToolTips.Trash);
        registerApp(Icon.CONDENSER_OUTPUT_MATTER_BALL, Settings.CONDENSER_OUTPUT, CondenserOutput.MATTER_BALLS,
                ButtonToolTips.CondenserOutput,
                ButtonToolTips.MatterBalls.text(CondenserOutput.MATTER_BALLS.requiredPower));
        registerApp(Icon.CONDENSER_OUTPUT_SINGULARITY, Settings.CONDENSER_OUTPUT, CondenserOutput.SINGULARITY,
                ButtonToolTips.CondenserOutput,
                ButtonToolTips.Singularity.text(CondenserOutput.SINGULARITY.requiredPower));

        registerApp(Icon.ACCESS_READ, Settings.ACCESS, AccessRestriction.READ, ButtonToolTips.IOMode,
                ButtonToolTips.Read);
        registerApp(Icon.ACCESS_WRITE, Settings.ACCESS, AccessRestriction.WRITE, ButtonToolTips.IOMode,
                ButtonToolTips.Write);
        registerApp(Icon.ACCESS_READ_WRITE, Settings.ACCESS, AccessRestriction.READ_WRITE, ButtonToolTips.IOMode,
                ButtonToolTips.ReadWrite);

        registerApp(Icon.POWER_UNIT_AE, Settings.POWER_UNITS, PowerUnits.AE, ButtonToolTips.PowerUnits,
                PowerUnits.AE.textComponent());
        // registerApp(Icon.POWER_UNIT_EU, Settings.POWER_UNITS, PowerUnits.EU, ButtonToolTips.PowerUnits,
        // PowerUnits.EU.textComponent());
        registerApp(Icon.POWER_UNIT_RF, Settings.POWER_UNITS, PowerUnits.FE, ButtonToolTips.PowerUnits,
                PowerUnits.FE.textComponent());

        registerApp(Icon.REDSTONE_IGNORE, Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE,
                ButtonToolTips.RedstoneMode,
                ButtonToolTips.AlwaysActive);
        registerApp(Icon.REDSTONE_LOW, Settings.REDSTONE_CONTROLLED, RedstoneMode.LOW_SIGNAL,
                ButtonToolTips.RedstoneMode,
                ButtonToolTips.ActiveWithoutSignal);
        registerApp(Icon.REDSTONE_HIGH, Settings.REDSTONE_CONTROLLED, RedstoneMode.HIGH_SIGNAL,
                ButtonToolTips.RedstoneMode,
                ButtonToolTips.ActiveWithSignal);
        registerApp(Icon.REDSTONE_PULSE, Settings.REDSTONE_CONTROLLED, RedstoneMode.SIGNAL_PULSE,
                ButtonToolTips.RedstoneMode,
                ButtonToolTips.ActiveOnPulse);

        registerApp(Icon.REDSTONE_LOW, Settings.REDSTONE_EMITTER, RedstoneMode.LOW_SIGNAL,
                ButtonToolTips.RedstoneMode,
                ButtonToolTips.EmitLevelsBelow);
        registerApp(Icon.REDSTONE_HIGH, Settings.REDSTONE_EMITTER, RedstoneMode.HIGH_SIGNAL,
                ButtonToolTips.RedstoneMode,
                ButtonToolTips.EmitLevelAbove);

        registerApp(Icon.ARROW_LEFT, Settings.OPERATION_MODE, OperationMode.FILL,
                ButtonToolTips.TransferDirection,
                ButtonToolTips.TransferToStorageCell);
        registerApp(Icon.ARROW_RIGHT, Settings.OPERATION_MODE, OperationMode.EMPTY,
                ButtonToolTips.TransferDirection,
                ButtonToolTips.TransferToNetwork);

        registerApp(Icon.ARROW_LEFT, Settings.IO_DIRECTION, RelativeDirection.LEFT,
                ButtonToolTips.TransferDirection,
                ButtonToolTips.TransferToStorageCell);
        registerApp(Icon.ARROW_RIGHT, Settings.IO_DIRECTION, RelativeDirection.RIGHT,
                ButtonToolTips.TransferDirection,
                ButtonToolTips.TransferToNetwork);

        registerApp(Icon.ARROW_UP, Settings.SORT_DIRECTION, SortDir.ASCENDING, ButtonToolTips.SortOrder,
                ButtonToolTips.Ascending);
        registerApp(Icon.ARROW_DOWN, Settings.SORT_DIRECTION, SortDir.DESCENDING, ButtonToolTips.SortOrder,
                ButtonToolTips.Descending);

        registerApp(Icon.TERMINAL_STYLE_SMALL, Settings.TERMINAL_STYLE, TerminalStyle.SMALL,
                ButtonToolTips.TerminalStyle,
                ButtonToolTips.TerminalStyle_Small);
        registerApp(Icon.TERMINAL_STYLE_MEDIUM, Settings.TERMINAL_STYLE, TerminalStyle.MEDIUM,
                ButtonToolTips.TerminalStyle,
                ButtonToolTips.TerminalStyle_Medium);
        registerApp(Icon.TERMINAL_STYLE_TALL, Settings.TERMINAL_STYLE, TerminalStyle.TALL,
                ButtonToolTips.TerminalStyle,
                ButtonToolTips.TerminalStyle_Tall);
        registerApp(Icon.TERMINAL_STYLE_FULL, Settings.TERMINAL_STYLE, TerminalStyle.FULL,
                ButtonToolTips.TerminalStyle,
                ButtonToolTips.TerminalStyle_Full);

        registerApp(Icon.SORT_BY_NAME, Settings.SORT_BY, SortOrder.NAME, ButtonToolTips.SortBy,
                ButtonToolTips.ItemName);
        registerApp(Icon.SORT_BY_AMOUNT, Settings.SORT_BY, SortOrder.AMOUNT, ButtonToolTips.SortBy,
                ButtonToolTips.NumberOfItems);
        registerApp(Icon.SORT_BY_MOD, Settings.SORT_BY, SortOrder.MOD, ButtonToolTips.SortBy, ButtonToolTips.Mod);

        registerApp(Icon.VIEW_MODE_STORED, Settings.VIEW_MODE, ViewItems.STORED, ButtonToolTips.View,
                ButtonToolTips.StoredItems);
        registerApp(Icon.VIEW_MODE_ALL, Settings.VIEW_MODE, ViewItems.ALL, ButtonToolTips.View,
                ButtonToolTips.StoredCraftable);
        registerApp(Icon.VIEW_MODE_CRAFTING, Settings.VIEW_MODE, ViewItems.CRAFTABLE, ButtonToolTips.View,
                ButtonToolTips.Craftable);

        registerApp(Icon.TYPE_FILTER_ALL, Settings.TYPE_FILTER, TypeFilter.ALL, ButtonToolTips.TypeFilter,
                ButtonToolTips.ShowAll);
        registerApp(Icon.TYPE_FILTER_ITEMS, Settings.TYPE_FILTER, TypeFilter.ITEMS, ButtonToolTips.TypeFilter,
                ButtonToolTips.ShowItemsOnly);
        registerApp(Icon.TYPE_FILTER_FLUIDS, Settings.TYPE_FILTER, TypeFilter.FLUIDS, ButtonToolTips.TypeFilter,
                ButtonToolTips.ShowFluidsOnly);

        registerApp(Icon.FUZZY_PERCENT_25, Settings.FUZZY_MODE, FuzzyMode.PERCENT_25, ButtonToolTips.FuzzyMode,
                ButtonToolTips.FZPercent_25);
        registerApp(Icon.FUZZY_PERCENT_50, Settings.FUZZY_MODE, FuzzyMode.PERCENT_50, ButtonToolTips.FuzzyMode,
                ButtonToolTips.FZPercent_50);
        registerApp(Icon.FUZZY_PERCENT_75, Settings.FUZZY_MODE, FuzzyMode.PERCENT_75, ButtonToolTips.FuzzyMode,
                ButtonToolTips.FZPercent_75);
        registerApp(Icon.FUZZY_PERCENT_99, Settings.FUZZY_MODE, FuzzyMode.PERCENT_99, ButtonToolTips.FuzzyMode,
                ButtonToolTips.FZPercent_99);
        registerApp(Icon.FUZZY_IGNORE, Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL, ButtonToolTips.FuzzyMode,
                ButtonToolTips.FZIgnoreAll);

        registerApp(Icon.FULLNESS_EMPTY, Settings.FULLNESS_MODE, FullnessMode.EMPTY, ButtonToolTips.OperationMode,
                ButtonToolTips.MoveWhenEmpty);
        registerApp(Icon.FULLNESS_HALF, Settings.FULLNESS_MODE, FullnessMode.HALF, ButtonToolTips.OperationMode,
                ButtonToolTips.MoveWhenWorkIsDone);
        registerApp(Icon.FULLNESS_FULL, Settings.FULLNESS_MODE, FullnessMode.FULL, ButtonToolTips.OperationMode,
                ButtonToolTips.MoveWhenFull);

        registerApp(Icon.BLOCKING_MODE_YES, Settings.BLOCKING_MODE, YesNo.YES, ButtonToolTips.InterfaceBlockingMode,
                ButtonToolTips.Blocking);
        registerApp(Icon.BLOCKING_MODE_NO, Settings.BLOCKING_MODE, YesNo.NO, ButtonToolTips.InterfaceBlockingMode,
                ButtonToolTips.NonBlocking);

        registerApp(Icon.VIEW_MODE_CRAFTING, Settings.CRAFT_ONLY, YesNo.YES, ButtonToolTips.Craft,
                ButtonToolTips.CraftOnly);
        registerApp(Icon.VIEW_MODE_ALL, Settings.CRAFT_ONLY, YesNo.NO, ButtonToolTips.Craft,
                ButtonToolTips.CraftEither);

        registerApp(Icon.CRAFT_HAMMER, Settings.CRAFT_VIA_REDSTONE, YesNo.YES, ButtonToolTips.EmitterMode,
                ButtonToolTips.CraftViaRedstone);
        registerApp(Icon.ACCESS_READ, Settings.CRAFT_VIA_REDSTONE, YesNo.NO, ButtonToolTips.EmitterMode,
                ButtonToolTips.EmitWhenCrafting);

        registerApp(Icon.STORAGE_FILTER_EXTRACTABLE_ONLY, Settings.STORAGE_FILTER, StorageFilter.EXTRACTABLE_ONLY,
                ButtonToolTips.ReportInaccessibleItems, ButtonToolTips.ReportInaccessibleItemsNo);
        registerApp(Icon.STORAGE_FILTER_EXTRACTABLE_NONE, Settings.STORAGE_FILTER, StorageFilter.NONE,
                ButtonToolTips.ReportInaccessibleItems,
                ButtonToolTips.ReportInaccessibleItemsYes);

        registerApp(Icon.PLACEMENT_BLOCK, Settings.PLACE_BLOCK, YesNo.YES, ButtonToolTips.BlockPlacement,
                ButtonToolTips.BlockPlacementYes);
        registerApp(Icon.PLACEMENT_ITEM, Settings.PLACE_BLOCK, YesNo.NO, ButtonToolTips.BlockPlacement,
                ButtonToolTips.BlockPlacementNo);

        registerApp(Icon.SCHEDULING_DEFAULT, Settings.SCHEDULING_MODE, SchedulingMode.DEFAULT,
                ButtonToolTips.SchedulingMode,
                ButtonToolTips.SchedulingModeDefault);
        registerApp(Icon.SCHEDULING_ROUND_ROBIN, Settings.SCHEDULING_MODE, SchedulingMode.ROUNDROBIN,
                ButtonToolTips.SchedulingMode,
                ButtonToolTips.SchedulingModeRoundRobin);
        registerApp(Icon.SCHEDULING_RANDOM, Settings.SCHEDULING_MODE, SchedulingMode.RANDOM,
                ButtonToolTips.SchedulingMode,
                ButtonToolTips.SchedulingModeRandom);

        registerApp(Icon.OVERLAY_OFF, Settings.OVERLAY_MODE, YesNo.NO, ButtonToolTips.OverlayMode,
                ButtonToolTips.OverlayModeNo);
        registerApp(Icon.OVERLAY_ON, Settings.OVERLAY_MODE, YesNo.YES, ButtonToolTips.OverlayMode,
                ButtonToolTips.OverlayModeYes);

        registerApp(Icon.FILTER_ON_EXTRACT_ENABLED, Settings.FILTER_ON_EXTRACT, YesNo.YES,
                ButtonToolTips.FilterOnExtract, ButtonToolTips.FilterOnExtractEnabled);
        registerApp(Icon.FILTER_ON_EXTRACT_DISABLED, Settings.FILTER_ON_EXTRACT, YesNo.NO,
                ButtonToolTips.FilterOnExtract, ButtonToolTips.FilterOnExtractDisabled);

        registerApp(Icon.CRAFT_HAMMER, Settings.CPU_SELECTION_MODE, CpuSelectionMode.ANY,
                ButtonToolTips.CpuSelectionMode, ButtonToolTips.CpuSelectionModeAny);
        registerApp(AEParts.TERMINAL, Settings.CPU_SELECTION_MODE, CpuSelectionMode.PLAYER_ONLY,
                ButtonToolTips.CpuSelectionMode, ButtonToolTips.CpuSelectionModePlayersOnly.text());
        registerApp(AEParts.EXPORT_BUS, Settings.CPU_SELECTION_MODE, CpuSelectionMode.MACHINE_ONLY,
                ButtonToolTips.CpuSelectionMode, ButtonToolTips.CpuSelectionModeAutomationOnly.text());

        registerApp(Icon.PATTERN_TERMINAL_ALL, Settings.TERMINAL_SHOW_PATTERN_PROVIDERS, ShowPatternProviders.ALL,
                ButtonToolTips.InterfaceTerminalDisplayMode,
                ButtonToolTips.ShowAllProviders);
        registerApp(Icon.PATTERN_TERMINAL_VISIBLE, Settings.TERMINAL_SHOW_PATTERN_PROVIDERS,
                ShowPatternProviders.VISIBLE,
                ButtonToolTips.InterfaceTerminalDisplayMode,
                ButtonToolTips.ShowVisibleProviders);
        registerApp(Icon.PATTERN_TERMINAL_NOT_FULL, Settings.TERMINAL_SHOW_PATTERN_PROVIDERS,
                ShowPatternProviders.NOT_FULL,
                ButtonToolTips.InterfaceTerminalDisplayMode,
                ButtonToolTips.ShowNonFullProviders);

        registerApp(Icon.UNLOCKED, Settings.LOCK_CRAFTING_MODE, LockCraftingMode.NONE,
                ButtonToolTips.LockCraftingMode,
                ButtonToolTips.LockCraftingModeNone);
        registerApp(Icon.REDSTONE_LOW, Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_WHILE_HIGH,
                ButtonToolTips.LockCraftingMode,
                ButtonToolTips.LockCraftingWhileRedstoneHigh);
        registerApp(Icon.REDSTONE_HIGH, Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_WHILE_LOW,
                ButtonToolTips.LockCraftingMode,
                ButtonToolTips.LockCraftingWhileRedstoneLow);
        registerApp(Icon.REDSTONE_PULSE, Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_UNTIL_PULSE,
                ButtonToolTips.LockCraftingMode,
                ButtonToolTips.LockCraftingUntilRedstonePulse);
        registerApp(Icon.ENTER, Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_UNTIL_RESULT,
                ButtonToolTips.LockCraftingMode,
                ButtonToolTips.LockCraftingUntilResultReturned);

        registerApp(Icon.INSCRIBER_SEPARATE_SIDES, Settings.INSCRIBER_SEPARATE_SIDES, YesNo.YES,
                ButtonToolTips.InscriberSideness,
                ButtonToolTips.InscriberSidenessSeparate);
        registerApp(Icon.INSCRIBER_COMBINED_SIDES, Settings.INSCRIBER_SEPARATE_SIDES, YesNo.NO,
                ButtonToolTips.InscriberSideness,
                ButtonToolTips.InscriberSidenessCombined);

        registerApp(Icon.AUTO_EXPORT_ON, Settings.AUTO_EXPORT, YesNo.YES,
                ButtonToolTips.AutoExport,
                ButtonToolTips.AutoExportOn);
        registerApp(Icon.AUTO_EXPORT_OFF, Settings.AUTO_EXPORT, YesNo.NO,
                ButtonToolTips.AutoExport,
                ButtonToolTips.AutoExportOff);

        registerApp(Icon.INSCRIBER_BUFFER_HIGH, Settings.INSCRIBER_BUFFER_SIZE, YesNo.YES,
                ButtonToolTips.InscriberBufferSize,
                ButtonToolTips.InscriberBufferHigh);
        registerApp(Icon.INSCRIBER_BUFFER_LOW, Settings.INSCRIBER_BUFFER_SIZE, YesNo.NO,
                ButtonToolTips.InscriberBufferSize,
                ButtonToolTips.InscriberBufferLow);

        // ==== Extended Settings ====
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

        registerApp(IconsExtended.WORLING_STATUS_IDLE, CPU_WORKING_STATUS_SETTING, WorkingStatus.IDLE,
                ExtendedLangs.WorkingStatusFilterModeTooltip,
                ExtendedLangs.WorkingStatusFilterModeOnlyIdleTooltip);
        registerApp(IconsExtended.WORLING_STATUS_WORKING, CPU_WORKING_STATUS_SETTING, WorkingStatus.WORKING,
                ExtendedLangs.WorkingStatusFilterModeTooltip,
                ExtendedLangs.WorkingStatusFilterModeOnlyWorkingTooltip);
        registerApp(IconsExtended.WORLING_STATUS_NONE, CPU_WORKING_STATUS_SETTING, WorkingStatus.NONE,
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

        registerApp(Icon.LEVEL_ITEM, TERMINAL_SHOW_MOLECULAR_ASSEMBLERS, ShowMolecularAssembler.ALL,
                ButtonToolTips.InterfaceTerminalDisplayMode, ExtendedLangs.CraftingShowMolecularAssemblerAll);
        registerApp(Icon.PATTERN_ACCESS_SHOW, TERMINAL_SHOW_MOLECULAR_ASSEMBLERS,
                ShowMolecularAssembler.ONLY_MOLECULAR_ASSEMBLER, ButtonToolTips.InterfaceTerminalDisplayMode,
                ExtendedLangs.CraftingShowMolecularAssemblerOnly);
        registerApp(Icon.PATTERN_ACCESS_HIDE, TERMINAL_SHOW_MOLECULAR_ASSEMBLERS,
                ShowMolecularAssembler.EXPECT_MOLECULAR_ASSEMBLER, ButtonToolTips.InterfaceTerminalDisplayMode,
                ExtendedLangs.CraftingShowMolecularAssemblerExpect);

    }
}
