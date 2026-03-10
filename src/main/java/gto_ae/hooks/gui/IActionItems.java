package gto_ae.hooks.gui;

import appeng.core.localization.LocalizationEnum;

public interface IActionItems {
    IIcon icon();

    LocalizationEnum displayName();

    LocalizationEnum displayValue();
}
