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

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import gto_ae.core.localization.ExtendedLangs;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AmountFormat;
import appeng.menu.slot.InaccessibleSlot;
import appeng.util.ConfigMenuInventory;
import appeng.util.ReadableNumberConverter;

public class ThroughputSlot extends InaccessibleSlot {

    private final boolean isInput;
    private final long amount;
    private final AEKey key;
    private final double lastRefreshInterval;

    public ThroughputSlot(ConfigMenuInventory i, int invSlot, long amount, long lastRefreshIntervalInMillis) {
        super(i, invSlot);
        this.key = i.getDelegate().getKey(invSlot);
        this.amount = amount;
        this.isInput = amount > 0;
        this.lastRefreshInterval = lastRefreshIntervalInMillis / 1000.0;
    }

    public boolean isInput() {
        return isInput;
    }

    public AEKey getKey() {
        return key;
    }

    public Component formatThroughputShort(AmountFormat format) {
        var abs = Math.abs(amount);
        var label = getKey().formatAmount(abs, format);
        var sign = isInput ? Component.literal("↑") : Component.literal("↓");
        var color = isInput ? ChatFormatting.AQUA : ChatFormatting.GOLD;
        return Component.empty()
                .append(label)
                .append(sign)
                .withStyle(color);
    }

    public Component formatThroughput() {
        var abs = Math.abs(amount);
        var perSec = lastRefreshInterval > 0 ? abs / lastRefreshInterval : 0;

        var sign = isInput ? "+" : "-";
        var label = AEKeyType.formatAmountFor(getKey().getType(), abs, 6);
        var labelSec = sign + AEKeyType.formatAmountFor(getKey().getType(), perSec, 6);

        var textTemplate = isInput ? ExtendedLangs.ThroughputImportPerSeconds
                : ExtendedLangs.ThroughputExportPerSeconds;
        var estimated = ExtendedLangs.ThroughputPerSecondEstimated.text(labelSec);
        var color = isInput ? ChatFormatting.AQUA : ChatFormatting.GOLD;
        return textTemplate.text(label, ReadableNumberConverter.format(lastRefreshInterval, 4))
                .withStyle(ChatFormatting.GRAY)
                .append(estimated.withStyle(color));
    }
}
