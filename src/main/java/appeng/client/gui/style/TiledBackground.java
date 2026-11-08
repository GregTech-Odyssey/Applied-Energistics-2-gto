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

package appeng.client.gui.style;

import net.minecraft.client.gui.GuiGraphics;

/**
 * A dialog background split into a fixed header, a repeating row and a fixed footer, so the dialog can be drawn with an
 * arbitrary number of rows instead of being stretched.
 * <p>
 * The source texture is expected to show {@code baseRows} rows between its header and footer. The first and the last
 * row are sliced separately: resource packs commonly paint an inner shadow along the top of the first row, and the last
 * row carries the separator closing the row area. Slicing every row from the first one would repeat that shadow on
 * every row of the drawn dialog.
 */
public final class TiledBackground {

    private final Blitter header;
    private final Blitter firstRow;
    private final Blitter row;
    private final Blitter lastRow;
    private final Blitter footer;

    /**
     * @param background   The full dialog background. Its source height also determines the footer height, so a
     *                     resource pack that draws a taller footer is honoured.
     * @param headerHeight Height of the fixed part above the row area.
     * @param rowHeight    Height of a single row in the source texture.
     * @param baseRows     Number of rows the source texture shows between header and footer.
     */
    public TiledBackground(Blitter background, int headerHeight, int rowHeight, int baseRows) {
        var width = background.getSrcWidth();
        var footerHeight = background.getSrcHeight() - headerHeight - baseRows * rowHeight;

        this.header = background.copy().src(0, 0, width, headerHeight);
        this.firstRow = background.copy().src(0, headerHeight, width, rowHeight);
        this.row = background.copy().src(0, headerHeight + rowHeight, width, rowHeight);
        this.lastRow = background.copy().src(0, headerHeight + (baseRows - 1) * rowHeight, width, rowHeight);
        this.footer = background.copy().src(0, headerHeight + baseRows * rowHeight, width, footerHeight);
    }

    /**
     * The height this background occupies when drawn with the given number of rows.
     */
    public int getHeight(int rows) {
        return this.header.getSrcHeight() + rows * this.row.getSrcHeight() + this.footer.getSrcHeight();
    }

    /**
     * The height of the fixed part drawn below the rows.
     */
    public int getFooterHeight() {
        return this.footer.getSrcHeight();
    }

    /**
     * Draws the background at the given position, repeating rows as needed.
     */
    public void draw(GuiGraphics guiGraphics, int x, int y, int rows) {
        this.header.dest(x, y).blit(guiGraphics);

        var rowHeight = this.row.getSrcHeight();
        var rowY = y + this.header.getSrcHeight();
        this.firstRow.dest(x, rowY).blit(guiGraphics);
        rowY += rowHeight;

        for (var i = 1; i < rows - 1; i++) {
            this.row.dest(x, rowY).blit(guiGraphics);
            rowY += rowHeight;
        }

        // A single-row dialog has no last row of its own; its only row is the first one, which already ends in a
        // separator.
        if (rows > 1) {
            this.lastRow.dest(x, rowY).blit(guiGraphics);
            rowY += rowHeight;
        }

        this.footer.dest(x, rowY).blit(guiGraphics);
    }
}
