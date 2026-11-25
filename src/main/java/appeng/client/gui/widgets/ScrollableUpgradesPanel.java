/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
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

package appeng.client.gui.widgets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.Upgrades;
import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.Rects;
import appeng.client.gui.Tooltip;
import appeng.client.gui.style.PaletteColor;
import appeng.menu.slot.AppEngSlot;

/**
 * Right-side vertical slot panel combining one or more slot groups (upgrades, singularity, view cells) with a compact
 * scrollbar when the active slot count exceeds {@link #DEFAULT_MAX_VISIBLE_ROWS}.
 */
public final class ScrollableUpgradesPanel implements ICompositeWidget {

    public static final int DEFAULT_MAX_VISIBLE_ROWS = 4;

    private static final int SLOT_SIZE = 18;
    private static final int SLOT_INNER_SIZE = 16;
    private static final int SLOT_X_OFFSET = 2;
    private static final int SLOT_Y_OFFSET = 6;
    private static final int TRACK_X_OFFSET = 22;
    private static final int SCROLLBAR_X_OFFSET = 20;
    private static final int WIDTH_PLAIN = 23;
    private static final int WIDTH_SCROLLABLE = 30;
    private static final int HIDDEN_SLOT_POS = -9999;

    private static final int COLOR_OUTER_BORDER = 0xFF413F54;
    private static final int DEFAULT_BORDER_COLOR = 0xFFF2F2F2;
    private static final int COLOR_BODY = 0xFFCBCCD4;
    private static final int COLOR_SHADOW = 0xFF878FA5;
    private static final int COLOR_SLOT_TOP = 0xFF9A9FB4;
    private static final int COLOR_SLOT_BG = 0xFFADB0C4;

    public record SlotGroup(List<Slot> slots, Supplier<List<Component>> tooltipSupplier) {
        public static SlotGroup ofUpgrades(List<Slot> slots, IUpgradeableObject upgradeableObject) {
            return new SlotGroup(slots,
                    () -> Upgrades.getTooltipLinesForMachine(upgradeableObject.getUpgrades().getUpgradableItem()));
        }
    }

    private final List<SlotGroup> groups;
    private final int maxVisibleRows;
    private final Scrollbar scrollbar;
    private final ArrayList<Slot> enabledSlots = new ArrayList<>();
    private final ArrayList<Supplier<List<Component>>> enabledTooltips = new ArrayList<>();

    private int x;
    private int y;
    private int borderColor = DEFAULT_BORDER_COLOR;

    public ScrollableUpgradesPanel(List<SlotGroup> groups) {
        this(groups, DEFAULT_MAX_VISIBLE_ROWS);
    }

    public ScrollableUpgradesPanel(List<SlotGroup> groups, int maxVisibleRows) {
        this.groups = groups;
        this.maxVisibleRows = maxVisibleRows;
        this.scrollbar = new Scrollbar(Scrollbar.SMALL);
        this.scrollbar.setCaptureMouseWheel(false);
    }

    @Override
    public void setPosition(Point position) {
        this.x = position.getX();
        this.y = position.getY();
    }

    @Override
    public void setSize(int width, int height) {
    }

    @Override
    public void populateScreen(Consumer<AbstractWidget> addWidget, Rect2i bounds, AEBaseScreen<?> screen) {
        this.borderColor = screen.getStyle().getColorOrDefault(PaletteColor.UPGRADES_BORDER, DEFAULT_BORDER_COLOR);
    }

    private void refreshEnabledSlots() {
        enabledSlots.clear();
        enabledTooltips.clear();
        for (int g = 0, gSize = groups.size(); g < gSize; g++) {
            var group = groups.get(g);
            var groupSlots = group.slots();
            for (int i = 0, sSize = groupSlots.size(); i < sSize; i++) {
                var slot = groupSlots.get(i);
                if (slot instanceof AppEngSlot aeSlot && !aeSlot.isSlotEnabled()) {
                    continue;
                }
                enabledSlots.add(slot);
                enabledTooltips.add(group.tooltipSupplier());
            }
        }
    }

    private int getVisibleRows() {
        return Math.min(maxVisibleRows, enabledSlots.size());
    }

    private boolean isScrollable() {
        return enabledSlots.size() > maxVisibleRows;
    }

    private int getInnerHeight(int visibleRows) {
        return visibleRows * SLOT_SIZE - 2;
    }

