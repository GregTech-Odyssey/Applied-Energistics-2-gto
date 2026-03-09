package gto_ae.core.localization;

public enum ExtendedLangs implements IExtendedLocalizationEnum {
    FacilityManagementShort("设施管理", "Facility Management"),
    WorkingStatus("工作状态：%s", "Working Status: %s"),
    WorkingStatusIdle("§a空闲§r", "§aIdle§r"),
    WorkingStatusWorking("§e工作中§r", "§eWorking§r"),
    WorkingStatusBusy("§c繁忙§r", "§cBusy§r"),
    WorkingStatusIdleTooltip("该设施当前处于空闲状态。", "This facility is currently idle."),
    WorkingStatusWorkingTooltip("该设施当前正在处理任务。", "This facility is currently working on tasks."),
    WorkingStatusBusyTooltip("该设施当前处于满负荷状态。", "This facility is currently in a full workload."),
    RecentThroughput("近期吞吐量（最近%d秒）", "Recent Throughput (Last %d seconds)"),
    TasksNumInCPU("CPU中的任务数量：%d", "Tasks in CPU: %d"),
    OpenGuiOfThisFacility("打开该设施的GUI界面", "Open GUI of this facility"),
    OpenGuiOfThisFacilityTooltip("打开该设施的GUI界面以查看其详细信息或进行交互",
            "Open the GUI of this facility to view its details or interact with it"),
    ThroughputImportPerSeconds("向网络输入：%s/(%d秒)", "Import throughput: %s/(%d seconds)"),
    ThroughputExportPerSeconds("从网络流出：%s/(%d秒)", "Export throughput: %s/(%d seconds)"),
    ThroughputPerSecondEstimated("[约每秒%s/%s]", "[%s/s estimated]"),
    HighlightFacilityPosAt("该设施位于坐标%s(维度[%s])（距离约%d格）", "The facility is at %s in dimension[%s](about %d blocks away)"),
    HighlightFacilityPos("在世界中高亮显示该设施的位置", "Highlight the facility position in the world"),

    FilterModeOnlyInputTooltip("仅当拥有输入统计的设施才会被显示", "Only facilities with input throughput will be shown"),
    FilterModeOnlyOutputTooltip("仅当拥有输出统计的设施才会被显示", "Only facilities with output throughput will be shown"),
    FilterModeInputAndOutputTooltip("仅当同时拥有输入和输出统计的设施才会被显示",
            "Only facilities with both input and output throughput will be shown"),
    FilterModeNoneTooltip("显示所有设施", "Show all facilities"),
    FilterModeTooltip("设施输入/输出过滤模式", "Facility Input/Output Filter Mode"),

    ThroughputSearchingTooltip("根据吞吐量统计中的物品名称或设备名称搜索设施",
            "Search facilities by item name or device name in throughput statistics"),
    ThroughputFilterSlotTooltip("根据槽位中的物品在吞吐量统计中是否出现来过滤设施",
            "Filter facilities based on whether the item in the slot appears in the throughput statistics"),
    FacilityIconFilterTooltip("根据槽位中的物品所示的设备类型来过滤设施",
            "Filter facilities based on the device type shown by the item in the slot"),

    WorkingStatusFilterModeTooltip("工作状态过滤模式", "Working Status Filter Mode"),
    WorkingStatusFilterModeOnlyIdleTooltip("仅显示空闲的设施", "Only show idle facilities"),
    WorkingStatusFilterModeOnlyWorkingTooltip("仅显示正在工作的设施", "Only show working facilities"),
    WorkingStatusFilterModeOnlyBusyTooltip("仅显示繁忙的设施", "Only show busy facilities"),
    WorkingStatusFilterModeNoneTooltip("显示所有设施", "Show all facilities"),
    WorkingStatusFilterModeWorkingOrBusyTooltip("仅显示正在工作或繁忙的设施", "Only show working or busy facilities"),

    CpuFilterModeTooltip("CPU任务数量过滤模式", "CPU Tasks Filter Mode"),
    CpuFilterModeOnlyZeroTooltip("仅显示CPU中没有任务的设施", "Only show facilities with zero tasks in CPU"),
    CpuFilterModeOnlyOneOrMoreTooltip("仅显示CPU中有一个或以上任务的设施", "Only show facilities with one or more tasks in CPU"),
    CpuFilterModeNoneTooltip("显示所有设施", "Show all facilities"),

    FreezeView("冻结视图", "Freeze View"),
    FreezeViewTooltip("冻结当前设施列表的视图，防止其随设施状态的变化而改变",
            "Freeze the current view of the facility list to prevent it from changing with the facility status"),

    ExpandToggleButton("展开设置", "Expand Settings"),
    CollapseToggleButton("收起设置", "Collapse Settings"),
    ;

    private final String enText;
    private final String cnText;

    ExtendedLangs(String cnText, String englishText) {
        this.enText = englishText;
        this.cnText = cnText;
    }

    @Override
    public String getEnglishText() {
        return enText;
    }

    @Override
    public String getChineseText() {
        return cnText;
    }

    @Override
    public String getTranslationKey() {
        return "ae2.gto_extension." + langKeyName();
    }

    private String langKeyName() {
        var sb = new StringBuilder();
        for (char c : name().toCharArray()) {
            if (Character.isUpperCase(c) && !sb.isEmpty()) {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

}
