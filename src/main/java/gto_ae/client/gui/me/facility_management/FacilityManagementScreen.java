/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package gto_ae.client.gui.me.facility_management;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.glodblock.github.extendedae.client.button.HighlightButton;
import com.glodblock.github.extendedae.util.MessageUtil;
import com.google.common.collect.HashMultimap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;

import appeng.api.client.AEKeyRendering;
import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.api.config.YesNo;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.stacks.*;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.common.StackSizeRenderer;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.*;
import appeng.client.guidebook.document.LytRect;
import appeng.client.guidebook.render.SimpleRenderContext;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.core.localization.GuiText;
import appeng.menu.slot.InaccessibleSlot;
import appeng.util.ConfigInventory;
import appeng.util.ReadableNumberConverter;

import gto_ae.api.config.ExtendedSettings;
import gto_ae.client.gui.IconsExtended;
import gto_ae.client.gui.widgets.AESlotWidget;
import gto_ae.client.gui.widgets.ExpandableToggleButton;
import gto_ae.client.gui.widgets.expandable.ExpandableGroup;
import gto_ae.core.localization.ExtendedLangs;
import gto_ae.helpers.facility_management.FrozenMachineStatus;
import gto_ae.helpers.facility_management.IO;
import gto_ae.helpers.facility_management.ThroughputCounter;
import gto_ae.helpers.facility_management.WorkingStatus;
import gto_ae.menu.implementations.FacilityManagementMenu;

public class FacilityManagementScreen<C extends FacilityManagementMenu> extends AEBaseScreen<C> {
    private final Int2ObjectOpenHashMap<FrozenMachineStatus> byFacilityUniqueId = new Int2ObjectOpenHashMap<>();
    // Used to show multiple pattern providers with the same name under a single header
    private final HashMultimap<PatternContainerGroup, FrozenMachineStatus> byGroup = HashMultimap.create();
    private final IntOpenHashSet shownIds = new IntOpenHashSet();
    private final ArrayList<PatternContainerGroup> groups = new ArrayList<>();
    private final ArrayList<Row> rows = new ArrayList<>();
    private final Int2ObjectOpenHashMap<HighlightButton> highlightBtns = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<IconButton> openGuiBtns = new Int2ObjectOpenHashMap<>();
    private final Int2IntOpenHashMap statsRowScrollLevels = new Int2IntOpenHashMap();
    private final Int2IntOpenHashMap configScrollLevels = new Int2IntOpenHashMap();

    private final Scrollbar scrollbar;
    private final AETextField searchField;
    private final ToggleButton freezeViewBtn;
    public final ExpandableGroup filterModeGroup;

    private int visibleRows = 0;
    private boolean refreshScheduled = false;

    public FacilityManagementScreen(C menu, Inventory playerInventory,
            Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.scrollbar = widgets.addScrollBar("scrollbar");
        this.imageWidth = GUI_WIDTH;

        // Add a terminalstyle button
        TerminalStyle terminalStyle = AEConfig.instance().getTerminalStyle();
        this.addToLeftToolbar(
                new SettingToggleButton<>(Settings.TERMINAL_STYLE, terminalStyle, this::toggleTerminalStyle));

        var filterModeBtn = new ExpandableToggleButton<>(ExtendedSettings.FILTER_MODE, IO.NONE,
                ExpandableToggleButton::sendToServer, ExpandableToggleButton.LayoutDirection.LEFT);
        var filterCpuJobsBtn = new ExpandableToggleButton<>(ExtendedSettings.HAS_CPU_TASK, YesNo.UNDECIDED,
                ExpandableToggleButton::sendToServer, ExpandableToggleButton.LayoutDirection.LEFT);
        var filterWorkingStatusBtn = new ExpandableToggleButton<>(ExtendedSettings.WORKING_STATUS_SETTING,
                WorkingStatus.NONE,
                ExpandableToggleButton::sendToServer, ExpandableToggleButton.LayoutDirection.LEFT);

        filterModeGroup = new ExpandableGroup(filterModeBtn, filterCpuJobsBtn, filterWorkingStatusBtn);

        freezeViewBtn = new ToggleButton(IconsExtended.VIEW_UNLOCKED, IconsExtended.VIEW_LOCKED,
                ExtendedLangs.FreezeView.text(), ExtendedLangs.FreezeViewTooltip.text(), (b) -> {
                    if (menu.viewFrozen) {
                        menu.freezeView(null);
                    } else {
                        menu.freezeView(shownIds);
                    }
                });
        this.addToLeftToolbar(filterModeBtn);
        this.addToLeftToolbar(filterCpuJobsBtn);
        this.addToLeftToolbar(filterWorkingStatusBtn);
        this.addToLeftToolbar(freezeViewBtn);

        this.searchField = widgets.addTextField("search");
        this.searchField.setResponder(str -> this.refreshList());
        this.searchField.setPlaceholder(GuiText.SearchPlaceholder.text());
        this.searchField.setTooltip(Tooltip.create(ExtendedLangs.ThroughputSearchingTooltip.text()));

        AESlotWidget slotWidget;
        widgets.add("filter1", slotWidget = new AESlotWidget(menu.ioFilterSlot, this));
        slotWidget.setTooltip(ExtendedLangs.ThroughputFilterSlotTooltip.text());
        widgets.add("filter2", slotWidget = new AESlotWidget(menu.iconFilterSlot, this));
        slotWidget.setTooltip(ExtendedLangs.FacilityIconFilterTooltip.text());
    }

