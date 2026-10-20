package dev.emi.emi.jemi.impl;

import java.util.AbstractList;
import java.util.Collection;
import java.util.List;

import com.mojang.datafixers.util.Either;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiKeyMapping;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

public class JemiTooltipBuilder implements ITooltipBuilder {
    private final ObjectArrayList<Either<FormattedText, TooltipComponent>> lines = new ObjectArrayList<>();
    // JEI permits direct mutations of getLines(), so the EMI tooltip must be a live projection.
    public final List<ClientTooltipComponent> tooltip = new AbstractList<>() {
        @Override
        public ClientTooltipComponent get(int index) {
            return lines.get(index).map(text -> ClientTooltipComponent.create(Language.getInstance().getVisualOrder(text)),
                    ClientTooltipComponent::create);
        }

        @Override
        public int size() { return lines.size(); }
    };
    private ITypedIngredient<?> ingredient;

    @Override
    public void add(FormattedText component) { lines.add(Either.left(component)); }

    @Override
    public void addAll(Collection<? extends FormattedText> components) {
        for (var component : components) add(component);
    }

    @Override
    public void add(TooltipComponent component) { lines.add(Either.right(component)); }

    @Override
    public void addKeyUsageComponent(String translationKey, IJeiKeyMapping keyMapping) {
        if (!keyMapping.isUnbound()) add(Component.translatable(translationKey, keyMapping.getTranslatedKeyMessage()));
    }

    @Override
    public void setIngredient(ITypedIngredient<?> ingredient) { this.ingredient = ingredient; }

    @Override
    public void clearIngredient() { ingredient = null; }

    @Override
    public void clear() {
        lines.clear();
        clearIngredient();
    }

    @Override
    public List<Either<FormattedText, TooltipComponent>> getLines() { return lines; }

    @Override
    public List<Component> toLegacyToComponents() {
        var result = new ObjectArrayList<Component>(lines.size());
        for (var line : lines) {
            var text = line.left().orElse(null);
            if (text != null) result.add(text instanceof Component component ? component : Component.literal(text.getString()));
        }
        return result;
    }

    @Override
    public void removeAll(List<Component> components) {
        lines.removeIf(line -> components.contains(line.left().orElse(null)));
    }
}
