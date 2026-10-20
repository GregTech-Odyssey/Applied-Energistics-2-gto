package dev.emi.emi.screen;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.forge.EmiForgePlatform;
import dev.emi.emi.input.EmiInput;
import dev.emi.emi.planner.PlannerText;
import dev.emi.emi.planner.ProductionPlanner;
import dev.emi.emi.planner.ProductionPlanner.*;
import dev.emi.emi.planner.compat.gto.GtoCapabilityAudit;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiHistory;
import dev.emi.emi.runtime.EmiSidebars;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.level.material.Fluid;
import org.lwjgl.glfw.GLFW;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class ProductionPlannerScreen extends Screen {
	private static final int HEADER_HEIGHT = 26;
	private static final int SUMMARY_TOP = 28;
	private static final int SUMMARY_HEIGHT = 90;
	private static final int TABLE_HEADER_Y = 122;
	private static final int ROW_TOP = 140;
	private static final int ROW_HEIGHT = 36;
	private static final int FOOTER_HEIGHT = 30;
	private static final int MENU_ROW_HEIGHT = 22;
	private static final int MENU_HEADER_HEIGHT = 20;
	private static final int MENU_MAX_ROWS = 10;
	private static final int TAB_WIDTH = 112;
	private static final int ICON_STEP = 50;
	private static final Bounds EMPTY = new Bounds(0, 0, 0, 0);
	private static final int BG_COLOR = 0xFF0B0B12;
	private static final int HEADER_COLOR = 0xFF24242A;
	private static final int PANEL_COLOR = 0xFF14141B;
	private static final int BORDER_COLOR = 0xFF55555F;
	private static final int ACTIVE_COLOR = 0xFF355048;
	private static final int HOVER_COLOR = 0xFF3A3A44;
	private static final double EPSILON = 0.0000001D;
	private static final boolean SHOW_GTO_AUDIT = EmiForgePlatform.isDevelopmentEnvironment();

	public final AbstractContainerScreen<?> old;
	private Bounds newLineButton = EMPTY;
	private Bounds closeLineButton = EMPTY;
	private Bounds targetIconBounds = EMPTY;
	private Bounds targetRateBounds = EMPTY;
	private List<TargetHitbox> targetHitboxes = List.of();
	private Bounds balanceButton = EMPTY;
	private Bounds clearTargetButton = EMPTY;
	private Bounds powerBounds = EMPTY;
	private Bounds standardVoltageBounds = EMPTY;
	private Bounds applyStandardVoltageBounds = EMPTY;
	private Bounds groupsButton = EMPTY;
	private Bounds toolsButton = EMPTY;
	private Bounds toolsMenuBounds = EMPTY;
	private Bounds exportLineButton = EMPTY;
	private Bounds importLineButton = EMPTY;
	private Bounds timeUnitButton = EMPTY;
	private Bounds buildSummaryButton = EMPTY;
	private Bounds recipeChangesButton = EMPTY;
	private Bounds graphViewButton = EMPTY;
	private Bounds gtoAuditButton = EMPTY;
	private boolean toolsOpen;
	private String gtoAuditStatus = "";
	private String lineTransferStatus = "";
	private boolean groupsOpen;
	private Group selectedGroup;
	private Bounds groupsModalBounds = EMPTY;
	private Bounds groupListArea = EMPTY;
	private Bounds groupRecipeArea = EMPTY;
	private Bounds groupLinkArea = EMPTY;
	private Bounds groupAddButton = EMPTY;
	private Bounds groupChildButton = EMPTY;
	private Bounds groupDeleteButton = EMPTY;
	private Bounds groupRenameButton = EMPTY;
	private Bounds groupCollapseButton = EMPTY;
	private Bounds groupCloseButton = EMPTY;
	private Bounds groupSelectedNameBounds = EMPTY;
	private List<GroupListHitbox> groupListHitboxes = List.of();
	private List<GroupRecipeHitbox> groupRecipeHitboxes = List.of();
	private List<GroupLinkHitbox> groupLinkHitboxes = List.of();
	private int groupListScroll;
	private int groupRecipeScroll;
	private int groupLinkScroll;
	private EditBox groupRenameField;
	private Group groupRenameTarget;
	private Group pendingGroupDrag;
	private Entry pendingRecipeDrag;
	private boolean groupDragActive;
	private boolean recipeDragActive;
	private double groupDragStartX;
	private double groupDragStartY;
	private double recipeDragStartX;
	private double recipeDragStartY;
	private List<GroupMainHitbox> groupMainHitboxes = List.of();
	private Bounds activeDropdownBounds = EMPTY;
	private Entry machineMenuEntry;
	private Bounds machineMenuAnchor = EMPTY;
	private int machineMenuScroll;
	private Entry machineConfigEntry;
	private Bounds machineConfigAnchor = EMPTY;
	private Bounds configCoilMinus = EMPTY;
	private Bounds configCoilValue = EMPTY;
	private Bounds configCoilPlus = EMPTY;
	private Bounds configParallelMinus = EMPTY;
	private Bounds configParallelValue = EMPTY;
	private Bounds configParallelPlus = EMPTY;
	private List<MachineSettingControl> configSpecialControls = List.of();
	private boolean coilMenuOpen;
	private Bounds coilMenuAnchor = EMPTY;
	private List<CoilOptionHitbox> coilOptionHitboxes = List.of();
	private Entry voltageMenuEntry;
	private Bounds voltageMenuAnchor = EMPTY;
	private int voltageMenuScroll;
	private boolean standardVoltageMenuOpen;
	private int standardVoltageMenuScroll;
	private List<MachineOptionHitbox> machineOptionHitboxes = List.of();
	private List<VoltageOptionHitbox> voltageOptionHitboxes = List.of();
	private List<TabHitbox> tabHitboxes = List.of();
	private List<RowHitbox> rowHitboxes = List.of();
	private List<FlowHitbox> flowHitboxes = List.of();
	private int rowScroll;
	private EditBox renameField;
	private int renameIndex = -1;
	private EditBox editField;
	private Entry editEntry;
	private EditKind editKind;
	private EditBox targetRateField;
	private Line targetEditLine;
	private Target targetEditTarget;
	private Bounds targetEditBounds = EMPTY;
	private EmiStack pendingStackClick = EmiStack.EMPTY;
	private int pendingStackButton = -1;
	private boolean searchOpen;
	private EditBox searchField;
	private Bounds searchPanelBounds = EMPTY;
	private Bounds searchCloseButton = EMPTY;
	private List<Integer> searchMatchRows = List.of();
	private int searchMatchIndex = -1;
	private static DisplayTimeUnit displayTimeUnit = DisplayTimeUnit.SECOND;
	private boolean buildSummaryOpen;
	private int buildSummaryScroll;
	private Bounds buildSummaryModalBounds = EMPTY;
	private Bounds buildSummaryCloseButton = EMPTY;
	private Bounds buildSummaryCopyButton = EMPTY;
	private Bounds buildSummarySaveButton = EMPTY;
	private Bounds buildSummaryMachinesButton = EMPTY;
	private Bounds buildSummaryFlowsButton = EMPTY;
	private Bounds buildSummaryStartupButton = EMPTY;
	private Bounds buildSummaryListArea = EMPTY;
	private List<BuildSummaryHitbox> buildSummaryHitboxes = List.of();
	private List<StartupSummaryHitbox> startupSummaryHitboxes = List.of();
	private boolean buildSummaryFlowsView;
	private boolean buildSummaryStartupView;
	private String buildSummaryTransferStatus = "";
	private boolean recipeChangesOpen;
	private int recipeChangesScroll;
	private Bounds recipeChangesModalBounds = EMPTY;
	private Bounds recipeChangesCloseButton = EMPTY;
	private Bounds recipeChangesAcceptAllButton = EMPTY;
	private Bounds recipeChangesListArea = EMPTY;
	private List<RecipeChangeHitbox> recipeChangeHitboxes = List.of();
	private String recipeChangesStatus = "";


	public ProductionPlannerScreen(AbstractContainerScreen<?> old) {
		super(EmiPort.literal(PlannerText.tr("planner.title", "Production Planner")));
		this.old = old;
		ProductionPlanner.ensureLoaded();
		ProductionPlanner.getOrCreateActiveLine();
	}

	@Override
	protected void init() {
		ProductionPlanner.cancelPendingRecipeReplacement();
		newLineButton = new Bounds(6, 4, 18, 18);
		closeLineButton = new Bounds(28, 4, 18, 18);
		if (searchOpen) {
			String query = searchField == null ? "" : searchField.getValue();
			createSearchField(query);
		}
		clampScroll();
	}

	@Override
	public void render(GuiGraphics raw, int mouseX, int mouseY, float delta) {
		EmiDrawContext context = EmiDrawContext.wrap(raw);
		context.fill(0, 0, width, height, BG_COLOR);
		context.fill(0, 0, width, HEADER_HEIGHT, HEADER_COLOR);
		renderHeader(context, raw, mouseX, mouseY, delta);
		Line line = ProductionPlanner.getOrCreateActiveLine();
		if (searchOpen) {
			refreshSearchMatches(line, false);
		}
		PlanTotals totals = calculate(line);
		renderSummary(context, line, totals, mouseX, mouseY);
		renderTableHeader(context);
		renderRows(context, line, mouseX, mouseY, delta);
		renderFooter(context, line, mouseX, mouseY);
		if (toolsOpen && !buildSummaryOpen && !groupsOpen && !recipeChangesOpen) {
			renderToolsMenu(context, mouseX, mouseY);
		}
		if (editField != null) {
			editField.render(raw, mouseX, mouseY, delta);
		}
		if (targetRateField != null) {
			targetRateField.render(raw, mouseX, mouseY, delta);
		}
		if (recipeChangesOpen) {
			renderRecipeChangesModal(context, line, mouseX, mouseY);
		} else if (buildSummaryOpen) {
			renderBuildSummaryModal(context, line, mouseX, mouseY);
		} else if (groupsOpen) {
			renderGroupsModal(context, line, mouseX, mouseY, delta);
		} else {
			renderDropdowns(context, line, mouseX, mouseY);
			if (isDropdownOpen()) {
				context.push();
				context.matrices().translate(0, 0, 1400);
				try {
					renderDropdownTooltip(context, mouseX, mouseY);
				} finally {
					context.pop();
				}
			} else if (!toolsOpen) {
				renderTooltip(context, mouseX, mouseY);
			}
		}
		if (searchOpen && !buildSummaryOpen && !recipeChangesOpen) {
			renderSearchOverlay(context, raw, line, mouseX, mouseY, delta);
		}
	}

	private void renderHeader(EmiDrawContext context, GuiGraphics raw, int mouseX, int mouseY, float delta) {
		drawButton(context, newLineButton, mouseX, mouseY, "+", false);
		drawButton(context, closeLineButton, mouseX, mouseY, "x", false);
		tabHitboxes = new ArrayList<>();
		List<Line> lines = ProductionPlanner.lines();
		int startX = 52;
		int maxTabs = Math.max(1, (width - startX - 8) / TAB_WIDTH);
		int active = ProductionPlanner.getActiveIndex();
		int first = 0;
		if (active >= maxTabs) {
			first = active - maxTabs + 1;
		}
		int visible = Math.min(maxTabs, lines.size() - first);
		for (int i = 0; i < visible; i++) {
			int index = first + i;
			Bounds bounds = new Bounds(startX + i * TAB_WIDTH, 3, TAB_WIDTH - 3, 20);
			boolean hovered = bounds.contains(mouseX, mouseY);
			int color = index == active ? ACTIVE_COLOR : hovered ? HOVER_COLOR : 0xFF2B2B32;
			context.fill(bounds.x(), bounds.y(), bounds.width(), bounds.height(), color);
			drawBorder(context, bounds, hovered || index == active ? 0xFFB0B0B8 : BORDER_COLOR);
			String display = ProductionPlanner.displayName(index);
			String trimmed = font.plainSubstrByWidth(display, bounds.width() - 8);
			context.drawCenteredText(EmiPort.literal(trimmed), bounds.x() + bounds.width() / 2, bounds.y() + 6, 0xFFFFFFFF);
			tabHitboxes.add(new TabHitbox(bounds, index));
		}
		if (renameField != null) {
			renameField.render(raw, mouseX, mouseY, delta);
		}
	}

	private void renderSummary(EmiDrawContext context, Line line, PlanTotals totals, int mouseX, int mouseY) {
		context.fill(4, SUMMARY_TOP, width - 8, SUMMARY_HEIGHT, PANEL_COLOR);
		drawBorder(context, new Bounds(4, SUMMARY_TOP, width - 8, SUMMARY_HEIGHT), BORDER_COLOR);
		int sectionWidth = Math.max(1, (width - 12) / 3);
		flowHitboxes = new ArrayList<>();
		renderFlowSection(context, line, PlannerText.tr("summary.external_prefix", "External Inputs/") + displayTimeUnit.suffix, totals.externalInputs(), 6, SUMMARY_TOP + 4, sectionWidth - 2, TargetMode.INPUT);
		renderFlowSection(context, line, PlannerText.tr("summary.internal_prefix", "Internal Flow/") + displayTimeUnit.suffix, totals.internalFlow(), 6 + sectionWidth, SUMMARY_TOP + 4, sectionWidth - 2, null);
		renderFlowSection(context, line, PlannerText.tr("summary.outputs_prefix", "Net Outputs/") + displayTimeUnit.suffix, totals.netOutputs(), 6 + sectionWidth * 2, SUMMARY_TOP + 4, sectionWidth - 2, TargetMode.OUTPUT);

		int controlsY = SUMMARY_TOP + SUMMARY_HEIGHT - 21;
		PowerSummary power = calculatePower(line);
		powerBounds = new Bounds(Math.max(272, width - 166), controlsY, Math.min(158, Math.max(80, width - 280)), 18);
		context.drawTextWithShadow(EmiPort.literal(PlannerText.tr("targets", "Targets:")), 8, controlsY + 5, 0xFFC8C8D0);
		targetHitboxes = new ArrayList<>();
		targetIconBounds = EMPTY;
		targetRateBounds = EMPTY;
		boolean capacityLimited = line.isBalanceEnabled() && line.hasMachineCapacityShortfall();
		int targetX = 52;
		List<Target> targets = line.getTargets();
		if (!targets.isEmpty()) {
			int targetAreaEnd = Math.max(targetX + 118, powerBounds.x() - 132);
			int shown = 0;
			for (Target target : targets) {
				if (shown >= 4 || targetX + 118 > targetAreaEnd) {
					break;
				}
				Bounds icon = new Bounds(targetX, controlsY, 18, 18);
				Bounds rate = new Bounds(targetX + 22, controlsY, 78, 18);
				Bounds remove = new Bounds(targetX + 104, controlsY + 1, 16, 16);
				EmiStack stack = target.getStack();
				context.drawStack(stack, icon.x(), icon.y(), EmiIngredient.RENDER_ICON);
				drawBorder(context, icon, capacityLimited ? 0xFFFFA04D : line.isBalanceEnabled() ? 0xFF7FD8A1 : 0xFFB0B0B8);
				drawValueBox(context, rate, mouseX, mouseY, target.getMode().label() + " " + formatDisplayRate(target.getRate()) + unitSuffixShort(stack), line.isBalanceEnabled() && !capacityLimited);
				drawButton(context, remove, mouseX, mouseY, "x", false);
				targetHitboxes.add(new TargetHitbox(target, icon, rate, remove));
				if (shown == 0) {
					targetIconBounds = icon;
					targetRateBounds = rate;
				}
				targetX += 124;
				shown++;
			}
			int hidden = targets.size() - shown;
			if (hidden > 0) {
				Bounds more = new Bounds(targetX, controlsY, 30, 18);
				drawValueBox(context, more, mouseX, mouseY, "+" + hidden, false);
				targetX += 34;
			}
		} else {
			targetIconBounds = new Bounds(targetX, controlsY, 18, 18);
			targetRateBounds = new Bounds(targetX + 22, controlsY, 78, 18);
			context.fill(targetIconBounds.x(), targetIconBounds.y(), targetIconBounds.width(), targetIconBounds.height(), 0xFF222229);
			drawBorder(context, targetIconBounds, BORDER_COLOR);
			drawValueBox(context, targetRateBounds, mouseX, mouseY, "--", false);
			targetX += 104;
		}
		balanceButton = new Bounds(targetX + 4, controlsY, 64, 18);
		clearTargetButton = new Bounds(balanceButton.right() + 4, controlsY, 44, 18);
		drawButton(context, balanceButton, mouseX, mouseY, line.isBalanceEnabled() ? (capacityLimited ? PlannerText.tr("limited", "LIMITED") : PlannerText.tr("balanced", "BALANCED")) : PlannerText.tr("balance", "BALANCE"), line.isBalanceEnabled() && !capacityLimited);
		drawButton(context, clearTargetButton, mouseX, mouseY, PlannerText.tr("clear", "CLEAR"), false);
		String message = line.getBalanceMessage();
		if (message == null || message.isBlank()) {
			message = PlannerText.tr("summary.goal_hint", "Click outputs for OUT targets; Ctrl + click inputs for IN goals");
		}
		String powerText = power.knownEntries > 0
			? PlannerText.tr("power", "Power") + ": " + formatCompactRate(power.averageEUt, false) + " EU/t" + (power.unknownEntries > 0 ? " +?" : "")
			: PlannerText.tr("power", "Power") + ": --";
		drawValueBox(context, powerBounds, mouseX, mouseY, powerText, power.knownEntries > 0);
		int messageColor = capacityLimited ? 0xFFFFB05C : line.isBalanceEnabled() ? 0xFF8ED6A4 : 0xFF8D8D98;
		int messageX = clearTargetButton.right() + 8;
		int messageWidth = Math.max(20, powerBounds.x() - messageX - 4);
		String trimmed = font.plainSubstrByWidth(message, messageWidth);
		context.drawTextWithShadow(EmiPort.literal(trimmed), messageX, controlsY + 5, messageColor);
	}

	private String linkModeLabel(LinkMode mode) {
		return switch (mode) {
			case AUTO -> PlannerText.tr("groups.auto", "AUTO");
			case MATCH -> PlannerText.tr("groups.match", "MATCH");
			case IGNORE -> PlannerText.tr("groups.ignore", "IGNORE");
		};
	}

	private void renderFlowSection(EmiDrawContext context, Line line, String label, List<Flow> flows, int x, int y, int w,
			TargetMode goalMode) {
		context.drawCenteredText(EmiPort.literal(label), x + w / 2, y, 0xFFE8E8EE);
		int available = Math.max(1, w - 8);
		int perRow = Math.max(1, available / ICON_STEP);
		int capacity = perRow * 2;
		int count = Math.min(capacity, flows.size());
		for (int i = 0; i < count; i++) {
			Flow flow = flows.get(i);
			int row = i / perRow;
			int col = i % perRow;
			int ix = x + 4 + col * ICON_STEP;
			int iy = y + 15 + row * 20;
			context.drawStack(flow.stack, ix, iy, EmiIngredient.RENDER_ICON);
			TargetMode selectedMode = targetMode(line, flow.stack);
			if (selectedMode != null) {
				drawBorder(context, new Bounds(ix, iy, 18, 18), selectedMode == TargetMode.INPUT ? 0xFF66D9FF : 0xFFFFFF66);
			}
			EmiRenderHelper.renderAmount(context, ix, iy, EmiPort.literal(formatCompactDisplayRate(flow.displayValue, flow.approximate)));
			flowHitboxes.add(new FlowHitbox(new Bounds(ix, iy, 18, 18), flow, goalMode, goalMode == null));
		}
		if (flows.size() > capacity) {
			String more = "+" + (flows.size() - capacity);
			context.drawTextWithShadow(EmiPort.literal(more), x + w - 30, y + 43, 0xFFB8B8C0);
		}
	}

	private void renderTableHeader(EmiDrawContext context) {
		context.fill(0, TABLE_HEADER_Y, width, 16, 0xFF1B1B22);
		TableLayout layout = tableLayout();
		context.drawTextWithShadow(EmiPort.literal(PlannerText.tr("header.mode", "MODE")), layout.modeX, TABLE_HEADER_Y + 4, 0xFFC8C8D0);
		context.drawTextWithShadow(EmiPort.literal(PlannerText.tr("header.machine", "MACHINE")), layout.machineX + 4, TABLE_HEADER_Y + 4, 0xFFC8C8D0);
		context.drawTextWithShadow(EmiPort.literal(PlannerText.tr("header.cfg", "CFG")), layout.cfgX, TABLE_HEADER_Y + 4, 0xFFC8C8D0);
		context.drawTextWithShadow(EmiPort.literal(PlannerText.tr("header.mach", "MACH")), layout.machinesLockX, TABLE_HEADER_Y + 4, 0xFFC8C8D0);
		context.drawTextWithShadow(EmiPort.literal(PlannerText.tr("header.par", "PAR")), layout.parallelLockX, TABLE_HEADER_Y + 4, 0xFFC8C8D0);
		context.drawTextWithShadow(EmiPort.literal(PlannerText.tr("header.volt", "VOLT")), layout.voltageX, TABLE_HEADER_Y + 4, 0xFFC8C8D0);
		context.drawTextWithShadow(EmiPort.literal(PlannerText.tr("header.oc", "OC")), layout.ocX, TABLE_HEADER_Y + 4, 0xFFC8C8D0);
		if (layout.showDuration) {
			context.drawTextWithShadow(EmiPort.literal(PlannerText.tr("header.duration", "DURATION")), layout.durationX + 4, TABLE_HEADER_Y + 4, 0xFFC8C8D0);
		}
		context.drawTextWithShadow(EmiPort.literal(PlannerText.tr("header.rate", "RATE")), layout.rateX + 4, TABLE_HEADER_Y + 4, 0xFFC8C8D0);
		context.drawTextWithShadow(EmiPort.literal(PlannerText.tr("header.recipe", "RECIPE")), layout.recipeX + 2, TABLE_HEADER_Y + 4, 0xFFC8C8D0);
		context.drawTextWithShadow(EmiPort.literal(PlannerText.tr("header.inputs_prefix", "INPUTS/") + displayTimeUnit.suffix), layout.inputsX, TABLE_HEADER_Y + 4, 0xFFC8C8D0);
		context.drawTextWithShadow(EmiPort.literal(PlannerText.tr("header.outputs_prefix", "OUTPUTS/") + displayTimeUnit.suffix), layout.outputsX, TABLE_HEADER_Y + 4, 0xFFC8C8D0);
	}

	private void renderRows(EmiDrawContext context, Line line, int mouseX, int mouseY, float delta) {
		rowHitboxes = new ArrayList<>();
		groupMainHitboxes = new ArrayList<>();
		List<PlannerDisplayRow> displayRows = buildPlannerDisplayRows(line);
		TableLayout layout = tableLayout();
		int visibleRows = visibleRows();
		clampScroll();
		int end = Math.min(displayRows.size(), rowScroll + visibleRows);
		for (int visible = 0, index = rowScroll; index < end; visible++, index++) {
			PlannerDisplayRow displayRow = displayRows.get(index);
			int y = ROW_TOP + visible * ROW_HEIGHT;
			int bg = (index & 1) == 0 ? 0xFF111118 : 0xFF16161D;
			context.fill(0, y, width, ROW_HEIGHT - 1, bg);

			if (displayRow.group != null) {
				Group group = displayRow.group;
				int indent = Math.min(96, displayRow.depth * 14);
				Bounds rowBounds = new Bounds(6, y + 4, width - 12, ROW_HEIGHT - 9);
				Bounds toggle = new Bounds(10 + indent, y + 9, 18, 18);
				boolean hovered = rowBounds.contains(mouseX, mouseY);
				context.fill(rowBounds.x(), rowBounds.y(), rowBounds.width(), rowBounds.height(), hovered ? 0xFF26312F : 0xFF1B2423);
				drawBorder(context, rowBounds, hovered ? 0xFF7FAE9F : 0xFF40504B);
				drawButton(context, toggle, mouseX, mouseY, group.isCollapsed() ? ">" : "v", group.isCollapsed());
				String name = group.getDisplayName(line);
				String suffix = group.isCollapsed()
					? "  [" + PlannerText.tr("groups.collapsed", "collapsed") + "]"
					: "";
				String label = font.plainSubstrByWidth(name + suffix, Math.max(80, width - 120 - indent));
				context.drawTextWithShadow(EmiPort.literal(label), 36 + indent, y + 13, 0xFFE8F2EE);
				context.drawTextWithShadow(EmiPort.literal(PlannerText.tr("groups.group_row", "GROUP")), Math.max(36 + indent, width - 62), y + 13, 0xFF88A39A);
				groupMainHitboxes.add(new GroupMainHitbox(rowBounds, group));
				renderSearchRowOverlay(context, index, y);
				continue;
			}

			Entry entry = displayRow.entry;
			if (entry == null) {
				continue;
			}
			EmiRecipe recipe = entry.getRecipe();
			EmiStack recipePathOutput = recipe == null ? EmiStack.EMPTY : firstOutput(recipe);
			RecipePathMode recipePathMode = recipe == null || recipePathOutput.isEmpty()
				? RecipePathMode.NONE
				: ProductionPlanner.getRecipePathMode(recipePathOutput, entry.getRecipeId());

			Bounds mode = new Bounds(layout.modeX, y + 7, layout.modeW, 20);
			Bounds machineProfile = new Bounds(layout.machineX, y + 7, layout.machineW, 20);
			Bounds machineConfig = new Bounds(layout.cfgX, y + 9, layout.cfgW, 16);
			Bounds machinesLock = new Bounds(layout.machinesLockX, y + 9, layout.lockW, 16);
			Bounds machinesMinus = new Bounds(layout.machinesMinusX, y + 9, layout.stepW, 16);
			Bounds machinesValue = new Bounds(layout.machinesValueX, y + 7, layout.valueW, 20);
			Bounds machinesPlus = new Bounds(layout.machinesPlusX, y + 9, layout.stepW, 16);
			Bounds parallelLock = new Bounds(layout.parallelLockX, y + 9, layout.lockW, 16);
			Bounds parallelMinus = new Bounds(layout.parallelMinusX, y + 9, layout.stepW, 16);
			Bounds parallelValue = new Bounds(layout.parallelValueX, y + 7, layout.valueW, 20);
			Bounds parallelPlus = new Bounds(layout.parallelPlusX, y + 9, layout.stepW, 16);
			Bounds voltage = new Bounds(layout.voltageX, y + 7, layout.voltageW, 20);
			Bounds oc = new Bounds(layout.ocX, y + 7, layout.ocW, 20);
			Bounds duration = layout.showDuration ? new Bounds(layout.durationX, y + 7, layout.durationW, 20) : EMPTY;
			Bounds rate = new Bounds(layout.rateX, y + 7, layout.rateW, 20);
			Bounds replace = new Bounds(layout.inputsX - 22, y + 9, 16, 16);
			Bounds remove = new Bounds(width - 22, y + 9, 16, 16);

			String modeLabel = line.isBalanceEnabled() ? PlannerText.tr("mode.balance", "BAL") : entry.isAutomatic() ? PlannerText.tr("mode.auto", "AUTO") : PlannerText.tr("mode.manual", "MAN");
			drawButton(context, mode, mouseX, mouseY, modeLabel, line.isBalanceEnabled() || entry.isAutomatic());
			drawMachineValueBox(context, machineProfile, mouseX, mouseY, entry.getMachineProfile(), !"generic".equals(entry.getMachineProfile().id()));
			if (entry.getMachineProfile().hasConfigurableSettings()) {
				drawButton(context, machineConfig, mouseX, mouseY, "CFG", machineConfigEntry == entry);
			}
			drawButton(context, machinesLock, mouseX, mouseY, "F", entry.isMachinesFixed());
			drawButton(context, machinesMinus, mouseX, mouseY, "-", false);
			drawValueBox(context, machinesValue, mouseX, mouseY, formatInteger(entry.getMachines()), entry.isAutomatic() || entry.isMachinesFixed());
			drawButton(context, machinesPlus, mouseX, mouseY, "+", false);
			drawButton(context, parallelLock, mouseX, mouseY, "F", entry.isParallelFixed());
			drawButton(context, parallelMinus, mouseX, mouseY, "-", false);
			drawValueBox(context, parallelValue, mouseX, mouseY, formatInteger(entry.getParallel()), entry.isAutomatic() || entry.isParallelFixed());
			drawButton(context, parallelPlus, mouseX, mouseY, "+", false);
			drawValueBox(context, voltage, mouseX, mouseY, entry.getVoltageName(), entry.getRecipeEUt() > 0L,
				ProductionPlanner.voltageTierColor(entry.getVoltageTier()));
			drawButton(context, oc, mouseX, mouseY, entry.getOcDisplayLabel(), entry.getOcMode() != OcMode.NONE);

			double durationSeconds = entry.getProcessedDurationSeconds();
			String durationText = durationSeconds > 0.0D ? formatDuration(durationSeconds) + (entry.isDurationOverridden() ? "*" : "") : "--";
			if (layout.showDuration) {
				drawValueBox(context, duration, mouseX, mouseY, durationText, entry.isAutomatic());
			}
			double rowRate = line.getEffectiveRate(entry);
			drawValueBox(context, rate, mouseX, mouseY, formatDisplayRate(rowRate) + "/" + displayTimeUnit.suffix, line.isBalanceEnabled() || !entry.isAutomatic());
			drawButton(context, replace, mouseX, mouseY, "R", recipePathMode != RecipePathMode.NONE);
			drawButton(context, remove, mouseX, mouseY, "x", false);

			int recipeIndent = Math.min(layout.compact ? 18 : 48, displayRow.depth * (layout.compact ? 5 : 9));
			Bounds recipeBounds = new Bounds(layout.recipeX, y + 3, Math.max(18, replace.x() - layout.recipeX - 4), ROW_HEIGHT - 7);
			if (recipe == null) {
				String missing = font.plainSubstrByWidth(PlannerText.tr("recipe.missing", "Missing") + ": " + entry.getRecipeId(), Math.max(12, recipeBounds.width() - 4));
				context.drawTextWithShadow(EmiPort.literal(missing), layout.recipeX + 2, y + 9, 0xFFFF7777);
			} else {
				EmiStack recipeIcon = firstOutput(recipe);
				int recipeIconX = layout.recipeX + 2 + recipeIndent;
				if (!recipeIcon.isEmpty()) {
					context.drawStack(recipeIcon, recipeIconX, y + 9, EmiIngredient.RENDER_ICON);
					flowHitboxes.add(new FlowHitbox(new Bounds(recipeIconX, y + 9, 18, 18),
						new Flow(recipeIcon, 0, 0, 0, 0, false, "", 0), null));
				} else {
					recipe.getCategory().renderSimplified(context.raw(), recipeIconX, y + 9, delta);
				}
				int recipeTextX = recipeIconX + 22;
				int recipeTextWidth = Math.max(0, recipeBounds.right() - recipeTextX - 2);
				if (recipeTextWidth >= 12) {
					String name = recipeName(recipe);
					String trimmed = font.plainSubstrByWidth(name, recipeTextWidth);
					context.drawTextWithShadow(EmiPort.literal(trimmed), recipeTextX, y + 7, 0xFFFFFFFF);
					String category = recipe.getCategory().getName().getString();
					if (recipePathMode != RecipePathMode.NONE) {
						category += " • " + recipePathModeLabel(recipePathMode);
					}
					if (entry.getGroupId() > 0) {
						category += " • " + line.getEntryGroupName(entry);
					}
					String categoryTrimmed = font.plainSubstrByWidth(category, recipeTextWidth);
					context.drawTextWithShadow(EmiPort.literal(categoryTrimmed), recipeTextX, y + 20, 0xFF90909B);
				}
				double effectiveRate = line.getEffectiveRate(entry);
				renderRateStacks(context, line, recipeInputs(recipe, effectiveRate), layout.inputsX, y + 9,
					layout.outputsX - layout.inputsX - 8, TargetMode.INPUT);
				renderRateStacks(context, line, recipeOutputs(recipe, effectiveRate), layout.outputsX, y + 9,
					width - layout.outputsX - 32, TargetMode.OUTPUT);
			}
			rowHitboxes.add(new RowHitbox(entry, mode, machineProfile, machineConfig, machinesLock, machinesMinus, machinesValue, machinesPlus, parallelLock, parallelMinus,
				parallelValue, parallelPlus, voltage, oc, duration, rate, replace, remove, recipeBounds));
			renderSearchRowOverlay(context, index, y);
		}
		if (line.getEntries().isEmpty()) {
			context.drawCenteredText(EmiPort.literal(PlannerText.tr("empty.add_recipe", "Open any EMI recipe and press the + planner button to add it to this line.")),
				width / 2, ROW_TOP + 32, 0xFFA0A0AA);
			context.drawCenteredText(EmiPort.literal(PlannerText.tr("empty.mode_help", "New recipes use AUTO when a recipe duration can be detected; MAN keeps direct rate control.")),
				width / 2, ROW_TOP + 48, 0xFF777783);
		}
	}

	private List<PlannerDisplayRow> buildPlannerDisplayRows(Line line) {
		List<PlannerDisplayRow> rows = new ArrayList<>();
		appendPlannerDisplayRows(line, 0, 0, rows);
		return rows;
	}

	private void appendPlannerDisplayRows(Line line, int groupId, int depth, List<PlannerDisplayRow> rows) {
		for (Entry entry : line.getEntries()) {
			if (entry.getGroupId() == groupId) {
				rows.add(new PlannerDisplayRow(entry, null, depth));
			}
		}
		for (Group group : line.getGroups()) {
			if (group.getParentId() != groupId) {
				continue;
			}
			rows.add(new PlannerDisplayRow(null, group, depth));
			if (!group.isCollapsed()) {
				appendPlannerDisplayRows(line, group.getId(), depth + 1, rows);
			}
		}
	}

	private void renderRateStacks(EmiDrawContext context, Line line, List<RateStack> stacks, int x, int y,
			int availableWidth, TargetMode goalMode) {
		int capacity = Math.max(1, availableWidth / 42);
		int count = Math.min(capacity, stacks.size());
		for (int i = 0; i < count; i++) {
			RateStack rate = stacks.get(i);
			int ix = x + i * 42;
			context.drawStack(rate.stack, ix, y, EmiIngredient.RENDER_ICON);
			TargetMode selectedMode = targetMode(line, rate.stack);
			if (selectedMode != null) {
				drawBorder(context, new Bounds(ix, y, 18, 18), selectedMode == TargetMode.INPUT ? 0xFF66D9FF : 0xFFFFFF66);
			}
			EmiRenderHelper.renderAmount(context, ix, y, EmiPort.literal(formatCompactDisplayRate(rate.amount, rate.approximate)));
			flowHitboxes.add(new FlowHitbox(new Bounds(ix, y, 18, 18), new Flow(rate.stack, 0, 0, 0, 0,
				rate.approximate, formatCompactDisplayRate(rate.amount, rate.approximate), rate.amount), goalMode));
		}
		if (stacks.size() > capacity) {
			context.drawTextWithShadow(EmiPort.literal("+" + (stacks.size() - capacity)), x + capacity * 42 - 18, y + 9, 0xFFB8B8C0);
		}
	}


	private void renderFooter(EmiDrawContext context, Line line, int mouseX, int mouseY) {
		int y = Math.max(ROW_TOP, height - FOOTER_HEIGHT);
		context.fill(0, y, width, FOOTER_HEIGHT, HEADER_COLOR);
		context.fill(0, y, width, 1, BORDER_COLOR);
		boolean compact = isCompactLayout();
		if (compact) {
			context.drawTextWithShadow(EmiPort.literal(PlannerText.tr("footer.volt_short", "VOLT:")), 10, y + 10, 0xFFC8C8D0);
			standardVoltageBounds = new Bounds(44, y + 5, 78, 20);
			applyStandardVoltageBounds = new Bounds(128, y + 5, 62, 20);
			groupsButton = new Bounds(196, y + 5, 70, 20);
			toolsButton = new Bounds(272, y + 5, 60, 20);
			gtoAuditButton = SHOW_GTO_AUDIT ? new Bounds(338, y + 5, 78, 20) : EMPTY;
		} else {
			context.drawTextWithShadow(EmiPort.literal(PlannerText.tr("footer.standard_voltage", "Standard Voltage for machines:")), 10, y + 10, 0xFFC8C8D0);
			standardVoltageBounds = new Bounds(158, y + 5, 92, 20);
			applyStandardVoltageBounds = new Bounds(256, y + 5, 70, 20);
			groupsButton = new Bounds(332, y + 5, 78, 20);
			toolsButton = new Bounds(416, y + 5, 68, 20);
			gtoAuditButton = SHOW_GTO_AUDIT ? new Bounds(490, y + 5, 78, 20) : EMPTY;
		}
		exportLineButton = EMPTY;
		importLineButton = EMPTY;
		timeUnitButton = EMPTY;
		buildSummaryButton = EMPTY;
		recipeChangesButton = EMPTY;
		graphViewButton = EMPTY;
		String label = line.getStandardVoltageTier() < 0 ? PlannerText.tr("footer.recipe_min", "Recipe Min") : line.getStandardVoltageName();
		drawValueBox(context, standardVoltageBounds, mouseX, mouseY, label, line.getStandardVoltageTier() >= 0,
			ProductionPlanner.voltageTierColor(line.getStandardVoltageTier()));
		drawButton(context, applyStandardVoltageBounds, mouseX, mouseY, compact ? PlannerText.tr("footer.apply_short", "APPLY") : PlannerText.tr("footer.apply_all", "APPLY ALL"), false);
		drawButton(context, groupsButton, mouseX, mouseY, (compact ? PlannerText.tr("footer.groups_short", "GROUPS") : PlannerText.tr("footer.groups", "GROUPS")) + " " + line.getGroups().size(), groupsOpen);
		drawButton(context, toolsButton, mouseX, mouseY, PlannerText.tr("footer.tools", "TOOLS"), toolsOpen);
		if (SHOW_GTO_AUDIT) {
			drawButton(context, gtoAuditButton, mouseX, mouseY, "GTO AUDIT", false);
		}
		String hint = !lineTransferStatus.isBlank()
			? lineTransferStatus
			: SHOW_GTO_AUDIT && !gtoAuditStatus.isBlank()
				? gtoAuditStatus
				: PlannerText.tr("footer.voltage_hint", "New machines inherit this voltage. Individual rows can override it.");
		if (ProductionPlanner.canUndo()) {
			hint += "  |  " + PlannerText.tr("footer.undo_hint", "Ctrl+Z: Undo");
		}
		hint += "  |  " + PlannerText.tr("footer.search_hint", "Ctrl+F: Search");
		int hintX = (SHOW_GTO_AUDIT ? gtoAuditButton : toolsButton).right() + 10;
		if (hintX < width - 20) {
			context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(hint, width - hintX - 8)), hintX, y + 10, 0xFF858590);
		}
	}

	private void renderToolsMenu(EmiDrawContext context, int mouseX, int mouseY) {
		int menuWidth = 148;
		int rowHeight = 22;
		int menuHeight = rowHeight * 6 + 4;
		int x = Math.max(4, Math.min(toolsButton.x(), width - menuWidth - 4));
		int y = Math.max(HEADER_HEIGHT + 4, toolsButton.y() - menuHeight - 2);
		toolsMenuBounds = new Bounds(x, y, menuWidth, menuHeight);
		context.push();
		context.matrices().translate(0, 0, 900);
		context.fill(x, y, menuWidth, menuHeight, 0xFF17171F);
		drawBorder(context, toolsMenuBounds, 0xFF777782);
		exportLineButton = new Bounds(x + 2, y + 2, menuWidth - 4, rowHeight);
		importLineButton = new Bounds(x + 2, y + 2 + rowHeight, menuWidth - 4, rowHeight);
		timeUnitButton = new Bounds(x + 2, y + 2 + rowHeight * 2, menuWidth - 4, rowHeight);
		buildSummaryButton = new Bounds(x + 2, y + 2 + rowHeight * 3, menuWidth - 4, rowHeight);
		recipeChangesButton = new Bounds(x + 2, y + 2 + rowHeight * 4, menuWidth - 4, rowHeight);
		graphViewButton = new Bounds(x + 2, y + 2 + rowHeight * 5, menuWidth - 4, rowHeight);
		drawButton(context, exportLineButton, mouseX, mouseY, PlannerText.tr("tools.export_line", "EXPORT LINE"), false);
		drawButton(context, importLineButton, mouseX, mouseY, PlannerText.tr("tools.import_line", "IMPORT LINE"), false);
		drawButton(context, timeUnitButton, mouseX, mouseY, PlannerText.tr("tools.time", "TIME") + " /" + displayTimeUnit.suffix, false);
		drawButton(context, buildSummaryButton, mouseX, mouseY, PlannerText.tr("tools.line_report", "LINE REPORT"), false);
		drawButton(context, recipeChangesButton, mouseX, mouseY, PlannerText.tr("tools.recipe_changes", "RECIPE CHANGES"), false);
		drawButton(context, graphViewButton, mouseX, mouseY, PlannerText.tr("tools.graph", "GRAPH VIEW"), false);
		context.pop();
	}


	private void renderRecipeChangesModal(EmiDrawContext context, Line line, int mouseX, int mouseY) {
		context.push();
		context.matrices().translate(0, 0, 1250);
		context.fill(0, 0, width, height, 0x99000000);
		int modalWidth = Math.max(520, Math.min(980, width - 48));
		int modalHeight = Math.max(320, Math.min(600, height - 56));
		int x = (width - modalWidth) / 2;
		int y = (height - modalHeight) / 2;
		recipeChangesModalBounds = new Bounds(x, y, modalWidth, modalHeight);
		context.fill(x, y, modalWidth, modalHeight, 0xFF15151D);
		drawBorder(context, recipeChangesModalBounds, 0xFF8A8A96);
		context.fill(x, y, modalWidth, 30, 0xFF24242D);
		context.drawCenteredText(EmiPort.literal(PlannerText.tr("migration.title", "Recipe Changes") + " - " + ProductionPlanner.displayName(ProductionPlanner.getActiveIndex())),
			x + modalWidth / 2, y + 10, 0xFFFFFFFF);
		recipeChangesCloseButton = new Bounds(x + modalWidth - 25, y + 5, 19, 19);
		drawButton(context, recipeChangesCloseButton, mouseX, mouseY, "x", false);

		List<RecipeChange> changes = ProductionPlanner.getRecipeChanges(line);
		int changedCount = 0;
		int missingCount = 0;
		for (RecipeChange change : changes) {
			if (change.kind() == RecipeChangeKind.MISSING) {
				missingCount++;
			} else {
				changedCount++;
			}
		}
		String summary = changes.isEmpty()
			? PlannerText.tr("migration.none", "No recipe changes detected")
			: PlannerText.tr("migration.summary", "%s changed, %s missing", changedCount, missingCount);
		context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(summary, modalWidth - 220)), x + 12, y + 40,
			changes.isEmpty() ? 0xFF8ED39F : 0xFFFFCF70);
		recipeChangesAcceptAllButton = changedCount > 0 ? new Bounds(x + modalWidth - 128, y + 34, 110, 20) : EMPTY;
		if (changedCount > 0) {
			drawButton(context, recipeChangesAcceptAllButton, mouseX, mouseY, PlannerText.tr("migration.accept_all", "ACCEPT ALL"), false);
		}

		List<String> lineImpact = changes.isEmpty() ? List.of() : ProductionPlanner.getLineRecipeChangeImpact(line);
		int impactLineCount = Math.min(5, lineImpact.size());
		int impactBlockHeight = impactLineCount > 0 ? 18 + impactLineCount * 13 : 0;
		if (impactLineCount > 0) {
			int impactY = y + 61;
			context.drawTextWithShadow(EmiPort.literal(PlannerText.tr("migration.line_impact.title", "LINE IMPACT - current planned craft rates")),
				x + 12, impactY, 0xFF70C7E0);
			for (int i = 0; i < impactLineCount; i++) {
				context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(lineImpact.get(i), modalWidth - 32)),
					x + 18, impactY + 14 + i * 13, 0xFF8ED39F);
			}
		}

		int listY = y + 62 + impactBlockHeight;
		int listBottom = y + modalHeight - 52;
		recipeChangesListArea = new Bounds(x + 10, listY, modalWidth - 20, Math.max(50, listBottom - listY));
		context.fill(recipeChangesListArea.x(), recipeChangesListArea.y(), recipeChangesListArea.width(), recipeChangesListArea.height(), 0xFF101017);
		drawBorder(context, recipeChangesListArea, BORDER_COLOR);
		recipeChangeHitboxes = new ArrayList<>();

		if (changes.isEmpty()) {
			context.drawCenteredText(EmiPort.literal(PlannerText.tr("migration.none_detail", "Saved recipe snapshots match the current recipe registry.")),
				recipeChangesListArea.x() + recipeChangesListArea.width() / 2, recipeChangesListArea.y() + 28, 0xFFA8A8B2);
		} else {
			int rowHeight = 104;
			int visible = Math.max(1, recipeChangesListArea.height() / rowHeight);
			recipeChangesScroll = Math.max(0, Math.min(recipeChangesScroll, Math.max(0, changes.size() - visible)));
			for (int v = 0, i = recipeChangesScroll; v < visible && i < changes.size(); v++, i++) {
				RecipeChange change = changes.get(i);
				int rowY = recipeChangesListArea.y() + v * rowHeight;
				Bounds row = new Bounds(recipeChangesListArea.x() + 2, rowY + 2, recipeChangesListArea.width() - 4, rowHeight - 4);
				int border = change.kind() == RecipeChangeKind.MISSING ? 0xFFE06B6B : 0xFFE0B86B;
				context.fill(row.x(), row.y(), row.width(), row.height(), 0xFF181820);
				drawBorder(context, row, border);
				String status = change.kind() == RecipeChangeKind.MISSING
					? PlannerText.tr("migration.missing", "MISSING")
					: PlannerText.tr("migration.changed", "CHANGED");
				context.drawTextWithShadow(EmiPort.literal(status + " | " + change.recipeName()), row.x() + 8, row.y() + 7, border);
				context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(change.recipeId(), row.width() - 136)), row.x() + 8, row.y() + 20, 0xFF858590);
				List<String> details = change.details();
				if (!details.isEmpty()) {
					context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(details.get(0), row.width() - 136)), row.x() + 8, row.y() + 34, 0xFFD0D0D8);
				}
				if (details.size() > 1) {
					context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(details.get(1), row.width() - 136)), row.x() + 8, row.y() + 47, 0xFFAAAAAF);
				}
				if (change.kind() == RecipeChangeKind.CHANGED) {
					List<String> impact = ProductionPlanner.getRecipeChangeImpact(line, change);
					for (int impactIndex = 0; impactIndex < Math.min(3, impact.size()); impactIndex++) {
						String prefix = impactIndex == 0 ? PlannerText.tr("migration.impact.label", "Impact") + ": " : "  ";
						context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(prefix + impact.get(impactIndex), row.width() - 136)),
							row.x() + 8, row.y() + 63 + impactIndex * 13, 0xFF8ED39F);
					}
				}
				Bounds action = new Bounds(row.right() - 112, row.y() + 28, 102, 20);
				drawButton(context, action, mouseX, mouseY,
					change.kind() == RecipeChangeKind.MISSING ? PlannerText.tr("migration.remove", "REMOVE ROW") : PlannerText.tr("migration.accept", "ACCEPT"), false);
				recipeChangeHitboxes.add(new RecipeChangeHitbox(change, row, action));
			}
		}

		String footer = PlannerText.tr("migration.footer", "ACCEPT stores the current recipe as the new baseline. Missing recipes must be removed or replaced.");
		context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(footer, modalWidth - 24)), x + 12, y + modalHeight - 39, 0xFF92929C);
		if (!recipeChangesStatus.isBlank()) {
			context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(recipeChangesStatus, modalWidth - 24)), x + 12, y + modalHeight - 24, 0xFF8ED39F);
		}
		context.pop();
	}

	private void renderBuildSummaryModal(EmiDrawContext context, Line line, int mouseX, int mouseY) {
		context.push();
		context.matrices().translate(0, 0, 1200);
		context.fill(0, 0, width, height, 0x99000000);
		int modalWidth = Math.max(420, Math.min(920, width - 40));
		int modalHeight = Math.max(300, Math.min(540, height - 60));
		int x = (width - modalWidth) / 2;
		int y = (height - modalHeight) / 2;
		buildSummaryModalBounds = new Bounds(x, y, modalWidth, modalHeight);
		context.fill(x, y, modalWidth, modalHeight, 0xFF15151D);
		drawBorder(context, buildSummaryModalBounds, 0xFF8A8A96);
		context.fill(x, y, modalWidth, 30, 0xFF24242D);
		context.drawCenteredText(EmiPort.literal(PlannerText.tr("report.title", "Line Report") + " - " + ProductionPlanner.displayName(ProductionPlanner.getActiveIndex())),
			x + modalWidth / 2, y + 10, 0xFFFFFFFF);
		buildSummaryCloseButton = new Bounds(x + modalWidth - 25, y + 5, 19, 19);
		buildSummarySaveButton = new Bounds(x + modalWidth - 119, y + 5, 86, 19);
		buildSummaryCopyButton = new Bounds(x + modalWidth - 211, y + 5, 86, 19);
		drawButton(context, buildSummaryCopyButton, mouseX, mouseY, PlannerText.tr("report.copy", "COPY REPORT"), false);
		drawButton(context, buildSummarySaveButton, mouseX, mouseY, PlannerText.tr("report.save", "SAVE REPORT"), false);
		drawButton(context, buildSummaryCloseButton, mouseX, mouseY, "x", false);

		List<BuildSummaryRow> rows = collectBuildSummary(line);
		PowerSummary power = calculatePower(line);
		PlanTotals totals = calculate(line);
		List<FlowSummaryRow> flowRows = collectFlowSummary(line, totals);
		List<StartupResource> startupRows = ProductionPlanner.getStartupResources(line);
		int totalMachines = 0;
		for (BuildSummaryRow row : rows) {
			totalMachines += row.machines;
		}
		String powerText = power.knownEntries > 0 ? formatCompactRate(power.averageEUt, false) + " EU/t" : "--";
		if (power.unknownEntries > 0) {
			powerText += " +?";
		}
		String stats = PlannerText.tr("report.total_machines", "Machines") + ": " + totalMachines + "   " + PlannerText.tr("report.setups", "Setups") + ": " + rows.size() + "   " + PlannerText.tr("report.external_inputs", "External inputs") + ": " + totals.externalInputs().size()
			+ "   " + PlannerText.tr("report.net_outputs", "Net outputs") + ": " + totals.netOutputs().size() + "   " + PlannerText.tr("report.average_power", "Average power") + ": " + powerText;
		context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(stats, modalWidth - 24)), x + 12, y + 39, 0xFFD5D5DE);

		int tabsY = y + 55;
		buildSummaryMachinesButton = new Bounds(x + 12, tabsY, 92, 18);
		buildSummaryFlowsButton = new Bounds(x + 108, tabsY, 76, 18);
		buildSummaryStartupButton = new Bounds(x + 188, tabsY, 84, 18);
		drawButton(context, buildSummaryMachinesButton, mouseX, mouseY, PlannerText.tr("report.machines", "MACHINES"), !buildSummaryFlowsView && !buildSummaryStartupView);
		drawButton(context, buildSummaryFlowsButton, mouseX, mouseY, PlannerText.tr("report.flows", "FLOWS"), buildSummaryFlowsView);
		drawButton(context, buildSummaryStartupButton, mouseX, mouseY, PlannerText.tr("report.startup", "STARTUP"), buildSummaryStartupView);
		String unitLabel = buildSummaryStartupView
			? PlannerText.tr("startup.unit_hint", "MIN = minimum startup stock   BUF = planned-concurrency buffer")
			: PlannerText.tr("report.display_unit", "Display unit") + ": /" + displayTimeUnit.suffix;
		context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(unitLabel, Math.max(80, modalWidth - 290))), x + 282, tabsY + 5, 0xFF8E8E99);

		int listY = y + 78;
		int listBottom = y + modalHeight - 42;
		buildSummaryListArea = new Bounds(x + 10, listY, modalWidth - 20, Math.max(40, listBottom - listY));
		context.fill(buildSummaryListArea.x(), buildSummaryListArea.y(), buildSummaryListArea.width(), buildSummaryListArea.height(), 0xFF101017);
		drawBorder(context, buildSummaryListArea, BORDER_COLOR);
		buildSummaryHitboxes = new ArrayList<>();
		startupSummaryHitboxes = new ArrayList<>();

		if (!buildSummaryFlowsView && !buildSummaryStartupView) {
			int rowHeight = 38;
			int visible = Math.max(1, buildSummaryListArea.height() / rowHeight);
			buildSummaryScroll = Math.max(0, Math.min(buildSummaryScroll, Math.max(0, rows.size() - visible)));
			for (int v = 0, i = buildSummaryScroll; v < visible && i < rows.size(); v++, i++) {
				BuildSummaryRow row = rows.get(i);
				int ry = listY + v * rowHeight;
				Bounds rb = new Bounds(x + 12, ry + 2, modalWidth - 24, rowHeight - 4);
				context.fill(rb.x(), rb.y(), rb.width(), rb.height(), (i & 1) == 0 ? 0xFF17171F : 0xFF1C1C24);
				drawBorder(context, rb, row.provisional ? 0xFFFFB05C : 0xFF454550);
				int tx = rb.x() + 8;
				if (row.icon != null && !row.icon.isEmpty()) {
					context.drawStack(row.icon, tx, rb.y() + 8, EmiIngredient.RENDER_ICON);
					tx += 23;
				}
				String left = row.machines + "x " + row.machineName;
				String right = row.voltageName + "  |  PAR " + row.parallel + "  |  "
					+ (row.unknownPower ? (row.averageEUt > EPSILON ? formatCompactRate(row.averageEUt, false) + " EU/t +?" : PlannerText.tr("report.power_unknown_inline", "Power ?")) : formatCompactRate(row.averageEUt, false) + " EU/t");
				int rightWidth = font.width(right);
				context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(left, Math.max(40, rb.width() - rightWidth - (tx - rb.x()) - 18))),
					tx, rb.y() + 6, 0xFFFFFFFF);
				context.drawTextWithShadow(EmiPort.literal(right), rb.right() - rightWidth - 7, rb.y() + 6,
					ProductionPlanner.voltageTierColor(row.voltageTier));
				String details = row.config + (row.recipeRows > 1 ? "  |  " + row.recipeRows + " " + PlannerText.tr("report.recipe_rows", "recipe rows") : "")
					+ (row.provisional ? "  |  " + PlannerText.tr("report.provisional", "provisional sizing") : "");
				context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(details, rb.width() - (tx - rb.x()) - 12)),
					tx, rb.y() + 20, row.provisional ? 0xFFFFC27A : 0xFF9696A2);
				buildSummaryHitboxes.add(new BuildSummaryHitbox(rb, row));
			}
			if (rows.isEmpty()) {
				context.drawCenteredText(EmiPort.literal(PlannerText.tr("report.no_machine_rows", "No active machine rows in this Line")), x + modalWidth / 2, listY + 24, 0xFF9A9AA5);
			}
		} else if (buildSummaryFlowsView) {
			int rowHeight = 26;
			int visible = Math.max(1, buildSummaryListArea.height() / rowHeight);
			buildSummaryScroll = Math.max(0, Math.min(buildSummaryScroll, Math.max(0, flowRows.size() - visible)));
			for (int v = 0, i = buildSummaryScroll; v < visible && i < flowRows.size(); v++, i++) {
				FlowSummaryRow row = flowRows.get(i);
				int ry = listY + v * rowHeight;
				Bounds rb = new Bounds(x + 12, ry + 2, modalWidth - 24, rowHeight - 4);
				context.fill(rb.x(), rb.y(), rb.width(), rb.height(), (i & 1) == 0 ? 0xFF17171F : 0xFF1C1C24);
				drawBorder(context, rb, row.color);
				int tx = rb.x() + 7;
				if (row.stack != null && !row.stack.isEmpty()) {
					context.drawStack(row.stack, tx, rb.y() + 3, EmiIngredient.RENDER_ICON);
					tx += 23;
				}
				String left = row.section + "  |  " + row.stack.getName().getString();
				String right = formatCompactDisplayRate(row.rate, row.approximate) + unitSuffix(row.stack);
				int rightWidth = font.width(right);
				context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(left, Math.max(40, rb.width() - rightWidth - (tx - rb.x()) - 16))),
					tx, rb.y() + 7, 0xFFFFFFFF);
				context.drawTextWithShadow(EmiPort.literal(right), rb.right() - rightWidth - 7, rb.y() + 7, row.color);
			}
			if (flowRows.isEmpty()) {
				context.drawCenteredText(EmiPort.literal(PlannerText.tr("report.no_flows", "No flows in this Line")), x + modalWidth / 2, listY + 24, 0xFF9A9AA5);
			}
		} else {
			int rowHeight = 30;
			int visible = Math.max(1, buildSummaryListArea.height() / rowHeight);
			buildSummaryScroll = Math.max(0, Math.min(buildSummaryScroll, Math.max(0, startupRows.size() - visible)));
			for (int v = 0, i = buildSummaryScroll; v < visible && i < startupRows.size(); v++, i++) {
				StartupResource row = startupRows.get(i);
				int ry = listY + v * rowHeight;
				Bounds rb = new Bounds(x + 12, ry + 2, modalWidth - 24, rowHeight - 4);
				int color = startupRoleColor(row.role());
				context.fill(rb.x(), rb.y(), rb.width(), rb.height(), (i & 1) == 0 ? 0xFF17171F : 0xFF1C1C24);
				drawBorder(context, rb, color);
				int tx = rb.x() + 7;
				if (row.stack() != null && !row.stack().isEmpty()) {
					context.drawStack(row.stack(), tx, rb.y() + 5, EmiIngredient.RENDER_ICON);
					tx += 23;
				}
				String role = startupRoleLabel(row.role());
				String left = role + "  |  " + row.stack().getName().getString();
				String right = "MIN " + formatStartupAmount(row.stack(), row.minimum(), row.approximate())
					+ "   BUF " + formatStartupAmount(row.stack(), row.recommended(), row.approximate());
				int rightWidth = font.width(right);
				context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(left, Math.max(40, rb.width() - rightWidth - (tx - rb.x()) - 16))),
					tx, rb.y() + 5, 0xFFFFFFFF);
				context.drawTextWithShadow(EmiPort.literal(right), rb.right() - rightWidth - 7, rb.y() + 5, color);
				String detail = startupRoleDescription(row.role());
				if (row.sources() > 1) {
					detail += "  |  " + PlannerText.tr("startup.sources", "%s recipe rows", row.sources());
				}
				context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(detail, rb.width() - (tx - rb.x()) - 12)),
					tx, rb.y() + 17, 0xFF9696A2);
				startupSummaryHitboxes.add(new StartupSummaryHitbox(rb, row));
			}
			if (startupRows.isEmpty()) {
				context.drawCenteredText(EmiPort.literal(PlannerText.tr("startup.none", "No catalysts, reusable resources or closed-cycle priming detected")),
					x + modalWidth / 2, listY + 24, 0xFF9A9AA5);
			}
		}

		String footer = buildSummaryTransferStatus.isBlank()
			? (buildSummaryStartupView
				? PlannerText.tr("startup.footer", "Startup is separate from steady-state rates. CYCLE rows are conservative priming estimates for closed recycle loops.")
				: PlannerText.tr("report.footer", "COPY/SAVE exports machines, targets, external inputs, internal flow, net outputs, startup resources and power."))
			: buildSummaryTransferStatus;
		context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(footer, modalWidth - 24)),
			x + 12, y + modalHeight - 25, buildSummaryTransferStatus.isBlank() ? 0xFF8E8E99 : 0xFFB7E8C9);
		if (buildSummaryCopyButton.contains(mouseX, mouseY)) {
			drawTooltip(context, mouseX, mouseY, PlannerText.tr("report.copy_tooltip", "Copy full Line Report to clipboard"),
				PlannerText.tr("report.copy_tooltip2", "Includes machines, targets, flows, startup resources and power"));
		} else if (buildSummarySaveButton.contains(mouseX, mouseY)) {
			drawTooltip(context, mouseX, mouseY, PlannerText.tr("report.save_tooltip", "Save full Line Report as .txt"),
				PlannerText.tr("report.saved_to", "Saved to config/emi-production-planner-exports"));
		}
		if (!buildSummaryFlowsView && !buildSummaryStartupView) {
			for (BuildSummaryHitbox hitbox : buildSummaryHitboxes) {
				if (hitbox.bounds.contains(mouseX, mouseY)) {
					BuildSummaryRow row = hitbox.row;
					List<String> tooltip = new ArrayList<>();
					tooltip.add(row.machines + "x " + row.machineName);
					tooltip.add(PlannerText.tr("report.voltage", "Voltage") + ": " + row.voltageName + "   " + PlannerText.tr("report.parallel", "Parallel") + ": " + row.parallel);
					tooltip.add(row.config);
					tooltip.add(row.unknownPower ? PlannerText.tr("report.power_unknown", "Average power: partially unknown") : PlannerText.tr("report.average_power", "Average power") + ": " + formatSummaryPower(row.averageEUt));
					if (row.provisional) {
						tooltip.add(PlannerText.tr("report.provisional_help", "At least one row uses provisional/unknown machine sizing"));
					}
					drawTooltip(context, mouseX, mouseY, tooltip.toArray(String[]::new));
					break;
				}
			}
		} else if (buildSummaryStartupView) {
			for (StartupSummaryHitbox hitbox : startupSummaryHitboxes) {
				if (hitbox.bounds.contains(mouseX, mouseY)) {
					StartupResource row = hitbox.row;
					drawTooltip(context, mouseX, mouseY,
						startupRoleLabel(row.role()) + ": " + row.stack().getName().getString(),
						PlannerText.tr("startup.minimum", "Minimum stock") + ": " + formatStartupAmount(row.stack(), row.minimum(), row.approximate()),
						PlannerText.tr("startup.buffer", "Recommended buffer") + ": " + formatStartupAmount(row.stack(), row.recommended(), row.approximate()),
						startupRoleDescription(row.role()));
					break;
				}
			}
		}
		context.pop();
	}


	private String buildSummaryText(Line line) {
		List<BuildSummaryRow> rows = collectBuildSummary(line);
		PowerSummary power = calculatePower(line);
		PlanTotals totals = calculate(line);
		int totalMachines = 0;
		for (BuildSummaryRow row : rows) {
			totalMachines += row.machines;
		}
		StringBuilder out = new StringBuilder();
		out.append(PlannerText.tr("report.title", "Production Planner Line Report")) .append(" - ")
			.append(ProductionPlanner.displayName(ProductionPlanner.getActiveIndex())).append('\n');
		out.append(PlannerText.tr("report.display_unit", "Display unit")).append(": /").append(displayTimeUnit.suffix).append('\n');
		out.append(PlannerText.tr("report.total_machines_text", "Total machines")).append(": ").append(totalMachines).append('\n');
		out.append(PlannerText.tr("report.distinct_setups", "Distinct setups")).append(": ").append(rows.size()).append('\n');
		out.append(PlannerText.tr("report.recipe_rows_text", "Recipe rows")).append(": ").append(line.getEntries().size()).append('\n');
		if (power.knownEntries > 0) {
			out.append(PlannerText.tr("report.average_power", "Average power")).append(": ").append(formatSummaryPower(power.averageEUt));
			if (power.unknownEntries > 0) {
				out.append(PlannerText.tr("report.unknown_entries", " + unknown entries"));
			}
			out.append('\n');
		}

		out.append("\n").append(PlannerText.tr("report.targets", "TARGETS")).append('\n');
		if (line.getTargets().isEmpty()) {
			out.append("- ").append(PlannerText.tr("report.none", "none")).append('\n');
		} else {
			for (Target target : line.getTargets()) {
				out.append("- ").append(target.getMode().label()).append(' ')
					.append(target.getStack().getName().getString()).append(": ")
					.append(formatReportRate(target.getStack(), target.getRate(), false)).append('\n');
			}
		}

		appendFlowReportSection(out, PlannerText.tr("report.external_inputs_title", "EXTERNAL INPUTS"), totals.externalInputs());
		appendFlowReportSection(out, PlannerText.tr("report.internal_flow_title", "INTERNAL FLOW"), totals.internalFlow());
		appendFlowReportSection(out, PlannerText.tr("report.net_outputs_title", "NET OUTPUTS"), totals.netOutputs());
		appendStartupReportSection(out, ProductionPlanner.getStartupResources(line));

		out.append("\n").append(PlannerText.tr("report.machines_title", "MACHINES")).append('\n');
		if (rows.isEmpty()) {
			out.append("- ").append(PlannerText.tr("report.none", "none")).append('\n');
		}
		for (BuildSummaryRow row : rows) {
			out.append("- ").append(row.machines).append("x ").append(row.machineName)
				.append(" | ").append(row.voltageName)
				.append(" | PAR ").append(row.parallel)
				.append(" | ").append(row.config);
			if (row.unknownPower) {
				out.append(" | ").append(PlannerText.tr("report.power_unknown_short", "Power ?"));
			} else {
				out.append(" | ").append(formatSummaryPower(row.averageEUt));
			}
			if (row.recipeRows > 1) {
				out.append(" | ").append(row.recipeRows).append(" " + PlannerText.tr("report.recipe_rows", "recipe rows"));
			}
			if (row.provisional) {
				out.append(" | ").append(PlannerText.tr("report.provisional", "provisional sizing"));
			}
			out.append('\n');
		}
		return out.toString();
	}

	private void appendFlowReportSection(StringBuilder out, String title, List<Flow> flows) {
		out.append('\n').append(title).append('\n');
		if (flows.isEmpty()) {
			out.append("- ").append(PlannerText.tr("report.none", "none")).append('\n');
			return;
		}
		for (Flow flow : flows) {
			out.append("- ").append(flow.stack.getName().getString()).append(": ")
				.append(formatReportRate(flow.stack, flow.displayValue, flow.approximate)).append('\n');
		}
	}

	private void appendStartupReportSection(StringBuilder out, List<StartupResource> startupRows) {
		out.append('\n').append(PlannerText.tr("startup.title", "STARTUP / PRIMING")).append('\n');
		if (startupRows.isEmpty()) {
			out.append("- ").append(PlannerText.tr("report.none", "none")).append('\n');
			return;
		}
		for (StartupResource row : startupRows) {
			out.append("- ").append(startupRoleLabel(row.role())).append(" | ")
				.append(row.stack().getName().getString())
				.append(" | ").append(PlannerText.tr("startup.minimum", "Minimum stock")).append(": ")
				.append(formatStartupAmount(row.stack(), row.minimum(), row.approximate()))
				.append(" | ").append(PlannerText.tr("startup.buffer", "Recommended buffer")).append(": ")
				.append(formatStartupAmount(row.stack(), row.recommended(), row.approximate()));
			if (row.sources() > 1) {
				out.append(" | ").append(PlannerText.tr("startup.sources", "%s recipe rows", row.sources()));
			}
			out.append('\n');
		}
	}

	private String formatReportRate(EmiStack stack, double perSecond, boolean approximate) {
		double display = toDisplayRate(perSecond);
		String compact = formatCompactRate(display, approximate);
		String exact = formatGroupedExact(display);
		String suffix = stack.getKey() instanceof Fluid ? " mB/" + displayTimeUnit.suffix : "/" + displayTimeUnit.suffix;
		String compactWithUnit = compact + suffix;
		String exactWithUnit = (approximate ? "~" : "") + exact + suffix;
		if (compact.equals((approximate ? "~" : "") + formatExactRate(display))) {
			return compactWithUnit;
		}
		return compactWithUnit + " (" + exactWithUnit + ")";
	}

	private List<FlowSummaryRow> collectFlowSummary(Line line, PlanTotals totals) {
		List<FlowSummaryRow> rows = new ArrayList<>();
		for (Target target : line.getTargets()) {
			rows.add(new FlowSummaryRow(target.getMode() == TargetMode.INPUT ? PlannerText.tr("flow.in_target", "IN TARGET") : PlannerText.tr("flow.out_target", "OUT TARGET"), target.getStack(),
				target.getRate(), false, target.getMode() == TargetMode.INPUT ? 0xFF66D9FF : 0xFFFFFF66));
		}
		for (Flow flow : totals.externalInputs()) {
			rows.add(new FlowSummaryRow(PlannerText.tr("flow.external_input", "EXTERNAL INPUT"), flow.stack, flow.displayValue, flow.approximate, 0xFF66D9FF));
		}
		for (Flow flow : totals.internalFlow()) {
			rows.add(new FlowSummaryRow(PlannerText.tr("flow.internal", "INTERNAL FLOW"), flow.stack, flow.displayValue, flow.approximate, 0xFFB8B8C0));
		}
		for (Flow flow : totals.netOutputs()) {
			rows.add(new FlowSummaryRow(PlannerText.tr("flow.net_output", "NET OUTPUT"), flow.stack, flow.displayValue, flow.approximate, 0xFFB7E8C9));
		}
		return rows;
	}


	private List<BuildSummaryRow> collectBuildSummary(Line line) {
		Map<String, MutableBuildSummary> grouped = new LinkedHashMap<>();
		for (Entry entry : line.getEntries()) {
			EmiRecipe recipe = entry.getRecipe();
			double rate = line.getEffectiveRate(entry);
			if (recipe == null || rate <= EPSILON) {
				continue;
			}
			MachineProfile profile = entry.getMachineProfile();
			String machineName = profile.displayName();
			String keyPrefix = profile.id();
			if ("generic".equals(profile.id())) {
				String category = recipe.getCategory().getName().getString();
				machineName = PlannerText.tr("generic.prefix", "Generic GT - ") + category;
				keyPrefix += ":" + category;
			}
			String config = buildSummaryConfig(entry);
			String key = keyPrefix + "|" + entry.getVoltageTier() + "|" + entry.getParallel() + "|" + config;
			MutableBuildSummary row = grouped.get(key);
			if (row == null) {
				row = new MutableBuildSummary(machineName, profile.icon(), entry.getVoltageTier(), entry.getVoltageName(), entry.getParallel(), config);
				grouped.put(key, row);
			}
			row.machines += Math.max(1, entry.getMachines());
			row.recipeRows++;
			if (entry.getRecipeEUt() > 0L && entry.getProcessedDurationTicks() > 0.0D) {
				row.averageEUt += entry.getAveragePowerEUt(rate);
			} else {
				row.unknownPower = true;
			}
			if (line.isBalanceEnabled()) {
				MachineSizing sizing = entry.getMachineSizing(rate);
				if (!sizing.available() || !sizing.exact()) {
					row.provisional = true;
				}
			}
		}
		List<BuildSummaryRow> rows = new ArrayList<>();
		for (MutableBuildSummary row : grouped.values()) {
			rows.add(new BuildSummaryRow(row.machineName, row.icon, row.voltageTier, row.voltageName, row.parallel,
				row.config, row.machines, row.recipeRows, row.averageEUt, row.unknownPower, row.provisional));
		}
		return rows;
	}

	private String buildSummaryConfig(Entry entry) {
		List<String> parts = new ArrayList<>();
		parts.add("OC " + entry.getOcDisplayLabel());
		if (entry.getMachineProfile().coilEfficiencyPerTier() > 0.0D) {
			parts.add(PlannerText.tr("config.coil", "Coil") + " " + entry.getCoilName());
		}
		for (MachineSettingSpec spec : ProductionPlanner.getMachineSettingSpecs(entry)) {
			int value = entry.getMachineSettingValue(spec);
			if (value != spec.defaultValue()) {
				parts.add(spec.englishLabel() + " " + entry.getMachineSettingDisplayValue(spec));
			}
		}
		return String.join("  |  ", parts);
	}

	private void renderGroupsModal(EmiDrawContext context, Line line, int mouseX, int mouseY, float delta) {
		context.push();
		context.matrices().translate(0, 0, 1000);
		if (selectedGroup != null && !line.getGroups().contains(selectedGroup)) {
			selectedGroup = null;
		}
		if (groupRenameTarget != null && !line.getGroups().contains(groupRenameTarget)) {
			cancelGroupRename();
		}
		context.fill(0, 0, width, height, 0x88000000);
		int modalWidth = Math.min(900, Math.max(640, width - 80));
		int modalHeight = Math.min(500, Math.max(360, height - 120));
		int x = (width - modalWidth) / 2;
		int y = (height - modalHeight) / 2;
		groupsModalBounds = new Bounds(x, y, modalWidth, modalHeight);
		context.fill(x, y, modalWidth, modalHeight, 0xFF15151D);
		drawBorder(context, groupsModalBounds, 0xFF8A8A96);
		context.fill(x, y, modalWidth, 28, 0xFF24242D);
		context.drawCenteredText(EmiPort.literal(PlannerText.tr("groups.title", "Groups and links")), x + modalWidth / 2, y + 9, 0xFFFFFFFF);

		int leftWidth = Math.min(250, modalWidth / 3);
		int listX = x + 10;
		int listY = y + 38;
		int listWidth = leftWidth - 20;
		int footerButtonY = y + modalHeight - 28;
		int footerHelpY = footerButtonY - 17;
		int contentBottomY = footerHelpY - 7;
		int listHeight = contentBottomY - listY;
		groupListArea = new Bounds(listX - 2, listY - 2, listWidth + 4, listHeight + 4);
		context.fill(groupListArea.x(), groupListArea.y(), groupListArea.width(), groupListArea.height(), 0xFF101017);
		drawBorder(context, groupListArea, BORDER_COLOR);

		List<Group> flattened = flattenGroups(line);
		int totalRows = flattened.size() + 1;
		int visibleRows = Math.max(1, listHeight / 24);
		groupListScroll = Math.max(0, Math.min(groupListScroll, Math.max(0, totalRows - visibleRows)));
		groupListHitboxes = new ArrayList<>();
		for (int visible = 0, logical = groupListScroll; visible < visibleRows && logical < totalRows; visible++, logical++) {
			Group group = logical == 0 ? null : flattened.get(logical - 1);
			int rowY = listY + visible * 24;
			int depth = group == null ? 0 : groupDepth(line, group);
			Bounds bounds = new Bounds(listX, rowY, listWidth, 21);
			Bounds collapse = group == null ? EMPTY : new Bounds(listX + 3 + Math.min(72, depth * 11), rowY + 2, 17, 17);
			boolean selected = group == selectedGroup;
			boolean hovered = bounds.contains(mouseX, mouseY);
			context.fill(bounds.x(), bounds.y(), bounds.width(), bounds.height(), selected ? ACTIVE_COLOR : hovered ? HOVER_COLOR : 0xFF24242C);
			drawBorder(context, bounds, selected ? 0xFF9ED5BE : BORDER_COLOR);
			String name = group == null ? PlannerText.tr("groups.root", "Root") : group.getDisplayName(line);
			int textX = bounds.x() + 5;
			if (group != null) {
				drawButton(context, collapse, mouseX, mouseY, group.isCollapsed() ? ">" : "v", group.isCollapsed());
				textX = collapse.right() + 4;
			}
			String label = font.plainSubstrByWidth(name, Math.max(12, bounds.right() - textX - 5));
			context.drawTextWithShadow(EmiPort.literal(label), textX, bounds.y() + 7, 0xFFFFFFFF);
			groupListHitboxes.add(new GroupListHitbox(bounds, collapse, group));
		}

		groupAddButton = new Bounds(listX, footerButtonY, 72, 20);
		groupChildButton = new Bounds(listX + 76, footerButtonY, 92, 20);
		groupDeleteButton = new Bounds(listX + 172, footerButtonY, Math.max(48, listWidth - 172), 20);
		drawButton(context, groupAddButton, mouseX, mouseY, PlannerText.tr("groups.new", "+ GROUP"), false);
		drawButton(context, groupChildButton, mouseX, mouseY, PlannerText.tr("groups.child", "+ CHILD"), selectedGroup != null);
		drawButton(context, groupDeleteButton, mouseX, mouseY, PlannerText.tr("groups.delete", "DELETE"), false);

		int rightX = x + leftWidth + 8;
		int rightWidth = modalWidth - leftWidth - 18;
		String selectedName = selectedGroup == null ? PlannerText.tr("groups.root", "Root") : selectedGroup.getDisplayName(line);
		groupSelectedNameBounds = new Bounds(rightX, y + 34, Math.min(210, Math.max(110, rightWidth - 270)), 20);
		context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(selectedName, groupSelectedNameBounds.width() - 4)), rightX, y + 39, 0xFFFFFFFF);
		groupRenameButton = selectedGroup == null ? EMPTY : new Bounds(groupSelectedNameBounds.right() + 6, y + 34, 72, 20);
		groupCollapseButton = selectedGroup == null ? EMPTY : new Bounds(groupRenameButton.right() + 6, y + 34, 78, 20);
		if (selectedGroup != null) {
			drawButton(context, groupRenameButton, mouseX, mouseY, PlannerText.tr("groups.rename", "RENAME"), false);
			drawButton(context, groupCollapseButton, mouseX, mouseY,
				selectedGroup.isCollapsed() ? PlannerText.tr("groups.expand", "EXPAND") : PlannerText.tr("groups.collapse", "COLLAPSE"), selectedGroup.isCollapsed());
		}
		if (selectedGroup != null) {
			Group parent = selectedGroup.getParent(line);
			String parentName = parent == null ? PlannerText.tr("groups.root", "Root") : parent.getDisplayName(line);
			int parentX = groupCollapseButton == EMPTY ? rightX + 160 : groupCollapseButton.right() + 8;
			if (parentX < rightX + rightWidth - 30) {
				context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth("< " + parentName, rightX + rightWidth - parentX)), parentX, y + 39, 0xFF9696A2);
			}
		}

		int recipesTitleY = y + 61;
		context.drawTextWithShadow(EmiPort.literal(PlannerText.tr("groups.recipes", "Recipes")), rightX, recipesTitleY, 0xFFC8C8D0);
		int recipesY = recipesTitleY + 16;
		int recipesHeight = Math.max(110, (modalHeight - 126) / 2);
		groupRecipeArea = new Bounds(rightX, recipesY, rightWidth, recipesHeight);
		context.fill(groupRecipeArea.x(), groupRecipeArea.y(), groupRecipeArea.width(), groupRecipeArea.height(), 0xFF101017);
		drawBorder(context, groupRecipeArea, BORDER_COLOR);
		List<Entry> entries = line.getEntries();
		int recipeVisible = Math.max(1, (recipesHeight - 4) / 25);
		groupRecipeScroll = Math.max(0, Math.min(groupRecipeScroll, Math.max(0, entries.size() - recipeVisible)));
		groupRecipeHitboxes = new ArrayList<>();
		if (entries.isEmpty()) {
			context.drawTextWithShadow(EmiPort.literal(PlannerText.tr("groups.empty_recipes", "There are no recipes in this line yet")), rightX + 7, recipesY + 8, 0xFF8E8E99);
		} else {
			for (int visible = 0, index = groupRecipeScroll; visible < recipeVisible && index < entries.size(); visible++, index++) {
				Entry entry = entries.get(index);
				int rowY = recipesY + 3 + visible * 25;
				Bounds rowBounds = new Bounds(rightX + 2, rowY, rightWidth - 4, 23);
				if (recipeDragActive && pendingRecipeDrag == entry) {
					context.fill(rowBounds.x(), rowBounds.y(), rowBounds.width(), rowBounds.height(), 0x553F806B);
				}
				EmiRecipe recipe = entry.getRecipe();
				EmiStack icon = recipe == null ? EmiStack.EMPTY : firstOutput(recipe);
				if (!icon.isEmpty()) {
					context.drawStack(icon, rightX + 4, rowY + 2, EmiIngredient.RENDER_ICON);
				}
				String name = recipe == null ? entry.getRecipeId().toString() : recipeName(recipe);
				int selectedId = selectedGroup == null ? 0 : selectedGroup.getId();
				boolean inSelected = entry.getGroupId() == selectedId;
				String currentGroup = line.getEntryGroupName(entry);
				String display = font.plainSubstrByWidth(name + "  [" + currentGroup + "]", Math.max(80, rightWidth - 132));
				context.drawTextWithShadow(EmiPort.literal(display), rightX + 27, rowY + 7, 0xFFE3E3E8);
				Bounds move = new Bounds(rightX + rightWidth - 96, rowY + 2, 90, 20);
				drawButton(context, move, mouseX, mouseY, inSelected ? PlannerText.tr("groups.in_group", "IN GROUP") : PlannerText.tr("groups.move_here", "MOVE HERE"), inSelected);
				groupRecipeHitboxes.add(new GroupRecipeHitbox(rowBounds, move, entry));
			}
		}

		int linksTitleY = recipesY + recipesHeight + 12;
		context.drawTextWithShadow(EmiPort.literal(PlannerText.tr("groups.links", "Links")), rightX, linksTitleY, 0xFFC8C8D0);
		int linksY = linksTitleY + 16;
		int linksHeight = Math.max(72, contentBottomY - linksY);
		groupLinkArea = new Bounds(rightX, linksY, rightWidth, linksHeight);
		context.fill(groupLinkArea.x(), groupLinkArea.y(), groupLinkArea.width(), groupLinkArea.height(), 0xFF101017);
		drawBorder(context, groupLinkArea, BORDER_COLOR);
		List<EmiStack> links = ProductionPlanner.getGroupLinkCandidates(line, selectedGroup);
		int linkVisible = Math.max(1, (linksHeight - 4) / 25);
		groupLinkScroll = Math.max(0, Math.min(groupLinkScroll, Math.max(0, links.size() - linkVisible)));
		groupLinkHitboxes = new ArrayList<>();
		if (links.isEmpty()) {
			String empty = PlannerText.tr("groups.empty_links", "No resources are both produced and consumed at this level");
			context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(empty, rightWidth - 12)), rightX + 6, linksY + 8, 0xFF8E8E99);
		} else {
			for (int visible = 0, index = groupLinkScroll; visible < linkVisible && index < links.size(); visible++, index++) {
				EmiStack stack = links.get(index);
				int rowY = linksY + 3 + visible * 25;
				context.drawStack(stack, rightX + 4, rowY + 2, EmiIngredient.RENDER_ICON);
				String name = font.plainSubstrByWidth(stack.getName().getString(), Math.max(60, rightWidth - 130));
				context.drawTextWithShadow(EmiPort.literal(name), rightX + 27, rowY + 7, 0xFFE3E3E8);
				LinkMode mode = line.getLinkMode(selectedGroup, stack);
				Bounds modeBounds = new Bounds(rightX + rightWidth - 96, rowY + 2, 90, 20);
				String modeText = linkModeLabel(mode);
				drawButton(context, modeBounds, mouseX, mouseY, modeText, mode == LinkMode.MATCH);
				groupLinkHitboxes.add(new GroupLinkHitbox(modeBounds, stack, mode));
			}
		}

		groupCloseButton = new Bounds(x + modalWidth - 82, footerButtonY, 72, 20);
		drawButton(context, groupCloseButton, mouseX, mouseY, PlannerText.tr("groups.close", "CLOSE"), false);
		String help = selectedGroup == null
			? PlannerText.tr("groups.root_link_help", "AUTO: prefer recycling   |   MATCH: force exact recycling   |   IGNORE: keep flows separate")
			: PlannerText.tr("groups.match_help", "MATCH: resource must balance inside this group") + "   |   "
				+ PlannerText.tr("groups.ignore_help", "IGNORE: resource may flow to the parent group");
		context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(help, modalWidth - 20)), x + 10, footerHelpY, 0xFF80808C);

		if (groupRenameField != null) {
			groupRenameField.render(context.raw(), mouseX, mouseY, delta);
		}
		if (groupDragActive && pendingGroupDrag != null) {
			renderGroupDragOverlay(context, line, mouseX, mouseY);
		} else if (recipeDragActive && pendingRecipeDrag != null) {
			renderRecipeDragOverlay(context, line, mouseX, mouseY);
		}
		context.pop();
	}

	private List<Group> flattenGroups(Line line) {
		List<Group> result = new ArrayList<>();
		appendGroupChildren(line, 0, result);
		return result;
	}

	private void appendGroupChildren(Line line, int parentId, List<Group> result) {
		for (Group group : line.getGroups()) {
			if (group.getParentId() == parentId) {
				result.add(group);
				if (!group.isCollapsed()) {
					appendGroupChildren(line, group.getId(), result);
				}
			}
		}
	}

	private int groupDepth(Line line, Group group) {
		int depth = 0;
		Group cursor = group;
		while (cursor != null && cursor.getParentId() > 0 && depth < 8) {
			depth++;
			cursor = cursor.getParent(line);
		}
		return depth;
	}

	private boolean applyGroupDrop(Line line, Group dragged, int mx, int my) {
		if (line == null || dragged == null) {
			return false;
		}
		for (GroupListHitbox hitbox : groupListHitboxes) {
			if (!hitbox.bounds.contains(mx, my)) {
				continue;
			}
			Group target = hitbox.group;
			if (target == dragged) {
				return false;
			}
			if (target == null) {
				ProductionPlanner.moveGroup(line, dragged, null, siblingCount(line, 0));
				selectedGroup = dragged;
				groupListScroll = 0;
				rowScroll = 0;
				return true;
			}
			if (isGroupDescendant(line, target, dragged)) {
				return false;
			}
			int localY = my - hitbox.bounds.y();
			int edge = Math.max(5, hitbox.bounds.height() / 3);
			if (localY < edge) {
				Group parent = target.getParent(line);
				ProductionPlanner.moveGroup(line, dragged, parent, siblingIndex(line, target));
			} else if (localY >= hitbox.bounds.height() - edge) {
				Group parent = target.getParent(line);
				ProductionPlanner.moveGroup(line, dragged, parent, siblingIndex(line, target) + 1);
			} else {
				ProductionPlanner.moveGroup(line, dragged, target, siblingCount(line, target.getId()));
			}
			selectedGroup = dragged;
			groupListScroll = 0;
			rowScroll = 0;
			return true;
		}
		if (groupListArea.contains(mx, my)) {
			ProductionPlanner.moveGroup(line, dragged, null, siblingCount(line, 0));
			selectedGroup = dragged;
			groupListScroll = 0;
			rowScroll = 0;
			return true;
		}
		return false;
	}

	private boolean applyRecipeDrop(Line line, Entry dragged, int mx, int my) {
		if (line == null || dragged == null) {
			return false;
		}
		for (GroupListHitbox hitbox : groupListHitboxes) {
			if (hitbox.bounds.contains(mx, my)) {
				ProductionPlanner.setEntryGroup(line, dragged, hitbox.group);
				selectedGroup = hitbox.group;
				rowScroll = 0;
				return true;
			}
		}
		for (GroupRecipeHitbox hitbox : groupRecipeHitboxes) {
			if (!hitbox.rowBounds.contains(mx, my) || hitbox.entry == dragged) {
				continue;
			}
			int targetIndex = line.getEntries().indexOf(hitbox.entry);
			if (targetIndex < 0) {
				continue;
			}
			if (my >= hitbox.rowBounds.y() + hitbox.rowBounds.height() / 2) {
				targetIndex++;
			}
			ProductionPlanner.moveEntry(line, dragged, targetIndex);
			groupRecipeScroll = 0;
			rowScroll = 0;
			return true;
		}
		return false;
	}

	private int siblingIndex(Line line, Group target) {
		if (line == null || target == null) {
			return 0;
		}
		int index = 0;
		for (Group group : line.getGroups()) {
			if (group.getParentId() != target.getParentId()) {
				continue;
			}
			if (group == target) {
				return index;
			}
			index++;
		}
		return index;
	}

	private int siblingCount(Line line, int parentId) {
		if (line == null) {
			return 0;
		}
		int count = 0;
		for (Group group : line.getGroups()) {
			if (group.getParentId() == parentId) {
				count++;
			}
		}
		return count;
	}

	private boolean isGroupDescendant(Line line, Group candidate, Group ancestor) {
		if (line == null || candidate == null || ancestor == null) {
			return false;
		}
		Group cursor = candidate;
		int guard = 0;
		while (cursor != null && guard++ < line.getGroups().size() + 1) {
			if (cursor == ancestor) {
				return true;
			}
			cursor = cursor.getParent(line);
		}
		return false;
	}

	private void renderGroupDragOverlay(EmiDrawContext context, Line line, int mouseX, int mouseY) {
		String action = PlannerText.tr("groups.drag_group", "Move group");
		for (GroupListHitbox hitbox : groupListHitboxes) {
			if (!hitbox.bounds.contains(mouseX, mouseY) || hitbox.group == pendingGroupDrag) {
				continue;
			}
			Group target = hitbox.group;
			if (target != null && isGroupDescendant(line, target, pendingGroupDrag)) {
				drawBorder(context, hitbox.bounds, 0xFFFF6666);
				action = PlannerText.tr("groups.invalid_drop", "Cannot move a group into itself");
				break;
			}
			int localY = mouseY - hitbox.bounds.y();
			int edge = Math.max(5, hitbox.bounds.height() / 3);
			if (target == null) {
				drawBorder(context, hitbox.bounds, 0xFF8CCFB8);
				action = PlannerText.tr("groups.drop_root", "Move to Root");
			} else if (localY < edge) {
				context.fill(hitbox.bounds.x(), hitbox.bounds.y(), hitbox.bounds.width(), 2, 0xFF9FE2C9);
				action = PlannerText.tr("groups.drop_before", "Place before") + " " + target.getDisplayName(line);
			} else if (localY >= hitbox.bounds.height() - edge) {
				context.fill(hitbox.bounds.x(), hitbox.bounds.bottom() - 2, hitbox.bounds.width(), 2, 0xFF9FE2C9);
				action = PlannerText.tr("groups.drop_after", "Place after") + " " + target.getDisplayName(line);
			} else {
				drawBorder(context, hitbox.bounds, 0xFF9FE2C9);
				action = PlannerText.tr("groups.drop_inside", "Move inside") + " " + target.getDisplayName(line);
			}
			break;
		}
		drawDragLabel(context, mouseX, mouseY, action);
	}

	private void renderRecipeDragOverlay(EmiDrawContext context, Line line, int mouseX, int mouseY) {
		String action = PlannerText.tr("groups.drag_recipe", "Move recipe");
		for (GroupListHitbox hitbox : groupListHitboxes) {
			if (hitbox.bounds.contains(mouseX, mouseY)) {
				drawBorder(context, hitbox.bounds, 0xFF9FE2C9);
				String name = hitbox.group == null ? PlannerText.tr("groups.root", "Root") : hitbox.group.getDisplayName(line);
				action = PlannerText.tr("groups.drop_recipe_group", "Move recipe to") + " " + name;
				drawDragLabel(context, mouseX, mouseY, action);
				return;
			}
		}
		for (GroupRecipeHitbox hitbox : groupRecipeHitboxes) {
			if (hitbox.rowBounds.contains(mouseX, mouseY) && hitbox.entry != pendingRecipeDrag) {
				int lineY = mouseY < hitbox.rowBounds.y() + hitbox.rowBounds.height() / 2
					? hitbox.rowBounds.y() : hitbox.rowBounds.bottom() - 2;
				context.fill(hitbox.rowBounds.x(), lineY, hitbox.rowBounds.width(), 2, 0xFF9FE2C9);
				action = PlannerText.tr("groups.reorder_recipe", "Reorder recipe");
				break;
			}
		}
		drawDragLabel(context, mouseX, mouseY, action);
	}

	private void drawDragLabel(EmiDrawContext context, int mouseX, int mouseY, String value) {
		String label = font.plainSubstrByWidth(value, 220);
		int w = font.width(label) + 8;
		int x = Math.min(width - w - 4, mouseX + 12);
		int y = Math.min(height - 18, mouseY + 10);
		context.fill(x, y, w, 16, 0xEE202027);
		drawBorder(context, new Bounds(x, y, w, 16), 0xFF8A8A96);
		context.drawTextWithShadow(EmiPort.literal(label), x + 4, y + 4, 0xFFFFFFFF);
	}

	private EmiStack firstOutput(EmiRecipe recipe) {
		if (recipe != null) {
			for (EmiStack stack : recipe.getOutputs()) {
				if (stack != null && !stack.isEmpty()) {
					return stack;
				}
			}
		}
		return EmiStack.EMPTY;
	}

	private void renderDropdowns(EmiDrawContext context, Line line, int mouseX, int mouseY) {
		activeDropdownBounds = EMPTY;
		machineOptionHitboxes = List.of();
		voltageOptionHitboxes = List.of();
		if (!isDropdownOpen()) {
			return;
		}
		context.push();
		context.matrices().translate(0, 0, 950);
		try {
			if (coilMenuOpen && machineConfigEntry != null) {
				renderCoilDropdown(context, mouseX, mouseY);
			} else if (machineConfigEntry != null) {
				renderMachineConfig(context, mouseX, mouseY);
			} else if (machineMenuEntry != null) {
				renderMachineDropdown(context, mouseX, mouseY);
			} else if (voltageMenuEntry != null || standardVoltageMenuOpen) {
				renderVoltageDropdown(context, line, mouseX, mouseY);
			}
		} finally {
			context.pop();
		}
	}

	private void renderMachineDropdown(EmiDrawContext context, int mouseX, int mouseY) {
		List<MachineProfile> profiles = ProductionPlanner.getCompatibleMachineProfiles(machineMenuEntry);
		if (profiles.isEmpty()) {
			closeDropdowns();
			return;
		}
		int visible = Math.min(MENU_MAX_ROWS, profiles.size());
		machineMenuScroll = Math.max(0, Math.min(machineMenuScroll, Math.max(0, profiles.size() - visible)));
		int menuWidth = 252;
		int menuHeight = MENU_HEADER_HEIGHT + visible * MENU_ROW_HEIGHT + 2;
		int x = Math.max(4, Math.min(machineMenuAnchor.x(), width - menuWidth - 4));
		int y = machineMenuAnchor.bottom() + 2;
		if (y + menuHeight > height - FOOTER_HEIGHT - 2) {
			y = Math.max(HEADER_HEIGHT + 2, machineMenuAnchor.y() - menuHeight - 2);
		}
		activeDropdownBounds = new Bounds(x, y, menuWidth, menuHeight);
		context.fill(x, y, menuWidth, menuHeight, 0xFF111118);
		drawBorder(context, activeDropdownBounds, 0xFFD0D0D8);
		context.fill(x + 1, y + 1, menuWidth - 2, MENU_HEADER_HEIGHT - 1, 0xFF262630);
		context.drawTextWithShadow(EmiPort.literal(PlannerText.tr("machine.select", "Select machine")), x + 7, y + 6, 0xFFFFFFFF);
		if (profiles.size() > visible) {
			String marker = (machineMenuScroll > 0 ? "^ " : "") + (machineMenuScroll + visible < profiles.size() ? "v" : "");
			context.drawTextWithShadow(EmiPort.literal(marker), x + menuWidth - 20, y + 6, 0xFFB8B8C0);
		}
		List<MachineOptionHitbox> hitboxes = new ArrayList<>();
		for (int i = 0; i < visible; i++) {
			MachineProfile profile = profiles.get(machineMenuScroll + i);
			Bounds row = new Bounds(x + 2, y + MENU_HEADER_HEIGHT + i * MENU_ROW_HEIGHT, menuWidth - 4, MENU_ROW_HEIGHT);
			Bounds favorite = new Bounds(row.right() - 22, row.y() + 2, 20, row.height() - 4);
			boolean selected = profile.id().equals(machineMenuEntry.getMachineProfileId());
			boolean hovered = row.contains(mouseX, mouseY);
			boolean preferred = ProductionPlanner.isPreferredMachine(machineMenuEntry, profile.id());
			context.fill(row.x(), row.y(), row.width(), row.height(), selected ? ACTIVE_COLOR : hovered ? HOVER_COLOR : 0xFF18181F);
			if (selected) {
				context.fill(row.x(), row.y(), 3, row.height(), 0xFF7FD8A1);
			}
			int textX = row.x() + 8;
			if (profile.hasIcon()) {
				context.drawStack(profile.icon(), row.x() + 6, row.y() + 3, EmiIngredient.RENDER_ICON);
				textX = row.x() + 28;
			}
			String name = font.plainSubstrByWidth(profile.displayName(), Math.max(8, favorite.x() - textX - 5));
			context.drawTextWithShadow(EmiPort.literal(name), textX, row.y() + 7, 0xFFFFFFFF);
			context.fill(favorite.x(), favorite.y(), favorite.width(), favorite.height(), preferred ? 0xFF5A5030 : 0xFF24242B);
			drawBorder(context, favorite, preferred ? 0xFFFFD76A : favorite.contains(mouseX, mouseY) ? 0xFFB8B8C0 : 0xFF55555E);
			context.drawCenteredText(EmiPort.literal("♥"), favorite.x() + favorite.width() / 2, favorite.y() + 5,
				preferred ? 0xFFFFD76A : 0xFF777780);
			hitboxes.add(new MachineOptionHitbox(row, favorite, profile));
		}
		machineOptionHitboxes = hitboxes;
	}

	private void renderVoltageDropdown(EmiDrawContext context, Line line, int mouseX, int mouseY) {
		boolean standard = standardVoltageMenuOpen;
		List<Integer> tiers = new ArrayList<>();
		if (standard) {
			tiers.add(-1);
			for (int tier = 0; tier <= ProductionPlanner.maxVoltageTier(); tier++) {
				tiers.add(tier);
			}
		} else if (voltageMenuEntry != null && voltageMenuEntry.getRecipeEUt() > 0L) {
			int minimum = Math.max(0, voltageMenuEntry.getRecipeTier());
			for (int tier = minimum; tier <= ProductionPlanner.maxVoltageTier(); tier++) {
				tiers.add(tier);
			}
		}
		if (tiers.isEmpty()) {
			closeDropdowns();
			return;
		}
		int visible = Math.min(MENU_MAX_ROWS, tiers.size());
		int scroll = standard ? standardVoltageMenuScroll : voltageMenuScroll;
		scroll = Math.max(0, Math.min(scroll, Math.max(0, tiers.size() - visible)));
		if (standard) {
			standardVoltageMenuScroll = scroll;
		} else {
			voltageMenuScroll = scroll;
		}
		Bounds anchor = standard ? standardVoltageBounds : voltageMenuAnchor;
		int menuWidth = 126;
		int menuHeight = MENU_HEADER_HEIGHT + visible * MENU_ROW_HEIGHT + 2;
		int x = Math.max(4, Math.min(anchor.x(), width - menuWidth - 4));
		int y = standard ? anchor.y() - menuHeight - 2 : anchor.bottom() + 2;
		if (!standard && y + menuHeight > height - FOOTER_HEIGHT - 2) {
			y = Math.max(HEADER_HEIGHT + 2, anchor.y() - menuHeight - 2);
		}
		activeDropdownBounds = new Bounds(x, y, menuWidth, menuHeight);
		context.fill(x, y, menuWidth, menuHeight, 0xFF111118);
		drawBorder(context, activeDropdownBounds, 0xFFD0D0D8);
		context.fill(x + 1, y + 1, menuWidth - 2, MENU_HEADER_HEIGHT - 1, 0xFF262630);
		context.drawTextWithShadow(EmiPort.literal(standard ? PlannerText.tr("voltage.standard", "Standard voltage") : PlannerText.tr("voltage.select", "Select voltage")), x + 7, y + 6, 0xFFFFFFFF);
		List<VoltageOptionHitbox> hitboxes = new ArrayList<>();
		int selectedTier = standard ? line.getStandardVoltageTier() : voltageMenuEntry.getVoltageTier();
		for (int i = 0; i < visible; i++) {
			int tier = tiers.get(scroll + i);
			Bounds row = new Bounds(x + 2, y + MENU_HEADER_HEIGHT + i * MENU_ROW_HEIGHT, menuWidth - 4, MENU_ROW_HEIGHT);
			boolean selected = tier == selectedTier;
			boolean hovered = row.contains(mouseX, mouseY);
			context.fill(row.x(), row.y(), row.width(), row.height(), selected ? ACTIVE_COLOR : hovered ? HOVER_COLOR : 0xFF18181F);
			if (selected) {
				context.fill(row.x(), row.y(), 3, row.height(), 0xFF7FD8A1);
			}
			String name = ProductionPlanner.voltageTierName(tier);
			context.drawTextWithShadow(EmiPort.literal(name), row.x() + 8, row.y() + 7, ProductionPlanner.voltageTierColor(tier));
			hitboxes.add(new VoltageOptionHitbox(row, tier));
		}
		voltageOptionHitboxes = hitboxes;
	}

	private void renderMachineConfig(EmiDrawContext context, int mouseX, int mouseY) {
		MachineProfile profile = machineConfigEntry.getMachineProfile();
		if (!profile.hasConfigurableSettings()) {
			closeDropdowns();
			return;
		}
		boolean coils = profile.coilEfficiencyPerTier() > 0.0D;
		boolean parallelControl = profile.parallelControl();
		List<MachineSettingSpec> specialSettings = ProductionPlanner.getMachineSettingSpecs(machineConfigEntry);
		int rows = (coils ? 1 : 0) + (parallelControl ? 1 : 0) + specialSettings.size();
		int menuWidth = specialSettings.isEmpty() ? 264 : 316;
		int menuHeight = MENU_HEADER_HEIGHT + rows * 26 + 20;
		int x = Math.max(4, Math.min(machineConfigAnchor.x(), width - menuWidth - 4));
		int y = machineConfigAnchor.bottom() + 2;
		if (y + menuHeight > height - FOOTER_HEIGHT - 2) {
			y = Math.max(HEADER_HEIGHT + 2, machineConfigAnchor.y() - menuHeight - 2);
		}
		activeDropdownBounds = new Bounds(x, y, menuWidth, menuHeight);
		context.fill(x, y, menuWidth, menuHeight, 0xFF111118);
		drawBorder(context, activeDropdownBounds, 0xFFD0D0D8);
		context.fill(x + 1, y + 1, menuWidth - 2, MENU_HEADER_HEIGHT - 1, 0xFF262630);
		String titleText = PlannerText.tr("machine.settings", "Machine settings") + " - " + profile.displayName();
		String title = font.plainSubstrByWidth(titleText, menuWidth - 12);
		context.drawTextWithShadow(EmiPort.literal(title), x + 7, y + 6, 0xFFFFFFFF);

		configCoilMinus = EMPTY;
		configCoilValue = EMPTY;
		configCoilPlus = EMPTY;
		configParallelMinus = EMPTY;
		configParallelValue = EMPTY;
		configParallelPlus = EMPTY;
		List<MachineSettingControl> specialControls = new ArrayList<>();
		int cy = y + MENU_HEADER_HEIGHT + 4;
		if (coils) {
			context.drawTextWithShadow(EmiPort.literal(PlannerText.tr("machine.coils", "Coils") + ":"), x + 8, cy + 5, 0xFFC8C8D0);
			configCoilMinus = new Bounds(x + 70, cy + 1, 18, 18);
			configCoilValue = new Bounds(x + 90, cy, 136, 20);
			configCoilPlus = new Bounds(x + 228, cy + 1, 18, 18);
			drawButton(context, configCoilMinus, mouseX, mouseY, "-", false);
			drawValueBox(context, configCoilValue, mouseX, mouseY, machineConfigEntry.getCoilName(), machineConfigEntry.getCoilTier() > 0);
			drawButton(context, configCoilPlus, mouseX, mouseY, "+", false);
			cy += 26;
		}
		if (parallelControl) {
			String parallelLabel = PlannerText.tr("machine.parallel_control", "Parallel Control") + ":";
			context.drawTextWithShadow(EmiPort.literal(parallelLabel), x + 8, cy + 5, 0xFFC8C8D0);
			int plusX = x + menuWidth - 36;
			configParallelMinus = new Bounds(plusX - 96, cy + 1, 18, 18);
			configParallelValue = new Bounds(plusX - 76, cy, 74, 20);
			configParallelPlus = new Bounds(plusX, cy + 1, 18, 18);
			drawButton(context, configParallelMinus, mouseX, mouseY, "-", false);
			drawValueBox(context, configParallelValue, mouseX, mouseY, Integer.toString(machineConfigEntry.getParallel()), true);
			drawButton(context, configParallelPlus, mouseX, mouseY, "+", false);
			cy += 26;
		}
		for (MachineSettingSpec spec : specialSettings) {
			int plusX = x + menuWidth - 36;
			int valueWidth = 84;
			int valueX = plusX - valueWidth - 2;
			int minusX = valueX - 20;
			String label = PlannerText.tr(spec.labelKey(), spec.englishLabel()) + ":";
			label = font.plainSubstrByWidth(label, Math.max(40, minusX - x - 16));
			context.drawTextWithShadow(EmiPort.literal(label), x + 8, cy + 5, 0xFFC8C8D0);
			Bounds minus = new Bounds(minusX, cy + 1, 18, 18);
			Bounds value = new Bounds(valueX, cy, valueWidth, 20);
			Bounds plus = new Bounds(plusX, cy + 1, 18, 18);
			drawButton(context, minus, mouseX, mouseY, "-", false);
			drawValueBox(context, value, mouseX, mouseY, machineConfigEntry.getMachineSettingDisplayValue(spec),
				machineConfigEntry.getMachineSettingValue(spec) != spec.defaultValue());
			drawButton(context, plus, mouseX, mouseY, "+", false);
			specialControls.add(new MachineSettingControl(spec, minus, value, plus));
			cy += 26;
		}
		configSpecialControls = List.copyOf(specialControls);
		String info;
		if (!specialSettings.isEmpty()) {
			info = PlannerText.tr("machine.special_help", "Special machine settings are applied to line calculations");
		} else if (coils) {
			info = PlannerText.tr("machine.coil_help", "Coil bonus affects both duration and EU/t multiplicatively");
		} else {
			info = PlannerText.tr("machine.parallel_help", "Parallel Control mirrors the row PAR setting");
		}
		context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(info, menuWidth - 16)), x + 8, y + menuHeight - 14, 0xFF858590);
	}

	private void renderCoilDropdown(EmiDrawContext context, int mouseX, int mouseY) {
		int count = ProductionPlanner.maxCoilTier() + 1;
		int menuWidth = 180;
		int menuHeight = MENU_HEADER_HEIGHT + count * MENU_ROW_HEIGHT + 2;
		int x = Math.max(4, Math.min(coilMenuAnchor.x(), width - menuWidth - 4));
		int y = coilMenuAnchor.bottom() + 2;
		if (y + menuHeight > height - FOOTER_HEIGHT - 2) {
			y = Math.max(HEADER_HEIGHT + 2, coilMenuAnchor.y() - menuHeight - 2);
		}
		activeDropdownBounds = new Bounds(x, y, menuWidth, menuHeight);
		context.fill(x, y, menuWidth, menuHeight, 0xFF111118);
		drawBorder(context, activeDropdownBounds, 0xFFD0D0D8);
		context.fill(x + 1, y + 1, menuWidth - 2, MENU_HEADER_HEIGHT - 1, 0xFF262630);
		context.drawTextWithShadow(EmiPort.literal(PlannerText.tr("machine.select_coils", "Select coils")), x + 7, y + 6, 0xFFFFFFFF);
		List<CoilOptionHitbox> hitboxes = new ArrayList<>();
		for (int tier = 0; tier < count; tier++) {
			Bounds row = new Bounds(x + 2, y + MENU_HEADER_HEIGHT + tier * MENU_ROW_HEIGHT, menuWidth - 4, MENU_ROW_HEIGHT);
			boolean selected = machineConfigEntry.getCoilTier() == tier;
			boolean hovered = row.contains(mouseX, mouseY);
			context.fill(row.x(), row.y(), row.width(), row.height(), selected ? ACTIVE_COLOR : hovered ? HOVER_COLOR : 0xFF18181F);
			if (selected) {
				context.fill(row.x(), row.y(), 3, row.height(), 0xFF7FD8A1);
			}
			String suffix = tier == 0 ? " (" + PlannerText.tr("coil.base", "base") + ")" : " (+" + tier + ")";
			context.drawTextWithShadow(EmiPort.literal(ProductionPlanner.coilTierName(tier) + suffix), row.x() + 8, row.y() + 7, 0xFFFFFFFF);
			hitboxes.add(new CoilOptionHitbox(row, tier));
		}
		coilOptionHitboxes = hitboxes;
	}

	private boolean isDropdownOpen() {
		return machineConfigEntry != null || machineMenuEntry != null || voltageMenuEntry != null || standardVoltageMenuOpen;
	}

	private void closeDropdowns() {
		toolsOpen = false;
		toolsMenuBounds = EMPTY;
		machineMenuEntry = null;
		machineConfigEntry = null;
		voltageMenuEntry = null;
		standardVoltageMenuOpen = false;
		machineMenuAnchor = EMPTY;
		machineConfigAnchor = EMPTY;
		voltageMenuAnchor = EMPTY;
		configCoilMinus = EMPTY;
		configCoilValue = EMPTY;
		configCoilPlus = EMPTY;
		configParallelMinus = EMPTY;
		configParallelValue = EMPTY;
		configParallelPlus = EMPTY;
		configSpecialControls = List.of();
		coilMenuOpen = false;
		coilMenuAnchor = EMPTY;
		coilOptionHitboxes = List.of();
		activeDropdownBounds = EMPTY;
		machineOptionHitboxes = List.of();
		voltageOptionHitboxes = List.of();
	}

	private void openMachineMenu(RowHitbox row) {
		closeDropdowns();
		machineMenuEntry = row.entry;
		machineMenuAnchor = row.machineProfile;
		List<MachineProfile> profiles = ProductionPlanner.getCompatibleMachineProfiles(row.entry);
		int current = 0;
		for (int i = 0; i < profiles.size(); i++) {
			if (profiles.get(i).id().equals(row.entry.getMachineProfileId())) {
				current = i;
				break;
			}
		}
		machineMenuScroll = Math.max(0, current - MENU_MAX_ROWS / 2);
	}

	private void openMachineConfig(RowHitbox row) {
		if (row == null || !row.entry.getMachineProfile().hasConfigurableSettings()) {
			return;
		}
		closeDropdowns();
		machineConfigEntry = row.entry;
		machineConfigAnchor = row.machineConfig;
	}

	private void openVoltageMenu(RowHitbox row) {
		if (row.entry.getRecipeEUt() <= 0L) {
			return;
		}
		closeDropdowns();
		voltageMenuEntry = row.entry;
		voltageMenuAnchor = row.voltage;
		int minimum = Math.max(0, row.entry.getRecipeTier());
		voltageMenuScroll = Math.max(0, row.entry.getVoltageTier() - minimum - MENU_MAX_ROWS / 2);
	}

	private void openStandardVoltageMenu(Line line) {
		closeDropdowns();
		standardVoltageMenuOpen = true;
		int index = line.getStandardVoltageTier() < 0 ? 0 : line.getStandardVoltageTier() + 1;
		standardVoltageMenuScroll = Math.max(0, index - MENU_MAX_ROWS / 2);
	}

	private void renderDropdownTooltip(EmiDrawContext context, int mouseX, int mouseY) {
		if (coilMenuOpen && machineConfigEntry != null) {
			for (CoilOptionHitbox option : coilOptionHitboxes) {
				if (option.bounds.contains(mouseX, mouseY)) {
					double multiplier = Math.pow(Math.max(0.01D, 1.0D - machineConfigEntry.getMachineProfile().coilEfficiencyPerTier()), option.tier);
					drawTooltip(context, mouseX, mouseY, ProductionPlanner.coilTierName(option.tier),
						PlannerText.tr("coil.tier_above_cupronickel", "Tier above Cupronickel") + ": " + option.tier,
						PlannerText.tr("machine.duration_eu_multiplier", "Duration/EU multiplier") + ": x" + formatExactRate(multiplier));
					return;
				}
			}
			return;
		}
		if (machineConfigEntry != null) {
			MachineProfile profile = machineConfigEntry.getMachineProfile();
			if (configCoilValue.contains(mouseX, mouseY)) {
				double perTier = profile.coilEfficiencyPerTier() * 100.0D;
				drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.coils", "Coils") + ": " + machineConfigEntry.getCoilName(),
					PlannerText.tr("coil.tier_above_cupronickel", "Tier above Cupronickel") + ": " + machineConfigEntry.getCoilTier(),
					PlannerText.tr("machine.detected_bonus", "Detected bonus") + ": -" + formatExactRate(perTier) + "% " + PlannerText.tr("machine.duration_eu_per_tier", "duration/EU per tier (multiplicative)"),
					PlannerText.tr("machine.current_multiplier", "Current multiplier") + ": x" + formatExactRate(machineConfigEntry.getCoilMultiplier()),
					PlannerText.tr("tooltip.use_wheel", "Use +/- or mouse wheel"));
				return;
			}
			if (configParallelValue.contains(mouseX, mouseY)) {
				drawTooltip(context, mouseX, mouseY, PlannerText.tr("machine.parallel_control", "Parallel Control") + ": " + machineConfigEntry.getParallel(),
					PlannerText.tr("machine.parallel_help", "Parallel Control mirrors the row PAR setting"),
					PlannerText.tr("machine.parallel_edit_help", "Use +/- here or edit PAR in the recipe row"));
				return;
			}
			for (MachineSettingControl control : configSpecialControls) {
				if (control.minus.contains(mouseX, mouseY) || control.value.contains(mouseX, mouseY) || control.plus.contains(mouseX, mouseY)) {
					MachineSettingSpec spec = control.spec;
					List<String> lines = new ArrayList<>();
					lines.add(PlannerText.tr(spec.labelKey(), spec.englishLabel()) + ": " + machineConfigEntry.getMachineSettingDisplayValue(spec));
					if (!spec.englishHelp().isBlank()) {
						lines.add(PlannerText.tr(spec.helpKey(), spec.englishHelp()));
					}
					lines.addAll(ProductionPlanner.getMachineSettingDetailLines(machineConfigEntry, spec));
					double durationMultiplier = machineConfigEntry.getMachineSettingDurationMultiplier();
					if (Math.abs(durationMultiplier - 1.0D) > EPSILON) {
						lines.add(PlannerText.tr("machine.current_duration_multiplier", "Current duration multiplier") + ": x" + formatExactRate(durationMultiplier));
					}
					double throughputMultiplier = machineConfigEntry.getMachineSettingThroughputMultiplier();
					if (Math.abs(throughputMultiplier - 1.0D) > EPSILON) {
						lines.add(PlannerText.tr("machine.throughput_multiplier", "Current throughput multiplier") + ": x"
							+ formatExactRate(throughputMultiplier));
					}
					lines.add(PlannerText.tr("tooltip.use_wheel", "Use +/- or mouse wheel"));
					drawTooltip(context, mouseX, mouseY, lines.toArray(String[]::new));
					return;
				}
			}
		}
		if (machineMenuEntry != null) {
			for (MachineOptionHitbox option : machineOptionHitboxes) {
				if (option.favoriteBounds.contains(mouseX, mouseY)) {
					boolean preferred = ProductionPlanner.isPreferredMachine(machineMenuEntry, option.profile.id());
					drawTooltip(context, mouseX, mouseY,
						preferred ? PlannerText.tr("tooltip.preferred", "Preferred machine") : PlannerText.tr("tooltip.set_preferred", "Set as preferred machine"),
						option.profile.displayName(),
						PlannerText.tr("tooltip.preferred_help", "Preferred machines are selected automatically for new recipes in this recipe category"),
						preferred ? PlannerText.tr("tooltip.clear_preferred", "Click to clear preference") : PlannerText.tr("tooltip.save_preferred", "Click to save preference"));
					return;
				}
				if (option.bounds.contains(mouseX, mouseY)) {
					MachineProfile profile = option.profile;
					List<String> lines = new ArrayList<>();
					lines.add(profile.displayName());
					lines.add(localizedProfileDescription(profile));
					lines.addAll(profile.modifierDescriptions());
					if (!profile.hasRuntimeModifiers()) {
						lines.add(PlannerText.tr("tooltip.no_runtime", "No runtime-specific modifiers detected; Generic GT math is used"));
					}
					drawTooltip(context, mouseX, mouseY, lines.toArray(String[]::new));
					return;
				}
			}
		}
	}

	private void renderTooltip(EmiDrawContext context, int mouseX, int mouseY) {
		if (newLineButton.contains(mouseX, mouseY)) {
			drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.new_line", "New production line"), PlannerText.tr("tooltip.rename_line", "Right-click a line tab to rename it"));
			return;
		}
		if (closeLineButton.contains(mouseX, mouseY)) {
			drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.delete_line", "Delete active production line"));
			return;
		}
		Line activeLine = ProductionPlanner.getOrCreateActiveLine();
		for (TargetHitbox hitbox : targetHitboxes) {
			Target target = hitbox.target;
			EmiStack stack = target.getStack();
			if (hitbox.icon.contains(mouseX, mouseY)) {
				List<String> lines = new ArrayList<>();
				lines.add(stack.getName().getString());
				lines.add((target.getMode() == TargetMode.INPUT ? PlannerText.tr("tooltip.input_goal", "Input goal") + ": " : PlannerText.tr("tooltip.output_target", "Output target") + ": ") + formatExactDisplayRate(target.getRate()) + unitSuffix(stack));
				if (activeLine.isBalanceEnabled() && activeLine.hasMachineCapacityShortfall()) {
					lines.add(PlannerText.tr("tooltip.achievable", "Achievable") + ": " + formatExactDisplayRate(activeLine.getAchievableTargetRate(target)) + unitSuffix(stack));
				}
				lines.add(PlannerText.tr("tooltip.toggle_goal", "Ctrl + left-click: toggle IN / OUT goal"));
				lines.add(PlannerText.tr("tooltip.view_recipes", "Left-click: view recipes"));
				lines.add(PlannerText.tr("tooltip.view_uses", "Right-click: view uses"));
				drawTooltip(context, mouseX, mouseY, lines.toArray(String[]::new));
				return;
			}
			if (hitbox.rate.contains(mouseX, mouseY)) {
				List<String> lines = new ArrayList<>();
				lines.add(target.getMode() == TargetMode.INPUT ? PlannerText.tr("tooltip.requested_input", "Requested external input rate") : PlannerText.tr("tooltip.requested_output", "Requested target output rate"));
				lines.add(stack.getName().getString());
				lines.add(PlannerText.tr("tooltip.type_exact", "Click to type an exact amount per") + " " + localizedTimeUnitLong());
				lines.add(PlannerText.tr("tooltip.items_fluids", "Items use /%s; fluids use mB/%s", displayTimeUnit.suffix, displayTimeUnit.suffix));
				if (activeLine.isBalanceEnabled() && activeLine.hasMachineCapacityShortfall()) {
					lines.add(PlannerText.tr("tooltip.achievable", "Achievable") + ": " + formatExactDisplayRate(activeLine.getAchievableTargetRate(target)) + unitSuffix(stack));
					if (!activeLine.getBottleneckName().isBlank()) {
						lines.add(PlannerText.tr("tooltip.bottleneck", "Bottleneck") + ": " + activeLine.getBottleneckName());
					}
				}
				drawTooltip(context, mouseX, mouseY, lines.toArray(String[]::new));
				return;
			}
			if (hitbox.remove.contains(mouseX, mouseY)) {
				drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.remove_target", "Remove target"), stack.getName().getString());
				return;
			}
		}
		if (activeLine.getTargets().isEmpty() && targetIconBounds.contains(mouseX, mouseY)) {
			drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.no_goals", "No balance goals"), PlannerText.tr("tooltip.add_out_goal", "Click recipe outputs to add OUT targets"), PlannerText.tr("tooltip.add_in_goal", "Ctrl + click inputs to add IN goals"));
			return;
		}
		if (balanceButton.contains(mouseX, mouseY)) {
			List<String> lines = new ArrayList<>();
			lines.add(activeLine.isBalanceEnabled() ? PlannerText.tr("tooltip.recalculate", "Recalculate line balance") : PlannerText.tr("tooltip.autobalance", "Auto-balance line"));
			lines.add(PlannerText.tr("tooltip.balance_match", "Matches internal produced/consumed resources"));
			lines.add(PlannerText.tr("tooltip.balance_solve", "and solves every selected target at its requested rate"));
			lines.add(PlannerText.tr("tooltip.balance_locks", "Fixed MACH/PAR values are treated as hard equipment constraints"));
			lines.add(PlannerText.tr("tooltip.balance_bottleneck", "A bottleneck is propagated through every recipe in the line"));
			if (activeLine.isBalanceEnabled() && activeLine.hasMachineCapacityShortfall()) {
				if (activeLine.getTargets().size() == 1) {
					lines.add(PlannerText.tr("tooltip.requested", "Requested") + ": " + formatExactDisplayRate(activeLine.getTargetRate()) + unitSuffix(activeLine.getTarget()));
					lines.add(PlannerText.tr("tooltip.achievable", "Achievable") + ": " + formatExactDisplayRate(activeLine.getAchievableTargetRate()) + unitSuffix(activeLine.getTarget()));
				} else {
					lines.add(PlannerText.tr("tooltip.targets_count", "Targets") + ": " + activeLine.getTargets().size());
					double percent = activeLine.getAchievableTargetRate() / Math.max(EPSILON, activeLine.getTargetRate()) * 100.0D;
					lines.add(PlannerText.tr("tooltip.achievable_throughput", "Achievable throughput") + ": " + formatExactRate(percent) + "%");
				}
				if (!activeLine.getBottleneckName().isBlank()) {
					lines.add(PlannerText.tr("tooltip.bottleneck", "Bottleneck") + ": " + activeLine.getBottleneckName());
				}
			}
			drawTooltip(context, mouseX, mouseY, lines.toArray(String[]::new));
			return;
		}
		if (clearTargetButton.contains(mouseX, mouseY)) {
			drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.clear_targets", "Clear all targets and leave balance mode"));
			return;
		}
		if (standardVoltageBounds.contains(mouseX, mouseY)) {
			drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.standard_voltage", "Standard Voltage for machines") + ": " + activeLine.getStandardVoltageName(),
				PlannerText.tr("tooltip.voltage_inherit", "New recipes inherit this voltage automatically"),
				PlannerText.tr("tooltip.left_choose_tier", "Left-click: choose a tier"), PlannerText.tr("tooltip.right_recipe_min", "Right-click: Recipe minimum"),
				PlannerText.tr("tooltip.voltage_overrides_preserved", "Rows with an individual VOLT override are preserved"));
			return;
		}
		if (applyStandardVoltageBounds.contains(mouseX, mouseY)) {
			drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.apply_voltage", "Apply Standard Voltage to every recipe in this Line"),
				PlannerText.tr("tooltip.apply_voltage2", "Clears individual VOLT overrides and makes every row inherit the Line setting"));
			return;
		}
		if (groupsButton.contains(mouseX, mouseY)) {
			drawTooltip(context, mouseX, mouseY, PlannerText.tr("groups.title", "Groups and links"),
				PlannerText.tr("groups.tooltip", "Nested groups and resource link rules"),
				PlannerText.tr("groups.tooltip2", "MATCH keeps a resource inside the group; IGNORE passes it to the parent"));
			return;
		}
		if (toolsButton.contains(mouseX, mouseY)) {
			drawTooltip(context, mouseX, mouseY, PlannerText.tr("tools.tooltip", "Planner tools"),
				PlannerText.tr("tools.tooltip_help", "Export / import Line, display time unit and Line Report"));
			return;
		}
		if (exportLineButton.contains(mouseX, mouseY)) {
			drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.export", "Export current Production Line"),
				PlannerText.tr("tooltip.export2", "Copies the complete Line JSON to the clipboard"),
				PlannerText.tr("tooltip.export3", "and writes a .json file to config/emi-production-planner-exports"),
				PlannerText.tr("tooltip.export4", "Includes recipes, groups, targets, MACH/PAR/VOLT and Machine CFG"));
			return;
		}
		if (importLineButton.contains(mouseX, mouseY)) {
			drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.import", "Import Production Line from clipboard"),
				PlannerText.tr("tooltip.import2", "Copy exported JSON, then click IMPORT"),
				PlannerText.tr("tooltip.import3", "The imported plan is created as a new Line"),
				PlannerText.tr("tooltip.import4", "Existing Lines are not overwritten"));
			return;
		}
		if (timeUnitButton.contains(mouseX, mouseY)) {
			drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.time_unit", "Display time unit") + ": /" + displayTimeUnit.suffix,
				PlannerText.tr("tooltip.time_cycle", "Cycles /s -> /min -> /h"),
				PlannerText.tr("tooltip.time_math", "Only the UI unit changes; Planner math remains per second"));
			return;
		}
		if (buildSummaryButton.contains(mouseX, mouseY)) {
			drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.build_summary", "Build Summary"),
				PlannerText.tr("tooltip.build_summary2", "Shows how many machines to build for the current Line"),
				PlannerText.tr("tooltip.build_summary3", "Groups identical VOLT / PAR / OC / Machine CFG setups"),
				PlannerText.tr("tooltip.build_summary4", "Uses the current MACH values; BALANCE sizes them automatically"));
			return;
		}
		if (recipeChangesButton.contains(mouseX, mouseY)) {
			drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.recipe_changes", "Recipe update check"),
				PlannerText.tr("tooltip.recipe_changes2", "Compares saved recipe snapshots with the current modpack recipes"),
				PlannerText.tr("tooltip.recipe_changes3", "Detects changed inputs, outputs, duration, EU/t, category and removed recipes"));
			return;
		}
		if (SHOW_GTO_AUDIT && gtoAuditButton.contains(mouseX, mouseY)) {
			drawTooltip(context, mouseX, mouseY, "Scan every GTO machine exposed as an EMI workstation",
				"Classifies AUTO / AUTO + OVERRIDE / OVERRIDE ONLY / SUSPICIOUS NONE / NONE",
				"Writes the full report to latest.log and copies it to the clipboard");
			return;
		}
		if (powerBounds.contains(mouseX, mouseY)) {
			PowerSummary power = calculatePower(activeLine);
			List<String> lines = new ArrayList<>();
			if (power.knownEntries > 0) {
				lines.add(PlannerText.tr("tooltip.average_power", "Average power") + ": " + formatExactRate(power.averageEUt) + " EU/t");
				lines.add(PlannerText.tr("tooltip.power_calc", "Calculated from current recipe rates and selected machine profiles"));
			} else {
				lines.add(PlannerText.tr("tooltip.power_unavailable", "Power unavailable for this line"));
			}
			if (power.unknownEntries > 0) {
				lines.add(power.unknownEntries + " " + PlannerText.tr("tooltip.power_missing", "active recipe(s) have no detected GT EU/t"));
			}
			lines.add(PlannerText.tr("tooltip.generic_fallback", "Profiles without exact modifiers currently fall back to generic GT math"));
			drawTooltip(context, mouseX, mouseY, lines.toArray(String[]::new));
			return;
		}
		for (TabHitbox tab : tabHitboxes) {
			if (tab.bounds.contains(mouseX, mouseY)) {
				drawTooltip(context, mouseX, mouseY, ProductionPlanner.displayName(tab.index), PlannerText.tr("tooltip.right_rename", "Right-click to rename"));
				return;
			}
		}
		for (RowHitbox row : rowHitboxes) {
			Entry entry = row.entry;
			if (row.mode.contains(mouseX, mouseY)) {
				if (activeLine.isBalanceEnabled()) {
					drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.rate_bal", "BAL rate"), PlannerText.tr("tooltip.rate_bal_help", "Rate is controlled by Line Auto-Balance"),
						PlannerText.tr("tooltip.rate_bal_exit", "Click to leave balance mode and restore AUTO/MAN rates"));
				} else if (entry.isAutomatic()) {
					drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.rate_auto", "AUTO rate"), PlannerText.tr("tooltip.rate_auto_help", "Displayed as crafts/%s; internally calculated per second", displayTimeUnit.suffix),
						PlannerText.tr("tooltip.rate_to_manual", "Click to switch to manual rate"));
				} else if (entry.getDurationTicks() > 0.0D) {
					drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.rate_manual", "MAN rate"), PlannerText.tr("tooltip.rate_manual_help", "Direct crafts/%s control", displayTimeUnit.suffix),
						PlannerText.tr("tooltip.rate_to_auto", "Click to switch to automatic machine calculation"));
				} else {
					drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.rate_manual", "MAN rate"), PlannerText.tr("tooltip.duration_missing", "Recipe duration was not detected"),
						PlannerText.tr("tooltip.duration_set_first", "Right-click Duration and enter seconds before enabling AUTO"));
				}
				return;
			}
			if (row.machineProfile.contains(mouseX, mouseY)) {
				MachineProfile profile = entry.getMachineProfile();
				List<MachineProfile> compatible = ProductionPlanner.getCompatibleMachineProfiles(entry);
				List<String> lines = new ArrayList<>();
				lines.add(PlannerText.tr("tooltip.machine", "Machine") + ": " + profile.displayName());
				lines.add(localizedProfileDescription(profile));
				lines.add(profile.parallelDescription());
				lines.add(profile.allowsPerfectOc() ? PlannerText.tr("tooltip.perfect_selectable", "Perfect OC: selectable") : PlannerText.tr("tooltip.perfect_unavailable", "Perfect OC: unavailable for this profile"));
				lines.addAll(profile.modifierDescriptions());
				if (profile.coilEfficiencyPerTier() > 0.0D) {
					lines.add(PlannerText.tr("tooltip.selected_coils", "Selected coils") + ": " + entry.getCoilName() + " (x" + formatExactRate(entry.getCoilMultiplier()) + ")");
				}
				if (profile.parallelControl()) {
					lines.add(PlannerText.tr("tooltip.parallel_setting", "Parallel Control setting") + ": " + entry.getParallel());
				}
				lines.add(PlannerText.tr("tooltip.open_machine", "Left-click: open machine selector"));
				lines.add(PlannerText.tr("tooltip.generic_gt", "Right-click: Generic GT"));
				lines.add(PlannerText.tr("tooltip.compatible_profiles", "Compatible profiles") + ": " + compatible.size());
				drawTooltip(context, mouseX, mouseY, lines.toArray(String[]::new));
				return;
			}
			if (row.machineConfig.contains(mouseX, mouseY) && entry.getMachineProfile().hasConfigurableSettings()) {
				List<String> lines = new ArrayList<>();
				lines.add(PlannerText.tr("tooltip.machine_settings", "Machine-specific settings"));
				if (entry.getMachineProfile().coilEfficiencyPerTier() > 0.0D) {
					lines.add(PlannerText.tr("tooltip.coils", "Coils") + ": " + entry.getCoilName() + " (tier +" + entry.getCoilTier() + ")");
				}
				if (entry.getMachineProfile().parallelControl()) {
					lines.add(PlannerText.tr("machine.parallel_control", "Parallel Control") + ": " + entry.getParallel());
				}
				for (MachineSettingSpec spec : ProductionPlanner.getMachineSettingSpecs(entry)) {
					lines.add(PlannerText.tr(spec.labelKey(), spec.englishLabel()) + ": " + entry.getMachineSettingDisplayValue(spec));
				}
				lines.add(PlannerText.tr("tooltip.configure_machine", "Click to configure this machine"));
				drawTooltip(context, mouseX, mouseY, lines.toArray(String[]::new));
				return;
			}
			if (row.machinesLock.contains(mouseX, mouseY)) {
				drawTooltip(context, mouseX, mouseY,
					PlannerText.tr("tooltip.fixed_mach", "Fixed machine count") + ": " + (entry.isMachinesFixed() ? PlannerText.tr("common.on", "ON") : PlannerText.tr("common.off", "OFF")),
					PlannerText.tr("tooltip.left_toggle", "Left-click to toggle"),
					entry.isMachinesFixed() ? PlannerText.tr("tooltip.balance_keep_mach", "BALANCE will keep MACH at %s", entry.getMachines()) : PlannerText.tr("tooltip.balance_resize_mach", "BALANCE may resize MACH automatically"));
				return;
			}
			if (row.parallelLock.contains(mouseX, mouseY)) {
				drawTooltip(context, mouseX, mouseY,
					PlannerText.tr("tooltip.fixed_par", "Fixed parallel") + ": " + (entry.isParallelFixed() ? PlannerText.tr("common.on", "ON") : PlannerText.tr("common.off", "OFF")),
					PlannerText.tr("tooltip.left_toggle", "Left-click to toggle"),
					entry.isParallelFixed() ? PlannerText.tr("tooltip.balance_keep_par", "BALANCE will keep PAR at %s", entry.getParallel()) : PlannerText.tr("tooltip.balance_resize_par", "BALANCE may resize PAR automatically"));
				return;
			}
			if (row.machineArea().contains(mouseX, mouseY)) {
				if (activeLine.isBalanceEnabled()) {
					List<String> lines = new ArrayList<>();
					lines.add(PlannerText.tr("tooltip.machines", "Machines") + ": " + entry.getMachines());
					addMachineSizingTooltip(lines, entry, activeLine.getEffectiveRate(entry));
					lines.add(entry.isMachinesFixed() ? PlannerText.tr("tooltip.mach_fixed", "MACH is fixed for BALANCE") : PlannerText.tr("tooltip.mach_fix_help", "Use the F button to fix MACH during BALANCE"));
					drawTooltip(context, mouseX, mouseY, lines.toArray(String[]::new));
				} else {
					drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.machines", "Machines") + ": " + entry.getMachines(),
						PlannerText.tr("tooltip.use_wheel", "Use +/- or mouse wheel"), PlannerText.tr("tooltip.shift_10", "Shift changes by 10"), PlannerText.tr("tooltip.right_exact", "Right-click the number to type an exact value"));
				}
				return;
			}
			if (row.parallelArea().contains(mouseX, mouseY)) {
				int configuredMaxParallel = entry.getConfiguredMaxParallel();
				if (activeLine.isBalanceEnabled()) {
					List<String> lines = new ArrayList<>();
					lines.add(PlannerText.tr("tooltip.parallel_per_machine", "Parallel per machine") + ": " + entry.getParallel());
					if (configuredMaxParallel > 0) {
						lines.add(PlannerText.tr("tooltip.configured_max_parallel", "Configured max parallel") + ": " + configuredMaxParallel);
					}
					addMachineSizingTooltip(lines, entry, activeLine.getEffectiveRate(entry));
					lines.add(entry.isParallelFixed() ? PlannerText.tr("tooltip.par_fixed", "PAR is fixed for BALANCE") : PlannerText.tr("tooltip.par_fix_help", "Use the F button to fix PAR during BALANCE"));
					drawTooltip(context, mouseX, mouseY, lines.toArray(String[]::new));
				} else if (configuredMaxParallel > 0) {
					drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.parallel_per_machine", "Parallel per machine") + ": " + entry.getParallel(),
						PlannerText.tr("tooltip.configured_max_parallel", "Configured max parallel") + ": " + configuredMaxParallel, PlannerText.tr("tooltip.use_wheel", "Use +/- or mouse wheel"),
						PlannerText.tr("tooltip.shift_10", "Shift changes by 10"), PlannerText.tr("tooltip.right_exact", "Right-click the number to type an exact value"));
				} else {
					drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.parallel_per_machine", "Parallel per machine") + ": " + entry.getParallel(),
						PlannerText.tr("tooltip.use_wheel", "Use +/- or mouse wheel"), PlannerText.tr("tooltip.shift_10", "Shift changes by 10"), PlannerText.tr("tooltip.right_exact", "Right-click the number to type an exact value"));
				}
				return;
			}
			if (row.voltage.contains(mouseX, mouseY)) {
				if (entry.getRecipeEUt() > 0L) {
					List<String> lines = new ArrayList<>();
					lines.add(PlannerText.tr("tooltip.machine_voltage", "Machine voltage") + ": " + entry.getVoltageName() + " (" + entry.getSelectedVoltage() + " V)");
					lines.add(PlannerText.tr("tooltip.recipe", "Recipe") + ": " + entry.getRecipeEUt() + " EU/t, tier " + entry.getRecipeTier());
					lines.add(PlannerText.tr("tooltip.overclocks", "Overclocks") + ": " + entry.getOverclockCount());
					if (entry.isVoltageFixedByMachine()) {
						lines.add(PlannerText.tr("tooltip.volt_fixed", "VOLT: fixed by selected machine profile"));
					} else {
						lines.add(entry.isVoltageOverridden() ? PlannerText.tr("tooltip.volt_override", "VOLT: individual row override") : PlannerText.tr("tooltip.volt_inherited", "VOLT: inherited from Line Standard"));
						lines.add(PlannerText.tr("tooltip.open_voltage", "Left-click: open voltage selector"));
						lines.add(PlannerText.tr("tooltip.reset_voltage", "Right-click: reset to Line Standard"));
					}
					lines.add(PlannerText.tr("tooltip.machine_profile", "Machine profile") + ": " + entry.getMachineProfileName());
					drawTooltip(context, mouseX, mouseY, lines.toArray(String[]::new));
				} else {
					drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.no_gt_eut", "GT EU/t was not detected for this recipe"),
						PlannerText.tr("tooltip.voltage_oc_disabled", "Voltage overclocking is disabled for this row"));
				}
				return;
			}
			if (row.oc.contains(mouseX, mouseY)) {
				String behavior = switch (entry.getOcMode()) {
					case NONE -> PlannerText.tr("tooltip.oc_none", "No voltage overclocking");
					case STANDARD -> Math.abs(entry.getOcDurationMultiplierPerStep() - 0.5D) > EPSILON
						? PlannerText.tr("tooltip.oc_4x_duration", "Each OC: 4x EU/t, x%s duration", formatExactRate(entry.getOcDurationMultiplierPerStep()))
						: PlannerText.tr("tooltip.oc_2x", "Each OC: 4x EU/t, 2x speed");
					case PERFECT -> PlannerText.tr("tooltip.oc_4x", "Each OC: 4x EU/t, 4x speed");
				};
				drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.oc_mode", "OC mode") + ": " + entry.getOcDisplayLabel(), behavior,
					PlannerText.tr("tooltip.oc_next", "Left-click: next mode"), PlannerText.tr("tooltip.oc_prev", "Right-click: previous mode"),
					entry.getMachineProfile().allowsPerfectOc() ? PlannerText.tr("tooltip.perfect_allowed", "Perfect OC is allowed by this profile") : PlannerText.tr("tooltip.perfect_denied", "This machine profile does not allow Perfect OC"));
				return;
			}
			if (row.duration.contains(mouseX, mouseY)) {
				List<String> lines = new ArrayList<>();
				double baseSeconds = entry.getDurationSeconds();
				double processedSeconds = entry.getProcessedDurationSeconds();
				lines.add(baseSeconds > 0.0D ? PlannerText.tr("tooltip.base_duration", "Base duration") + ": " + formatExactRate(baseSeconds) + " s" : PlannerText.tr("tooltip.base_duration", "Base duration") + ": " + PlannerText.tr("tooltip.not_detected", "not detected"));
				if (processedSeconds > 0.0D && entry.getOverclockCount() > 0) {
					lines.add(PlannerText.tr("tooltip.after_oc", "After %s OC", entry.getOverclockCount()) + ": " + formatExactRate(processedSeconds) + " s");
				}
				if (entry.getProcessedEUt() > 0L) {
					lines.add(PlannerText.tr("tooltip.processed_power", "Processed power") + ": " + entry.getProcessedEUt() + " EU/t");
				}
				if (entry.getMachineProfile().coilEfficiencyPerTier() > 0.0D) {
					lines.add(PlannerText.tr("tooltip.coils", "Coils") + ": " + entry.getCoilName() + " -> x" + formatExactRate(entry.getCoilMultiplier()) + " duration/EU");
				}
				if (Math.abs(entry.getMachineSettingDurationMultiplier() - 1.0D) > EPSILON) {
					lines.add(PlannerText.tr("tooltip.machine_settings_duration", "Machine settings -> x%s duration", formatExactRate(entry.getMachineSettingDurationMultiplier())));
				}
				if (Math.abs(entry.getMachineSettingThroughputMultiplier() - 1.0D) > EPSILON) {
					lines.add(PlannerText.tr("tooltip.machine_settings_throughput", "Machine settings -> x%s throughput", formatExactRate(entry.getMachineSettingThroughputMultiplier())));
				}
				if (entry.isDurationOverridden()) {
					double detected = entry.getDetectedDurationSeconds();
					lines.add(detected > 0.0D ? PlannerText.tr("tooltip.recipe_duration", "Recipe duration") + ": " + formatExactRate(detected) + " s" : PlannerText.tr("tooltip.recipe_duration_unavailable", "Recipe duration unavailable"));
					lines.add(PlannerText.tr("tooltip.manual_duration", "* Manual base-duration override is active"));
				}
				lines.add(PlannerText.tr("tooltip.right_duration", "Right-click to type base duration in seconds"));
				lines.add(PlannerText.tr("tooltip.middle_reset_duration", "Middle-click to reset to recipe duration"));
				drawTooltip(context, mouseX, mouseY, lines.toArray(String[]::new));
				return;
			}
			if (row.rate.contains(mouseX, mouseY)) {
				if (activeLine.isBalanceEnabled()) {
					double balancedRate = activeLine.getEffectiveRate(entry);
					List<String> lines = new ArrayList<>();
					lines.add(PlannerText.tr("tooltip.balanced_crafts", "Balanced crafts") + "/" + displayTimeUnit.suffix + ": " + formatExactDisplayRate(balancedRate));
					if (isCompactLayout() && entry.getProcessedDurationSeconds() > 0.0D) {
						lines.add(PlannerText.tr("tooltip.duration", "Duration") + ": " + formatExactRate(entry.getProcessedDurationSeconds()) + " s");
					}
					addMachineSizingTooltip(lines, entry, balancedRate);
					if (entry.getRecipeEUt() > 0L) {
						lines.add(PlannerText.tr("tooltip.average_power", "Average power") + ": " + formatExactRate(entry.getAveragePowerEUt(balancedRate)) + " EU/t");
					}
					lines.add(PlannerText.tr("tooltip.resource_exact", "Resource flow stays at the exact BAL rate; spare machine capacity is not treated as extra output"));
					lines.add(PlannerText.tr("tooltip.return_auto_man", "Click BAL mode to return to AUTO/MAN"));
					drawTooltip(context, mouseX, mouseY, lines.toArray(String[]::new));
				} else if (entry.isAutomatic()) {
					double seconds = entry.getProcessedDurationSeconds();
					if (isCompactLayout()) {
						drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.crafts", "Crafts") + "/" + displayTimeUnit.suffix + ": " + formatExactDisplayRate(entry.getEffectiveRate()),
							PlannerText.tr("tooltip.machines_parallel", "%s machines x %s parallel / %s s", entry.getMachines(), entry.getParallel(), formatExactRate(seconds)),
							PlannerText.tr("tooltip.overclocks_power", "%s overclock(s), %s EU/t", entry.getOverclockCount(), entry.getProcessedEUt()),
							PlannerText.tr("tooltip.edit_duration", "Right-click: edit base duration"), PlannerText.tr("tooltip.reset_duration", "Middle-click: reset duration override"),
							PlannerText.tr("tooltip.switch_man", "Switch to MAN to edit the displayed rate directly"));
					} else {
						drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.crafts", "Crafts") + "/" + displayTimeUnit.suffix + ": " + formatExactDisplayRate(entry.getEffectiveRate()),
							PlannerText.tr("tooltip.machines_parallel", "%s machines x %s parallel / %s s", entry.getMachines(), entry.getParallel(), formatExactRate(seconds)),
							PlannerText.tr("tooltip.overclocks_power", "%s overclock(s), %s EU/t", entry.getOverclockCount(), entry.getProcessedEUt()),
							PlannerText.tr("tooltip.switch_man", "Switch to MAN to edit the displayed rate directly"));
					}
				} else {
					drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.manual_crafts", "Manual crafts") + "/" + displayTimeUnit.suffix + ": " + formatExactDisplayRate(entry.getRate()),
						PlannerText.tr("tooltip.mouse_wheel", "Mouse wheel: 1"), PlannerText.tr("tooltip.modifiers", "Shift: 10   Ctrl: 0.1   Alt: 0.01"), "Right-click to type an exact value");
				}
				return;
			}
			if (row.replace.contains(mouseX, mouseY)) {
				List<String> lines = new ArrayList<>();
				lines.add(PlannerText.tr("tooltip.replace", "Replace recipe"));
				lines.add(PlannerText.tr("tooltip.replace2", "Left-click: open alternative recipes for this row's primary output"));
				lines.add(PlannerText.tr("tooltip.path_cycle", "Right-click: cycle global path rule (PREFER -> LOCK -> AVOID -> OFF)"));
				EmiRecipe recipe = entry.getRecipe();
				if (recipe != null && recipe.getId() != null) {
					EmiStack output = firstOutput(recipe);
					if (!output.isEmpty()) {
						RecipePathMode pathMode = ProductionPlanner.getRecipePathMode(output, recipe.getId());
						if (pathMode != RecipePathMode.NONE) {
							lines.add(PlannerText.tr("tooltip.path_current", "Global path: %s", recipePathModeLabel(pathMode)));
							lines.add(recipePathModeDescription(pathMode));
						}
					}
				}
				lines.add(PlannerText.tr("tooltip.replace4", "Group, MACH/PAR/VOLT and compatible machine CFG are preserved"));
				drawTooltip(context, mouseX, mouseY, lines.toArray(String[]::new));
				return;
			}
			if (row.remove.contains(mouseX, mouseY)) {
				drawTooltip(context, mouseX, mouseY, PlannerText.tr("tooltip.remove_recipe", "Remove recipe from line"));
				return;
			}
		}
		for (FlowHitbox hitbox : flowHitboxes) {
			if (hitbox.bounds.contains(mouseX, mouseY)) {
				Flow flow = hitbox.flow;
				List<ClientTooltipComponent> tooltip = new ArrayList<>();
				tooltip.add(ClientTooltipComponent.create(EmiPort.ordered(flow.stack.getName())));
				if (flow.input > EPSILON || flow.output > EPSILON || flow.internal > EPSILON || flow.external > EPSILON) {
					tooltip.add(line(PlannerText.tr("flow.total_consumed", "Total consumed") + ": " + formatExactDisplayRate(flow.input) + unitSuffix(flow.stack)));
					tooltip.add(line(PlannerText.tr("flow.total_produced", "Total produced") + ": " + formatExactDisplayRate(flow.output) + unitSuffix(flow.stack)));
					tooltip.add(line(PlannerText.tr("flow.internal_flow", "Internal flow") + ": " + formatExactDisplayRate(flow.internal) + unitSuffix(flow.stack)));
					tooltip.add(line(PlannerText.tr("flow.external_input_label", "External input") + ": " + formatExactDisplayRate(flow.external) + unitSuffix(flow.stack)));
					tooltip.add(line(PlannerText.tr("flow.net_output_label", "Net output") + ": " + formatExactDisplayRate(Math.max(0, flow.output - flow.input)) + unitSuffix(flow.stack)));
				} else {
					tooltip.add(line(PlannerText.tr("flow.rate", "Rate") + ": " + formatExactDisplayRate(flow.displayValue) + unitSuffix(flow.stack)));
				}
				if (flow.approximate) {
					tooltip.add(line(PlannerText.tr("flow.expected", "Expected value: chance or alternative ingredient involved")));
				}
				if (hitbox.rootLink) {
					LinkMode mode = activeLine.getLinkMode(null, flow.stack);
					tooltip.add(line(PlannerText.tr("flow.link_mode", "Cycle mode") + ": " + linkModeLabel(mode)));
					tooltip.add(line(PlannerText.tr("flow.link_mode_hint", "Middle-click: AUTO -> MATCH -> IGNORE")));
				}
				if (hitbox.goalMode == TargetMode.OUTPUT) {
					tooltip.add(line(isTarget(ProductionPlanner.getOrCreateActiveLine(), flow.stack)
						? PlannerText.tr("flow.already_goal", "Already selected as a Line goal")
						: PlannerText.tr("flow.add_out", "Left-click: add as OUT Auto-Balance target")));
					tooltip.add(line(PlannerText.tr("flow.shift_recipes", "Shift + left-click: view recipes")));
				} else if (hitbox.goalMode == TargetMode.INPUT) {
					tooltip.add(line(isTarget(ProductionPlanner.getOrCreateActiveLine(), flow.stack)
						? PlannerText.tr("flow.already_goal", "Already selected as a Line goal")
						: PlannerText.tr("flow.add_in", "Ctrl + left-click: add as IN input goal")));
					tooltip.add(line(PlannerText.tr("tooltip.view_recipes", "Left-click: view recipes")));
				} else {
					tooltip.add(line(PlannerText.tr("tooltip.view_recipes", "Left-click: view recipes")));
				}
				tooltip.add(line(PlannerText.tr("tooltip.view_uses", "Right-click: view uses")));
				EmiRenderHelper.drawTooltip(this, context, tooltip, mouseX, mouseY);
				return;
			}
		}
	}

	private void addMachineSizingTooltip(List<String> lines, Entry entry, double craftsPerSecond) {
		MachineSizing sizing = entry.getMachineSizing(craftsPerSecond);
		if (!sizing.available()) {
			lines.add(PlannerText.tr("sizing.unavailable_row", "Machine sizing unavailable for this row"));
			return;
		}
		lines.add(PlannerText.tr("sizing.required_parallel", "Required effective parallel") + ": " + formatExactRate(sizing.requiredEffectiveParallel()));
		String setupLabel = (entry.isMachinesFixed() || entry.isParallelFixed() ? PlannerText.tr("sizing.constrained", "Constrained setup") : PlannerText.tr("sizing.recommended", "Recommended setup")) + ": ";
		lines.add(setupLabel + PlannerText.tr("sizing.setup", "%s machine(s) x %s parallel", sizing.machines(), sizing.parallel()));
		lines.add(PlannerText.tr("sizing.installed_capacity", "Installed capacity") + ": " + formatExactDisplayRate(sizing.capacityRate()) + " crafts/" + displayTimeUnit.suffix);
		if (sizing.sufficient()) {
			lines.add(PlannerText.tr("sizing.headroom", "Headroom") + ": " + formatExactRate(sizing.headroomPercent()) + "%");
		} else {
			lines.add(PlannerText.tr("sizing.shortfall", "CAPACITY SHORTFALL") + ": " + formatExactRate(sizing.shortfallPercent()) + "%");
		}
		if (entry.isMachinesFixed() || entry.isParallelFixed()) {
			lines.add(PlannerText.tr("sizing.locks", "Locks: MACH %s, PAR %s", entry.isMachinesFixed() ? PlannerText.tr("common.fixed", "FIXED") : PlannerText.tr("common.auto", "auto"), entry.isParallelFixed() ? PlannerText.tr("common.fixed", "FIXED") : PlannerText.tr("common.auto", "auto")));
		}
		lines.add((sizing.exact() ? PlannerText.tr("sizing.exact", "Sizing: exact from detected machine limit") : PlannerText.tr("sizing.provisional", "Sizing: provisional")) + " - " + sizing.note());
	}

	private void drawTooltip(EmiDrawContext context, int mouseX, int mouseY, String... lines) {
		List<ClientTooltipComponent> tooltip = new ArrayList<>();
		for (String value : lines) {
			tooltip.add(line(value));
		}
		EmiRenderHelper.drawTooltip(this, context, tooltip, mouseX, mouseY);
	}

	private ClientTooltipComponent line(String value) {
		return ClientTooltipComponent.create(EmiPort.ordered(EmiPort.literal(value)));
	}

	private boolean handleGroupsClick(Line line, int mx, int my, int button) {
		if (button == 0 && groupCloseButton.contains(mx, my)) {
			commitGroupRename();
			clearGroupDragState();
			groupsOpen = false;
			return true;
		}
		if (button == 0 && selectedGroup != null && groupRenameButton.contains(mx, my)) {
			startGroupRename(selectedGroup, groupSelectedNameBounds);
			return true;
		}
		if (button == 0 && selectedGroup != null && groupCollapseButton.contains(mx, my)) {
			ProductionPlanner.setGroupCollapsed(line, selectedGroup, !selectedGroup.isCollapsed());
			groupListScroll = 0;
			rowScroll = 0;
			return true;
		}
		for (GroupListHitbox hitbox : groupListHitboxes) {
			if (hitbox.group != null && button == 0 && hitbox.collapse.contains(mx, my)) {
				ProductionPlanner.setGroupCollapsed(line, hitbox.group, !hitbox.group.isCollapsed());
				selectedGroup = hitbox.group;
				groupListScroll = 0;
				rowScroll = 0;
				return true;
			}
			if (hitbox.bounds.contains(mx, my)) {
				if (button == 1 && hitbox.group != null) {
					selectedGroup = hitbox.group;
					startGroupRename(hitbox.group, hitbox.bounds);
					return true;
				}
				if (button == 0) {
					selectedGroup = hitbox.group;
					groupRecipeScroll = 0;
					groupLinkScroll = 0;
					if (hitbox.group != null) {
						pendingGroupDrag = hitbox.group;
						groupDragStartX = mx;
						groupDragStartY = my;
					}
					return true;
				}
			}
		}
		if (button == 0 && groupAddButton.contains(mx, my)) {
			selectedGroup = ProductionPlanner.createGroup(line, null);
			groupListScroll = 0;
			groupLinkScroll = 0;
			return true;
		}
		if (button == 0 && groupChildButton.contains(mx, my) && selectedGroup != null) {
			selectedGroup = ProductionPlanner.createGroup(line, selectedGroup);
			groupListScroll = 0;
			groupLinkScroll = 0;
			return true;
		}
		if (button == 0 && groupDeleteButton.contains(mx, my) && selectedGroup != null) {
			Group parent = selectedGroup.getParent(line);
			ProductionPlanner.removeGroup(line, selectedGroup);
			selectedGroup = parent;
			groupListScroll = 0;
			groupRecipeScroll = 0;
			groupLinkScroll = 0;
			rowScroll = 0;
			return true;
		}
		for (GroupRecipeHitbox hitbox : groupRecipeHitboxes) {
			if (button == 0 && hitbox.moveBounds.contains(mx, my)) {
				ProductionPlanner.setEntryGroup(line, hitbox.entry, selectedGroup);
				rowScroll = 0;
				return true;
			}
			if (button == 0 && hitbox.rowBounds.contains(mx, my)) {
				pendingRecipeDrag = hitbox.entry;
				recipeDragStartX = mx;
				recipeDragStartY = my;
				return true;
			}
		}
		for (GroupLinkHitbox hitbox : groupLinkHitboxes) {
			if (button == 0 && hitbox.bounds.contains(mx, my)) {
				boolean rebalance = line.isBalanceEnabled();
				LinkMode next = selectedGroup == null ? hitbox.mode.nextRoot() : hitbox.mode.nextGroup();
				ProductionPlanner.setGroupLinkMode(line, selectedGroup, hitbox.stack, next);
				if (rebalance) {
					ProductionPlanner.balanceLine(line);
				}
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		ProductionPlanner.beginHistoryAction();
		try {
		int mx = (int) mouseX;
		int my = (int) mouseY;
		Line currentLine = ProductionPlanner.getOrCreateActiveLine();
		if (recipeChangesOpen) {
			if (button == 0 && recipeChangesCloseButton.contains(mx, my)) {
				recipeChangesOpen = false;
				return true;
			}
			if (button == 0 && recipeChangesAcceptAllButton.contains(mx, my)) {
				int accepted = ProductionPlanner.acceptAllRecipeChanges(currentLine);
				recipeChangesStatus = PlannerText.tr("migration.accepted_count", "Accepted %s recipe change(s)", accepted);
				recipeChangesScroll = 0;
				return true;
			}
			for (RecipeChangeHitbox hitbox : recipeChangeHitboxes) {
				if (button == 0 && hitbox.action().contains(mx, my)) {
					RecipeChange change = hitbox.change();
					if (change.kind() == RecipeChangeKind.MISSING) {
						ProductionPlanner.removeEntry(currentLine, change.entry());
						recipeChangesStatus = PlannerText.tr("migration.removed", "Removed missing recipe row");
					} else if (ProductionPlanner.acceptRecipeChange(currentLine, change.entry())) {
						recipeChangesStatus = PlannerText.tr("migration.accepted", "Accepted current recipe as the new baseline");
					}
					recipeChangesScroll = Math.max(0, recipeChangesScroll);
					return true;
				}
			}
			return true;
		}
		if (buildSummaryOpen) {
			if (button == 0 && buildSummaryMachinesButton.contains(mx, my)) {
				buildSummaryFlowsView = false;
				buildSummaryStartupView = false;
				buildSummaryScroll = 0;
				return true;
			}
			if (button == 0 && buildSummaryFlowsButton.contains(mx, my)) {
				buildSummaryFlowsView = true;
				buildSummaryStartupView = false;
				buildSummaryScroll = 0;
				return true;
			}
			if (button == 0 && buildSummaryStartupButton.contains(mx, my)) {
				buildSummaryFlowsView = false;
				buildSummaryStartupView = true;
				buildSummaryScroll = 0;
				return true;
			}
			if (button == 0 && buildSummaryCopyButton.contains(mx, my)) {
				String text = buildSummaryText(currentLine);
				Minecraft.getInstance().keyboardHandler.setClipboard(text);
				buildSummaryTransferStatus = PlannerText.tr("transfer.report_copied", "Line Report copied to clipboard");
				return true;
			}
			if (button == 0 && buildSummarySaveButton.contains(mx, my)) {
				String text = buildSummaryText(currentLine);
				String path = ProductionPlanner.writeBuildSummaryExportFile(text);
				if (path.isBlank()) {
					buildSummaryTransferStatus = PlannerText.tr("transfer.report_save_failed", "Could not save Line Report; use COPY instead");
				} else {
					int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
					String fileName = slash >= 0 ? path.substring(slash + 1) : path;
					buildSummaryTransferStatus = PlannerText.tr("transfer.saved", "Saved") + " " + fileName;
				}
				return true;
			}
			if (button == 0 && buildSummaryCloseButton.contains(mx, my)) {
				buildSummaryOpen = false;
				return true;
			}
			return true;
		}
		if (searchOpen && searchField != null) {
			if (button == 0 && searchCloseButton.contains(mx, my)) {
				closeSearch();
				return true;
			}
			if (searchField.mouseClicked(mouseX, mouseY, button)) {
				EmiPort.focus(searchField, true);
				return true;
			}
			if (searchPanelBounds.contains(mx, my)) {
				return true;
			}
			if (searchField.isFocused()) {
				EmiPort.focus(searchField, false);
			}
		}
		if (groupsOpen) {
			if (groupRenameField != null) {
				if (groupRenameField.mouseClicked(mouseX, mouseY, button)) {
					EmiPort.focus(groupRenameField, true);
					return true;
				}
				commitGroupRename();
			}
			return handleGroupsClick(currentLine, mx, my, button);
		}
		if (isDropdownOpen()) {
			if (coilMenuOpen && machineConfigEntry != null) {
				if (button == 0) {
					for (CoilOptionHitbox option : coilOptionHitboxes) {
						if (option.bounds.contains(mx, my)) {
							ProductionPlanner.setCoilTier(machineConfigEntry, option.tier);
							coilMenuOpen = false;
							activeDropdownBounds = EMPTY;
							return true;
						}
					}
				}
				if (activeDropdownBounds.contains(mx, my) || coilMenuAnchor.contains(mx, my)) {
					return true;
				}
				coilMenuOpen = false;
				activeDropdownBounds = EMPTY;
				return true;
			}
			if (machineConfigEntry != null) {
				if (button == 0 && configCoilMinus.contains(mx, my)) {
					ProductionPlanner.cycleCoilTier(machineConfigEntry, -1);
					return true;
				}
				if (button == 0 && configCoilPlus.contains(mx, my)) {
					ProductionPlanner.cycleCoilTier(machineConfigEntry, 1);
					return true;
				}
				if ((button == 0 || button == 1) && configCoilValue.contains(mx, my)) {
					if (button == 0) {
						coilMenuOpen = true;
						coilMenuAnchor = configCoilValue;
						activeDropdownBounds = EMPTY;
					} else {
						ProductionPlanner.cycleCoilTier(machineConfigEntry, -1);
					}
					return true;
				}
				if (button == 0 && configParallelMinus.contains(mx, my)) {
					ProductionPlanner.setParallel(machineConfigEntry, machineConfigEntry.getParallel() - countAdjustmentStep());
					return true;
				}
				if (button == 0 && configParallelPlus.contains(mx, my)) {
					ProductionPlanner.setParallel(machineConfigEntry, machineConfigEntry.getParallel() + countAdjustmentStep());
					return true;
				}
				for (MachineSettingControl control : configSpecialControls) {
					if (control.minus.contains(mx, my) && (button == 0 || button == 1)) {
						ProductionPlanner.cycleMachineSetting(machineConfigEntry, control.spec, -1);
						return true;
					}
					if (control.plus.contains(mx, my) && (button == 0 || button == 1)) {
						ProductionPlanner.cycleMachineSetting(machineConfigEntry, control.spec, 1);
						return true;
					}
					if (control.value.contains(mx, my) && (button == 0 || button == 1)) {
						ProductionPlanner.cycleMachineSetting(machineConfigEntry, control.spec, button == 0 ? 1 : -1);
						return true;
					}
				}
			}
			if (button == 0 && machineMenuEntry != null) {
				for (MachineOptionHitbox option : machineOptionHitboxes) {
					if (option.favoriteBounds.contains(mx, my)) {
						ProductionPlanner.togglePreferredMachine(machineMenuEntry, option.profile.id());
						return true;
					}
					if (option.bounds.contains(mx, my)) {
						ProductionPlanner.setMachineProfile(machineMenuEntry, option.profile.id());
						closeDropdowns();
						return true;
					}
				}
			}
			if (button == 0 && (voltageMenuEntry != null || standardVoltageMenuOpen)) {
				for (VoltageOptionHitbox option : voltageOptionHitboxes) {
					if (option.bounds.contains(mx, my)) {
						if (standardVoltageMenuOpen) {
							ProductionPlanner.setLineStandardVoltage(currentLine, option.tier);
						} else {
							ProductionPlanner.setVoltageTier(voltageMenuEntry, option.tier);
						}
						closeDropdowns();
						return true;
					}
				}
			}
			Bounds anchor = machineConfigEntry != null ? machineConfigAnchor : machineMenuEntry != null ? machineMenuAnchor : standardVoltageMenuOpen ? standardVoltageBounds : voltageMenuAnchor;
			if (activeDropdownBounds.contains(mx, my) || anchor.contains(mx, my)) {
				if (anchor.contains(mx, my)) {
					closeDropdowns();
				}
				return true;
			}
			closeDropdowns();
		}
		if (editField != null && editField.mouseClicked(mouseX, mouseY, button)) {
			EmiPort.focus(editField, true);
			return true;
		}
		if (targetRateField != null && targetRateField.mouseClicked(mouseX, mouseY, button)) {
			EmiPort.focus(targetRateField, true);
			return true;
		}
		if (renameField != null && renameField.mouseClicked(mouseX, mouseY, button)) {
			EmiPort.focus(renameField, true);
			return true;
		}
		if (editField != null) {
			commitEntryEdit();
		}
		if (targetRateField != null) {
			commitTargetRateEdit();
		}
		if (renameField != null) {
			commitRename();
		}
		if (button == 0 && newLineButton.contains(mx, my)) {
			closeDropdowns();
			ProductionPlanner.createLine();
			rowScroll = 0;
			return true;
		}
		if (button == 0 && closeLineButton.contains(mx, my)) {
			closeDropdowns();
			ProductionPlanner.removeActiveLine();
			ProductionPlanner.getOrCreateActiveLine();
			rowScroll = 0;
			return true;
		}
		for (TabHitbox tab : tabHitboxes) {
			if (tab.bounds.contains(mx, my)) {
				if (button == 1) {
					startRename(tab);
				} else if (button == 0) {
					closeDropdowns();
					ProductionPlanner.setActiveIndex(tab.index);
					rowScroll = 0;
					if (searchOpen) {
						refreshSearchMatches(ProductionPlanner.getOrCreateActiveLine(), true);
					}
				}
				return true;
			}
		}
		Line line = ProductionPlanner.getOrCreateActiveLine();
		if (button == 0 && toolsButton.contains(mx, my)) {
			boolean open = !toolsOpen;
			closeDropdowns();
			toolsOpen = open;
			return true;
		}
		if (toolsOpen && !toolsMenuBounds.contains(mx, my)) {
			toolsOpen = false;
		}
		if ((button == 0 || button == 1) && standardVoltageBounds.contains(mx, my)) {
			if (button == 1) {
				ProductionPlanner.setLineStandardVoltage(line, -1);
			} else {
				openStandardVoltageMenu(line);
			}
			return true;
		}
		if (button == 0 && applyStandardVoltageBounds.contains(mx, my)) {
			ProductionPlanner.applyLineStandardVoltageToAll(line);
			return true;
		}
		if (button == 0 && groupsButton.contains(mx, my)) {
			closeDropdowns();
			groupsOpen = true;
			selectedGroup = null;
			groupListScroll = 0;
			groupRecipeScroll = 0;
			groupLinkScroll = 0;
			return true;
		}
		if (button == 0 && exportLineButton.contains(mx, my)) {
			closeDropdowns();
			String json = ProductionPlanner.exportActiveLineJson();
			if (json.isBlank()) {
				lineTransferStatus = PlannerText.tr("transfer.export_failed", "Export failed");
			} else {
				Minecraft.getInstance().keyboardHandler.setClipboard(json);
				String path = ProductionPlanner.writeActiveLineExportFile(json);
				if (path.isBlank()) {
					lineTransferStatus = PlannerText.tr("transfer.json_copied", "Line JSON copied to clipboard");
				} else {
					int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
					String fileName = slash >= 0 ? path.substring(slash + 1) : path;
					lineTransferStatus = PlannerText.tr("transfer.exported", "Exported %s + copied JSON to clipboard", fileName);
				}
			}
			return true;
		}
		if (button == 0 && importLineButton.contains(mx, my)) {
			closeDropdowns();
			String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
			LineTransferResult result = ProductionPlanner.importLineJson(clipboard);
			lineTransferStatus = result.message();
			if (result.success()) {
				rowScroll = 0;
				clampScroll();
			}
			return true;
		}
		if (button == 0 && timeUnitButton.contains(mx, my)) {
			displayTimeUnit = displayTimeUnit.next();
			return true;
		}
		if (button == 0 && buildSummaryButton.contains(mx, my)) {
			closeDropdowns();
			closeSearch();
			groupsOpen = false;
			buildSummaryOpen = true;
			buildSummaryFlowsView = false;
			buildSummaryStartupView = false;
			buildSummaryScroll = 0;
			buildSummaryTransferStatus = "";
			return true;
		}
		if (button == 0 && recipeChangesButton.contains(mx, my)) {
			closeDropdowns();
			closeSearch();
			groupsOpen = false;
			buildSummaryOpen = false;
			toolsOpen = false;
			recipeChangesOpen = true;
			recipeChangesScroll = 0;
			recipeChangesStatus = "";
			ProductionPlanner.getRecipeChanges(line);
			return true;
		}
		if (button == 0 && graphViewButton.contains(mx, my)) {
			closeDropdowns();
			closeSearch();
			groupsOpen = false;
			toolsOpen = false;
			ProductionPlanner.save();
			Minecraft.getInstance().setScreen(new ProductionPlannerGraphScreen(this));
			return true;
		}
		if (toolsOpen && toolsMenuBounds.contains(mx, my)) {
			return true;
		}
		if (SHOW_GTO_AUDIT && button == 0 && gtoAuditButton.contains(mx, my)) {
			closeDropdowns();
			GtoCapabilityAudit.AuditResult result = GtoCapabilityAudit.run();
			Minecraft.getInstance().keyboardHandler.setClipboard(result.report());
			gtoAuditStatus = result.shortStatus();
			return true;
		}
		for (TargetHitbox hitbox : targetHitboxes) {
			if (hitbox.icon.contains(mx, my) && button == 0 && EmiInput.isControlDown()) {
				ProductionPlanner.toggleTargetMode(line, hitbox.target);
				return true;
			}
			if (hitbox.icon.contains(mx, my) && (button == 0 || button == 1)) {
				queueStackClick(hitbox.target.getStack(), button);
				return true;
			}
			if (button == 0 && hitbox.remove.contains(mx, my)) {
				ProductionPlanner.removeBalanceTarget(line, hitbox.target);
				return true;
			}
			if ((button == 0 || button == 1) && hitbox.rate.contains(mx, my)) {
				startTargetRateEdit(line, hitbox.target, hitbox.rate);
				return true;
			}
		}
		if (button == 0 && balanceButton.contains(mx, my)) {
			ProductionPlanner.balanceLine(line);
			return true;
		}
		if (button == 0 && clearTargetButton.contains(mx, my)) {
			ProductionPlanner.clearBalanceTarget(line);
			return true;
		}
		for (FlowHitbox hitbox : flowHitboxes) {
			if (!hitbox.bounds.contains(mx, my)) {
				continue;
			}
			if (hitbox.rootLink && button == 2) {
				boolean rebalance = line.isBalanceEnabled();
				LinkMode mode = line.getLinkMode(null, hitbox.flow.stack);
				ProductionPlanner.setGroupLinkMode(line, null, hitbox.flow.stack, mode.nextRoot());
				if (rebalance) {
					ProductionPlanner.balanceLine(line);
				}
				return true;
			}
			if (button == 0) {
				double defaultRate = (hitbox.flow.stack.getKey() instanceof Fluid ? 1000.0D : 1.0D) / displayTimeUnit.multiplier;
				if (hitbox.goalMode == TargetMode.OUTPUT && !EmiInput.isShiftDown() && !EmiInput.isControlDown()) {
					ProductionPlanner.setBalanceTarget(line, hitbox.flow.stack, defaultRate, TargetMode.OUTPUT);
					return true;
				}
				if (hitbox.goalMode == TargetMode.INPUT && EmiInput.isControlDown()) {
					ProductionPlanner.setBalanceTarget(line, hitbox.flow.stack, defaultRate, TargetMode.INPUT);
					return true;
				}
				queueStackClick(hitbox.flow.stack, button);
				return true;
			}
			if (button == 1) {
				queueStackClick(hitbox.flow.stack, button);
				return true;
			}
		}
		for (GroupMainHitbox hitbox : groupMainHitboxes) {
			if (hitbox.bounds.contains(mx, my)) {
				if (button == 0) {
					ProductionPlanner.setGroupCollapsed(line, hitbox.group, !hitbox.group.isCollapsed());
					clampScroll();
					return true;
				}
				if (button == 1) {
					groupsOpen = true;
					selectedGroup = hitbox.group;
					groupListScroll = 0;
					groupRecipeScroll = 0;
					groupLinkScroll = 0;
					return true;
				}
			}
		}
		for (RowHitbox row : rowHitboxes) {
			Entry entry = row.entry;
			if (button == 0 && row.mode.contains(mx, my)) {
				if (line.isBalanceEnabled()) {
					ProductionPlanner.disableBalance(line);
				} else if (entry.isAutomatic()) {
					ProductionPlanner.setAutomatic(entry, false);
				} else if (!ProductionPlanner.setAutomatic(entry, true)) {
					startEntryEdit(row, EditKind.DURATION);
				}
				return true;
			}
			if ((button == 0 || button == 1 || button == 2) && row.machineProfile.contains(mx, my)) {
				if (button == 0) {
					openMachineMenu(row);
				} else {
					ProductionPlanner.setMachineProfile(entry, "generic");
				}
				return true;
			}
			if (button == 0 && row.machineConfig.contains(mx, my) && entry.getMachineProfile().hasConfigurableSettings()) {
				openMachineConfig(row);
				return true;
			}
			if (button == 0 && row.machinesLock.contains(mx, my)) {
				ProductionPlanner.setMachinesFixed(entry, !entry.isMachinesFixed());
				return true;
			}
			if (button == 0 && row.machinesMinus.contains(mx, my)) {
				ProductionPlanner.setMachines(entry, entry.getMachines() - countAdjustmentStep());
				return true;
			}
			if (button == 0 && row.machinesPlus.contains(mx, my)) {
				ProductionPlanner.setMachines(entry, entry.getMachines() + countAdjustmentStep());
				return true;
			}
			if (button == 1 && row.machinesValue.contains(mx, my)) {
				startEntryEdit(row, EditKind.MACHINES);
				return true;
			}
			if (button == 0 && row.parallelLock.contains(mx, my)) {
				ProductionPlanner.setParallelFixed(entry, !entry.isParallelFixed());
				return true;
			}
			if (button == 0 && row.parallelMinus.contains(mx, my)) {
				ProductionPlanner.setParallel(entry, entry.getParallel() - countAdjustmentStep());
				return true;
			}
			if (button == 0 && row.parallelPlus.contains(mx, my)) {
				ProductionPlanner.setParallel(entry, entry.getParallel() + countAdjustmentStep());
				return true;
			}
			if (button == 1 && row.parallelValue.contains(mx, my)) {
				startEntryEdit(row, EditKind.PARALLEL);
				return true;
			}
			if ((button == 0 || button == 1 || button == 2) && row.voltage.contains(mx, my)) {
				if (entry.isVoltageFixedByMachine()) {
					return true;
				}
				if (button == 0) {
					openVoltageMenu(row);
				} else {
					ProductionPlanner.resetVoltageToLine(line, entry);
				}
				return true;
			}
			if ((button == 0 || button == 1) && row.oc.contains(mx, my)) {
				ProductionPlanner.cycleOcMode(entry, button == 0 ? 1 : -1);
				return true;
			}
			if (button == 1 && row.duration.contains(mx, my)) {
				startEntryEdit(row, EditKind.DURATION);
				return true;
			}
			if (button == 2 && row.duration.contains(mx, my)) {
				ProductionPlanner.clearDurationOverride(entry);
				if (entry.isAutomatic() && entry.getDurationTicks() <= 0.0D) {
					ProductionPlanner.setAutomatic(entry, false);
				}
				return true;
			}
			if (row.rate.contains(mx, my) && !line.isBalanceEnabled()) {
				if (button == 1 && entry.isAutomatic() && isCompactLayout()) {
					startEntryEdit(row, EditKind.DURATION);
					return true;
				}
				if (button == 2 && entry.isAutomatic() && isCompactLayout()) {
					ProductionPlanner.clearDurationOverride(entry);
					if (entry.getDurationTicks() <= 0.0D) {
						ProductionPlanner.setAutomatic(entry, false);
					}
					return true;
				}
				if (button == 1 && !entry.isAutomatic()) {
					startEntryEdit(row, EditKind.RATE);
					return true;
				}
			}
			if (row.replace.contains(mx, my)) {
				if (button == 0) {
					return openRecipeReplacement(line, entry);
				}
				if (button == 1) {
					EmiRecipe recipe = entry.getRecipe();
					if (recipe != null && recipe.getId() != null) {
						EmiStack output = firstOutput(recipe);
						if (!output.isEmpty()) {
							ProductionPlanner.cycleRecipePathMode(output, recipe.getId());
							return true;
						}
					}
				}
			}
			if (button == 0 && row.remove.contains(mx, my)) {
				ProductionPlanner.removeEntry(line, entry);
				clampScroll();
				return true;
			}
			if (button == 0 && row.recipeBounds.contains(mx, my)) {
				EmiRecipe recipe = entry.getRecipe();
				if (recipe != null) {
					ProductionPlanner.save();
					EmiApi.displayRecipe(recipe);
				}
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
		} finally {
			ProductionPlanner.endHistoryAction();
		}
	}


	private boolean openRecipeReplacement(Line line, Entry entry) {
		if (line == null || entry == null || entry.getRecipe() == null) {
			return false;
		}
		EmiRecipe current = entry.getRecipe();
		EmiStack output = firstOutput(current);
		if (output.isEmpty()) {
			return false;
		}
		List<EmiRecipe> alternatives = findOutputRecipes(output);
		if (alternatives.isEmpty() || !ProductionPlanner.beginRecipeReplacement(line, entry, output)) {
			return false;
		}
		if (!openRecipeScreen(output, alternatives, current)) {
			ProductionPlanner.cancelPendingRecipeReplacement();
			return false;
		}
		return true;
	}

	private void queueStackClick(EmiStack stack, int button) {
		if (stack == null || stack.isEmpty()) {
			pendingStackClick = EmiStack.EMPTY;
			pendingStackButton = -1;
			return;
		}
		pendingStackClick = stack.copy();
		pendingStackButton = button;
	}

	private void clearPendingStackClick() {
		pendingStackClick = EmiStack.EMPTY;
		pendingStackButton = -1;
	}

	private boolean openStackRecipes(EmiStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		List<EmiRecipe> recipes = findOutputRecipes(stack);
		if (recipes.isEmpty()) {
			return false;
		}
		EmiRecipe preferred = findPreferredRecipe(stack, recipes);
		return openRecipeScreen(stack, recipes, preferred);
	}

	private boolean openStackUses(EmiStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		List<EmiRecipe> uses = findUseRecipes(stack);
		if (uses.isEmpty()) {
			return false;
		}
		return openRecipeScreen(stack, uses, null);
	}

	private boolean openRecipeScreen(EmiStack context, List<EmiRecipe> recipes, EmiRecipe preferred) {
		Map<EmiRecipeCategory, List<EmiRecipe>> pages = new LinkedHashMap<>();
		for (EmiRecipe recipe : recipes) {
			if (recipe == null || recipe.getCategory() == null) {
				continue;
			}
			pages.computeIfAbsent(recipe.getCategory(), key -> new ArrayList<>()).add(recipe);
		}
		if (pages.isEmpty()) {
			return false;
		}

		ProductionPlanner.save();
		EmiSidebars.lookup(context);
		Minecraft client = Minecraft.getInstance();
		dev.emi.emi.screen.RecipeScreen recipeScreen = new RecipeScreen(old, pages);
		EmiHistory.push(this);
		client.setScreen(recipeScreen);
		if (client.screen != recipeScreen) {
			return false;
		}
		if (preferred != null) {
			recipeScreen.focusRecipe(preferred);
		}
		return true;
	}

	private List<EmiRecipe> findOutputRecipes(EmiStack stack) {
		List<EmiRecipe> recipes = new ArrayList<>();
		for (EmiRecipe recipe : EmiApi.getRecipeManager().getRecipes()) {
			boolean matches = false;
			for (EmiStack output : recipe.getOutputs()) {
				if (sameLookupStack(stack, output)) {
					matches = true;
					break;
				}
			}
			if (matches && !recipes.contains(recipe)) {
				recipes.add(recipe);
			}
		}
		return recipes;
	}

	private List<EmiRecipe> findUseRecipes(EmiStack stack) {
		List<EmiRecipe> recipes = new ArrayList<>();
		for (EmiRecipe recipe : EmiApi.getRecipeManager().getRecipes()) {
			boolean matches = ingredientListContains(recipe.getInputs(), stack)
				|| ingredientListContains(recipe.getCatalysts(), stack);
			if (matches && !recipes.contains(recipe)) {
				recipes.add(recipe);
			}
		}
		return recipes;
	}

	private boolean ingredientListContains(List<EmiIngredient> ingredients, EmiStack stack) {
		for (EmiIngredient ingredient : ingredients) {
			for (EmiStack candidate : ingredient.getEmiStacks()) {
				if (sameLookupStack(stack, candidate)) {
					return true;
				}
			}
		}
		return false;
	}

	private EmiRecipe findPreferredRecipe(EmiStack stack, List<EmiRecipe> recipes) {
		EmiRecipe globalPreferred = ProductionPlanner.getPreferredRecipePath(stack);
		if (globalPreferred != null && globalPreferred.getId() != null) {
			for (EmiRecipe recipe : recipes) {
				if (recipe != null && globalPreferred.getId().equals(recipe.getId())) {
					return recipe;
				}
			}
		}
		if (ProductionPlanner.isRecipePathLocked(stack)) {
			return null;
		}

		EmiRecipe preferred = BoM.getRecipe(stack);
		if (preferred != null && preferred.getId() != null
				&& !ProductionPlanner.isRecipePathAvoided(stack, preferred.getId()) && recipes.contains(preferred)) {
			return preferred;
		}
		for (EmiRecipe recipe : recipes) {
			for (EmiStack output : recipe.getOutputs()) {
				if (!sameLookupStack(stack, output)) {
					continue;
				}
				preferred = BoM.getRecipe(output);
				if (preferred != null && preferred.getId() != null
						&& !ProductionPlanner.isRecipePathAvoided(stack, preferred.getId()) && recipes.contains(preferred)) {
					return preferred;
				}
			}
		}
		return null;
	}

	private boolean sameLookupStack(EmiStack a, EmiStack b) {
		if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
			return false;
		}
		if (a.isEqual(b)) {
			return true;
		}
		Object aKey = a.getKey();
		Object bKey = b.getKey();
		if (aKey != null && aKey.equals(bKey)) {
			return true;
		}
		return a.getId() != null && a.getId().equals(b.getId());
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (groupsOpen && button == 0) {
			if (pendingGroupDrag != null) {
				double dx = mouseX - groupDragStartX;
				double dy = mouseY - groupDragStartY;
				if (groupDragActive || dx * dx + dy * dy >= 16.0D) {
					groupDragActive = true;
					return true;
				}
			}
			if (pendingRecipeDrag != null) {
				double dx = mouseX - recipeDragStartX;
				double dy = mouseY - recipeDragStartY;
				if (recipeDragActive || dx * dx + dy * dy >= 16.0D) {
					recipeDragActive = true;
					return true;
				}
			}
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (pendingStackButton == button && pendingStackClick != null && !pendingStackClick.isEmpty()) {
			EmiStack stack = pendingStackClick;
			clearPendingStackClick();
			if (button == 0) {
				return openStackRecipes(stack);
			}
			if (button == 1) {
				return openStackUses(stack);
			}
		} else if (pendingStackButton >= 0) {
			clearPendingStackClick();
		}
		if (groupsOpen && button == 0) {
			Line line = ProductionPlanner.getOrCreateActiveLine();
			int mx = (int) mouseX;
			int my = (int) mouseY;
			if (groupDragActive && pendingGroupDrag != null) {
				applyGroupDrop(line, pendingGroupDrag, mx, my);
			} else if (recipeDragActive && pendingRecipeDrag != null) {
				applyRecipeDrop(line, pendingRecipeDrag, mx, my);
			}
			if (pendingGroupDrag != null || pendingRecipeDrag != null) {
				clearGroupDragState();
				return true;
			}
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		ProductionPlanner.beginHistoryAction();
		try {
		int mx = (int) mouseX;
		int my = (int) mouseY;
		if (recipeChangesOpen) {
			if (recipeChangesListArea.contains(mx, my)) {
				List<RecipeChange> changes = ProductionPlanner.getRecipeChanges(ProductionPlanner.getOrCreateActiveLine());
				int rowHeight = 104;
				int visible = Math.max(1, recipeChangesListArea.height() / rowHeight);
				recipeChangesScroll = Math.max(0, Math.min(recipeChangesScroll - (int) Math.signum(amount), Math.max(0, changes.size() - visible)));
			}
			return true;
		}
		if (buildSummaryOpen) {
			if (buildSummaryListArea.contains(mx, my)) {
				Line line = ProductionPlanner.getOrCreateActiveLine();
				int rowHeight = buildSummaryStartupView ? 30 : buildSummaryFlowsView ? 26 : 38;
				int rows = buildSummaryStartupView
					? ProductionPlanner.getStartupResources(line).size()
					: buildSummaryFlowsView ? collectFlowSummary(line, calculate(line)).size() : collectBuildSummary(line).size();
				int visible = Math.max(1, buildSummaryListArea.height() / rowHeight);
				buildSummaryScroll = Math.max(0, Math.min(buildSummaryScroll - (int) Math.signum(amount), Math.max(0, rows - visible)));
			}
			return true;
		}
		if (toolsOpen) {
			return true;
		}
		if (groupsOpen) {
			Line line = ProductionPlanner.getOrCreateActiveLine();
			int direction = (int) -Math.signum(amount);
			if (groupListArea.contains(mx, my)) {
				int total = flattenGroups(line).size() + 1;
				int visible = Math.max(1, groupListArea.height() / 24);
				groupListScroll = Math.max(0, Math.min(groupListScroll + direction, Math.max(0, total - visible)));
				return true;
			}
			if (groupRecipeArea.contains(mx, my)) {
				int visible = Math.max(1, (groupRecipeArea.height() - 4) / 25);
				groupRecipeScroll = Math.max(0, Math.min(groupRecipeScroll + direction, Math.max(0, line.getEntries().size() - visible)));
				return true;
			}
			if (groupLinkArea.contains(mx, my)) {
				int total = ProductionPlanner.getGroupLinkCandidates(line, selectedGroup).size();
				int visible = Math.max(1, (groupLinkArea.height() - 4) / 25);
				groupLinkScroll = Math.max(0, Math.min(groupLinkScroll + direction, Math.max(0, total - visible)));
				return true;
			}
			return true;
		}
		if (isDropdownOpen() && activeDropdownBounds.contains(mx, my)) {
			if (machineConfigEntry != null) {
				int direction = (int) Math.signum(amount);
				if (configCoilValue.contains(mx, my) || configCoilMinus.contains(mx, my) || configCoilPlus.contains(mx, my)) {
					ProductionPlanner.cycleCoilTier(machineConfigEntry, direction);
					return true;
				}
				if (configParallelValue.contains(mx, my) || configParallelMinus.contains(mx, my) || configParallelPlus.contains(mx, my)) {
					ProductionPlanner.setParallel(machineConfigEntry, machineConfigEntry.getParallel() + direction * countAdjustmentStep());
					return true;
				}
				for (MachineSettingControl control : configSpecialControls) {
					if (control.minus.contains(mx, my) || control.value.contains(mx, my) || control.plus.contains(mx, my)) {
						ProductionPlanner.cycleMachineSetting(machineConfigEntry, control.spec, direction);
						return true;
					}
				}
				return true;
			}
			int direction = (int) -Math.signum(amount);
			if (machineMenuEntry != null) {
				int size = ProductionPlanner.getCompatibleMachineProfiles(machineMenuEntry).size();
				machineMenuScroll = Math.max(0, Math.min(machineMenuScroll + direction, Math.max(0, size - MENU_MAX_ROWS)));
			} else {
				int size = standardVoltageMenuOpen
					? ProductionPlanner.maxVoltageTier() + 2
					: Math.max(0, ProductionPlanner.maxVoltageTier() - Math.max(0, voltageMenuEntry.getRecipeTier()) + 1);
				if (standardVoltageMenuOpen) {
					standardVoltageMenuScroll = Math.max(0, Math.min(standardVoltageMenuScroll + direction, Math.max(0, size - MENU_MAX_ROWS)));
				} else {
					voltageMenuScroll = Math.max(0, Math.min(voltageMenuScroll + direction, Math.max(0, size - MENU_MAX_ROWS)));
				}
			}
			return true;
		}
		for (RowHitbox row : rowHitboxes) {
			Entry entry = row.entry;
			int direction = (int) Math.signum(amount);
			if (row.machinesLock.contains(mx, my) || row.parallelLock.contains(mx, my)) {
				return true;
			}
			if (row.machineArea().contains(mx, my)) {
				ProductionPlanner.setMachines(entry, entry.getMachines() + direction * countAdjustmentStep());
				return true;
			}
			if (row.parallelArea().contains(mx, my)) {
				ProductionPlanner.setParallel(entry, entry.getParallel() + direction * countAdjustmentStep());
				return true;
			}
			Line line = ProductionPlanner.getOrCreateActiveLine();
			if (row.rate.contains(mx, my) && !line.isBalanceEnabled() && !entry.isAutomatic()) {
				ProductionPlanner.setRate(entry, entry.getRate() + Math.signum(amount) * adjustmentStep() / displayTimeUnit.multiplier);
				return true;
			}
		}
		if (my >= ROW_TOP) {
			rowScroll -= (int) Math.signum(amount);
			clampScroll();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
		} finally {
			ProductionPlanner.endHistoryAction();
		}
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (searchOpen && searchField != null && searchField.isFocused()) {
			searchField.charTyped(chr, modifiers);
			refreshSearchMatches(ProductionPlanner.getOrCreateActiveLine(), true);
			return true;
		}
		if (groupRenameField != null && groupRenameField.isFocused()) {
			groupRenameField.charTyped(chr, modifiers);
			return true;
		}
		if (editField != null && editField.isFocused()) {
			editField.charTyped(chr, modifiers);
			return true;
		}
		if (targetRateField != null && targetRateField.isFocused()) {
			targetRateField.charTyped(chr, modifiers);
			return true;
		}
		if (renameField != null && renameField.isFocused()) {
			renameField.charTyped(chr, modifiers);
			return true;
		}
		return super.charTyped(chr, modifiers);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (recipeChangesOpen) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				recipeChangesOpen = false;
			}
			return true;
		}
		if (buildSummaryOpen) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				buildSummaryOpen = false;
			}
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_F && EmiInput.isControlDown()) {
			openSearch();
			return true;
		}
		if (searchOpen) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				closeSearch();
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				cycleSearchMatch(EmiInput.isShiftDown() ? -1 : 1);
				return true;
			}
			if (searchField != null && searchField.isFocused()
					&& !(keyCode == GLFW.GLFW_KEY_Z && EmiInput.isControlDown() && !EmiInput.isShiftDown())) {
				searchField.keyPressed(keyCode, scanCode, modifiers);
				refreshSearchMatches(ProductionPlanner.getOrCreateActiveLine(), true);
				return true;
			}
		}
		if (groupRenameField != null && groupRenameField.isFocused()) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				cancelGroupRename();
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				commitGroupRename();
				return true;
			}
			groupRenameField.keyPressed(keyCode, scanCode, modifiers);
			return true;
		}
		if (groupsOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
			clearGroupDragState();
			groupsOpen = false;
			return true;
		}
		if (toolsOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
			toolsOpen = false;
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && isDropdownOpen()) {
			closeDropdowns();
			return true;
		}
		if (editField != null && editField.isFocused()) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				cancelEntryEdit();
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				commitEntryEdit();
				return true;
			}
			editField.keyPressed(keyCode, scanCode, modifiers);
			return true;
		}
		if (targetRateField != null && targetRateField.isFocused()) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				cancelTargetRateEdit();
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				commitTargetRateEdit();
				return true;
			}
			targetRateField.keyPressed(keyCode, scanCode, modifiers);
			return true;
		}
		if (renameField != null && renameField.isFocused()) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				cancelRename();
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				commitRename();
				return true;
			}
			renameField.keyPressed(keyCode, scanCode, modifiers);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_Z && EmiInput.isControlDown() && !EmiInput.isShiftDown()) {
			if (ProductionPlanner.undo()) {
				resetAfterUndo();
			}
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE || minecraft.options.keyInventory.matches(keyCode, scanCode)) {
			onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private void resetAfterUndo() {
		cancelEntryEdit();
		cancelTargetRateEdit();
		cancelRename();
		cancelGroupRename();
		closeDropdowns();
		clearPendingStackClick();
		clearGroupDragState();
		groupsOpen = false;
		buildSummaryOpen = false;
		selectedGroup = null;
		groupListScroll = 0;
		groupRecipeScroll = 0;
		groupLinkScroll = 0;
		rowScroll = 0;
		clampScroll();
		if (searchOpen) {
			refreshSearchMatches(ProductionPlanner.getOrCreateActiveLine(), true);
		}
	}

	private void startEntryEdit(RowHitbox row, EditKind kind) {
		editEntry = row.entry;
		editKind = kind;
		Bounds bounds = switch (kind) {
			case RATE -> row.rate;
			case MACHINES -> row.machinesValue;
			case PARALLEL -> row.parallelValue;
			case DURATION -> row.duration.width() > 0 ? row.duration : row.rate;
		};
		String label = switch (kind) {
			case RATE -> PlannerText.tr("edit.crafts_per", "Crafts per") + " " + localizedTimeUnitLong();
			case MACHINES -> PlannerText.tr("edit.machines", "Machines");
			case PARALLEL -> PlannerText.tr("edit.parallel", "Parallel");
			case DURATION -> PlannerText.tr("edit.duration_seconds", "Duration seconds");
		};
		editField = new EditBox(minecraft.font, bounds.x() + 2, bounds.y() + 2,
			Math.max(20, bounds.width() - 4), bounds.height() - 4, EmiPort.literal(label));
		editField.setMaxLength(24);
		String value = switch (kind) {
			case RATE -> formatExactDisplayRate(row.entry.getRate());
			case MACHINES -> Integer.toString(row.entry.getMachines());
			case PARALLEL -> Integer.toString(row.entry.getParallel());
			case DURATION -> row.entry.getDurationSeconds() > 0.0D ? formatExactRate(row.entry.getDurationSeconds()) : "";
		};
		editField.setValue(value);
		EmiPort.focus(editField, true);
	}

	private void commitEntryEdit() {
		ProductionPlanner.beginHistoryAction();
		try {
			if (editField != null && editEntry != null && editKind != null) {
				try {
					String text = editField.getValue().trim().replace(',', '.');
					switch (editKind) {
						case RATE -> ProductionPlanner.setRate(editEntry, Double.parseDouble(text) / displayTimeUnit.multiplier);
						case MACHINES -> ProductionPlanner.setMachines(editEntry, Integer.parseInt(text));
						case PARALLEL -> ProductionPlanner.setParallel(editEntry, Integer.parseInt(text));
						case DURATION -> {
							double seconds = text.isEmpty() ? 0.0D : Double.parseDouble(text);
							ProductionPlanner.setDurationOverrideSeconds(editEntry, seconds);
							if (editEntry.getDurationTicks() > 0.0D) {
								ProductionPlanner.setAutomatic(editEntry, true);
							}
						}
					}
				} catch (Throwable ignored) {
				}
			}
		} finally {
			ProductionPlanner.endHistoryAction();
			cancelEntryEdit();
		}
	}

	private void cancelEntryEdit() {
		if (editField != null) {
			EmiPort.focus(editField, false);
		}
		editField = null;
		editEntry = null;
		editKind = null;
	}

	private void startTargetRateEdit(Line line, Target target, Bounds bounds) {
		if (line == null || target == null || target.getStack() == null || target.getStack().isEmpty()) {
			return;
		}
		targetEditLine = line;
		targetEditTarget = target;
		targetEditBounds = bounds == null ? targetRateBounds : bounds;
		targetRateField = new EditBox(minecraft.font, targetEditBounds.x() + 2, targetEditBounds.y() + 2,
			Math.max(20, targetEditBounds.width() - 4), targetEditBounds.height() - 4, EmiPort.literal(PlannerText.tr("edit.target_rate", "Target rate")));
		targetRateField.setMaxLength(24);
		targetRateField.setValue(formatExactDisplayRate(target.getRate()));
		EmiPort.focus(targetRateField, true);
	}

	private void commitTargetRateEdit() {
		if (targetRateField != null && targetEditLine != null && targetEditTarget != null) {
			try {
				double rate = Double.parseDouble(targetRateField.getValue().trim().replace(',', '.'));
				ProductionPlanner.setTargetRate(targetEditLine, targetEditTarget, rate / displayTimeUnit.multiplier);
			} catch (Throwable ignored) {
			}
		}
		cancelTargetRateEdit();
	}

	private void cancelTargetRateEdit() {
		if (targetRateField != null) {
			EmiPort.focus(targetRateField, false);
		}
		targetRateField = null;
		targetEditLine = null;
		targetEditTarget = null;
		targetEditBounds = EMPTY;
	}

	private void startRename(TabHitbox tab) {
		renameIndex = tab.index;
		renameField = new EditBox(minecraft.font, tab.bounds.x() + 2, tab.bounds.y() + 2,
			Math.max(20, tab.bounds.width() - 4), tab.bounds.height() - 4, EmiPort.literal(PlannerText.tr("edit.line_name", "Line name")));
		renameField.setMaxLength(48);
		renameField.setValue(ProductionPlanner.displayName(tab.index));
		EmiPort.focus(renameField, true);
	}

	private void commitRename() {
		if (renameField != null && renameIndex >= 0) {
			ProductionPlanner.renameLine(renameIndex, renameField.getValue());
		}
		cancelRename();
	}

	private void cancelRename() {
		if (renameField != null) {
			EmiPort.focus(renameField, false);
		}
		renameField = null;
		renameIndex = -1;
	}

	private void startGroupRename(Group group, Bounds bounds) {
		if (group == null || bounds == null) {
			return;
		}
		cancelGroupRename();
		groupRenameTarget = group;
		int x = bounds.x() + 2;
		int y = bounds.y() + 2;
		int w = Math.max(60, bounds.width() - 4);
		int h = Math.max(16, bounds.height() - 4);
		groupRenameField = new EditBox(minecraft.font, x, y, w, h, EmiPort.literal(PlannerText.tr("groups.rename_label", "Group name")));
		groupRenameField.setMaxLength(64);
		String current = group.getName();
		groupRenameField.setValue(current == null || current.isBlank()
			? group.getDisplayName(ProductionPlanner.getOrCreateActiveLine()) : current);
		EmiPort.focus(groupRenameField, true);
	}

	private void commitGroupRename() {
		if (groupRenameField != null && groupRenameTarget != null) {
			ProductionPlanner.renameGroup(ProductionPlanner.getOrCreateActiveLine(), groupRenameTarget, groupRenameField.getValue());
		}
		cancelGroupRename();
	}

	private void cancelGroupRename() {
		if (groupRenameField != null) {
			EmiPort.focus(groupRenameField, false);
		}
		groupRenameField = null;
		groupRenameTarget = null;
	}

	private void clearGroupDragState() {
		pendingGroupDrag = null;
		pendingRecipeDrag = null;
		groupDragActive = false;
		recipeDragActive = false;
	}

	@Override
	public void onClose() {
		if (editField != null) {
			commitEntryEdit();
		}
		if (targetRateField != null) {
			commitTargetRateEdit();
		}
		if (renameField != null) {
			commitRename();
		}
		if (groupRenameField != null) {
			commitGroupRename();
		}
		ProductionPlanner.save();
		Minecraft.getInstance().setScreen(old);
	}

	@Override
	public void removed() {
		ProductionPlanner.save();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}


	private void openSearch() {
		if (groupsOpen) {
			clearGroupDragState();
			groupsOpen = false;
		}
		closeDropdowns();
		if (editField != null) {
			commitEntryEdit();
		}
		if (targetRateField != null) {
			commitTargetRateEdit();
		}
		if (renameField != null) {
			commitRename();
		}
		if (groupRenameField != null) {
			commitGroupRename();
		}
		String query = searchField == null ? "" : searchField.getValue();
		searchOpen = true;
		createSearchField(query);
		refreshSearchMatches(ProductionPlanner.getOrCreateActiveLine(), true);
	}

	private void createSearchField(String query) {
		int panelWidth = Math.min(370, Math.max(250, width - 110));
		int x = Math.max(52, width - panelWidth - 8);
		int y = 3;
		searchPanelBounds = new Bounds(x, y, panelWidth, 22);
		searchCloseButton = new Bounds(searchPanelBounds.right() - 20, y + 2, 18, 18);
		int fieldX = x + 38;
		int fieldWidth = Math.max(90, panelWidth - 132);
		searchField = new EditBox(minecraft.font, fieldX, y + 3, fieldWidth, 16, EmiPort.literal(PlannerText.tr("search.placeholder", "Search current line")));
		searchField.setMaxLength(128);
		searchField.setValue(query == null ? "" : query);
		EmiPort.focus(searchField, true);
	}

	private void closeSearch() {
		if (searchField != null) {
			EmiPort.focus(searchField, false);
		}
		searchOpen = false;
		searchField = null;
		searchPanelBounds = EMPTY;
		searchCloseButton = EMPTY;
		searchMatchRows = List.of();
		searchMatchIndex = -1;
	}

	private void refreshSearchMatches(Line line, boolean resetSelection) {
		if (!searchOpen || searchField == null || line == null) {
			searchMatchRows = List.of();
			searchMatchIndex = -1;
			return;
		}
		String query = normalizeSearch(searchField.getValue());
		if (query.isBlank()) {
			searchMatchRows = List.of();
			searchMatchIndex = -1;
			return;
		}
		List<PlannerDisplayRow> rows = buildPlannerDisplayRows(line);
		List<Integer> matches = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++) {
			if (matchesSearch(line, rows.get(i), query)) {
				matches.add(i);
			}
		}
		int previousRow = currentSearchRow();
		searchMatchRows = matches;
		if (matches.isEmpty()) {
			searchMatchIndex = -1;
			return;
		}
		boolean selectionChanged = false;
		if (resetSelection) {
			searchMatchIndex = 0;
			selectionChanged = true;
		} else if (previousRow >= 0 && matches.contains(previousRow)) {
			searchMatchIndex = matches.indexOf(previousRow);
		} else if (searchMatchIndex < 0 || searchMatchIndex >= matches.size()) {
			searchMatchIndex = 0;
			selectionChanged = true;
		}
		if (selectionChanged) {
			ensureSearchMatchVisible();
		}
	}

	private boolean matchesSearch(Line line, PlannerDisplayRow row, String query) {
		StringBuilder haystack = new StringBuilder();
		if (row.group != null) {
			haystack.append(row.group.getDisplayName(line));
			return normalizeSearch(haystack.toString()).contains(query);
		}
		Entry entry = row.entry;
		if (entry == null) {
			return false;
		}
		EmiRecipe recipe = entry.getRecipe();
		appendSearchText(haystack, entry.getRecipeId());
		MachineProfile profile = entry.getMachineProfile();
		if (profile != null) {
			appendSearchText(haystack, profile.displayName());
			appendSearchText(haystack, profile.id());
		}
		if (entry.getGroupId() > 0) {
			appendSearchText(haystack, line.getEntryGroupName(entry));
		}
		if (recipe != null) {
			appendSearchText(haystack, recipeName(recipe));
			appendSearchText(haystack, recipe.getCategory().getName().getString());
			appendSearchText(haystack, recipe.getId() == null ? null : recipe.getId().toString());
			for (EmiIngredient ingredient : recipe.getInputs()) {
				appendSearchIngredient(haystack, ingredient);
			}
			for (EmiIngredient ingredient : recipe.getCatalysts()) {
				appendSearchIngredient(haystack, ingredient);
			}
			for (EmiStack stack : recipe.getOutputs()) {
				appendSearchStack(haystack, stack);
			}
		}
		return normalizeSearch(haystack.toString()).contains(query);
	}

	private void appendSearchIngredient(StringBuilder haystack, EmiIngredient ingredient) {
		if (ingredient == null) {
			return;
		}
		for (EmiStack stack : ingredient.getEmiStacks()) {
			appendSearchStack(haystack, stack);
		}
	}

	private void appendSearchStack(StringBuilder haystack, EmiStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		appendSearchText(haystack, stack.getName().getString());
		appendSearchText(haystack, stack.getId() == null ? null : stack.getId().toString());
	}

	private void appendSearchText(StringBuilder haystack, Object value) {
		if (value == null) {
			return;
		}
		haystack.append(' ').append(value);
	}

	private String normalizeSearch(String text) {
		return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
	}

	private void cycleSearchMatch(int direction) {
		refreshSearchMatches(ProductionPlanner.getOrCreateActiveLine(), false);
		if (searchMatchRows.isEmpty()) {
			return;
		}
		int size = searchMatchRows.size();
		searchMatchIndex = Math.floorMod(searchMatchIndex + direction, size);
		ensureSearchMatchVisible();
	}

	private void ensureSearchMatchVisible() {
		int row = currentSearchRow();
		if (row < 0) {
			return;
		}
		int visible = visibleRows();
		if (row < rowScroll) {
			rowScroll = row;
		} else if (row >= rowScroll + visible) {
			rowScroll = row - visible + 1;
		}
		clampScroll();
	}

	private int currentSearchRow() {
		if (searchMatchIndex < 0 || searchMatchIndex >= searchMatchRows.size()) {
			return -1;
		}
		return searchMatchRows.get(searchMatchIndex);
	}

	private boolean isSearchFiltering() {
		return searchOpen && searchField != null && !normalizeSearch(searchField.getValue()).isBlank();
	}

	private void renderSearchRowOverlay(EmiDrawContext context, int displayRowIndex, int y) {
		if (!isSearchFiltering()) {
			return;
		}
		boolean match = searchMatchRows.contains(displayRowIndex);
		Bounds bounds = new Bounds(2, y + 1, Math.max(1, width - 4), ROW_HEIGHT - 3);
		if (!match) {
			context.fill(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 0x99000000);
			return;
		}
		drawBorder(context, bounds, displayRowIndex == currentSearchRow() ? 0xFFFFFF66 : 0xFF66D9FF);
	}

	private void renderSearchOverlay(EmiDrawContext context, GuiGraphics raw, Line line, int mouseX, int mouseY, float delta) {
		if (searchField == null) {
			createSearchField("");
			refreshSearchMatches(line, true);
		}
		context.fill(searchPanelBounds.x(), searchPanelBounds.y(), searchPanelBounds.width(), searchPanelBounds.height(), 0xFF1A1A22);
		drawBorder(context, searchPanelBounds, 0xFF8A8A96);
		context.drawTextWithShadow(EmiPort.literal(PlannerText.tr("search.find", "Find:")), searchPanelBounds.x() + 6, searchPanelBounds.y() + 7, 0xFFD0D0D8);
		String count = searchMatchRows.isEmpty() ? "0/0" : (searchMatchIndex + 1) + "/" + searchMatchRows.size();
		int countX = searchCloseButton.x() - 8 - font.width(count);
		context.drawTextWithShadow(EmiPort.literal(count), countX, searchPanelBounds.y() + 7, searchMatchRows.isEmpty() ? 0xFFFF7777 : 0xFFB8E6CF);
		drawButton(context, searchCloseButton, mouseX, mouseY, "x", false);
		searchField.render(raw, mouseX, mouseY, delta);
	}

	private String localizedProfileDescription(MachineProfile profile) {
		if (profile == null) return "";
		String description = profile.description();
		return switch (profile.id()) {
			case "generic" -> PlannerText.tr("profile.generic.description", description);
			case "chemical_reactor" -> PlannerText.tr("profile.chemical_reactor.description", description);
			case "large_chemical_reactor" -> PlannerText.tr("profile.large_chemical_reactor.description", description);
			case "electrolyzer" -> PlannerText.tr("profile.electrolyzer.description", description);
			default -> {
				if ("EMI workstation; detected machine properties are applied".equals(description)) {
					yield PlannerText.tr("profile.runtime.modeled", description);
				}
				if ("EMI workstation; unknown bonuses use Generic GT math".equals(description)) {
					yield PlannerText.tr("profile.runtime.generic", description);
				}
				yield description;
			}
		};
	}

	private String localizedTimeUnitLong() {
		return switch (displayTimeUnit) {
			case SECOND -> PlannerText.tr("time.second", "second");
			case MINUTE -> PlannerText.tr("time.minute", "minute");
			case HOUR -> PlannerText.tr("time.hour", "hour");
		};
	}

	private double adjustmentStep() {
		if (EmiInput.isShiftDown()) {
			return 10.0D;
		}
		if (EmiInput.isControlDown()) {
			return 0.1D;
		}
		if (EmiInput.isAltDown()) {
			return 0.01D;
		}
		return 1.0D;
	}

	private int countAdjustmentStep() {
		return EmiInput.isShiftDown() ? 10 : 1;
	}

	private int visibleRows() {
		return Math.max(1, (height - ROW_TOP - FOOTER_HEIGHT - 4) / ROW_HEIGHT);
	}

	private void clampScroll() {
		Line line = ProductionPlanner.getOrCreateActiveLine();
		int max = Math.max(0, buildPlannerDisplayRows(line).size() - visibleRows());
		rowScroll = Math.max(0, Math.min(rowScroll, max));
	}

	private boolean isCompactLayout() {
		return width < 1050;
	}

	private TableLayout tableLayout() {
		if (!isCompactLayout()) {
			return new TableLayout(false, true,
				8, 42, 54, 112, 170, 26,
				198, 216, 230, 262,
				280, 298, 312, 344,
				16, 14, 32,
				362, 54, 420, 44, 468, 62, 534, 68,
				608, inputsColumnX(), outputsColumnX());
		}
		return new TableLayout(true, false,
			4, 36, 42, 90, 136, 24,
			164, 179, 191, 215,
			231, 246, 258, 282,
			14, 12, 24,
			298, 44, 346, 34, 0, 0, 384, 42,
			430, inputsColumnX(), outputsColumnX());
	}

	private int inputsColumnX() {
		if (isCompactLayout()) {
			return Math.max(500, width - 220);
		}
		return Math.max(780, width * 45 / 100);
	}

	private int outputsColumnX() {
		int inputs = inputsColumnX();
		if (isCompactLayout()) {
			return Math.max(inputs + 70, width - 100);
		}
		return Math.max(inputs + 180, width * 72 / 100);
	}

	private String recipePathModeLabel(RecipePathMode mode) {
		return switch (mode == null ? RecipePathMode.NONE : mode) {
			case PREFER -> PlannerText.tr("path.mode.prefer", "PREF");
			case LOCK -> PlannerText.tr("path.mode.lock", "LOCK");
			case AVOID -> PlannerText.tr("path.mode.avoid", "AVOID");
			default -> PlannerText.tr("path.mode.off", "OFF");
		};
	}

	private String recipePathModeDescription(RecipePathMode mode) {
		return switch (mode == null ? RecipePathMode.NONE : mode) {
			case PREFER -> PlannerText.tr("path.desc.prefer", "Auto dependency recursion prefers this recipe, but may fall back if it cannot be used");
			case LOCK -> PlannerText.tr("path.desc.lock", "Auto dependency recursion may use only this recipe for this output");
			case AVOID -> PlannerText.tr("path.desc.avoid", "Auto dependency recursion skips this recipe for this output");
			default -> PlannerText.tr("path.desc.off", "No global path rule");
		};
	}

	private String recipeName(EmiRecipe recipe) {
		for (EmiStack stack : recipe.getOutputs()) {
			if (!stack.isEmpty()) {
				return stack.getName().getString();
			}
		}
		return recipe.getId() == null ? recipe.getCategory().getName().getString() : recipe.getId().toString();
	}

	private PowerSummary calculatePower(Line line) {
		double averageEUt = 0.0D;
		int knownEntries = 0;
		int unknownEntries = 0;
		for (Entry entry : line.getEntries()) {
			double rate = line.getEffectiveRate(entry);
			if (rate <= EPSILON) {
				continue;
			}
			if (entry.getRecipeEUt() > 0L && entry.getProcessedDurationTicks() > 0.0D) {
				averageEUt += entry.getAveragePowerEUt(rate);
				knownEntries++;
			} else {
				unknownEntries++;
			}
		}
		return new PowerSummary(averageEUt, knownEntries, unknownEntries);
	}

	private PlanTotals calculate(Line line) {
		Map<EmiStack, MutableFlow> map = new LinkedHashMap<>();
		for (Entry entry : line.getEntries()) {
			EmiRecipe recipe = entry.getRecipe();
			if (recipe == null) {
				continue;
			}
			for (RateStack input : recipeInputs(recipe, line.getEffectiveRate(entry))) {
				MutableFlow flow = map.computeIfAbsent(normalize(input.stack), s -> new MutableFlow(s));
				flow.input += input.amount;
				flow.approximate |= input.approximate;
			}
			for (RateStack output : recipeOutputs(recipe, line.getEffectiveRate(entry))) {
				MutableFlow flow = map.computeIfAbsent(normalize(output.stack), s -> new MutableFlow(s));
				flow.output += output.amount;
				flow.approximate |= output.approximate;
			}
		}
		List<Flow> external = new ArrayList<>();
		List<Flow> internal = new ArrayList<>();
		List<Flow> outputs = new ArrayList<>();
		for (MutableFlow mutable : map.values()) {
			boolean rootIgnored = !line.hasTarget(mutable.stack) && line.getLinkMode(null, mutable.stack) == LinkMode.IGNORE;
			double internalAmount = rootIgnored ? 0.0D : Math.min(mutable.input, mutable.output);
			double externalAmount = rootIgnored ? mutable.input : Math.max(0, mutable.input - mutable.output);
			double outputAmount = rootIgnored ? mutable.output : Math.max(0, mutable.output - mutable.input);
			if (externalAmount > EPSILON) {
				external.add(new Flow(mutable.stack, mutable.input, mutable.output, internalAmount, externalAmount,
					mutable.approximate, formatCompactRate(externalAmount, mutable.approximate)));
			}
			if (internalAmount > EPSILON) {
				internal.add(new Flow(mutable.stack, mutable.input, mutable.output, internalAmount, externalAmount,
					mutable.approximate, formatCompactRate(internalAmount, mutable.approximate)));
			}
			if (outputAmount > EPSILON) {
				outputs.add(new Flow(mutable.stack, mutable.input, mutable.output, internalAmount, externalAmount,
					mutable.approximate, formatCompactRate(outputAmount, mutable.approximate)));
			}
		}
		return new PlanTotals(external, internal, outputs);
	}

	private List<RateStack> recipeInputs(EmiRecipe recipe, double rate) {
		List<RateStack> result = new ArrayList<>();
		for (EmiIngredient ingredient : recipe.getInputs()) {
			if (ingredient == null || ingredient.isEmpty()) {
				continue;
			}
			ResourceRole role = ProductionPlanner.getInputResourceRole(recipe, ingredient);
			if (role != ResourceRole.CONSUMED && role != ResourceRole.CONTAINER) {
				continue;
			}
			EmiStack stack = firstStack(ingredient);
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			double chance = ingredient.getChance();
			double amount = ingredient.getAmount() * Math.max(0.0D, chance) * rate;
			boolean approximate = Math.abs(chance - 1.0F) > 0.0001F || ingredient.getEmiStacks().size() > 1;
			result.add(new RateStack(normalize(stack), amount, approximate));
		}
		return result;
	}

	private List<RateStack> recipeOutputs(EmiRecipe recipe, double rate) {
		List<RateStack> result = new ArrayList<>();
		for (EmiStack stack : recipe.getOutputs()) {
			if (stack == null || stack.isEmpty() || ProductionPlanner.isStartupOnlyOutput(recipe, stack)) {
				continue;
			}
			double chance = stack.getChance();
			double amount = stack.getAmount() * Math.max(0.0D, chance) * rate;
			boolean approximate = Math.abs(chance - 1.0F) > 0.0001F;
			result.add(new RateStack(normalize(stack), amount, approximate));
		}
		for (EmiIngredient ingredient : recipe.getInputs()) {
			if (ingredient == null || ingredient.isEmpty()) {
				continue;
			}
			EmiStack input = firstStack(ingredient);
			EmiStack remainder = ProductionPlanner.getInputRemainder(ingredient);
			if (input == null || input.isEmpty() || remainder == null || remainder.isEmpty() || sameLookupStack(input, remainder)
					|| recipeOutputsContain(recipe, remainder)) {
				continue;
			}
			double chance = ingredient.getChance();
			double amount = ingredient.getAmount() * Math.max(0.0D, chance) * Math.max(1L, remainder.getAmount()) * rate;
			boolean approximate = Math.abs(chance - 1.0F) > 0.0001F || ingredient.getEmiStacks().size() > 1;
			result.add(new RateStack(normalize(remainder), amount, approximate));
		}
		return result;
	}

	private boolean recipeOutputsContain(EmiRecipe recipe, EmiStack target) {
		for (EmiStack output : recipe.getOutputs()) {
			if (output != null && !output.isEmpty() && sameLookupStack(output, target)) {
				return true;
			}
		}
		return false;
	}

	private EmiStack firstStack(EmiIngredient ingredient) {
		for (EmiStack stack : ingredient.getEmiStacks()) {
			if (!stack.isEmpty()) {
				return stack;
			}
		}
		return EmiStack.EMPTY;
	}

	private EmiStack normalize(EmiStack stack) {
		return stack.copy().setAmount(1).setChance(1);
	}

	private double toDisplayRate(double perSecond) {
		return perSecond * displayTimeUnit.multiplier;
	}

	private String formatStartupAmount(EmiStack stack, double amount, boolean approximate) {
		String value = formatCompactRate(amount, approximate);
		return stack != null && stack.getKey() instanceof Fluid ? value + " mB" : value;
	}

	private String startupRoleLabel(ResourceRole role) {
		return switch (role) {
			case CATALYST -> PlannerText.tr("startup.role.catalyst", "CATALYST");
			case TOOL -> PlannerText.tr("startup.role.tool", "TOOL");
			case RETURNED -> PlannerText.tr("startup.role.returned", "RETURNED");
			case CONTAINER -> PlannerText.tr("startup.role.container", "CONTAINER");
			case CYCLE_SEED -> PlannerText.tr("startup.role.cycle", "CYCLE");
			case CONSUMED -> PlannerText.tr("startup.role.consumed", "CONSUMED");
		};
	}

	private String startupRoleDescription(ResourceRole role) {
		return switch (role) {
			case CATALYST -> PlannerText.tr("startup.desc.catalyst", "Input is explicitly returned by the recipe and is only needed to prime the running machines");
			case TOOL -> PlannerText.tr("startup.desc.tool", "Reusable/damageable crafting tool; counted as a startup requirement, not a per-second input");
			case RETURNED -> PlannerText.tr("startup.desc.returned", "Input returns itself through EMI remainder semantics and is treated as reusable inventory");
			case CONTAINER -> PlannerText.tr("startup.desc.container", "Returned container in a closed loop; an initial stock is needed for circulation");
			case CYCLE_SEED -> PlannerText.tr("startup.desc.cycle", "Closed steady-state recycle needs an initial seed before it can self-sustain");
			case CONSUMED -> PlannerText.tr("startup.desc.consumed", "Ordinary consumed resource");
		};
	}

	private int startupRoleColor(ResourceRole role) {
		return switch (role) {
			case CATALYST -> 0xFFFFD866;
			case TOOL -> 0xFF66D9FF;
			case RETURNED -> 0xFF86D99A;
			case CONTAINER -> 0xFF78A8FF;
			case CYCLE_SEED -> 0xFFD99CFF;
			case CONSUMED -> 0xFFB8B8C0;
		};
	}

	private String formatDisplayRate(double perSecond) {
		return formatRate(toDisplayRate(perSecond));
	}

	private String formatExactDisplayRate(double perSecond) {
		return formatExactRate(toDisplayRate(perSecond));
	}

	private String formatCompactDisplayRate(double perSecond, boolean approximate) {
		return formatCompactRate(toDisplayRate(perSecond), approximate);
	}

	private String formatRate(double value) {
		if (value >= 1000.0D) {
			return formatCompactRate(value, false);
		}
		return formatExactRate(value);
	}

	private String formatInteger(int value) {
		return value >= 1000 ? formatCompactRate(value, false) : Integer.toString(value);
	}

	private String formatDuration(double seconds) {
		if (seconds >= 1000.0D) {
			return formatCompactRate(seconds, false) + "s";
		}
		return formatExactRate(seconds) + "s";
	}

	private String formatCompactRate(double value, boolean approximate) {
		double abs = Math.abs(value);
		String[] suffixes = { "", "K", "M", "G", "T", "P", "E" };
		int unit = 0;
		while (abs >= 1000.0D && unit < suffixes.length - 1) {
			abs /= 1000.0D;
			unit++;
		}
		if (value < 0) {
			abs = -abs;
		}
		String number;
		if (Math.abs(abs) >= 100 || unit == 0 && Math.abs(abs) >= 10) {
			number = BigDecimal.valueOf(abs).setScale(0, RoundingMode.HALF_UP).toPlainString();
		} else if (Math.abs(abs) >= 10) {
			number = trimNumber(abs, 1);
		} else {
			number = trimNumber(abs, 2);
		}
		return (approximate ? "~" : "") + number + suffixes[unit];
	}

	private String formatExactRate(double value) {
		return trimNumber(value, 4);
	}

	private String formatSummaryPower(double value) {
		return formatCompactRate(value, false) + " EU/t (" + formatGroupedExact(value) + " EU/t)";
	}

	private String formatGroupedExact(double value) {
		String exact = formatExactRate(value);
		int dot = exact.indexOf('.');
		String integer = dot >= 0 ? exact.substring(0, dot) : exact;
		String fraction = dot >= 0 ? exact.substring(dot) : "";
		String sign = integer.startsWith("-") ? "-" : "";
		if (!sign.isEmpty()) {
			integer = integer.substring(1);
		}
		StringBuilder grouped = new StringBuilder(integer.length() + integer.length() / 3 + fraction.length() + 1);
		for (int i = 0; i < integer.length(); i++) {
			if (i > 0 && (integer.length() - i) % 3 == 0) {
				grouped.append(',');
			}
			grouped.append(integer.charAt(i));
		}
		return sign + grouped + fraction;
	}

	private String trimNumber(double value, int scale) {
		return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
	}

	private String unitSuffix(EmiStack stack) {
		return stack.getKey() instanceof Fluid ? " mB/" + displayTimeUnit.suffix : " /" + displayTimeUnit.suffix;
	}

	private boolean isTarget(Line line, EmiStack stack) {
		return targetMode(line, stack) != null;
	}

	private TargetMode targetMode(Line line, EmiStack stack) {
		if (line == null || stack == null || stack.isEmpty()) {
			return null;
		}
		for (Target target : line.getTargets()) {
			if (target.getStack().isEqual(stack, EmiPort.compareStrict())) {
				return target.getMode();
			}
		}
		return null;
	}

	private String unitSuffixShort(EmiStack stack) {
		return stack.getKey() instanceof Fluid ? "mB/" + displayTimeUnit.suffix : "/" + displayTimeUnit.suffix;
	}

	private void drawValueBox(EmiDrawContext context, Bounds bounds, int mouseX, int mouseY, String label, boolean active) {
		drawValueBox(context, bounds, mouseX, mouseY, label, active, 0xFFFFFFFF);
	}

	private void drawValueBox(EmiDrawContext context, Bounds bounds, int mouseX, int mouseY, String label, boolean active, int textColor) {
		boolean hovered = bounds.contains(mouseX, mouseY);
		int color = active ? 0xFF263B36 : hovered ? HOVER_COLOR : 0xFF222229;
		context.fill(bounds.x(), bounds.y(), bounds.width(), bounds.height(), color);
		drawBorder(context, bounds, hovered ? 0xFFD0D0D8 : BORDER_COLOR);
		String trimmed = font.plainSubstrByWidth(label, Math.max(4, bounds.width() - 4));
		context.drawCenteredText(EmiPort.literal(trimmed), bounds.x() + bounds.width() / 2, bounds.y() + 6, textColor);
	}

	private void drawMachineValueBox(EmiDrawContext context, Bounds bounds, int mouseX, int mouseY, MachineProfile profile, boolean active) {
		boolean hovered = bounds.contains(mouseX, mouseY);
		int color = active ? 0xFF263B36 : hovered ? HOVER_COLOR : 0xFF222229;
		context.fill(bounds.x(), bounds.y(), bounds.width(), bounds.height(), color);
		drawBorder(context, bounds, hovered ? 0xFFD0D0D8 : BORDER_COLOR);
		int textX = bounds.x() + 4;
		if (profile != null && profile.hasIcon()) {
			context.drawStack(profile.icon(), bounds.x() + 3, bounds.y() + 2, EmiIngredient.RENDER_ICON);
			textX = bounds.x() + 22;
		}
		String label = profile == null ? "Generic GT" : profile.displayName();
		String trimmed = font.plainSubstrByWidth(label, Math.max(4, bounds.right() - textX - 3));
		context.drawTextWithShadow(EmiPort.literal(trimmed), textX, bounds.y() + 6, 0xFFFFFFFF);
	}

	private void drawButton(EmiDrawContext context, Bounds bounds, int mouseX, int mouseY, String label, boolean active) {
		boolean hovered = bounds.contains(mouseX, mouseY);
		int color = active ? ACTIVE_COLOR : hovered ? HOVER_COLOR : 0xFF2B2B32;
		context.fill(bounds.x(), bounds.y(), bounds.width(), bounds.height(), color);
		drawBorder(context, bounds, hovered ? 0xFFD0D0D8 : BORDER_COLOR);
		context.drawCenteredText(EmiPort.literal(label), bounds.x() + bounds.width() / 2, bounds.y() + 5, 0xFFFFFFFF);
	}

	private void drawBorder(EmiDrawContext context, Bounds bounds, int color) {
		context.fill(bounds.x(), bounds.y(), bounds.width(), 1, color);
		context.fill(bounds.x(), bounds.bottom() - 1, bounds.width(), 1, color);
		context.fill(bounds.x(), bounds.y(), 1, bounds.height(), color);
		context.fill(bounds.right() - 1, bounds.y(), 1, bounds.height(), color);
	}

	private static final class TabHitbox {
		private final Bounds bounds;
		private final int index;

		private TabHitbox(Bounds bounds, int index) {
			this.bounds = bounds;
			this.index = index;
		}
	}

	private enum DisplayTimeUnit {
		SECOND(1.0D, "s", "second"),
		MINUTE(60.0D, "min", "minute"),
		HOUR(3600.0D, "h", "hour");

		private final double multiplier;
		private final String suffix;
		private final String longLabel;

		DisplayTimeUnit(double multiplier, String suffix, String longLabel) {
			this.multiplier = multiplier;
			this.suffix = suffix;
			this.longLabel = longLabel;
		}

		private DisplayTimeUnit next() {
			return switch (this) {
				case SECOND -> MINUTE;
				case MINUTE -> HOUR;
				case HOUR -> SECOND;
			};
		}
	}

	private static final class MutableBuildSummary {
		private final String machineName;
		private final EmiStack icon;
		private final int voltageTier;
		private final String voltageName;
		private final int parallel;
		private final String config;
		private int machines;
		private int recipeRows;
		private double averageEUt;
		private boolean unknownPower;
		private boolean provisional;

		private MutableBuildSummary(String machineName, EmiStack icon, int voltageTier, String voltageName, int parallel, String config) {
			this.machineName = machineName;
			this.icon = icon;
			this.voltageTier = voltageTier;
			this.voltageName = voltageName;
			this.parallel = parallel;
			this.config = config;
		}
	}

	private record BuildSummaryRow(String machineName, EmiStack icon, int voltageTier, String voltageName, int parallel,
			String config, int machines, int recipeRows, double averageEUt, boolean unknownPower, boolean provisional) {
	}

	private record FlowSummaryRow(String section, EmiStack stack, double rate, boolean approximate, int color) {
	}

	private record BuildSummaryHitbox(Bounds bounds, BuildSummaryRow row) {
	}

	private record RecipeChangeHitbox(RecipeChange change, Bounds row, Bounds action) {
	}


	private record StartupSummaryHitbox(Bounds bounds, StartupResource row) {
	}

	private enum EditKind {
		RATE, MACHINES, PARALLEL, DURATION
	}

	private record MachineOptionHitbox(Bounds bounds, Bounds favoriteBounds, MachineProfile profile) {
	}

	private record VoltageOptionHitbox(Bounds bounds, int tier) {
	}

	private record CoilOptionHitbox(Bounds bounds, int tier) {
	}

	private record MachineSettingControl(MachineSettingSpec spec, Bounds minus, Bounds value, Bounds plus) {
	}

	private record TargetHitbox(Target target, Bounds icon, Bounds rate, Bounds remove) {
	}

	private record GroupListHitbox(Bounds bounds, Bounds collapse, Group group) {
	}

	private record GroupRecipeHitbox(Bounds rowBounds, Bounds moveBounds, Entry entry) {
	}

	private record GroupLinkHitbox(Bounds bounds, EmiStack stack, LinkMode mode) {
	}

	private record GroupMainHitbox(Bounds bounds, Group group) {
	}

	private record TableLayout(boolean compact, boolean showDuration,
			int modeX, int modeW, int machineX, int machineW, int cfgX, int cfgW,
			int machinesLockX, int machinesMinusX, int machinesValueX, int machinesPlusX,
			int parallelLockX, int parallelMinusX, int parallelValueX, int parallelPlusX,
			int lockW, int stepW, int valueW,
			int voltageX, int voltageW, int ocX, int ocW, int durationX, int durationW,
			int rateX, int rateW, int recipeX, int inputsX, int outputsX) {
	}

	private record PlannerDisplayRow(Entry entry, Group group, int depth) {
	}

	private static final class RowHitbox {
		private final Entry entry;
		private final Bounds mode;
		private final Bounds machineProfile;
		private final Bounds machineConfig;
		private final Bounds machinesLock;
		private final Bounds machinesMinus;
		private final Bounds machinesValue;
		private final Bounds machinesPlus;
		private final Bounds parallelLock;
		private final Bounds parallelMinus;
		private final Bounds parallelValue;
		private final Bounds parallelPlus;
		private final Bounds voltage;
		private final Bounds oc;
		private final Bounds duration;
		private final Bounds rate;
		private final Bounds replace;
		private final Bounds remove;
		private final Bounds recipeBounds;

		private RowHitbox(Entry entry, Bounds mode, Bounds machineProfile, Bounds machineConfig, Bounds machinesLock, Bounds machinesMinus, Bounds machinesValue, Bounds machinesPlus,
				Bounds parallelLock, Bounds parallelMinus, Bounds parallelValue, Bounds parallelPlus, Bounds voltage, Bounds oc, Bounds duration, Bounds rate,
				Bounds replace, Bounds remove, Bounds recipeBounds) {
			this.entry = entry;
			this.mode = mode;
			this.machineProfile = machineProfile;
			this.machineConfig = machineConfig;
			this.machinesLock = machinesLock;
			this.machinesMinus = machinesMinus;
			this.machinesValue = machinesValue;
			this.machinesPlus = machinesPlus;
			this.parallelLock = parallelLock;
			this.parallelMinus = parallelMinus;
			this.parallelValue = parallelValue;
			this.parallelPlus = parallelPlus;
			this.voltage = voltage;
			this.oc = oc;
			this.duration = duration;
			this.rate = rate;
			this.replace = replace;
			this.remove = remove;
			this.recipeBounds = recipeBounds;
		}

		private Bounds machineArea() {
			return new Bounds(machinesLock.x(), machinesLock.y(), machinesPlus.right() - machinesLock.x(), machinesLock.height());
		}

		private Bounds parallelArea() {
			return new Bounds(parallelLock.x(), parallelLock.y(), parallelPlus.right() - parallelLock.x(), parallelLock.height());
		}
	}

	private static final class FlowHitbox {
		private final Bounds bounds;
		private final Flow flow;
		private final TargetMode goalMode;
		private final boolean rootLink;

		private FlowHitbox(Bounds bounds, Flow flow, TargetMode goalMode) {
			this(bounds, flow, goalMode, false);
		}

		private FlowHitbox(Bounds bounds, Flow flow, TargetMode goalMode, boolean rootLink) {
			this.bounds = bounds;
			this.flow = flow;
			this.goalMode = goalMode;
			this.rootLink = rootLink;
		}
	}

	private record PowerSummary(double averageEUt, int knownEntries, int unknownEntries) {
	}

	private static final class RateStack {
		private final EmiStack stack;
		private final double amount;
		private final boolean approximate;

		private RateStack(EmiStack stack, double amount, boolean approximate) {
			this.stack = stack;
			this.amount = amount;
			this.approximate = approximate;
		}
	}

	private static final class MutableFlow {
		private final EmiStack stack;
		private double input;
		private double output;
		private boolean approximate;

		private MutableFlow(EmiStack stack) {
			this.stack = stack;
		}
	}

	private static final class Flow {
		private final EmiStack stack;
		private final double input;
		private final double output;
		private final double internal;
		private final double external;
		private final boolean approximate;
		private final String displayAmount;
		private final double displayValue;

		private Flow(EmiStack stack, double input, double output, double internal, double external,
				boolean approximate, String displayAmount) {
			this(stack, input, output, internal, external, approximate, displayAmount,
				external > EPSILON ? external : internal > EPSILON ? internal : Math.max(0, output - input));
		}

		private Flow(EmiStack stack, double input, double output, double internal, double external,
				boolean approximate, String displayAmount, double displayValue) {
			this.stack = stack;
			this.input = input;
			this.output = output;
			this.internal = internal;
			this.external = external;
			this.approximate = approximate;
			this.displayAmount = displayAmount;
			this.displayValue = displayValue;
		}
	}

	private record PlanTotals(List<Flow> externalInputs, List<Flow> internalFlow, List<Flow> netOutputs) {
	}
}