    private int getPanelWidth() {
        return isScrollable() ? WIDTH_SCROLLABLE : WIDTH_PLAIN;
    }

    private int getPanelHeight(int visibleRows) {
        return SLOT_Y_OFFSET + getInnerHeight(visibleRows) + 8;
    }

    @Override
    public Rect2i getBounds() {
        int total = enabledSlots.size();
        if (total == 0) {
            return new Rect2i(x, y, 0, 0);
        }
        return new Rect2i(x, y, getPanelWidth(), getPanelHeight(getVisibleRows()));
    }

    @Override
    public void updateBeforeRender() {
        refreshEnabledSlots();
        int total = enabledSlots.size();
        if (total == 0) {
            scrollbar.setVisible(false);
            return;
        }

        int visibleRows = getVisibleRows();
        boolean scrollable = total > maxVisibleRows;
        scrollbar.setVisible(scrollable);
        if (scrollable) {
            scrollbar.setPosition(new Point(this.x + SCROLLBAR_X_OFFSET, this.y + SLOT_Y_OFFSET));
            scrollbar.setHeight(getInnerHeight(visibleRows));
            scrollbar.setRange(0, total - visibleRows, 1);
        } else {
            scrollbar.setRange(0, 0, 1);
        }

        int scroll = scrollbar.getCurrentScroll();
        for (int i = 0; i < total; i++) {
            var slot = enabledSlots.get(i);
            int row = i - scroll;
            boolean inView = row >= 0 && row < visibleRows;
            if (slot instanceof AppEngSlot aeSlot) {
                aeSlot.setActive(inView);
            }
            if (inView) {
                slot.x = this.x + SLOT_X_OFFSET;
                slot.y = this.y + SLOT_Y_OFFSET + row * SLOT_SIZE;
            } else {
                slot.x = HIDDEN_SLOT_POS;
                slot.y = HIDDEN_SLOT_POS;
            }
        }
    }

    @Override
    public void drawBackgroundLayer(GuiGraphics guiGraphics, Rect2i bounds, Point mouse) {
        int total = enabledSlots.size();
        if (total == 0) {
            return;
        }

        int visibleRows = getVisibleRows();
        boolean scrollable = total > maxVisibleRows;
        int panelWidth = scrollable ? WIDTH_SCROLLABLE : WIDTH_PLAIN;
        int innerHeight = getInnerHeight(visibleRows);
        int panelHeight = getPanelHeight(visibleRows);

        int ox = bounds.getX() + this.x;
        int oy = bounds.getY() + this.y;

        // Outer dark border (left open at dx=0 to merge with terminal wall)
        guiGraphics.fill(ox + 1, oy, ox + panelWidth, oy + 1, COLOR_OUTER_BORDER);
        guiGraphics.fill(ox + panelWidth - 1, oy + 1, ox + panelWidth, oy + panelHeight - 1, COLOR_OUTER_BORDER);
        guiGraphics.fill(ox + 1, oy + panelHeight - 1, ox + panelWidth, oy + panelHeight, COLOR_OUTER_BORDER);

        // Panel body
        guiGraphics.fill(ox, oy + 2, ox + panelWidth - 2, oy + panelHeight - 4, COLOR_BODY);

        // Panel inner highlights & bottom shadow
        guiGraphics.fill(ox + 1, oy + 1, ox + panelWidth - 1, oy + 2, borderColor);
        guiGraphics.fill(ox + panelWidth - 2, oy + 2, ox + panelWidth - 1, oy + panelHeight - 4, borderColor);
        guiGraphics.fill(ox + 1, oy + panelHeight - 4, ox + panelWidth - 1, oy + panelHeight - 3, borderColor);
        guiGraphics.fill(ox + 1, oy + panelHeight - 3, ox + panelWidth - 1, oy + panelHeight - 1, COLOR_SHADOW);

        // Slot column white frame
        int frameTop = oy + SLOT_Y_OFFSET - 1;
        int frameBottom = oy + SLOT_Y_OFFSET + innerHeight;
        guiGraphics.fill(ox + 1, frameTop, ox + 19, frameTop + 1, borderColor);
        guiGraphics.fill(ox + 1, frameBottom, ox + 19, frameBottom + 1, borderColor);
        guiGraphics.fill(ox + 1, frameTop + 1, ox + 2, frameBottom, borderColor);
        guiGraphics.fill(ox + 18, frameTop + 1, ox + 19, frameBottom, borderColor);

        // Individual slot backgrounds
        for (int row = 0; row < visibleRows; row++) {
            int sy = oy + SLOT_Y_OFFSET + row * SLOT_SIZE;
            guiGraphics.fill(ox + SLOT_X_OFFSET, sy, ox + SLOT_X_OFFSET + SLOT_INNER_SIZE, sy + 1, COLOR_SLOT_TOP);
            guiGraphics.fill(ox + SLOT_X_OFFSET, sy + 1, ox + SLOT_X_OFFSET + SLOT_INNER_SIZE, sy + SLOT_INNER_SIZE,
                    COLOR_SLOT_BG);
        }

        // Scrollbar track
        if (scrollable) {
            guiGraphics.fill(ox + TRACK_X_OFFSET - 1, frameTop, ox + TRACK_X_OFFSET + 4, frameTop + 1, borderColor);
            guiGraphics.fill(ox + TRACK_X_OFFSET - 1, frameBottom, ox + TRACK_X_OFFSET + 4, frameBottom + 1,
                    borderColor);
            guiGraphics.fill(ox + TRACK_X_OFFSET - 1, frameTop + 1, ox + TRACK_X_OFFSET, frameBottom, borderColor);
            guiGraphics.fill(ox + TRACK_X_OFFSET + 3, frameTop + 1, ox + TRACK_X_OFFSET + 4, frameBottom, borderColor);
            guiGraphics.fill(ox + TRACK_X_OFFSET, frameTop + 1, ox + TRACK_X_OFFSET + 3, frameBottom, COLOR_SLOT_TOP);
        }
    }

