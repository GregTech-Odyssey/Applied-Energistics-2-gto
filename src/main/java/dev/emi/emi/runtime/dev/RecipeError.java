package dev.emi.emi.runtime.dev;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

import java.util.List;

public record RecipeError(Severity severity, List<ClientTooltipComponent> tooltip) {
    
    public static enum Severity {
        ERROR,
        WARNING
    }
}
