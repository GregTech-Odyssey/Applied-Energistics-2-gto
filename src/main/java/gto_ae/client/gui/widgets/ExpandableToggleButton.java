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

package gto_ae.client.gui.widgets;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import appeng.api.config.*;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.core.localization.ButtonToolTips;
import appeng.core.sync.network.NetworkHandler;

import gto_ae.client.gui.widgets.expandable.ExpandableGroup;
import gto_ae.client.gui.widgets.expandable.IExpandable;
import gto_ae.core.localization.ExtendedLangs;
import gto_ae.core.sync.packets.ConfigButtonDirectPacket;
import gto_ae.hooks.gui.IIcon;
import gto_ae.hooks.gui.IPopulateScreenWidget;

public class ExpandableToggleButton<T extends Enum<T>> extends IconButton
        implements IExpandable, IPopulateScreenWidget {
    private static final int LAYOUT_SPACING = 4;

    private final Setting<T> buttonSetting;
    private final IHandler<T, ExpandableToggleButton<T>> onPress;
    private T currentValue;
    private List<Component> currentSubButtonTooltip = Collections.emptyList();
    private final LayoutDirection layoutDirection;

    private boolean collapsed = true;
    private final List<SubButton> expandedButtons = new ArrayList<>();
    private ExpandableGroup group = null;

    @Override
    public void toggleCollapsed() {
        toggleCollapsed(this);
    }

    @Override
    public boolean isCollapsed() {
        return collapsed;
    }

    @Override
    public void setGroup(ExpandableGroup group) {
        this.group = group;
    }

    @Override
    public ExpandableGroup getGroup() {
        return group;
    }

    @Override
    public void populate(Consumer<AbstractWidget> addWidget, Rect2i bounds, AEBaseScreen<?> screen) {
        for (var b : expandedButtons) {
            addWidget.accept(b);
        }
    }

    @FunctionalInterface
    public interface IHandler<E extends Enum<E>, T extends ExpandableToggleButton<E>> {
        void handle(T button, E newValue);
    }

    public static <T extends Enum<T>> void sendToServer(ExpandableToggleButton<T> button, T value) {
        NetworkHandler.instance().sendToServer(new ConfigButtonDirectPacket<>(button.getSetting(), value));
    }

    public ExpandableToggleButton(Setting<T> setting, T val,
            IHandler<T, ExpandableToggleButton<T>> onPress, LayoutDirection layoutDirection) {
        this(setting, val, t -> true, onPress, layoutDirection);
    }

    public ExpandableToggleButton(Setting<T> setting, T val, Predicate<T> isValidValue,
            IHandler<T, ExpandableToggleButton<T>> onPress, LayoutDirection layoutDirection) {
        super(ExpandableToggleButton::toggleCollapsed);
        this.onPress = onPress;
        this.layoutDirection = layoutDirection;

        this.buttonSetting = setting;
        setCurrentValue(val);

        // Build a list of values (in order) that are valid w.r.t. the given predicate
        EnumSet<T> validValues = EnumSet.allOf(val.getDeclaringClass());
        validValues.removeIf(isValidValue.negate());
        validValues.removeIf(s -> !setting.getValues().contains(s));
        for (T v : validValues) {
            var subBtn = new SubButton(v);
            subBtn.visible = false;
            this.expandedButtons.add(subBtn);
        }

    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
        relayout();
        super.renderWidget(guiGraphics, mouseX, mouseY, partial);
    }

    public void setCurrentValue(T currentValue) {
        this.currentValue = currentValue;
        ExpandableToggleButton.this.currentSubButtonTooltip = SettingToggleButton.getAppearances()
                .get(new SettingToggleButton.EnumPair<>(buttonSetting, currentValue)).tooltipLines();
    }

    private void onPress(T value) {
        setCurrentValue(value);
        this.onPress.handle(this, value);
        toggleCollapsed(this);
    }

    private static void toggleCollapsed(Button btn) {
        if (!(btn instanceof ExpandableToggleButton<?> btnExpandable)) {
            return;
        }
        btnExpandable.collapsed = !btnExpandable.collapsed;
        btnExpandable.relayout();
        if (btnExpandable.collapsed) {
            btnExpandable.onCollapse();
            return;
        }
        btnExpandable.onExpand();
    }

    public void relayout() {
        if (this.collapsed) {
            for (var b : this.expandedButtons) {
                b.visible = false;
            }
            return;
        }
        var candidateCount = this.expandedButtons.size();
        var halfDistance = (candidateCount * 16 + (candidateCount - 1) * LAYOUT_SPACING) / 2 - 8;
        for (int i = 0; i < candidateCount; i++) {
            var b = this.expandedButtons.get(i);
            switch (this.layoutDirection) {
                case LEFT -> {
                    b.setX(this.getX() - LAYOUT_SPACING - b.getWidth());
                    b.setY(this.getY() + this.getHeight() / 2 - halfDistance
                            + i * (b.getHeight() + LAYOUT_SPACING));
                }
                case RIGHT -> {
                    b.setX(this.getX() + this.getWidth() + LAYOUT_SPACING);
                    b.setY(this.getY() + this.getHeight() / 2 - halfDistance
                            + i * (b.getHeight() + LAYOUT_SPACING));
                }
                case UP -> {
                    b.setY(this.getY() - LAYOUT_SPACING - b.getHeight());
                    b.setX(this.getX() + this.getWidth() / 2 - halfDistance
                            + i * (b.getWidth() + LAYOUT_SPACING));
                }
                case DOWN -> {
                    b.setY(this.getY() + this.getHeight() + LAYOUT_SPACING);
                    b.setX(this.getX() + this.getWidth() / 2 - halfDistance
                            + i * (b.getWidth() + LAYOUT_SPACING));
                }
            }
            b.visible = true;
        }
    }

    @Nullable
    private SettingToggleButton.ButtonAppearance getAppearance() {
        if (this.buttonSetting != null && this.currentValue != null) {
            return SettingToggleButton.getAppearances()
                    .get(new SettingToggleButton.EnumPair<>(this.buttonSetting, this.currentValue));
        }
        return null;
    }

    @Override
    protected @Nullable IIcon getIIcon() {
        var app = getAppearance();
        if (app != null && app.icon() != null) {
            return app.icon();
        }
        return Icon.TOOLBAR_BUTTON_BACKGROUND;
    }

    @Override
    protected Item getItemOverlay() {
        var app = getAppearance();
        if (app != null && app.item() != null) {
            return app.item();
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
            setCurrentValue(e);
        }
    }

    @Override
    public List<Component> getTooltipMessage() {
        var list = new ArrayList<Component>();
        list.add(collapsed ? ExtendedLangs.ExpandToggleButton.text() : ExtendedLangs.CollapseToggleButton.text());
        list.addAll(currentSubButtonTooltip);
        return list;
    }

    private class SubButton extends IconButton {
        private final SettingToggleButton.ButtonAppearance app;

        public SubButton(T value) {
            super(b -> ExpandableToggleButton.this.onPress(value));
            this.app = SettingToggleButton.getAppearances()
                    .get(new SettingToggleButton.EnumPair<>(buttonSetting, value));
        }

        @Override
        protected @Nullable IIcon getIIcon() {
            if (app != null && app.icon() != null) {
                return app.icon();
            }
            return Icon.TOOLBAR_BUTTON_BACKGROUND;
        }

        @Override
        protected Item getItemOverlay() {
            if (app != null && app.item() != null) {
                return app.item();
            }
            return null;
        }

        @Override
        public List<Component> getTooltipMessage() {
            if (app == null) {
                return Collections.singletonList(ButtonToolTips.NoSuchMessage.text());
            }

            return app.tooltipLines();
        }
    }

    public enum LayoutDirection {
        UP, DOWN, LEFT, RIGHT
    }

}
