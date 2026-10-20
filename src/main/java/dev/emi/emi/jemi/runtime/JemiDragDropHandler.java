package dev.emi.emi.jemi.runtime;

import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.jemi.JemiPlugin;
import dev.emi.emi.jemi.JemiUtil;
import dev.emi.emi.runtime.EmiDrawContext;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class JemiDragDropHandler implements EmiDragDropHandler<Screen> {

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean dropStack(Screen screen, EmiIngredient stack, int x, int y) {
        try {
            return this.<Object>drop(screen, (Optional<ITypedIngredient<Object>>) (Optional) JemiUtil.getTyped(stack.getEmiStacks().get(0)), x, y);
        } catch (Exception e) {
            return false;
        }
    }


    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean dropStacks(Screen screen, List<? extends EmiIngredient> stacks, int x, int y) {
        if (stacks == null || stacks.isEmpty()) {
            return false;
        }

        boolean insideGhostTarget = false;
        for (EmiIngredient stack : stacks) {
            if (stack == null || stack.isEmpty() || stack.getEmiStacks().isEmpty()) {
                continue;
            }
            try {
                Optional<ITypedIngredient<Object>> typed = (Optional) JemiUtil.getTyped(stack.getEmiStacks().get(0));
                if (typed.isEmpty()) {
                    continue;
                }
                for (IGhostIngredientHandler.Target<Object> target : getTargets(screen, typed.get())) {
                    if (target.getArea().contains(x, y)) {
                        insideGhostTarget = true;
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
            if (insideGhostTarget) {
                break;
            }
        }
        if (!insideGhostTarget) {
            return false;
        }

        Set<String> usedAreas = new HashSet<>();
        boolean placed = false;
        for (EmiIngredient stack : stacks) {
            if (stack == null || stack.isEmpty() || stack.getEmiStacks().isEmpty()) {
                continue;
            }
            try {
                Optional<ITypedIngredient<Object>> typed = (Optional) JemiUtil.getTyped(stack.getEmiStacks().get(0));
                if (typed.isEmpty()) {
                    continue;
                }
                List<IGhostIngredientHandler.Target<Object>> targets = new java.util.ArrayList<>(getTargets(screen, typed.get()));
                targets.sort((a, b) -> {
                    Rect2i ar = a.getArea();
                    Rect2i br = b.getArea();
                    int row = Integer.compare(ar.getY(), br.getY());
                    return row != 0 ? row : Integer.compare(ar.getX(), br.getX());
                });
                for (IGhostIngredientHandler.Target<Object> target : targets) {
                    Rect2i area = target.getArea();
                    String key = area.getX() + ":" + area.getY() + ":" + area.getWidth() + ":" + area.getHeight();
                    if (!usedAreas.add(key)) {
                        continue;
                    }
                    target.accept(typed.get().getIngredient());
                    placed = true;
                    break;
                }
            } catch (Throwable ignored) {
            }
        }
        return placed;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void render(Screen screen, EmiIngredient dragged, GuiGraphics raw, int mouseX, int mouseY, float delta) {
        try {
            this.<Object>render(screen, EmiDrawContext.wrap(raw), (Optional<ITypedIngredient<Object>>) (Optional) JemiUtil.getTyped(dragged.getEmiStacks().get(0)));
        } catch (Exception e) {
        }
    }

    private <I> boolean drop(Screen screen, Optional<ITypedIngredient<I>> optional, int x, int y) {
        if (optional.isPresent()) {
            for (IGhostIngredientHandler.Target<I> target : getTargets(screen, optional.get())) {
                if (target.getArea().contains(x, y)) {
                    target.accept(optional.get().getIngredient());
                    return true;
                }
            }
        }
        return false;
    }

    private <I> void render(Screen screen, EmiDrawContext context, Optional<ITypedIngredient<I>> optional) {
        if (optional.isPresent()) {
            for (IGhostIngredientHandler.Target<I> target : getTargets(screen, optional.get())) {
                Rect2i r = target.getArea();
                context.fill(r.getX(), r.getY(), r.getWidth(), r.getHeight(), 0x8822BB33);
            }
        }
    }

    private <I> List<IGhostIngredientHandler.Target<I>> getTargets(Screen screen, ITypedIngredient<I> typed) {
        Optional<IGhostIngredientHandler<Screen>> optGhost = JemiPlugin.runtime.getScreenHelper().getGhostIngredientHandler(screen);
        if (optGhost.isPresent()) {
            IGhostIngredientHandler<Screen> ghost = optGhost.get();
            return ghost.getTargetsTyped(screen, typed, false);
        }
        return List.of();
    }
}