    @Override
    public void init() {
        this.visibleRows = config.getTerminalStyle().getRows(
                (this.height - GUI_HEADER_HEIGHT - GUI_FOOTER_HEIGHT - GUI_TOP_AND_BOTTOM_PADDING) / ROW_HEIGHT);
        // Render inventory in correct place.
        this.imageHeight = GUI_HEADER_HEIGHT + GUI_FOOTER_HEIGHT + this.visibleRows * ROW_HEIGHT;

        super.init();

        this.highlightBtns.forEach((k, v) -> {
            v.setVisibility(false);
            addRenderableWidget(v);
        });
        this.openGuiBtns.forEach((k, v) -> {
            v.setVisibility(false);
            addRenderableWidget(v);
        });
        // numLines may have changed, recalculate scroll bar.
        this.resetScrollbar();
    }

    @Override
    public void updateBeforeRender() {
        super.updateBeforeRender();
        freezeViewBtn.setState(menu.viewFrozen);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX,
            int mouseY) {
        this.highlightBtns.forEach((key, value) -> value.setVisibility(false));
        this.openGuiBtns.forEach((key, value) -> value.setVisibility(false));
        this.menu.slots.removeIf(slot -> slot instanceof InaccessibleSlot);

        int textColor = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();

        final int scrollLevel = scrollbar.getCurrentScroll();
        int i = 0;
        for (;i<this.visibleRows;++i) {
            if (scrollLevel + i >= this.rows.size()) {
                continue;
            }
            var row = this.rows.get(scrollLevel + i);
            int finalI = i;
            switch (row) {
                case InfoRow(FrozenMachineStatus container) -> {
                    var statusText = ExtendedLangs.WorkingStatus.text(
                            switch (container.getStatus()) {
                                case WORKING -> ExtendedLangs.WorkingStatusWorking.text();
                                case IDLE -> ExtendedLangs.WorkingStatusIdle.text();
                                case BUSY -> ExtendedLangs.WorkingStatusBusy.text();
                                default -> throw new IllegalStateException("can not be other states here");
                            });
                    var cpuUsageText = ExtendedLangs.TasksNumInCPU.text(container.getJobCount());
                    var throughputText = ExtendedLangs.RecentThroughput.text(ReadableNumberConverter
                            .format(container.getThroughputCounter().getLastRefreshInterval() / 1000D, 3));
                    float scale = 0.63f;
                    StackSizeRenderer.renderSizeLabel(guiGraphics, font,
                            GUI_PADDING_X + PATTERN_PROVIDER_NAME_MARGIN_X,
                            GUI_HEADER_HEIGHT + i * ROW_HEIGHT + 1,
                            statusText, scale, true, true);
                    StackSizeRenderer.renderSizeLabel(guiGraphics, font,
                            GUI_PADDING_X + PATTERN_PROVIDER_NAME_MARGIN_X,
                            GUI_HEADER_HEIGHT + i * ROW_HEIGHT + 1 + font.lineHeight * scale,
                            cpuUsageText, scale, true, true);
                    StackSizeRenderer.renderSizeLabel(guiGraphics, font,
                            GUI_PADDING_X + PATTERN_PROVIDER_NAME_MARGIN_X,
                            GUI_HEADER_HEIGHT + i * ROW_HEIGHT + 1 + font.lineHeight * scale * 2,
                            throughputText, scale, true, true);

                    var btn = this.highlightBtns.get(container.getFacilityUid());
                    if (btn != null) {
                        btn.setPosition(
                                this.leftPos + GUI_PADDING_X + 7 * SLOT_SIZE,
                                this.topPos + (i - 1) * SLOT_SIZE + 36 + V_SHIFT);
                        btn.setVisibility(true);
                    }
                    var guiBtn = this.openGuiBtns.get(container.getFacilityUid());
                    if (guiBtn != null) {
                        guiBtn.setPosition(
                                this.leftPos + GUI_PADDING_X + 8 * SLOT_SIZE,
                                this.topPos + (i - 1) * SLOT_SIZE + 36 + V_SHIFT);
                        guiBtn.setVisibility(true);
                    }
                }
                case StatRow(FrozenMachineStatus container) -> {
                    var throughput = container.getThroughputCounter();
                    var offset = statsRowScrollLevels.getOrDefault(container.getFacilityUid(), 0);
                    var counter = new AtomicInteger(-offset);
                    var aeKeyInv = ConfigInventory.configStacks(null, 9, () -> {
                    }, true);
                    var vanillaInv = aeKeyInv.createMenuWrapper();
                    throughput.map.reference2LongEntrySet().stream()
                            .sorted(Comparator.<Reference2LongMap.Entry<AEKey>>comparingLong(
                                    Reference2LongMap.Entry::getLongValue).reversed())
                            .forEach(entry -> {
                                if (counter.get() < 0) {
                                    counter.getAndIncrement();
                                    return;
                                }
                                int throughputIndex;
                                if ((throughputIndex = counter.getAndIncrement()) >= 9) {
                                    return;
                                }

                                var key = entry.getKey();
                                long value = entry.getLongValue();

                                aeKeyInv.setStack(throughputIndex, new GenericStack(key, Math.abs(value)));
                                var slot = new ThroughputSlot(vanillaInv, throughputIndex, value,
                                        container.getThroughputCounter().getLastRefreshInterval());
                                slot.x = throughputIndex * SLOT_SIZE + GUI_PADDING_X;
                                slot.y = (finalI - 1) * SLOT_SIZE + 36 + V_SHIFT;
                                this.menu.slots.add(slot);
                            });
                    while (counter.get() < 9) {
                        int idx = counter.getAndIncrement();
                        InaccessibleSlot slot = new InaccessibleSlot(vanillaInv, idx);
                        slot.setEmptyTooltip(() -> List.of(ExtendedLangs.DisplayMachineConfig.text(),
                                ExtendedLangs.UseToDisplayMachineThroughput.text().withStyle(ChatFormatting.GRAY)));
                        slot.x = idx * SLOT_SIZE + GUI_PADDING_X;
                        slot.y = (finalI - 1) * SLOT_SIZE + 36 + V_SHIFT;
                        this.menu.slots.add(slot);
                    }
                }
                case ConfigDisplayRow(FrozenMachineStatus container) -> {
                    var offset = configScrollLevels.getOrDefault(container.getFacilityUid(), 0);
                    var aeKeyInv = ConfigInventory.configStacks(null, 9, () -> {
                    }, true);
                    var vanillaInv = aeKeyInv.createMenuWrapper();
                    var entries = container.getConfiguredSetting().reference2LongEntrySet().toArray();
                    Arrays.sort(entries, Comparator
                            .comparingLong(e -> Math.abs(((Reference2LongMap.Entry<AEKey>) e).getLongValue()))
                            .reversed());
                    for (int idx = 0; idx < 9; idx++) {
                        if (idx + offset < entries.length) {
                            var entry = (Reference2LongMap.Entry<AEKey>) entries[idx + offset];
                            AEKey key = entry.getKey();
                            long value = entry.getLongValue();
                            aeKeyInv.setStack(idx, new GenericStack(key, value));
                        }
                        InaccessibleSlot slot = new InaccessibleSlot(vanillaInv, idx);
                        slot.setIcon(IconsExtended.SLOT_BG_CONFIG);
                        slot.setEmptyTooltip(() -> List.of(ExtendedLangs.DisplayMachineConfig.text(),
                                ExtendedLangs.UseToDisplayMachineConfig.text().withStyle(ChatFormatting.GRAY)));
                        slot.x = idx * SLOT_SIZE + GUI_PADDING_X;
                        slot.y = (finalI - 1) * SLOT_SIZE + 36 + V_SHIFT;
                        this.menu.slots.add(slot);
                    }
                }
                case GroupHeaderRow(PatternContainerGroup group) -> {
                    if (group.icon() != null) {
                        var renderContext = new SimpleRenderContext(LytRect.empty(), guiGraphics);
                        renderContext.renderItem(
                                group.icon().getReadOnlyStack(),
                                GUI_PADDING_X + PATTERN_PROVIDER_NAME_MARGIN_X,
                                GUI_PADDING_Y + GUI_HEADER_HEIGHT + i * ROW_HEIGHT,
                                8,
                                8);
                    }

                    final int rows = this.byGroup.get(group).size();

                    FormattedText displayName;
                    if (rows > 1) {
                        displayName = Component.empty()
                                .append(group.name())
                                .append(Component.literal(" (" + rows + ')'));
                    } else {
                        displayName = group.name();
                    }

                    var text = Language.getInstance().getVisualOrder(
                            this.font.substrByWidth(displayName, TEXT_MAX_WIDTH - 10));

                    guiGraphics.drawString(font, text, GUI_PADDING_X + PATTERN_PROVIDER_NAME_MARGIN_X + 10,
                            GUI_PADDING_Y + GUI_HEADER_HEIGHT + i * ROW_HEIGHT, textColor, false);
                }
            }
        }
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot s) {
        if (s instanceof ThroughputSlot slot) {
            if (slot.getIcon() != null) {
                slot.getIcon().getBlitter()
                        .dest(s.x, s.y)
                        .opacity(slot.getOpacityOfIcon())
                        .blit(guiGraphics);
            }
            try {
                AEKeyRendering.drawInGui(
                        minecraft,
                        guiGraphics,
                        s.x,
                        s.y, slot.getKey());
            } catch (Exception err) {
                AELog.warn("[AppEng] AE prevented crash while drawing slot: " + err);
            }

            // If a view mode is selected that only shows craftable items, display the "craftable" text
            // regardless of stack size
            var useLargeFonts = config.isUseLargeFonts();
            AmountFormat format = useLargeFonts ? AmountFormat.SLOT_LARGE_FONT
                    : AmountFormat.SLOT;
            var text = slot.formatThroughputShort(format);
            StackSizeRenderer.renderSizeLabel(guiGraphics, this.font, s.x, s.y, text, useLargeFonts ? 0.85f : 0.5f,
                    false, false);
            return;
        }
        super.renderSlot(guiGraphics, s);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double wheelDelta) {
        var hoveredLineIndex = getHoveredLineIndex((int) x, (int) y);
        if (Screen.hasShiftDown() && hoveredLineIndex != -1) {
            var row = rows.get(hoveredLineIndex);
            FrozenMachineStatus group;
            Int2IntOpenHashMap scrollLevels;
            AEKeyMap<AEKey> groupCollection;
            if (row instanceof StatRow(FrozenMachineStatus group0)) {
                group = group0;
                scrollLevels = statsRowScrollLevels;
                groupCollection = group.getThroughputCounter().map;
            } else if (row instanceof ConfigDisplayRow(FrozenMachineStatus group0)) {
                group = group0;
                scrollLevels = configScrollLevels;
                groupCollection = group.getConfiguredSetting();
            } else {
                return super.mouseScrolled(x, y, wheelDelta);
            }

            int currentScroll = scrollLevels.getOrDefault(group.getFacilityUid(), 0);
            int maxScroll = Math.max(0, groupCollection.size() - 9);
            int newScroll = Math.max(0, Math.min(maxScroll, currentScroll + (int) -wheelDelta));
            if (newScroll != currentScroll) {
                scrollLevels.put(group.getFacilityUid(), newScroll);
            }
            return true;
        }
        return super.mouseScrolled(x, y, wheelDelta);
    }

