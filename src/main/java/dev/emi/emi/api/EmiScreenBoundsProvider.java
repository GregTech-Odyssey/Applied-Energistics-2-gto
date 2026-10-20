package dev.emi.emi.api;

import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface EmiScreenBoundsProvider<T extends Screen> {

    /**
     * Provides the bounds for a given screen.
     * Return null or Bounds.EMPTY if this provider does not handle the screen.
     *
     * @param screen The screen to get bounds for
     * @return The bounds for the screen, or null if not handled
     */
    @Nullable
    Bounds getBounds(T screen);
}