    @Override
    public void drawForegroundLayer(GuiGraphics guiGraphics, Rect2i bounds, Point mouse) {
        if (scrollbar.isVisible()) {
            scrollbar.drawForegroundLayer(guiGraphics, bounds, mouse);
        }
    }

    @Override
    public void tick() {
        if (scrollbar.isVisible()) {
            scrollbar.tick();
        }
    }

    @Override
    public boolean onMouseDown(Point mousePos, int button) {
        if (scrollbar.isVisible() && mousePos.isIn(scrollbar.getBounds())) {
            return scrollbar.onMouseDown(mousePos, button);
        }
        return false;
    }

    @Override
    public boolean onMouseUp(Point mousePos, int button) {
        if (scrollbar.isVisible()) {
            return scrollbar.onMouseUp(mousePos, button);
        }
        return false;
    }

    @Override
    public boolean wantsAllMouseUpEvents() {
        return true;
    }

    @Override
    public boolean onMouseDrag(Point mousePos, int button) {
        if (scrollbar.isVisible()) {
            return scrollbar.onMouseDrag(mousePos, button);
        }
        return false;
    }

    @Override
    public boolean onMouseWheel(Point mousePos, double delta) {
        if (scrollbar.isVisible()) {
            scrollbar.onMouseWheel(mousePos, delta);
            return true;
        }
        return false;
    }

    @Override
    public void addExclusionZones(List<Rect2i> exclusionZones, Rect2i screenBounds) {
        if (enabledSlots.isEmpty()) {
            return;
        }
        var b = getBounds();
        exclusionZones.add(Rects.expand(new Rect2i(
                screenBounds.getX() + b.getX(),
                screenBounds.getY() + b.getY(),
                b.getWidth(),
                b.getHeight()), 2));
    }

    @Nullable
    @Override
    public Tooltip getTooltip(int mouseX, int mouseY) {
        int total = enabledSlots.size();
        if (total == 0) {
            return null;
        }
        int visibleRows = getVisibleRows();
        int relY = mouseY - (this.y + SLOT_Y_OFFSET);
        if (mouseX < this.x + 1 || mouseX >= this.x + 19 || relY < 0 || relY >= visibleRows * SLOT_SIZE) {
            return null;
        }
        int idx = scrollbar.getCurrentScroll() + relY / SLOT_SIZE;
        if (idx < 0 || idx >= total) {
            return null;
        }
        var lines = enabledTooltips.get(idx).get();
        return lines.isEmpty() ? null : new Tooltip(lines);
    }

    public static Supplier<List<Component>> constantTooltip(Component line) {
        List<Component> list = Collections.singletonList(line);
        return () -> list;
    }
}
