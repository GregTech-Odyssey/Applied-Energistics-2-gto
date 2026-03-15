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
    RecentThroughput("近期统计（最近%d秒）", "Recent Throughput (last %d seconds)"),
    TasksNumInCPU("CPU中的任务数量：%d", "Tasks in CPU: %d"),
    OpenGuiOfThisFacility("打开该设施的GUI界面", "Open GUI of this facility"),
    OpenGuiOfThisFacilityTooltip("打开该设施的GUI界面以查看其详细信息或进行交互",
            "Open the GUI of this facility to view its details or interact with it"),
    ThroughputImportPerSeconds("向网络输入：%s/(%d秒)", "Import throughput: %s/(%d seconds)"),
    ThroughputExportPerSeconds("从网络流出：%s/(%d秒)", "Export throughput: %s/(%d seconds)"),
    ThroughputPerSecondEstimated("[约每秒%s/s]", "[%s/s estimated]"),
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

    DisplayMachineConfig("机器配置", "Config Display"),
    UseToDisplayMachineConfig("此行用于展示机器配置", "This line is used to display machine configuration"),
    DisplayMachineThroughput("吞吐量显示", "Display Throughput"),
    UseToDisplayMachineThroughput("此行用于展示机器吞吐量", "This line is used to display machine throughput"),
    HoldShiftToScrollThisRow("按住Shift可使用鼠标滚轮水平滚动该行", "Hold Shift to scroll this row horizontally with mouse wheel"),

    FreezeView("冻结视图", "Freeze View"),
    FreezeViewTooltip("冻结当前设施列表的视图，防止其随设施状态的变化而改变",
            "Freeze the current view of the facility list to prevent it from changing with the facility status"),

    ExpandToggleButton("展开设置", "Expand Settings"),
    CollapseToggleButton("收起设置", "Collapse Settings"),

    NoPatternCurrently("当前ME网络中没有存储空白样板", "No blank pattern is currently stored in the ME network"),
    ShiftEncodingDesc("[Shift + 左击] 将样板编码后存入背包", "[Shift + Click] Encode pattern and put it into inventory"),
    ShiftEncodingClearDesc("特殊情况：当编码的样板没有有效输出时，清空背包所有已经编码的样板",
            "Special Case: When the encoded pattern has no valid output, clear all encoded patterns in inventory"),

    FilterByCpuNameOrProductName("按CPU名称或主产物名称过滤", "Filter by CPU name or main product name"),

    CraftingCycleErrorMain("检测到循环依赖，自动合成无法进行", "Cyclic dependency detected, automatic crafting cannot proceed"),
    CraftingCycleErrorCount("\n发现 %s 个环:", "\nFound %s cycles:"),
    CraftingCycleErrorMoreCycles("\n    ... 还有 %s 个环未显示", "\n    ... and %s more cycles not shown"),
    CraftingCycleErrorCycleNumber("\n    环 %s：", "\n    Cycle %s:"),
    CraftingCycleErrorIndent("\n          ", "\n          "),
    CraftingCycleErrorFooter("\n\n请处理所有循环依赖后才可以进行自动合成",
            "\n\nPlease resolve all cyclic dependencies before automatic crafting"),
    CraftingCycleErrorClickInstruction("\n点击物品ID可复制到剪贴板", "\nClick on item ID to copy to clipboard"),
    CraftingCycleErrorItemPrefix("- ", "- "),
    CraftingCycleErrorBracketOpen(" (", " ("),
    CraftingCycleErrorBracketClose(")", ")"),
    CraftingCycleErrorClickToCopy("点击复制: %s", "Click to copy: %s"),
    CraftingShowMolecularAssemblerOnly("只显示合成样板", "Show crafting pattern only"),
    CraftingShowMolecularAssemblerExpect("不显示合成样板", "Expect crafting pattern"),
    CraftingShowMolecularAssemblerAll("默认", "Default"),
    Me2In1ShiftTransferTo("Shift + 左键将样板转移到", "Shift + Left Click to transfer pattern to"),
    Me2In1ShiftTransferToInventoryOrBuffer("背包或缓冲区", "Inventory or buffer"),
    Me2In1ShiftTransferToAccessor("当前页面中空白的样板管理终端", "Blank slots on the current page of the Pattern Terminal"),
    Me2In1EncodeToAccessorTitle("编码到样板管理终端", "Encode to Pattern Terminal"),
    Me2In1EncodeToAccessor("直接编码到当前页面中的空白部分", "Encode directly to the blank slots on the current page of terminal"),
    Me2In1DraggableMarkTooltip("按住并拖动以调整该面板位置", "Hold and drag to adjust the position of this panel"),
    Me2In1MaterialSlot("材料槽", "Material Slot"),
    Me2In1MaterialSlotLine1("将带有材料类型的物品（如xx板，xx杆等）放入此槽位",
            "Place items with materials (such as xx plate, xx rod, etc.) in this slot"),
    Me2In1MaterialSlotLine2("编码时将自动应用材料类型到可替换的物品上",
            "The material type will be automatically applied to replaceable items when encoding"),
    Me2In1AutoEncodeRenamePattern("自动编码重命名样板", "Auto Encode Renaming Pattern"),
    Me2In1AutoEncodeRenamePatternLine1("启用后，所有重命名后的物品将额外编码一份样板，数量与原样板中设置的一致。",
            "When enabled, all renamed items will encode an additional pattern, with the same quantity as set in the original pattern."),
    Me2In1EmiCatalyst("编码默认不填充催化剂", "Shift + Left Click: Fill catalysts into the pattern"),
    Me2In1EmiCatalystFill("Shift + 左击：将催化剂填充至样板", "Shift + Left Click: Fill catalysts into the pattern"),
    Me2In1EmiCatalystVirtual("Ctrl + 左击：将催化剂（虚拟物品）填充至样板", "Hold Ctrl to encode catalysts as virtual item catalysts"),
    Me2In1EmiMultiblockSub("Shift + 左击：编码基础结构和当前模块", "Shift + Click: Encode base structure and this module"),
    Me2In1EmiMultiblockSubAll("Ctrl + 左击：编码到当前模块为止的全部结构", "Ctrl + Click: Encode all modules up to this one"),
    Me2In1EmiGtBatchEncode("Alt + 左击：批量编码，试图替换的材料用黄色标记",
            "Batch encoding is available while holding Alt, materials to be replaced are marked in yellow"),
    Me2In1EmiGtBatchEncodeLine1("替换失败的材料（如该材料不存在这种物品）将在编码时保持原样板的状态",
            "Materials that fail to replace (e.g., the material does not exist for this item) will retain the original state of the pattern during encoding"),
    Me2In1SaveDefaultRenamePattern("保存默认重命名样板，将可以在自动填充时使用！",
            "Save the default renaming pattern, which can be used for auto-filling!"),
    Me2In1AutoSearch("使用EMI填充配方时，", "When using EMI to fill recipes,"),
    Me2In1AutoSearchOn("自动填充配方的目录名称到样板搜索栏中",
            "automatically fills the directory name of the recipe into the pattern search bar"),
    Me2In1AutoSearchOff("不自动在样板终端中搜索", "does not automatically search in the pattern terminal"),
    Me2In1AutoSearchConfig("中键点击以配置自定义目录名称搜索映射", "Middle-click to configure custom directory name search mapping"),
    Me2In1VanillaCraftStation("分子装配", "Molecular Assembl"),
    PatternContentAccessTerminal("样板内容管理终端", "Pattern Content Access Terminal"),
    Me2In1Wireless("无线2合1终端", "Wireless 2-in-1 Terminal"),
    Me2In1("ME2合1终端", "ME 2-in-1 Terminal"),
    Me2In1SearchIn("按样板输入搜索。", "Search by pattern input."),
    Me2In1SearchOut("按样板输出搜索。", "Search by pattern output."),
    Me2In1SearchProvider("按样板供应器名称搜索。", "Search by pattern provider name."),
    Me2In1CollapseOrExpandToolbar("折叠/展开 工具栏", "Collapse/Expand Toolbar"),
    Me2In1CollapseOrExpandToolbarDesc("折叠或展开显示元件与网络工具槽",
            "Collapse or expand the display components and network tool slots"),
    Me2In1ResetPanelPosition("重置面板位置", "Reset Panel Position"),
    Me2In1ResetPanelPositionLine1("重置所有面板位置到默认位置", "Reset all panel positions to default"),
    Me2In1QuickRemovePattern("点击移除以此为主产物的处理样板至缓冲槽",
            "Click to remove patterns with this main product to the buffer slot"),
    Me2In1QuickRemovePatternLine1("shift + 点击以额外移除其合成树中不参与其他样板的处理样板",
            "Shift + Click to additionally remove patterns in its crafting tree that are not involved in other patterns"),
    Me2In1QuantumBridge("安装纠缠奇点", "Install Quantum Entangled Singularity"),
    Me2In1QuantumBridgeInfo("该终端已内置量子环，无需额外插件即可实现远程访问ME网络",
            "This terminal has a built-in quantum ring, allowing remote access to the ME network without additional plugins"),
    Me2In1AddMapping("添加配方搜索映射", "Add Recipe Search Mapping"),
    Me2In1AddMappingDesc("单击打开EMI中的配方，然后点击想要自定义映射的目录中配方的\"+\"按钮以添加映射。自定义的配方映射保存于config/me2in1category.json中。",
            "Click to open the recipe in EMI, then click the \"+\" button of the recipe in the directory you want to customize the mapping for to add the mapping. The custom recipe mappings are saved in config/me2in1category.json."),
    Me2In1ConfigMapping("配置配方搜索映射", "Configure Recipe Search Mapping"),
    CraftAddMissingToEmi("收藏缺失", "Bookmark Missing"),
    CraftAddMissingToEmiDesc("将缺失的物品添加到EMI书签页", "Add missing items to EMI bookmark page"),
    CraftMissingStart("缺失合成", "Missing Crafting"),
    CraftMissingStartDesc("在材料不足的情况下仍然开始合成，缺失的原料将被等待",
            "Start crafting even when materials are insufficient, missing ingredients will be waited for"),
    CraftUsedPercent("已使用 %s%%", "Used %s%%"),
    FetchingItems("取得信息中...", "Fetching items..."),
    MeStorageAmount("ME网络存储数量", "ME Network Stored Amount"),
    PickCraftAllRight("已启动合成！", "Crafting started!"),
    PickCraftError1("计算合成路径时发生错误。", "An error occurred while calculating the crafting path."),
    PickCraftError2("没有足够的材料/CPU来合成所需物品。", "Insufficient materials/No available CPU to craft the desired item."),
    PickCraftError3("创建的任务数已达上限。", "The number of created tasks has reached the limit."),
    HighlightButtonTryOpenUi("右键以试图打开其界面", "R-click to try to open its UI"),
    CraftPauseJob("暂停", "Pause"),
    CraftResumeJob("继续", "Resume"),
    CraftPauseJobDesc("暂停正在进行中的发配；已推送的样板不会被撤回", "Pause the ongoing crafting; pushed patterns will not be withdrawn"),
    CraftResumeJobDesc("继续已暂停的发配", "Resume the paused crafting"),
    CraftTempOrder("中键点击以创建临时合成订单，下单一份该配方的原材料",
            "Middle-click to create a temporary crafting order, ordering one set of raw materials for this recipe"),
    CraftEncodeSend("§o[右键点击] 编码并发送样板§r", "§o[Right Click] Encode and send pattern§r"),
    CraftEncodeSendDesc("点击选择目的地样板供应器，并将样板发送至该供应器",
            "Click to select the destination Pattern Provider and encode the current recipe to it");

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
