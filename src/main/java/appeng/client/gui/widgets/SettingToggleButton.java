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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import appeng.api.config.Setting;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.LocalizationEnum;
import appeng.util.EnumCycler;

import gto_ae.hooks.gui.IIcon;

public class SettingToggleButton<T extends Enum<T>> extends IconButton {

    private static final Map<EnumPair<?>, ButtonAppearance> appearances = new HashMap<>();
    private static boolean appearancesInitialized = false;
    private final Setting<T> buttonSetting;
    private final IHandler<SettingToggleButton<T>> onPress;
    private final EnumSet<T> validValues;
    private T currentValue;

    @FunctionalInterface
    public interface IHandler<T extends SettingToggleButton<?>> {
        void handle(T button, boolean backwards);
    }

    public SettingToggleButton(Setting<T> setting, T val,
            IHandler<SettingToggleButton<T>> onPress) {
        this(setting, val, t -> true, onPress);
    }

    public SettingToggleButton(Setting<T> setting, T val, Predicate<T> isValidValue,
            IHandler<SettingToggleButton<T>> onPress) {
        super(SettingToggleButton::onPress);
        this.onPress = onPress;

        // Build a list of values (in order) that are valid w.r.t. the given predicate
        EnumSet<T> validValues = EnumSet.allOf(val.getDeclaringClass());
        validValues.removeIf(isValidValue.negate());
        validValues.removeIf(s -> !setting.getValues().contains(s));
        this.validValues = validValues;

        this.buttonSetting = setting;
        this.currentValue = val;

    }

    private static void onPress(Button btn) {
        if (btn instanceof SettingToggleButton) {
            ((SettingToggleButton<?>) btn).triggerPress();
        }
    }

    private void triggerPress() {
        boolean backwards = false;
        // This isn't great, but we don't get any information about right-clicks
        // otherwise
        Screen currentScreen = Minecraft.getInstance().screen;
        if (currentScreen instanceof AEBaseScreen) {
            backwards = ((AEBaseScreen<?>) currentScreen).isHandlingRightClick();
        }
        onPress.handle(this, backwards);
    }

    @Nullable
    private ButtonAppearance getApperance() {
        if (this.buttonSetting != null && this.currentValue != null) {
            return getAppearances().get(new EnumPair<>(this.buttonSetting, this.currentValue));
        }
        return null;
    }

    @Override
    protected @Nullable IIcon getIcon() {
        var app = getApperance();
        if (app != null && app.icon != null) {
            return app.icon;
        }
        return Icon.TOOLBAR_BUTTON_BACKGROUND;
    }

    @Override
    protected Item getItemOverlay() {
        var app = getApperance();
        if (app != null && app.item != null) {
            return app.item;
        }
        return null;
    }

    public Setting<T> getSetting() {
        return this.buttonSetting;
    }

    public T getCurrentValue() {
        return this.currentValue;
    }

    public void set(T e) {
        if (this.currentValue != e) {
            this.currentValue = e;
        }
    }

    public T getNextValue(boolean backwards) {
        return EnumCycler.rotateEnum(currentValue, backwards, validValues);
    }

    @Override
    public List<Component> getTooltipMessage() {

        if (this.buttonSetting == null || this.currentValue == null) {
            return Collections.emptyList();
        }

        var buttonAppearance = appearances.get(new EnumPair<>(this.buttonSetting, this.currentValue));
        if (buttonAppearance == null) {
            return Collections.singletonList(ButtonToolTips.NoSuchMessage.text());
        }

        return buttonAppearance.tooltipLines;
    }

    public static final class EnumPair<T extends Enum<T>> {

        final Setting<T> setting;
        final T value;

        public EnumPair(Setting<T> setting, T value) {
            this.setting = setting;
            this.value = value;
        }

        @Override
        public int hashCode() {
            return this.setting.hashCode() ^ this.value.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (this.getClass() != obj.getClass()) {
                return false;
            }
            final EnumPair<?> other = (EnumPair<?>) obj;
            return other.setting == this.setting && other.value == this.value;
        }
    }

    public record ButtonAppearance(@Nullable IIcon icon, @Nullable Item item, List<Component> tooltipLines) {
    }

    public static Map<EnumPair<?>, ButtonAppearance> getAppearances() {
        initSettings();
        return appearances;
    }

    public static <T extends Enum<T>> void registerApp(IIcon icon, Setting<T> setting, T val,
            LocalizationEnum title, Component... tooltipLines) {
        var lines = new ArrayList<Component>();
        lines.add(title.text());
        Collections.addAll(lines, tooltipLines);

        appearances.put(
                new EnumPair<>(setting, val),
                new ButtonAppearance(icon, null, lines));
    }

    public static <T extends Enum<T>> void registerApp(ItemLike item, Setting<T> setting, T val,
            LocalizationEnum title, Component... tooltipLines) {
        var lines = new ArrayList<Component>();
        lines.add(title.text());
        Collections.addAll(lines, tooltipLines);

        appearances.put(
                new EnumPair<>(setting, val),
                new ButtonAppearance(null, item.asItem(), lines));
    }

    public static <T extends Enum<T>> void registerApp(IIcon icon, Setting<T> setting, T val,
            LocalizationEnum title, LocalizationEnum hint) {
        registerApp(icon, setting, val, title, hint.text());
    }

    public static <T extends Enum<T>> void registerApp(IIcon icon, Setting<T> setting, T val, Component title,
            Component... tooltipLines) {
        var lines = new ArrayList<Component>();
        lines.add(title);
        Collections.addAll(lines, tooltipLines);

        appearances.put(
                new EnumPair<>(setting, val),
                new ButtonAppearance(icon, null, lines));
    }

    private static void initSettings() {
        if (!appearancesInitialized) {
            appearancesInitialized = true;
            for (Runnable r : deferredRegistrations) {
                r.run();
            }
            deferredRegistrations = null;
        }
    }

    public static void deferAppearanceRegistration(Runnable registration) {
        if (appearancesInitialized) {
            throw new IllegalStateException("Cannot register appearances after they have been frozen");
        }
        deferredRegistrations.add(registration);
    }

    private static List<Runnable> deferredRegistrations = new ArrayList<>();
}
