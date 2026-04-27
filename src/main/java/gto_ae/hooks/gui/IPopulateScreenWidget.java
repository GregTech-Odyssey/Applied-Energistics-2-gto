package gto_ae.hooks.gui;

import java.util.function.Consumer;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;

import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.Rects;

@FunctionalInterface
public interface IPopulateScreenWidget extends ICompositeWidget {
    @Override
    default void setPosition(Point position) {
    }

    @Override
    default void setSize(int width, int height) {
    }

    @Override
    default Rect2i getBounds() {
        return Rects.ZERO;
    }

    /**
     * Reinitializes a Vanilla screen and populates it with additional vanilla widgets.
     * <p/>
     * This is called initially when the screen is first shown, and called again everytime the screen is resized, as
     * Vanilla does it's positioning logic entirely in this method.
     *
     * @param bounds The bounding box of the screen in window coordinates.
     */
    default void populateScreen(Consumer<AbstractWidget> addWidget, Rect2i bounds, AEBaseScreen<?> screen) {
        populate(addWidget, bounds, screen);
    }

    void populate(Consumer<AbstractWidget> addWidget, Rect2i bounds, AEBaseScreen<?> screen);
}
