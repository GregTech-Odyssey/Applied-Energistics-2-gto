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

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiFavorites;
import dev.emi.emi.screen.EmiScreenManager;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.Scrollbar;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import appeng.integration.modules.emi.EmiStackHelper;
import appeng.integration.modules.emi.IStackInteractionScreen;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;

import gto_ae.core.localization.ExtendedLangs;

/**
 * This screen shows the computed crafting plan and allows the player to select a CPU on which it should be scheduled
 * for crafting.
 */
public class CraftConfirmScreen extends AEBaseScreen<CraftConfirmMenu> implements IStackInteractionScreen {

    private final CraftConfirmTableRenderer table;

    private final Button start;
    private final Button selectCPU;
    private final Button gto$addMissing;
    private final Scrollbar scrollbar;
    private final AETextField searchField;
    private final CraftingScreenTitle dialogTitle;
    private final NumberFormat byteFormat = NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT);
    private long displayedCpuBytes = -1;
    private String formattedCpuBytes = "";
    @Nullable
    private CraftingPlanSummary displayedPlan;
    private final CraftingMaterialSearch<CraftingPlanSummaryEntry> materialSearch = new CraftingMaterialSearch<>(
            CraftingPlanSummaryEntry::getWhat);

    public CraftConfirmScreen(CraftConfirmMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.table = new CraftConfirmTableRenderer(this, 9, 19);
        byteFormat.setMaximumFractionDigits(1);
        byteFormat.setRoundingMode(RoundingMode.DOWN);

        this.scrollbar = widgets.addScrollBar("scrollbar");

        this.dialogTitle = new CraftingScreenTitle(Minecraft.getInstance().font,
                style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB());
        dialogTitle.setMessage(GuiText.CraftingPlan.text(GuiText.CalculatingWait.text()));
        widgets.add("dialogTitle", dialogTitle);
        setTextHidden(TEXT_ID_DIALOG_TITLE, true);

        this.searchField = widgets.addTextField("materialSearch", true);
        this.searchField.setPlaceholder(GuiText.SearchPlaceholder.text());
        this.searchField.setTooltipMessage(Collections.singletonList(ExtendedLangs.SearchCraftingMaterials.text()));
        this.searchField.setMaxLength(100);
        this.searchField.setResponder(term -> {
            materialSearch.setSearchTerm(term);
            scrollbar.setCurrentScroll(0);
        });

        this.start = widgets.addButton("start", GuiText.Start.text(), this::start);
        this.start.active = false;

        this.selectCPU = widgets.addButton("selectCpu", getNextCpuButtonLabel(), this::selectNextCpu);
        this.selectCPU.active = false;

        widgets.addButton("cancel", GuiText.Cancel.text(), menu::goBack);

        gto$addMissing = Button.builder(ExtendedLangs.CraftAddMissingToEmi.text(), this::gto$addMissing)
                .bounds(82, 181, 50, 20)
                .tooltip(Tooltip.create(ExtendedLangs.CraftAddMissingToEmiDesc.text()))
                .build();
        widgets.add("gto$addMissing", (Consumer<AbstractWidget> addWidget, Rect2i bounds, AEBaseScreen<?> screen) -> {
            gto$addMissing.setPosition(82 + bounds.getX(), 181 + bounds.getY());
            addWidget.accept(gto$addMissing);
        });
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        var errorResult = menu.submitError.result();
        if (errorResult != null && errorResult.errorCode() != null) {
            switchToScreen(new CraftErrorScreen(this, errorResult.errorCode(), errorResult.errorDetail()));
            return;
        }

        this.selectCPU.setMessage(getNextCpuButtonLabel());

        CraftingPlanSummary plan = menu.getPlan();
        boolean planIsStartable;
        if (plan == null) {
            planIsStartable = false;
        } else {
            boolean simulationButAllowMissing = AEConfig.instance().isAllowMissingCraftingJobs() && plan.isSimulation();
            start.setMessage(simulationButAllowMissing ? ExtendedLangs.CraftMissingStart.text() : GuiText.Start.text());
            start.setTooltip(Tooltip.create(
                    simulationButAllowMissing ? ExtendedLangs.CraftMissingStartDesc.text() : GuiText.Start.text()));

            planIsStartable = !plan.isSimulation() || simulationButAllowMissing;
        }
        this.start.active = !this.menu.hasNoCPU() && planIsStartable;
        this.selectCPU.active = planIsStartable;
        this.gto$addMissing.visible = plan != null && plan.isSimulation();

        // Show additional status about the selected CPU and plan when the planning is done
        if (plan != displayedPlan) {
            displayedPlan = plan;
            Component planDetails = GuiText.CalculatingWait.text();
            if (plan != null) {
                planDetails = GuiText.BytesUsed.text(byteFormat.format(plan.getUsedBytes()));
            }
            dialogTitle.setMessage(GuiText.CraftingPlan.text(planDetails));
        }
        Component cpuDetails = Component.empty();
        if (plan != null) {
            if (plan.isSimulation()) {
                cpuDetails = GuiText.PartialPlan.text();
            } else if (this.menu.getCpuAvailableBytes() > 0) {
                long cpuBytes = this.menu.getCpuAvailableBytes();
                if (cpuBytes != displayedCpuBytes) {
                    displayedCpuBytes = cpuBytes;
                    formattedCpuBytes = byteFormat.format(cpuBytes);
                }
                cpuDetails = GuiText.ConfirmCraftCpuStatus.text(
                        formattedCpuBytes,
                        this.menu.getCpuCoProcessors());
            } else {
                cpuDetails = GuiText.ConfirmCraftNoCpu.text();
            }
        }

        setTextContent("cpu_status", cpuDetails);

        final int size = plan != null ? materialSearch.filter(plan.getEntries()).size() : 0;
        scrollbar.setRange(0, this.table.getScrollableRows(size), 1);
    }

    private Component getNextCpuButtonLabel() {
        if (this.menu.hasNoCPU()) {
            return GuiText.NoCraftingCPUs.text();
        }

        Component cpuName;
        if (this.menu.cpuName == null) {
            cpuName = GuiText.Automatic.text();
        } else {
            cpuName = this.menu.cpuName;
        }

        return GuiText.SelectedCraftingCPU.text(cpuName);
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX,
            int mouseY) {

        CraftingPlanSummary plan = menu.getPlan();
        if (plan != null) {
            this.table.render(guiGraphics, mouseX, mouseY, materialSearch.filter(plan.getEntries()),
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

    // Allow players to confirm a craft via the enter key
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int p_keyPressed_3_) {
        if (!searchField.isFocused() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            if (this.start.active) {
                this.start();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, p_keyPressed_3_);
    }

    private void selectNextCpu() {
        getMenu().cycleSelectedCPU(!isHandlingRightClick());
    }

    private void start() {
        getMenu().startJob();
    }

    private void gto$addMissing(Button button) {
        var plan = menu.getPlan();
        if (plan == null) {
            return;
        }
        plan.getEntries().forEach(entry -> {
            if (entry.getMissingAmount() > 0) {
                EmiFavorites.addFavorite(
                        EmiStackHelper.toEmiStack(
                                new GenericStack(entry.getWhat(), entry.getWhat().getAmountPerUnit())));
                EmiScreenManager.repopulatePanels(SidebarType.FAVORITES);
            }
        });
    }
}
