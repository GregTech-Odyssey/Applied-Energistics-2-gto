/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.config;

import gto_ae.client.gui.IconsExtended;
import gto_ae.core.localization.ExtendedLangs;
import gto_ae.hooks.gui.IActionItems;
import gto_ae.hooks.gui.IIcon;

import appeng.client.gui.Icon;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.LocalizationEnum;

public enum ActionItems implements IActionItems {
    WRENCH(Icon.WRENCH, ButtonToolTips.PartitionStorage, ButtonToolTips.PartitionStorageHint),
    CLOSE(Icon.CLEAR, ButtonToolTips.Clear, ButtonToolTips.ClearSettings),
    STASH(Icon.ARROW_UP, ButtonToolTips.Stash, ButtonToolTips.StashDesc),
    STASH_TO_PLAYER_INV(Icon.ARROW_DOWN, ButtonToolTips.StashToPlayer, ButtonToolTips.StashToPlayerDesc),
    ENCODE(Icon.WHITE_ARROW_DOWN, ButtonToolTips.Encode, ButtonToolTips.EncodeDescription),
    CYCLE_PROCESSING_OUTPUT(Icon.SCHEDULING_DEFAULT, ButtonToolTips.CycleProcessingOutput,
            ButtonToolTips.CycleProcessingOutputTooltip),
    TERMINAL_SETTINGS(Icon.WRENCH, ButtonToolTips.TerminalSettings, null),

    ENCODING_TO_INVENTORY(IconsExtended.ENCODING_TO_INVENTORY, ExtendedLangs.ShiftEncodingDesc,
            ExtendedLangs.ShiftEncodingClearDesc);

    private final IIcon icon;
    private final LocalizationEnum displayName;
    private final LocalizationEnum displayValue;

    ActionItems(IIcon icon, LocalizationEnum displayName, LocalizationEnum displayValue) {
        this.icon = icon;
        this.displayName = displayName;
        this.displayValue = displayValue;
    }

    @Override
    public IIcon icon() {
        return icon;
    }

    @Override
    public LocalizationEnum displayName() {
        return displayName;
    }

    @Override
    public LocalizationEnum displayValue() {
        return displayValue;
    }
}
