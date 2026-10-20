package dev.emi.emi.jemi.impl;

import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawablesView;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public class JemiRecipeSlotDrawablesView implements IRecipeSlotDrawablesView {

    @Override
    public @Unmodifiable List<IRecipeSlotDrawable> getSlots() {
        return List.of();
    }
}
