package com.gto.ae.client.gui.widgets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;

import appeng.client.gui.widgets.SettingToggleButton;

import com.gto.ae.api.config.ExtendedSettings;
import com.gto.ae.helpers.facility_management.WorkingStatus;

class ExpandableToggleButtonTest {
    @Test
    void cpuFilterOptionsStayWithinTheListAndFollowTheirAnchor() {
        withFilter((button, options, selected) -> {
            button.toggleCollapsed();
            // The scrollbar starts just to the right of the filter's left edge.
            for (var anchor : new int[][] { { -18, 6, -94, -16 }, { -12, 2, -88, -11 },
                    { 214, 122, 138, 216 } }) {
                button.setX(anchor[0]);
                button.setY(anchor[1]);
                button.relayout();
                assertEquals(3, options.size());
                assertEquals(button.getX(), options.getLast().getX() + 16);
                for (int i = 0; i < options.size(); i++) {
                    var option = options.get(i);
                    assertTrue(option.visible);
                    assertEquals(button.getX() - 56 + i * 20, option.getX());
                    assertEquals(button.getY() + 20, option.getY());
                    assertTrue(option.getX() >= anchor[2], "Options must stay inside the CPU list");
                    assertTrue(option.getX() + option.getWidth() < anchor[3], "Options must not cover the scrollbar");
                }
                var bounds = button.getExpandedBound();
                assertEquals(options.getFirst().getX(), bounds.getX());
                assertEquals(button.getY(), bounds.getY());
                assertEquals(72, bounds.getWidth());
                assertEquals(36, bounds.getHeight());
            }
            button.toggleCollapsed();
            options.forEach(option -> assertFalse(option.visible));
        });
    }

    @Test
    void selectingAnExpandedOptionUpdatesTheFilterAndCollapsesAllOptions() {
        withFilter((button, options, selected) -> {
            button.setX(-18);
            button.setY(6);
            button.toggleCollapsed();
            ((Button) options.get(1)).onPress();
            assertEquals(WorkingStatus.WORKING, selected.get());
            assertEquals(WorkingStatus.WORKING, button.getCurrentValue());
            assertTrue(button.isCollapsed());
            options.forEach(option -> assertFalse(option.visible));
        });
    }

    private static void withFilter(FilterCheck check) {
        var setting = ExtendedSettings.CPU_WORKING_STATUS_SETTING;
        var appearance = new SettingToggleButton.ButtonAppearance(null, null, List.of());
        Map<SettingToggleButton.EnumPair<?>, SettingToggleButton.ButtonAppearance> appearances = Map.of(
                new SettingToggleButton.EnumPair<>(setting, WorkingStatus.IDLE), appearance,
                new SettingToggleButton.EnumPair<>(setting, WorkingStatus.WORKING), appearance,
                new SettingToggleButton.EnumPair<>(setting, WorkingStatus.NONE), appearance);
        try (var settings = mockStatic(SettingToggleButton.class)) {
            settings.when(SettingToggleButton::getAppearances).thenReturn(appearances);
            var selected = new AtomicReference<WorkingStatus>();
            var button = new ExpandableToggleButton<>(setting, WorkingStatus.NONE,
                    (b, value) -> selected.set(value), ExpandableToggleButton.LayoutDirection.DOWN_LEFT);
            var options = new ArrayList<AbstractWidget>();
            button.populate(options::add, null, null);
            check.run(button, options, selected);
        }
    }

    @FunctionalInterface
    private interface FilterCheck {
        void run(ExpandableToggleButton<WorkingStatus> button, ArrayList<AbstractWidget> options,
                AtomicReference<WorkingStatus> selected);
    }
}