    @Override
    protected void renderTooltip(@NotNull GuiGraphics guiGraphics, int x, int y) {
        // Draw line tooltip
        var hoveredLineIndex = getHoveredLineIndex(x, y);
        if (hoveredLineIndex != -1) {
            Row row = rows.get(hoveredLineIndex);
            if (hoveredSlot == null) {
                if (row instanceof GroupHeaderRow(PatternContainerGroup group) && !group.tooltip().isEmpty()) {
                    guiGraphics.renderTooltip(font, group.tooltip(), Optional.empty(), x, y);
                    return;
                }
            }
            if (hoveredSlot instanceof InaccessibleSlot slot && !slot.getDisplayStack().isEmpty()) {
                List<Component> currentToolTip;

                if (slot instanceof ThroughputSlot t) {
                    currentToolTip = AEKeyRendering.getTooltip(t.getKey());
                    currentToolTip.add(t.formatThroughput());
                } else {
                    currentToolTip = getTooltipFromContainerItem(slot.getItem());
                }

                boolean hasMoreThan9Items = switch (row) {
                    case StatRow(FrozenMachineStatus container) -> container.getThroughputCounter().map.size() > 9;
                    case ConfigDisplayRow(FrozenMachineStatus container) -> container.getConfiguredSetting().size() > 9;
                    default -> false;
                };
                if (hasMoreThan9Items) {
                    currentToolTip.add(ExtendedLangs.HoldShiftToScrollThisRow.text().withStyle(ChatFormatting.GRAY));
                }

                renderKeyTooltipThroughItemAPI(
                        guiGraphics,
                        slot instanceof ThroughputSlot t ? t.getKey() : AEItemKey.of(slot.getItem()),
                        x, y, currentToolTip);
                return;
            }
        }
        super.renderTooltip(guiGraphics, x, y);
    }

