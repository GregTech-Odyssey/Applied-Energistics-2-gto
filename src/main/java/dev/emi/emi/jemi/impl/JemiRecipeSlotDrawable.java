package dev.emi.emi.jemi.impl;

import com.google.common.collect.Lists;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.jemi.impl.JemiIngredientConsumer;
import dev.emi.emi.jemi.widget.JemiSlotWidget;
import mezz.jei.api.gui.builder.IIngredientConsumer;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.TagKey;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.runtime.EmiDrawContext;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class JemiRecipeSlotDrawable implements IRecipeSlotDrawable {
    public JemiSlotWidget widget;
    public List<IIngredientConsumer> overrides = Lists.newArrayList();

    @Override
    public List<ITypedIngredient<?>> getAllIngredientsList() { return widget.slot.getAllIngredientsList(); }

    @Override
    public Stream<ITypedIngredient<?>> getDisplayedIngredients() { return widget.slot.getDisplayedIngredients(); }

    @Override
    public Optional<TagKey<?>> getTagKey() { return widget.slot.getTagKey(); }

    @Override
    public Stream<ITypedIngredient<?>> getAllIngredients() {
        return widget.slot.getAllIngredients();
    }

    @Override
    public Optional<ITypedIngredient<?>> getDisplayedIngredient() {
        return widget.slot.getDisplayedIngredient();
    }

    @Override
    public RecipeIngredientRole getRole() {
        return widget.slot.getRole();
    }

    @Override
    public void drawHighlight(GuiGraphics raw, int color) {
        widget.slot.drawHighlight(raw, color);
    }

    @Override
    public Optional<String> getSlotName() {
        return widget.slot.getSlotName();
    }

    @Override
    public void draw(GuiGraphics raw) {
        // I don't think I will
    }

    @Override
    public void drawHoverOverlays(GuiGraphics raw) {
        // I don't think I will
    }

    @Override
    public List<Component> getTooltip() {
        var builder = new JemiTooltipBuilder();
        getTooltip(builder);
        return builder.toLegacyToComponents();
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltipBuilder) {
        var alternatives = widget.slot.stack.getEmiStacks();
        if (alternatives.isEmpty()) return;
        var text = new ObjectArrayList<Component>(alternatives.getFirst().getTooltipText());
        if (widget.slot.tooltipCallback != null) widget.slot.tooltipCallback.onTooltip(widget.slot, text);
        tooltipBuilder.addAll(text);
        if (widget.slot.richTooltipCallback != null) widget.slot.richTooltipCallback.onRichTooltip(widget.slot, tooltipBuilder);
    }

    @Override
    public void drawTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        EmiRenderHelper.drawTooltip(Minecraft.getInstance().screen, EmiDrawContext.wrap(graphics),
                widget.getTooltip(mouseX, mouseY), mouseX, mouseY);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return widget.getBounds().contains((int) mouseX, (int) mouseY);
    }

    @Override
    public void setPosition(int x, int y) {
        // Nope
    }

    @Override
    public IIngredientConsumer createDisplayOverrides() {
        // "Implemented" but also just ignored
        JemiIngredientConsumer consumer = new JemiIngredientConsumer();
        overrides.add(consumer);
        return consumer;
    }

    @Override
    public void clearDisplayOverrides() {
        overrides.clear();
    }

    @Override
    public Rect2i getRect() {
        Bounds bounds = widget.getBounds();
        return new Rect2i(bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    @Override
    public Rect2i getAreaIncludingBackground() {
        return getRect();
    }
}
