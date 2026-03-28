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

package appeng.client.gui.me.crafting;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.common.Repo;
import appeng.core.localization.GuiText;
import appeng.menu.me.common.IClientRepo;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import appeng.util.ReadableNumberConverter;

import gto_ae.core.localization.ExtendedLangs;
import gto_ae.hooks.gui.menu.IRepoMenu;

public class CraftConfirmTableRenderer extends AbstractTableRenderer<CraftingPlanSummaryEntry> {
    @Nullable
    private Repo gto$repo;

    public CraftConfirmTableRenderer(AEBaseScreen<?> screen, int x, int y) {
        super(screen, x, y, 5);
    }

    @Override
    protected void beforeTableRender() {
        if (gto$repo != null)
            return;
        IClientRepo repo = (screen.getMenu()) instanceof IRepoMenu me ? me.getClientRepo() : null;
        if (repo instanceof Repo repo1) {
            gto$repo = repo1;
        }
    }

    @Override
    protected List<Component> getEntryDescription(CraftingPlanSummaryEntry entry) {
        List<Component> lines = new ArrayList<>(3);
        if (entry.getStoredAmount() > 0) {
            String amount = entry.getWhat().formatAmount(entry.getStoredAmount(), AmountFormat.SLOT);
            lines.add(GuiText.FromStorage.text(amount));
        }

        storedAmount: {
            if (gto$repo == null)
                break storedAmount;
            var e = gto$repo.getByKey(entry.getWhat());
            if (e == null)
                break storedAmount;
            long storedTotal = e.getStoredAmount();
            long storedAmount = entry.getStoredAmount();
            if (storedTotal <= 0 || storedAmount <= 0)
                break storedAmount;
            float storedPercent = Math.min((float) storedAmount / storedTotal, 1.0f);
            ChatFormatting color = storedPercent < 0.25f ? ChatFormatting.DARK_GREEN
                    : storedPercent < 0.5f ? ChatFormatting.GOLD
                            : storedPercent < 0.75f ? ChatFormatting.RED : ChatFormatting.DARK_RED;
            lines.add(ExtendedLangs.CraftUsedPercent.text(ReadableNumberConverter.format(storedPercent * 100, 5))
                    .withStyle(color));
        }

        if (entry.getMissingAmount() > 0) {
            String amount = entry.getWhat().formatAmount(entry.getMissingAmount(), AmountFormat.SLOT);
            lines.add(GuiText.Missing.text(amount));
        }
        if (entry.getEmittingAmount() > 0) {
            String amount = entry.getWhat().formatAmount(entry.getEmittingAmount(), AmountFormat.SLOT);
            lines.add(ExtendedLangs.EmitCrafting.text(amount));
        } else if (entry.getCraftAmount() > 0) {
            String amount = entry.getWhat().formatAmount(entry.getCraftAmount(), AmountFormat.SLOT);
            lines.add(GuiText.ToCraft.text(amount));
        }
        return lines;
    }

    @Override
    protected AEKey getEntryStack(CraftingPlanSummaryEntry entry) {
        return entry.getWhat();
    }

    @Override
    protected List<Component> getEntryTooltip(CraftingPlanSummaryEntry entry) {
        List<Component> lines = AEKeyRendering.getTooltip(entry.getWhat());

        // The tooltip compares the unabbreviated amounts
        if (entry.getStoredAmount() > 0) {
            lines.add(GuiText.FromStorage
                    .text(entry.getWhat().formatAmount(entry.getStoredAmount(), AmountFormat.FULL)));
        }

        if (entry.getEmittingAmount() > 0) {
            lines.add(ExtendedLangs.EmitCrafting
                    .text(entry.getWhat().formatAmount(entry.getEmittingAmount(), AmountFormat.FULL)));
            lines.add(ExtendedLangs.EmitCraftingNotes.text());
        } else if (entry.getCraftAmount() > 0) {
            lines.add(GuiText.ToCraft
                    .text(entry.getWhat().formatAmount(entry.getCraftAmount(), AmountFormat.FULL)));
        }

        if (entry.getMissingAmount() > 0) {
            lines.add(GuiText.Missing.text(
                    entry.getWhat().formatAmount(entry.getMissingAmount(), AmountFormat.FULL)));
        }

        return lines;

    }

    @Override
    protected int getEntryOverlayColor(CraftingPlanSummaryEntry entry) {
        return entry.getMissingAmount() > 0 ? 0x1AFF0000 : 0;
    }

}
