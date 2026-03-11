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

package appeng.client.gui.widgets;

import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

import appeng.api.config.ActionItems;
import appeng.core.localization.LocalizationEnum;

import gto_ae.hooks.gui.IActionItems;
import gto_ae.hooks.gui.IIcon;
import gto_ae.hooks.gui.INoMouseRedirectionWidget;

public class ActionButton extends IconButton implements INoMouseRedirectionWidget {
    private static final Pattern PATTERN_NEW_LINE = Pattern.compile("\\n", Pattern.LITERAL);
    private IIcon icon;

    private IActionItems lAction;

    public ActionButton(ActionItems action, Runnable onPress) {
        this((IActionItems) action, onPress);
    }

    public ActionButton(ActionItems action, Consumer<IActionItems> onPress) {
        this((IActionItems) action, onPress);
    }

    public ActionButton(IActionItems action, Runnable onPress) {
        this(action, a -> onPress.run());
    }

    public ActionButton(IActionItems action, Consumer<IActionItems> onPress) {
        super(btn -> onPress.accept(action));

        setAction(action);
    }

    public void setAction(IActionItems action) {
        if (action == lAction) {
            return;
        }
        icon = action.icon();
        setMessage(buildMessage(action.displayName(), action.displayValue()));
    }

    public IActionItems getAction() {
        return lAction;
    }

    @Override
    protected IIcon getIcon() {
        return icon;
    }

    private Component buildMessage(LocalizationEnum displayName, @Nullable LocalizationEnum displayValue) {
        String name = displayName.text().getString();
        if (displayValue == null) {
            return Component.literal(name);
        }
        String value = displayValue.text().getString();

        value = PATTERN_NEW_LINE.matcher(value).replaceAll("\n");
        final StringBuilder sb = new StringBuilder(value);

        int i = sb.lastIndexOf("\n");
        if (i <= 0) {
            i = 0;
        }
        while (i + 30 < sb.length() && (i = sb.lastIndexOf(" ", i + 30)) != -1) {
            sb.replace(i, i + 1, "\n");
        }

        return Component.literal(name + '\n' + sb);
    }

}