    @Override
    protected void slotClicked(@Nullable Slot slot, int slotIdx, int mouseButton, ClickType clickType) {
        if (slot instanceof InaccessibleSlot) {
            return;
        }
        super.slotClicked(slot, slotIdx, mouseButton, clickType);
    }

    private int getHoveredLineIndex(int x, int y) {
        x = x - leftPos - GUI_PADDING_X;
        y = y - topPos - SLOT_SIZE - V_SHIFT;
        if (x < 0 || y < 0) {
            return -1;
        }
        if (x >= SLOT_SIZE * COLUMNS || y >= visibleRows * ROW_HEIGHT) {
            return -1;
        }

        var rowIndex = scrollbar.getCurrentScroll() + y / ROW_HEIGHT;
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return -1;
        }
        return rowIndex;
    }

    @Override
    public boolean mouseClicked(double xCoord, double yCoord, int btn) {
        if (btn == 1 && this.searchField.isMouseOver(xCoord, yCoord)) {
            this.searchField.setValue("");
            // Don't return immediately to also grab focus.
        }

        return super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX,
            int mouseY, float partialTicks) {
        // Draw the top of the dialog
        blit(guiGraphics, offsetX, offsetY, HEADER_BBOX);

        final int scrollLevel = scrollbar.getCurrentScroll();

        int currentY = offsetY + GUI_HEADER_HEIGHT;

        // Draw the footer now so slots will draw on top of it
        blit(guiGraphics, offsetX, currentY + this.visibleRows * ROW_HEIGHT, FOOTER_BBOX);

        for (int i = 0; i < this.visibleRows; ++i) {
            // Draw the dialog background for this row
            // Skip 1 pixel for the first row in order to not over-draw on the top scrollbox border,
            // and do the same but for the bottom border on the last row
            boolean firstLine = i == 0;
            boolean lastLine = i == this.visibleRows - 1;

            // Draw the background for the slots in an inventory row
            Rect2i bbox = selectRowBackgroundBox(null, firstLine, lastLine);
            blit(guiGraphics, offsetX, currentY, bbox);
            if (scrollLevel + i < this.rows.size()) {
                var row = this.rows.get(scrollLevel + i);
                bbox = selectRowBackgroundBox(row, firstLine, lastLine);
//                bbox.setWidth(GUI_PADDING_X + SLOT_SIZE * 9 - 1);
                blit(guiGraphics, offsetX, currentY, bbox);
            }

            currentY += ROW_HEIGHT;
        }
    }

    private Rect2i selectRowBackgroundBox(Row row, boolean firstLine, boolean lastLine) {
        switch (row) {
            case InfoRow ignored1 -> {
                if (firstLine) {
                    return ROW_INFO_TOP_BBOX;
                } else if (lastLine) {
                    return ROW_INFO_BOTTOM_BBOX;
                } else {
                    return ROW_INFO_MIDDLE_BBOX;
                }
            }
            case StatRow ignored1 -> {
                if (firstLine) {
                    return ROW_INVENTORY_TOP_BBOX;
                } else if (lastLine) {
                    return ROW_INVENTORY_BOTTOM_BBOX;
                } else {
                    return ROW_INVENTORY_MIDDLE_BBOX;
                }
            }
            case ConfigDisplayRow ignored -> {
                if (firstLine) {
                    return ROW_INVENTORY_TOP_BBOX;
                } else if (lastLine) {
                    return ROW_INVENTORY_BOTTOM_BBOX;
                } else {
                    return ROW_INVENTORY_MIDDLE_BBOX;
                }
            }
            case null, default -> {
                if (firstLine) {
                    return ROW_TEXT_TOP_BBOX;
                } else if (lastLine) {
                    return ROW_TEXT_BOTTOM_BBOX;
                } else {
                    return ROW_TEXT_MIDDLE_BBOX;
                }
            }
        }

    }

    @Override
    public boolean charTyped(char character, int key) {
        if (character == ' ' && this.searchField.getValue().isEmpty()) {
            return true;
        }
        return super.charTyped(character, key);
    }

    public void clear() {
        this.byFacilityUniqueId.clear();
        // invalid caches on refresh
        this.refreshList();
    }

    public void updateFacility(FrozenMachineStatus status) {
        var add = byFacilityUniqueId.putIfAbsent(status.getFacilityUid(), status);

        if (add == null) {
            refreshScheduled = true;
        } else {
            refreshScheduled |= add.clientUpdate(status);
        }
    }

    public void removeFacility(int facilityId) {
        var removed = byFacilityUniqueId.remove(facilityId);

        if (removed != null) {
            refreshScheduled = true;
        }
    }

    @Override
    public void containerTick() {
        if (refreshScheduled) {
            refreshScheduled = false;
            refreshList();
        }
        super.containerTick();
    }

    /**
     * 由客户端数据构建gui列表，按照grouping和搜索过滤。
     * <p>
     * <strong>耗时操作。</strong>
     */
    private void refreshList() {
        this.byGroup.clear();
        this.highlightBtns.forEach((k, v) -> this.removeWidget(v));
        this.highlightBtns.clear();
        this.openGuiBtns.forEach((k, v) -> this.removeWidget(v));
        this.openGuiBtns.clear();
        this.shownIds.clear();

        final String searchFilterLowerCase = this.searchField.getValue().toLowerCase();

        Set<Integer> intset = byFacilityUniqueId.keySet();

        for (int facilityId : intset) {
            var entry = byFacilityUniqueId.get(facilityId);

            // Shortcut to skip any filter if search term is ""/empty
            boolean found = searchFilterLowerCase.isEmpty();

            // if found, filter skipped or machine name matching the search term, add it
            if (found || entry.getSearchName().toLowerCase().contains(searchFilterLowerCase)
                    || entry.getThroughputCounter().map.keySet().stream().anyMatch(
                            key -> key.getDisplayName().getString().toLowerCase().contains(searchFilterLowerCase))
                    || entry.getConfiguredSetting().keySet().stream().anyMatch(
                            key -> key.getDisplayName().getString().toLowerCase().contains(searchFilterLowerCase))) {
                this.byGroup.put(entry.getTerminalGroup(), entry);
            }
        }

        this.groups.clear();
        this.groups.addAll(this.byGroup.keySet());

        this.groups.sort(GROUP_COMPARATOR);

        this.rows.clear();
        this.rows.ensureCapacity(this.getMaxRows());

        for (var group : this.groups) {
            this.rows.add(new GroupHeaderRow(group));

            var containers = new ArrayList<>(this.byGroup.get(group));
            for (var container : containers) {
                this.rows.add(new InfoRow(container));

                var btn = new HighlightButton();
                var posInfo = container.getFacilityPosition();
                btn.setMultiplier(this.playerToBlockDis(posInfo.blockPos()));
                btn.setTarget(posInfo.blockPos(), posInfo.side(), posInfo.dimension());
                btn.setSuccessJob(() -> {
                    if (this.getPlayer() != null) {
                        Component message = MessageUtil.createEnhancedHighlightMessage(this.getPlayer(),
                                posInfo.blockPos(), posInfo.dimension(),
                                ExtendedLangs.HighlightFacilityPosAt.getTranslationKey());
                        this.getPlayer().displayClientMessage(message, false);
                    }
                });
                btn.setTooltip(Tooltip.create(ExtendedLangs.HighlightFacilityPos.text()));
                btn.setVisibility(false);
                this.highlightBtns.put(container.getFacilityUid(), this.addRenderableWidget(btn));

                var guiBtn = IconButton.simple(IconsExtended.OPEN_GUI, (b) -> {
                    menu.openGui(container.getFacilityUid());
                });
                guiBtn.setTooltip(Tooltip.create(ExtendedLangs.OpenGuiOfThisFacility.text()
                        .append("\n")
                        .append(ExtendedLangs.OpenGuiOfThisFacilityTooltip.text().withStyle(ChatFormatting.GRAY))));
                guiBtn.setVisibility(true);
                this.openGuiBtns.put(container.getFacilityUid(), this.addRenderableWidget(guiBtn));

                if (!container.getConfiguredSetting().isEmpty()) {
                    this.rows.add(new ConfigDisplayRow(container));
                }
                if (container.getThroughputCounter() != ThroughputCounter.EMPTY &&
                        !container.getThroughputCounter().isDisableShowingInTerminal()) {
                    this.rows.add(new StatRow(container));
                }

                this.shownIds.add(container.getFacilityUid());
            }
        }

        // lines may have changed - recalculate scroll bar.
        this.resetScrollbar();
    }

    private double playerToBlockDis(BlockPos pos) {
        if (pos == null) {
            return 0;
        }
        var ps = this.getPlayer().getOnPos();
        return pos.distSqr(ps);
    }

    /**
     * Should be called whenever this.lines.size() or this.numLines changes.
     */
    private void resetScrollbar() {
        // Needs to take the border into account, so offset for 1 px on the top and bottom.
        scrollbar.setHeight(this.visibleRows * ROW_HEIGHT - 2);
        scrollbar.setRange(0, this.rows.size() - this.visibleRows, 2);
    }

    private void reinitialize() {
        this.children().removeAll(this.renderables);
        this.renderables.clear();
        this.init();
    }

    private void toggleTerminalStyle(SettingToggleButton<TerminalStyle> btn, boolean backwards) {
        TerminalStyle next = btn.getNextValue(backwards);
        AEConfig.instance().setTerminalStyle(next);
        btn.set(next);
        this.reinitialize();
    }

    /**
     * The max amount of unique names and each inv row. Not affected by the filtering.
     *
     * @return max amount of unique names and each inv row
     */
    private int getMaxRows() {
        return this.groups.size() + this.shownIds.size() * 3;
    }

    /**
     * A version of blit that lets us pass a source rectangle
     *
     * @see GuiGraphics#blit(ResourceLocation, int, int, int, int, int, int)
     */
    private void blit(GuiGraphics guiGraphics, int offsetX, int offsetY, Rect2i srcRect) {
        var texture = AppEng.makeId("textures/guis/facilitymanagement.png");
        guiGraphics.blit(texture,
                offsetX, offsetY,
                0,
                srcRect.getX(), srcRect.getY(),
                srcRect.getWidth(),
                srcRect.getHeight(),
                256, 320);
    }

    sealed interface Row {
    }

    /**
     * A row containing a header for a group.
     */
    record GroupHeaderRow(PatternContainerGroup group) implements Row {
    }

    /**
     * A row containing slots for a subset of a pattern container inventory.
     */
    record StatRow(FrozenMachineStatus container) implements Row {
    }

    record InfoRow(FrozenMachineStatus container) implements Row {
    }

    record ConfigDisplayRow(FrozenMachineStatus container) implements Row {
    }

    // =====================================================
    // -- Constants for the UI texture and layout, not necessarily the same as the actual drawn size due to scaling.
    // =====================================================
    private static final int V_SHIFT = 24;

    private static final int GUI_WIDTH = 195;
    private static final int GUI_TOP_AND_BOTTOM_PADDING = 54;

    private static final int GUI_PADDING_X = 8;
    private static final int GUI_PADDING_Y = 6;

    private static final int GUI_HEADER_HEIGHT = 17 + V_SHIFT;
    private static final int GUI_FOOTER_HEIGHT = 97;
    private static final int COLUMNS = 9;

    /**
     * Additional margin in pixel for a text row inside the scrolling box.
     */
    private static final int PATTERN_PROVIDER_NAME_MARGIN_X = 2;

    /**
     * The maximum length for the string of a text row in pixel.
     */
    private static final int TEXT_MAX_WIDTH = 155;

    /**
     * Height of a table-row in pixels.
     */
    private static final int ROW_HEIGHT = 18;

    /**
     * Size of a slot in both x and y dimensions in pixel, most likely always the same as ROW_HEIGHT.
     */
    private static final int SLOT_SIZE = ROW_HEIGHT;

    // Bounding boxes of key areas in the UI texture.
    // The upper part of the UI, anything above the scrollable area (incl. its top border)
    private static final Rect2i HEADER_BBOX = new Rect2i(0, 0, GUI_WIDTH, GUI_HEADER_HEIGHT);
    // Background for a text row in the scroll-box.
    // Spans across the whole texture including the right and left borders including the scrollbar.
    // Covers separate textures for the top, middle and bottoms rows for more customization.
    private static final Rect2i ROW_TEXT_TOP_BBOX = new Rect2i(0, 17 + V_SHIFT, GUI_WIDTH, ROW_HEIGHT);
    private static final Rect2i ROW_TEXT_MIDDLE_BBOX = new Rect2i(0, 53 + V_SHIFT, GUI_WIDTH, ROW_HEIGHT);
    private static final Rect2i ROW_TEXT_BOTTOM_BBOX = new Rect2i(0, 89 + V_SHIFT, GUI_WIDTH, ROW_HEIGHT);
    // Background for a inventory row in the scroll-box.
    // Spans across the whole texture including the right and left borders including the scrollbar.
    // Covers separate textures for the top, middle and bottoms rows for more customization.
    private static final Rect2i ROW_INVENTORY_TOP_BBOX = new Rect2i(0, 35 + V_SHIFT, GUI_WIDTH, ROW_HEIGHT);
    private static final Rect2i ROW_INVENTORY_MIDDLE_BBOX = new Rect2i(0, 71 + V_SHIFT, GUI_WIDTH, ROW_HEIGHT);
    private static final Rect2i ROW_INVENTORY_BOTTOM_BBOX = new Rect2i(0, 107 + V_SHIFT, GUI_WIDTH, ROW_HEIGHT);
    private static final Rect2i ROW_INFO_TOP_BBOX = new Rect2i(0, 256, GUI_WIDTH, ROW_HEIGHT);
    private static final Rect2i ROW_INFO_MIDDLE_BBOX = new Rect2i(0, 256 + 18, GUI_WIDTH, ROW_HEIGHT);
    private static final Rect2i ROW_INFO_BOTTOM_BBOX = new Rect2i(0, 256 + 18 + 18, GUI_WIDTH, ROW_HEIGHT);
    // This is the lower part of the UI, anything below the scrollable area (incl. its bottom border)
    private static final Rect2i FOOTER_BBOX = new Rect2i(0, 125 + V_SHIFT, GUI_WIDTH, GUI_FOOTER_HEIGHT);

    private static final Comparator<PatternContainerGroup> GROUP_COMPARATOR = Comparator
            .comparing(group -> group.name().getString().toLowerCase(Locale.ROOT));

}
