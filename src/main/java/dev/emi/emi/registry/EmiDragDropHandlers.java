package dev.emi.emi.registry;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

public class EmiDragDropHandlers {
    public static Map<Class<?>, List<EmiDragDropHandler<?>>> fromClass = new Reference2ObjectOpenHashMap<>();
    public static List<EmiDragDropHandler<?>> generic = Lists.newArrayList();

    public static void clear() {
        fromClass.clear();
        generic.clear();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void render(Screen screen, EmiIngredient stack, GuiGraphics draw, int mouseX, int mouseY,
            float delta) {
        var registered = fromClass.get(screen.getClass());
        if (registered != null) {
            for (EmiDragDropHandler handler : registered) {
                handler.render(screen, stack, draw, mouseX, mouseY, delta);
            }
        }
        for (EmiDragDropHandler handler : generic) {
            handler.render(screen, stack, draw, mouseX, mouseY, delta);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static boolean dropStack(Screen screen, EmiIngredient stack, int x, int y) {
        var registered = fromClass.get(screen.getClass());
        if (registered != null) {
            for (EmiDragDropHandler handler : registered) {
                if (handler.dropStack(screen, stack, x, y)) {
                    return true;
                }
            }
        }
        for (EmiDragDropHandler handler : generic) {
            if (handler.dropStack(screen, stack, x, y)) {
                return true;
            }
        }
        return false;
    }
}
