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

package appeng.client.gui.me.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.DurationFormatUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

import appeng.api.config.CpuSelectionMode;
import appeng.api.config.CraftingCpuHeight;
import appeng.api.config.Settings;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.TiledBackground;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.Scrollbar;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.core.localization.GuiText;
import appeng.integration.modules.emi.IStackInteractionScreen;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.menu.me.crafting.CraftingStatus;
import appeng.menu.me.crafting.CraftingStatusEntry;

import com.gto.ae.core.localization.ExtendedLangs;

/**
 * This screen shows the current crafting job that a crafting CPU is working on (if any).
 */
public class CraftingCPUScreen<T extends CraftingCPUMenu> extends AEBaseScreen<T> implements IStackInteractionScreen {

    private static final int ROW_HEIGHT = 23;
    private static final int BASE_ROWS = 6;
    private static final int HEADER_HEIGHT = 19;
    private static final int MIN_ROWS = 2;

    private final CraftingStatusTableRenderer table;

    private final Button cancel;

    private final Scrollbar scrollbar;
    private final AETextField searchField;
    private final CraftingScreenTitle dialogTitle;

    private final SettingToggleButton<CpuSelectionMode> schedulingModeButton;

    private final TiledBackground background;

    private int visibleRows;
    private boolean layoutApplied;

    private CraftingStatus status;
    private final CraftingMaterialSearch<CraftingStatusEntry> materialSearch = new CraftingMaterialSearch<>(
            CraftingStatusEntry::getWhat);

    public CraftingCPUScreen(T menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        var background = style.getBackground();
        if (background == null) {
            throw new IllegalStateException("Screen " + getClass() + " requires a background image to be resizable");
        }
        this.background = new TiledBackground(background, HEADER_HEIGHT, ROW_HEIGHT, BASE_ROWS);

        this.table = new CraftingStatusTableRenderer(this, 9, 19);

        this.scrollbar = widgets.addScrollBar("scrollbar");

        this.dialogTitle = new CraftingScreenTitle(Minecraft.getInstance().font,
                style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB());
        widgets.add("dialogTitle", dialogTitle);
        setTextHidden(TEXT_ID_DIALOG_TITLE, true);

        this.searchField = widgets.addTextField("materialSearch", true);
        searchField.setPlaceholder(GuiText.SearchPlaceholder.text());
        searchField.setTooltipMessage(List.of(
                ExtendedLangs.SearchCraftingMaterials.text(),
                ExtendedLangs.SearchHoveredEmiItem.text()));
        searchField.setMaxLength(100);
        searchField.setResponder(term -> {
            materialSearch.setSearchTerm(term);
            scrollbar.setCurrentScroll(0);
        });

        this.cancel = this.widgets.addButton("cancel", GuiText.Cancel.text(), menu::cancelCrafting);

        this.schedulingModeButton = new ServerSettingToggleButton<>(Settings.CPU_SELECTION_MODE,
                CpuSelectionMode.ANY);

        // Purely a client-side layout preference, so it is offered on the crafting status screen as well.
        this.addToLeftToolbar(new SettingToggleButton<>(Settings.CRAFTING_CPU_HEIGHT,
                config.getCraftingCpuHeight(), this::toggleHeight));

        // This screen is reused for the crafting status in Terminals, where it should not show the CPU config button
        if (menu.allowConfiguration()) {
            this.addToLeftToolbar(this.schedulingModeButton);
        }
    }

    @Override
    protected void init() {
        this.applySize();
        super.init();
    }

    /**
     * Computes the dialog size for the configured row count and hands it to the widgets that depend on it.
     * <p>
     * Must not be called only from {@link #init()}: GTOCore replaces that method with a mixin whose
     * {@code super.init()} goes straight to {@code AEBaseScreen}, so the body above never runs on the real screen. It
     * is therefore also called from {@link #updateBeforeRender()} via {@link #relayoutIfResized()}.
     */
    private void applySize() {
        this.visibleRows = this.getVisibleRows();
        this.imageHeight = this.background.getHeight(this.visibleRows);
        this.table.setRows(this.visibleRows);
        this.scrollbar.setHeight(this.visibleRows * ROW_HEIGHT - 1);
    }

    /**
     * Rebuilds the widget layout when the configured size no longer matches what is on screen, which happens on first
     * display, when the button is pressed and when the window is resized.
     */
    private void relayoutIfResized() {
        if (this.layoutApplied && this.getVisibleRows() == this.visibleRows) {
            return;
        }
        // Size first: init() lays the widgets out from imageHeight, and that value has to be final by then.
        this.applySize();
        this.clearWidgets();
        this.init();
        this.layoutApplied = true;
    }

    /**
     * The dialog height the style was authored for, i.e. with {@link #BASE_ROWS} rows.
     */
    protected final int getBaseImageHeight() {
        return this.background.getHeight(BASE_ROWS);
    }

