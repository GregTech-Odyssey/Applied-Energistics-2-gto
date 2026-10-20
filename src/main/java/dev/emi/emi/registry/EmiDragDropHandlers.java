package dev.emi.emi.registry;

import com.google.common.collect.Lists;
import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import appeng.mixins.emi.accessor.HandledScreenAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.Map;
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
    public static boolean dropStacks(Screen screen, List<? extends EmiIngredient> stacks, int x, int y) {
        if (stacks == null || stacks.isEmpty()) {
            return false;
        }
        if (dropStacksToCellWorkbench(screen, stacks, x, y)) {
            return true;
        }
        if (dropStacksToAe2StorageBus(screen, stacks, x, y)) {
            return true;
        }
        if (dropStacksToAe2PatternInputs(screen, stacks, x, y)) {
            return true;
        }
        var registered = fromClass.get(screen.getClass());
        if (registered != null) {
            for (EmiDragDropHandler handler : registered) {
                if (handler.dropStacks(screen, stacks, x, y)) {
                    return true;
                }
            }
        }
        for (EmiDragDropHandler handler : generic) {
            if (handler.dropStacks(screen, stacks, x, y)) {
                return true;
            }
        }
        return dropStacksThroughSingleHandlers(screen, stacks, x, y);
    }

    private static boolean dropStacksThroughSingleHandlers(Screen screen, List<? extends EmiIngredient> stacks, int x,
            int y) {
        if (!(screen instanceof AbstractContainerScreen<?> handled)) {
            return false;
        }
        EmiIngredient first = null;
        int firstIndex = -1;
        for (int i = 0; i < stacks.size(); i++) {
            EmiIngredient stack = stacks.get(i);
            if (stack != null && !stack.isEmpty()) {
                first = stack;
                firstIndex = i;
                break;
            }
        }
        if (first == null || !dropStack(screen, first, x, y)) {
            return false;
        }

        HandledScreenAccessor accessor = (HandledScreenAccessor) handled;
        int left = accessor.getX();
        int top = accessor.getY();
        List<Slot> targets = Lists.newArrayList(handled.getMenu().slots);
        targets.sort((a, b) -> {
            int yCompare = Integer.compare(a.y, b.y);
            return yCompare != 0 ? yCompare : Integer.compare(a.x, b.x);
        });

        Slot hovered = null;
        for (Slot slot : targets) {
            int sx = left + slot.x;
            int sy = top + slot.y;
            if (x >= sx && x < sx + 16 && y >= sy && y < sy + 16) {
                hovered = slot;
                break;
            }
        }

        int hoveredIndex = hovered == null ? -1 : targets.indexOf(hovered);
        boolean[] used = new boolean[targets.size()];
        if (hoveredIndex >= 0) {
            used[hoveredIndex] = true;
        }
        int scanStart = hoveredIndex < 0 ? 0 : (hoveredIndex + 1) % Math.max(1, targets.size());
        for (int i = firstIndex + 1; i < stacks.size(); i++) {
            EmiIngredient stack = stacks.get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            boolean placed = false;
            for (int checked = 0; checked < targets.size(); checked++) {
                int index = (scanStart + checked) % targets.size();
                if (used[index]) {
                    continue;
                }
                Slot slot = targets.get(index);
                if (dropStack(screen, stack, left + slot.x + 8, top + slot.y + 8)) {
                    used[index] = true;
                    scanStart = (index + 1) % targets.size();
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                break;
            }
        }
        return true;
    }

    private static boolean dropStacksToAe2PatternInputs(Screen screen, List<? extends EmiIngredient> stacks, int x,
            int y) {
        if (!(screen instanceof AbstractContainerScreen<?> handled)) {
            return false;
        }

        Object menu = handled.getMenu();
        List<Slot> inputs = getAe2PatternSlots(menu, "getProcessingInputSlots");
        List<Slot> outputs = getAe2PatternSlots(menu, "getProcessingOutputSlots");
        if (inputs.isEmpty() || outputs.isEmpty()) {
            return false;
        }

        Boolean processingMode = isAe2ProcessingPatternMode(menu);
        if (Boolean.FALSE.equals(processingMode)) {
            return false;
        }

        inputs.removeIf(slot -> !isAe2PatternSlotEnabled(slot));
        outputs.removeIf(slot -> !isAe2PatternSlotEnabled(slot));
        if (inputs.isEmpty()) {
            return false;
        }
        if (processingMode == null && outputs.isEmpty()) {
            return false;
        }

        HandledScreenAccessor accessor = (HandledScreenAccessor) handled;
        int left = accessor.getX();
        int top = accessor.getY();
        List<Slot> patternSlots = Lists.newArrayList(inputs);
        patternSlots.addAll(outputs);

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Slot slot : patternSlots) {
            minX = Math.min(minX, slot.x);
            minY = Math.min(minY, slot.y);
            maxX = Math.max(maxX, slot.x + 16);
            maxY = Math.max(maxY, slot.y + 16);
        }
        if (x < left + minX || x >= left + maxX || y < top + minY || y >= top + maxY) {
            return false;
        }

        inputs.sort((a, b) -> {
            int yCompare = Integer.compare(a.y, b.y);
            return yCompare != 0 ? yCompare : Integer.compare(a.x, b.x);
        });

        int target = 0;
        for (EmiIngredient ingredient : stacks) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            while (target < inputs.size()) {
                Slot slot = inputs.get(target++);
                if (!slot.getItem().isEmpty()) {
                    continue;
                }
                if (dropStack(screen, ingredient, left + slot.x + 8, top + slot.y + 8)) {
                    break;
                }
            }
            if (target >= inputs.size()) {
                break;
            }
        }

        return true;
    }

    private static List<Slot> getAe2PatternSlots(Object menu, String methodName) {
        List<Slot> result = Lists.newArrayList();
        try {
            Object value = menu.getClass().getMethod(methodName).invoke(menu);
            if (value instanceof Object[] array) {
                for (Object element : array) {
                    if (element instanceof Slot slot) {
                        result.add(slot);
                    }
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return result;
    }

    private static Boolean isAe2ProcessingPatternMode(Object menu) {
        try {
            Object mode = menu.getClass().getMethod("getMode").invoke(menu);
            if (mode instanceof Enum<?> enumMode) {
                return "PROCESSING".equals(enumMode.name());
            }
            if (mode != null) {
                return "PROCESSING".equalsIgnoreCase(mode.toString());
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return null;
    }

    private static boolean isAe2PatternSlotEnabled(Slot slot) {
        try {
            Object enabled = slot.getClass().getMethod("isSlotEnabled").invoke(slot);
            if (enabled instanceof Boolean b) {
                return b;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return slot.isActive();
    }

    private static boolean dropStacksToAe2StorageBus(Screen screen, List<? extends EmiIngredient> stacks, int x,
            int y) {
        if (!(screen instanceof AbstractContainerScreen<?> handled) || !isAe2StorageBus(handled)) {
            return false;
        }
        HandledScreenAccessor accessor = (HandledScreenAccessor) handled;
        int left = accessor.getX();
        int top = accessor.getY();
        List<Slot> targets = Lists.newArrayList();
        for (Slot slot : handled.getMenu().slots) {
            if (isAe2FilterSlotEnabled(slot) && hasAe2FilterSetter(slot)) {
                targets.add(slot);
            }
        }
        if (targets.isEmpty()) {
            return false;
        }
        targets.sort((a, b) -> {
            int yCompare = Integer.compare(a.y, b.y);
            return yCompare != 0 ? yCompare : Integer.compare(a.x, b.x);
        });
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Slot slot : targets) {
            minX = Math.min(minX, slot.x - 1);
            minY = Math.min(minY, slot.y - 1);
            maxX = Math.max(maxX, slot.x + 17);
            maxY = Math.max(maxY, slot.y + 17);
        }
        if (x < left + minX || x >= left + maxX || y < top + minY || y >= top + maxY) {
            return false;
        }

        int target = 0;
        boolean placed = false;
        for (EmiIngredient ingredient : stacks) {
            if (ingredient == null || ingredient.isEmpty() || ingredient.getEmiStacks().isEmpty()) {
                continue;
            }
            ItemStack itemStack = toAe2FilterStack(ingredient);
            if (itemStack.isEmpty()) {
                continue;
            }
            while (target < targets.size() && !targets.get(target).getItem().isEmpty()) {
                target++;
            }
            while (target < targets.size()) {
                Slot slot = targets.get(target++);
                if (setAe2Filter(slot, itemStack)) {
                    placed = true;
                    break;
                }
            }
            if (target >= targets.size() && !placed) {
                break;
            }
        }
        return placed;
    }

    private static ItemStack toAe2FilterStack(EmiIngredient ingredient) {
        if (ingredient == null || ingredient.isEmpty() || ingredient.getEmiStacks().isEmpty()) {
            return ItemStack.EMPTY;
        }
        var emiStack = ingredient.getEmiStacks().get(0);
        ItemStack itemStack = emiStack.getItemStack();
        if (!itemStack.isEmpty()) {
            itemStack = itemStack.copy();
            itemStack.setCount(1);
            return itemStack;
        }

        Object fluid = emiStack.getKey();
        if (fluid == null) {
            return ItemStack.EMPTY;
        }
        try {
            Class<?> fluidKeyClass = Class.forName("appeng.api.stacks.AEFluidKey");
            Object fluidKey = null;
            Object nbt = emiStack.getNbt();
            for (var method : fluidKeyClass.getMethods()) {
                if (!"of".equals(method.getName())) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 2 && params[0].isInstance(fluid)
                        && (nbt == null || params[1].isInstance(nbt))) {
                    fluidKey = method.invoke(null, fluid, nbt);
                    break;
                }
            }
            if (fluidKey == null) {
                for (var method : fluidKeyClass.getMethods()) {
                    if (!"of".equals(method.getName())) {
                        continue;
                    }
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length == 1 && params[0].isInstance(fluid)) {
                        fluidKey = method.invoke(null, fluid);
                        break;
                    }
                }
            }
            if (fluidKey == null) {
                return ItemStack.EMPTY;
            }

            Class<?> genericStackClass = Class.forName("appeng.api.stacks.GenericStack");
            for (var method : genericStackClass.getMethods()) {
                if (!"wrapInItemStack".equals(method.getName())) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 2 && params[0].isInstance(fluidKey)
                        && (params[1] == long.class || params[1] == Long.class)) {
                    Object wrapped = method.invoke(null, fluidKey, 1L);
                    if (wrapped instanceof ItemStack stack) {
                        return stack;
                    }
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return ItemStack.EMPTY;
    }

    private static boolean isAe2StorageBus(AbstractContainerScreen<?> screen) {
        Class<?> screenType = screen.getClass();
        while (screenType != null) {
            if ("StorageBusScreen".equals(screenType.getSimpleName())) {
                return true;
            }
            screenType = screenType.getSuperclass();
        }
        Class<?> menuType = screen.getMenu().getClass();
        while (menuType != null) {
            if ("StorageBusMenu".equals(menuType.getSimpleName())) {
                return true;
            }
            menuType = menuType.getSuperclass();
        }
        return false;
    }

    private static boolean isAe2FilterSlotEnabled(Slot slot) {
        try {
            Object enabled = slot.getClass().getMethod("isSlotEnabled").invoke(slot);
            if (enabled instanceof Boolean b) {
                return b;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return slot.isActive();
    }

    private static boolean hasAe2FilterSetter(Slot slot) {
        try {
            slot.getClass().getMethod("setFilterTo", ItemStack.class);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean setAe2Filter(Slot slot, ItemStack stack) {
        try {
            try {
                Object allowed = slot.getClass().getMethod("canSetFilterTo", ItemStack.class).invoke(slot, stack);
                if (allowed instanceof Boolean b && !b) {
                    return false;
                }
            } catch (NoSuchMethodException ignored) {
            }
            slot.getClass().getMethod("setFilterTo", ItemStack.class).invoke(slot, stack);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean dropStacksToCellWorkbench(Screen screen, List<? extends EmiIngredient> stacks, int x,
            int y) {
        if (!(screen instanceof AbstractContainerScreen<?> handled)) {
            return false;
        }
        HandledScreenAccessor accessor = (HandledScreenAccessor) handled;
        int left = accessor.getX();
        int top = accessor.getY();
        List<Slot> targets = Lists.newArrayList();
        for (Slot slot : handled.getMenu().slots) {
            if (slot.isActive() && isCellPartitionSlot(slot)) {
                targets.add(slot);
            }
        }
        if (targets.isEmpty()) {
            return false;
        }
        targets.sort((a, b) -> {
            int yCompare = Integer.compare(a.y, b.y);
            return yCompare != 0 ? yCompare : Integer.compare(a.x, b.x);
        });
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Slot slot : targets) {
            minX = Math.min(minX, slot.x);
            minY = Math.min(minY, slot.y);
            maxX = Math.max(maxX, slot.x + 16);
            maxY = Math.max(maxY, slot.y + 16);
        }
        if (x < left + minX || x >= left + maxX || y < top + minY || y >= top + maxY) {
            return false;
        }
        int target = 0;
        boolean placed = false;
        for (EmiIngredient stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            while (target < targets.size() && !targets.get(target).getItem().isEmpty()) {
                target++;
            }
            if (target >= targets.size()) {
                break;
            }
            Slot slot = targets.get(target);
            if (dropStack(screen, stack, left + slot.x + 8, top + slot.y + 8)) {
                target++;
                placed = true;
            }
        }
        return placed;
    }

    private static boolean isCellPartitionSlot(Slot slot) {
        Class<?> type = slot.getClass();
        while (type != null) {
            if ("CellPartitionSlot".equals(type.getSimpleName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
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
