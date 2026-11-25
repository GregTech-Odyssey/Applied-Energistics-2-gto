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

package appeng.client.gui.widgets;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.Rects;
import appeng.client.gui.style.Blitter;
import appeng.core.AppEng;

import com.gto.ae.client.gui.widgets.expandable.IExpandable;

/**
 * A stacked button panel on the left or right side of our UIs.
 */
public class VerticalButtonBar implements ICompositeWidget {

    private static final ResourceLocation VERTICAL_BUTTONS_BG = AppEng
            .makeId("textures/gui/sprites/vertical_buttons_bg.png");

    // Vertical space between buttons
    private static final int VERTICAL_SPACING = 6;

    // The margin between the right side of the buttons and the GUI
    private static final int MARGIN = 2;

    public final List<Button> buttons = new ArrayList<>();

    // The origin of the last initialized screen in window coordinates
    private Point screenOrigin = Point.ZERO;
    // This bounding rectangle relative to the screens origin
    private Rect2i bounds = Rects.ZERO;

    private Point position;

    public VerticalButtonBar() {
    }

    public void add(Button button) {
        buttons.add(button);
    }

    @Override
    public void setPosition(Point position) {
        this.position = position;
    }

    @Override
    public void setSize(int width, int height) {
        // Setting the size for this control is not supported
    }

    @Override
    public Rect2i getBounds() {
        return bounds;
    }

    /**
     * We need to update every frame because buttons can become visible/invisible at any point in time.
     */
    @Override
    public void updateBeforeRender() {
        int currentY = position.getY() + MARGIN;
        int maxWidth = 0;

        // Align the button's right edge with the UI and account for margin
        for (Button button : buttons) {
            if (!button.visible) {
                continue;
            }

            // Vanilla widgets need to be in window space
            button.setX(screenOrigin.getX() + position.getX() - MARGIN - button.getWidth());
            button.setY(screenOrigin.getY() + currentY);

            currentY += button.getHeight() + VERTICAL_SPACING;
            maxWidth = Math.max(button.getWidth(), maxWidth);
        }

        // Set up a bounding rectangle for JEI exclusion zones
        if (maxWidth == 0) {
            bounds = Rects.ZERO;
        } else {
            int boundX = position.getX() - maxWidth - 2 * MARGIN;
            int boundY = position.getY();
            bounds = new Rect2i(
                    boundX,
                    boundY,
                    maxWidth + 2 * MARGIN,
                    currentY - boundY);
        }
    }

    @Override
    public void addExclusionZones(List<Rect2i> exclusionZones, Rect2i screenBounds) {
        ICompositeWidget.super.addExclusionZones(exclusionZones, screenBounds);
        for (var button : buttons) {
            if (button instanceof IExpandable expandable) {
                if (!expandable.isCollapsed()) {
                    exclusionZones
                            .add(expandable.getExpandedBound());
                }
            }
        }
    }

    /**
     * Called when the parent UI is repositioned or resized. All buttons need to be re-added since Vanilla clears the
     * widgets when this happens.
     */
    @Override
    public void populateScreen(Consumer<AbstractWidget> addWidget, Rect2i bounds, AEBaseScreen<?> screen) {
        this.screenOrigin = Point.fromTopLeft(bounds);
        for (var button : this.buttons) {
            if (button.isFocused()) {
                button.setFocused(false);
            }
            addWidget.accept(button);
        }
    }

    @Override
    public void drawBackgroundLayer(GuiGraphics guiGraphics, Rect2i bounds, Point mouse) {
        if (this.bounds.getWidth() <= 0 || this.bounds.getHeight() <= 0) {
            return;
        }
        Blitter.blitNineSliced(
                guiGraphics,
                VERTICAL_BUTTONS_BG,
                bounds.getX() + this.bounds.getX() - 2,
                bounds.getY() + this.bounds.getY() - 1,
                1,
                this.bounds.getWidth() + 1,
                this.bounds.getHeight() + 4,
                0, 2, 0, 4,
                21, 26);
    }
}