    /**
     * SHORT keeps the historic 6 rows, FULL uses whatever fits the window, and TALL sits in between. TALL has to beat
     * SHORT even on small windows, where {@code maxRows} barely exceeds the base rows and a plain 2/3 fraction would
     * round back down to 6 and make the button look broken.
     */
    private int getVisibleRows() {
        var maxRows = Math.max(MIN_ROWS,
                Math.floorDiv(height - 2 * config.getTerminalMargin() - this.getBaseImageHeight(), ROW_HEIGHT)
                        + BASE_ROWS);
        return switch (config.getCraftingCpuHeight()) {
            case SHORT -> Math.min(BASE_ROWS, maxRows);
            case TALL -> Math.min(maxRows, (BASE_ROWS + maxRows + 1) / 2);
            case FULL -> maxRows;
        };
    }

    private void toggleHeight(SettingToggleButton<CraftingCpuHeight> button, boolean backwards) {
        var next = button.getNextValue(backwards);
        config.setCraftingCpuHeight(next);
        button.set(next);
        // The next frame picks the new size up through relayoutIfResized() and re-runs the layout.
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        this.background.draw(guiGraphics, offsetX, offsetY, this.visibleRows);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        // init() is wrapped by other mods (see relayoutIfResized), so the size is re-checked here every frame.
        this.relayoutIfResized();

        // Update the dialog title with an ETA if possible
        Component title = this.getGuiDisplayName(GuiText.CraftingStatus.text());
        if (status != null) {
            final long elapsedTime = status.getElapsedTime();
            final double remainingItems = status.getRemainingItemCount();
            final double startItems = status.getStartItemCount();
            final long eta = (long) (elapsedTime / Math.max(1d, startItems - remainingItems)
                    * remainingItems);

            if (eta > 0 && !getVisualEntries().isEmpty()) {
                final long etaInMilliseconds = TimeUnit.MILLISECONDS.convert(eta, TimeUnit.NANOSECONDS);
                final String etaTimeText = DurationFormatUtils.formatDuration(etaInMilliseconds,
                        GuiText.ETAFormat.getLocal());
                title = title.copy().append(" - " + etaTimeText);
            }
        }
        // Mention that items can't be stored if applicable
        if (menu.isCantStoreItems()) {
            title = title.copy().append(" - ").append(GuiText.CantStoreItems.text().withStyle(ChatFormatting.RED));
        }
        dialogTitle.setMessage(title);

        final int size = materialSearch.filter(getVisualEntries()).size();
        scrollbar.setRange(0, this.table.getScrollableRows(size), 1);

        this.schedulingModeButton.set(this.menu.getSchedulingMode());
    }

    private List<CraftingStatusEntry> getVisualEntries() {
        return this.status != null ? status.getEntries() : Collections.emptyList();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float btn) {
        this.cancel.active = !getVisualEntries().isEmpty();

        super.render(guiGraphics, mouseX, mouseY, btn);
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);

        if (status != null) {
            this.table.render(guiGraphics, mouseX, mouseY, materialSearch.filter(status.getEntries()),
                    scrollbar.getCurrentScroll());
        }
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public StackWithBounds getStackUnderMouse(double mouseX, double mouseY) {
        var hovered = table.getHoveredStack();
        if (hovered != null) {
            return hovered;
        }
        return super.getStackUnderMouse(mouseX, mouseY);
    }

    @Override
    public List<Rect2i> getExclusionZones() {
        var zones = super.getExclusionZones();
        zones.add(searchField.getTooltipArea());
        return zones;
    }

    @Override
    protected boolean searchForEmiItem(String itemName) {
        fillSearchField(searchField, itemName);
        return true;
    }

    public void postUpdate(CraftingStatus status) {
        Long2ObjectMap<CraftingStatusEntry> entries;
        if (this.status == null || status.isFullStatus()) {
            // Start from scratch.
            // We can't just reuse the status because we have to filter out deleted entries.
            entries = new Long2ObjectLinkedOpenHashMap<>();
        } else {
            // Merge the status entries.
            entries = new Long2ObjectLinkedOpenHashMap<>(this.status.getEntries().size());
            for (CraftingStatusEntry entry : this.status.getEntries()) {
                entries.put(entry.getSerial(), entry);
            }
        }

        for (CraftingStatusEntry entry : status.getEntries()) {
            if (entry.isDeleted()) {
                entries.remove(entry.getSerial());
                continue;
            }

            CraftingStatusEntry existingEntry = entries.get(entry.getSerial());
            if (existingEntry != null) {
                entries.put(entry.getSerial(), new CraftingStatusEntry(
                        existingEntry.getSerial(),
                        existingEntry.getWhat(),
                        entry.getStoredAmount(),
                        entry.getActiveAmount(),
                        entry.getPendingAmount()));
            } else {
                entries.put(entry.getSerial(), entry);
            }
        }

        List<CraftingStatusEntry> sortedEntries = new ArrayList<>(entries.values());
        Collections.sort(sortedEntries);
        this.status = new CraftingStatus(
                true,
                status.getElapsedTime(),
                status.getRemainingItemCount(),
                status.getStartItemCount(),
                sortedEntries);
    }

}
