package com.gto.ae.hooks.gui;

import appeng.core.localization.LocalizationEnum;

public interface IActionItems {
    IIcon icon();

    LocalizationEnum displayName();

    LocalizationEnum displayValue();
}
