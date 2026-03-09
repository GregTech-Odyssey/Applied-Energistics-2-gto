package gto_ae.hooks.gui;

import java.util.function.Consumer;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;

import appeng.client.gui.AEBaseScreen;

public interface IPopulateScreenWidget {
    /**
     * Reinitializes a Vanilla screen and populates it with additional vanilla widgets.
     * <p/>
     * This is called initially when the screen is first shown, and called again everytime the screen is resized, as
     * Vanilla does it's positioning logic entirely in this method.
     *
     * @param bounds The bounding box of the screen in window coordinates.
     */
    default void populateScreen(Consumer<AbstractWidget> addWidget, Rect2i bounds, AEBaseScreen<?> screen) {
    }
}
