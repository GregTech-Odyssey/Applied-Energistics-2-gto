package dev.emi.emi.jemi.impl;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class JemiRecipeSlotsView implements IRecipeSlotsView {
    private final ObjectArrayList<IRecipeSlotView> slots;

    public JemiRecipeSlotsView(List<JemiRecipeSlot> slots) {
        this.slots = new ObjectArrayList<>(slots);
    }

    @Override
    public List<IRecipeSlotView> getSlotViews() {
        return slots;
    }

    @Override
    public List<IRecipeSlotView> getSlotViews(RecipeIngredientRole role) {
        var result = new ObjectArrayList<IRecipeSlotView>(slots.size());
        for (var slot : slots)
            if (slot.getRole() == role)
                result.add(slot);
        return result;
    }

    @Override
    public Optional<IRecipeSlotView> findSlotByName(String slotName) {
        for (IRecipeSlotView slot : slots) {
            if (Objects.equals(slot.getSlotName().orElse(null), slotName)) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }
}
