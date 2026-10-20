package dev.emi.emi.jemi.impl.extras;

import mezz.jei.api.gui.placement.IPlaceable;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;

public class JemiPlaceable<T extends IPlaceable<T>> implements IPlaceable<T> {
    public int x = 0, y = 0;
    public int width, height;

    public JemiPlaceable(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public T setPosition(int xPos, int yPos) {
        this.x = xPos;
        this.y = yPos;
        return (T) this;
    }

    @Override
    public T setPosition(int areaX, int areaY, int areaWidth, int areaHeight,
            HorizontalAlignment horizontalAlignment, VerticalAlignment verticalAlignment) {
        return setPosition(areaX + horizontalAlignment.getXPos(areaWidth, width),
                areaY + verticalAlignment.getYPos(areaHeight, height));
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }
}
