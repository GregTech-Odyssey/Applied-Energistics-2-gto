package dev.emi.emi.screen;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.config.SidebarSide;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.input.EmiInput;
import dev.emi.emi.planner.PlannerText;
import dev.emi.emi.registry.EmiDragDropHandlers;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.runtime.*;
import dev.emi.emi.runtime.EmiFavorite.Role;
import dev.emi.emi.runtime.EmiFavoriteGroups.AmountEntry;
import dev.emi.emi.runtime.EmiFavoriteGroups.ChainPlan;
import dev.emi.emi.screen.tooltip.EmiTooltipComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.*;

public final class FavoriteGroupSidebar {
    private static EmiFavorite pressedFavorite;
    private static EmiFavorite dragFavorite;
    private static int pressedButton = -1;
    private static boolean dragging;
    private static boolean reorderDragging;
    private static boolean externalFavoriteDragging;
    private static boolean includeGroupDragging;
    private static EmiFavoriteGroups.Group pressedPageGroup;
    private static boolean pageGroupDragging;
    private static int pageGroupInsertion = -1;
    private static AutoCraftJob autoCraftJob;
    private static MeAutoCraftJob meAutoCraftJob;
    private static PullJob pullJob;
    private static final int ME_BURST_LIMIT = 64;
    private static final int ME_GRID_SETTLE_STABLE_TICKS = 3;
    private static final int ME_GRID_SETTLE_LIMIT = 60;
    private static final int ME_PULL_IDLE = 0;
    private static final int ME_PULL_WAIT_SHIFT = 1;
    private static final int ME_PULL_WAIT_CURSOR = 2;
    private static final int ME_PULL_WAIT_INSERT = 3;
    private static final int ME_PULL_TIMEOUT_TICKS = 40;
    private static final int ME_PULL_CURSOR_STABLE_TICKS = 2;

    private FavoriteGroupSidebar() {
    }

    public static void render(EmiDrawContext context, int mouseX, int mouseY, float delta) {
        Layout layout = layout();
        if (layout.boxes.isEmpty() && !dragging && !pageGroupDragging) {
            return;
        }
        context.push();
        context.matrices().translate(0, 0, 220);
        for (GroupBox box : layout.boxes) {
            renderGroup(context, box, mouseX, mouseY);
        }
        if (dragging && pressedFavorite != null && dragFavorite != null) {
            renderDragPreview(context, layout, mouseX, mouseY);
        }
        if (pageGroupDragging && pressedPageGroup != null) {
            renderPageGroupPlacementPreview(context, layout, pressedPageGroup);
            if (pageGroupInsertion >= 0 && EmiFavoriteGroups.hasGroupMovePreview(pressedPageGroup)) {
                renderPageGroupCursor(context, pressedPageGroup, mouseX, mouseY);
            } else {
                renderPageGroupPreview(context, pressedPageGroup, mouseX, mouseY);
            }
            renderPageDropTargets(context, layout, mouseX, mouseY);
            renderPageGroupDropTargets(context, layout, pressedPageGroup, mouseX, mouseY, delta);
        }
        context.pop();
    }

    public static void renderTooltip(Screen screen, EmiDrawContext context, int mouseX, int mouseY) {
        if (pageGroupDragging) {
            return;
        }
        GroupBox box = hoveredHandle(layout(), mouseX, mouseY);
        if (box == null) {
            return;
        }
        List<ClientTooltipComponent> tooltip = new ArrayList<>();
        tooltip.add(new GroupHeaderTooltipComponent());
        AutoCraftJob job = activeJob(box.group);
        if (job != null) {
            appendAutoCraftTooltip(tooltip, job);
        } else {
            MeAutoCraftJob meJob = activeMeJob(box.group);
            if (meJob != null) {
                appendMeAutoCraftTooltip(tooltip, meJob);
            }
        }
        PullJob pull = activePullJob(box.group);
        if (pull != null) {
            appendPullTooltip(tooltip, pull);
        }
        if (EmiInput.isAltDown()) {
            tooltip.add(line(PlannerText.tr("bookmark.help.toggle_group_mode", "LMB - Toggle Group Mode"), ChatFormatting.YELLOW));
            tooltip.add(line(PlannerText.tr("bookmark.help.open_crafting_tree", "CTRL + LMB - Open Crafting Tree"), ChatFormatting.YELLOW));
            tooltip.add(line(PlannerText.tr("bookmark.help.toggle_crafting_chain", "RMB - Toggle Crafting Chain"), ChatFormatting.YELLOW));
            tooltip.add(line(PlannerText.tr("bookmark.help.send_to_chat", "CTRL + L - Send Bookmark Group to Chat"), ChatFormatting.YELLOW));
            tooltip.add(line(PlannerText.tr("bookmark.help.toggle_collapse", "ALT + LMB - Toggle Collapse/Expand"), ChatFormatting.YELLOW));
            tooltip.add(line(PlannerText.tr("bookmark.help.remove_group", "SHIFT + A - Remove Group"), ChatFormatting.YELLOW));
            tooltip.add(line(PlannerText.tr("bookmark.help.craft_items", "SHIFT + C - Craft Items"), ChatFormatting.YELLOW));
            tooltip.add(line(PlannerText.tr("bookmark.help.open_exact_recipe", "LMB on Recipe - Open Exact Recipe"), ChatFormatting.YELLOW));
            tooltip.add(line(PlannerText.tr("bookmark.help.create_include_group", "ALT + LMB + Drag - Create/Include Group"), ChatFormatting.YELLOW));
            tooltip.add(line(PlannerText.tr("bookmark.help.remove_exclude_group", "RMB + Drag - Remove/Exclude Group"), ChatFormatting.YELLOW));
            tooltip.add(line(PlannerText.tr("bookmark.help.change_recipe_quantity", "SHIFT/CTRL + Scroll - Change Recipe Quantity"), ChatFormatting.YELLOW));
            tooltip.add(line(PlannerText.tr("bookmark.help.change_whole_group", "CTRL + SHIFT + Scroll - Change Whole Group"), ChatFormatting.YELLOW));
            tooltip.add(line(PlannerText.tr("bookmark.help.output_stack_step", "+ ALT - Use Output Stack Size Step"), ChatFormatting.YELLOW));
            tooltip.add(line(PlannerText.tr("bookmark.help.move_position", "SHIFT + LMB + Drag - Move Position"), ChatFormatting.YELLOW));
            tooltip.add(line(PlannerText.tr("bookmark.help.reorder_item", "LMB + Drag on Bookmark - Reorder Bookmark"), ChatFormatting.YELLOW));
            tooltip.add(line(PlannerText.tr("bookmark.help.move_page", "SHIFT + Drag Handle to < > - Move Page"), ChatFormatting.YELLOW));
            tooltip.add(line(PlannerText.tr("bookmark.help.fill_ghost_slots", "SHIFT + Drag Handle to Ghost Slots - Fill Group"), ChatFormatting.YELLOW));
            tooltip.add(line(PlannerText.tr("bookmark.help.craft_missing", "CTRL + SHIFT + C - Craft Missing Items"), ChatFormatting.YELLOW));
            tooltip.add(line(PlannerText.tr("bookmark.help.pull_bookmarked", "SHIFT + P - Pull Bookmarked Items"), ChatFormatting.YELLOW));
            tooltip.add(line(PlannerText.tr("bookmark.help.pull_missing", "CTRL + SHIFT + P - Pull Missing Items"), ChatFormatting.YELLOW));
        }
        if (box.group.craftingChain) {
            appendChainTooltip(tooltip, box.group);
        }
        EmiRenderHelper.drawTooltip(screen, context, tooltip, mouseX, mouseY);
    }

    private static void renderGroup(EmiDrawContext context, GroupBox box, int mouseX, int mouseY) {
        int color = box.group.craftingChain ? 0xFFFF55FF : box.group.groupMode ? 0xFF00FFFF : 0xFF55FF55;
        Bounds b = box.bracket;
        boolean onRight = box.bracketOnRight;
        int x = onRight ? b.x() : b.right() - 2;
        context.fill(x, b.y(), 2, b.height(), color);
        if (onRight) {
            context.fill(x - 5, b.y(), 7, 2, color);
            context.fill(x - 5, b.bottom() - 2, 7, 2, color);
        } else {
            context.fill(x, b.y(), 7, 2, color);
            context.fill(x, b.bottom() - 2, 7, 2, color);
        }

        Bounds h = box.handle;
        int bg = h.contains(mouseX, mouseY) ? 0xEE252525 : 0xDD101010;
        context.fill(h.x(), h.y(), h.width(), h.height(), bg);
        context.fill(h.x(), h.y(), h.width(), 1, color);
        context.fill(h.x(), h.bottom() - 1, h.width(), 1, color);
        context.fill(h.x(), h.y(), 1, h.height(), color);
        context.fill(h.right() - 1, h.y(), 1, h.height(), color);
        int gx = h.x() + 2;
        int gy = h.y() + 2;
        context.fill(gx, gy, 2, 2, color);
        context.fill(gx + 3, gy, 2, 2, color);
        context.fill(gx, gy + 3, 2, 2, color);
        context.fill(gx + 3, gy + 3, 2, 2, color);
        if (box.group.collapsed) {
            context.fill(h.x() + 2, h.y() + 4, h.width() - 4, 1, 0xFFFFFFFF);
        }
        AutoCraftJob job = activeJob(box.group);
        if (job != null) {
            int inner = Math.max(1, h.width() - 2);
            int total = Math.max(1, job.steps.size());
            int completed = Math.min(job.index, total);
            int progress = completed >= total ? inner : Math.max(1, (int) Math.ceil(inner * (completed / (double) total)));
            context.fill(h.x() + 1, h.bottom() - 2, inner, 1, 0xFF303030);
            context.fill(h.x() + 1, h.bottom() - 2, Math.min(inner, progress), 1, job.missingOnly ? 0xFFFFAA00 : 0xFF55FFFF);
        } else {
            MeAutoCraftJob meJob = activeMeJob(box.group);
            if (meJob != null) {
                int inner = Math.max(1, h.width() - 2);
                int total = Math.max(1, meJob.steps.size());
                int completed = Math.min(meJob.index, total);
                int progress = completed >= total ? inner : Math.max(1, (int) Math.ceil(inner * (completed / (double) total)));
                context.fill(h.x() + 1, h.bottom() - 2, inner, 1, 0xFF303030);
                context.fill(h.x() + 1, h.bottom() - 2, Math.min(inner, progress), 1,
                        meJob.missingOnly ? 0xFFFFAA00 : 0xFF55FFFF);
            }
        }
    }

    private static void renderDragPreview(EmiDrawContext context, Layout layout, int mouseX, int mouseY) {
        int start = rawIndex(pressedFavorite);
        int end = rawIndex(dragFavorite);
        if (start < 0 || end < 0) {
            return;
        }
        if (reorderDragging) {
            VisibleSlot target = null;
            for (VisibleSlot slot : layout.slots) {
                if (slot.favorite == pressedFavorite) {
                    context.fill(slot.bounds.x(), slot.bounds.y(), slot.bounds.width(), slot.bounds.height(), 0x66101010);
                }
                if (slot.favorite == dragFavorite) {
                    target = slot;
                }
            }
            if (target != null && dragFavorite != pressedFavorite && layout.space != null) {
                int local = target.visibleIndex - layout.pageStart;
                int insertion = end > start ? local + 1 : local;
                int markerX = layout.space.getEdgeX(insertion);
                int markerY = layout.space.getEdgeY(insertion);
                context.fill(markerX - 1, markerY, 2, EmiScreenManager.ENTRY_SIZE, 0xFF55FFFF);
            }
            EmiIngredient ingredient = concreteDragIngredient(pressedFavorite);
            if (!ingredient.isEmpty()) {
                context.push();
                context.matrices().translate(0, 0, 260);
                context.drawStack(ingredient, mouseX - 8, mouseY - 8, EmiIngredient.RENDER_ICON);
                context.pop();
            }
            return;
        }
        int min = Math.min(start, end);
        int max = Math.max(start, end);
        int color = pressedButton == 1 ? 0x66FF5555 : EmiInput.isShiftDown() ? 0x66FF55FF : 0x6600FFFF;
        for (VisibleSlot slot : layout.slots) {
            int raw = rawIndex(slot.favorite);
            if (raw >= min && raw <= max) {
                context.fill(slot.bounds.x(), slot.bounds.y(), slot.bounds.width(), slot.bounds.height(), color);
            }
        }
    }

    private static void renderPageGroupPlacementPreview(EmiDrawContext context, Layout layout, EmiFavoriteGroups.Group group) {
        if (pageGroupInsertion < 0 || !EmiFavoriteGroups.hasGroupMovePreview(group)) {
            return;
        }
        for (VisibleSlot slot : layout.slots) {
            if (containsIdentity(group.members(), slot.favorite)) {
                Bounds b = slot.bounds;
                context.fill(b.x(), b.y(), b.width(), 1, 0xFF55FFFF);
                context.fill(b.x(), b.bottom() - 1, b.width(), 1, 0xFF55FFFF);
                context.fill(b.x(), b.y(), 1, b.height(), 0xFF55FFFF);
                context.fill(b.right() - 1, b.y(), 1, b.height(), 0xFF55FFFF);
            }
        }
    }

    private static void renderPageGroupCursor(EmiDrawContext context, EmiFavoriteGroups.Group group, int mouseX, int mouseY) {
        for (EmiFavorite favorite : group.members()) {
            EmiIngredient ingredient = concreteDragIngredient(favorite);
            if (!ingredient.isEmpty()) {
                context.push();
                context.matrices().translate(0, 0, 240);
                context.drawStack(ingredient, mouseX - 8, mouseY - 8, EmiIngredient.RENDER_ICON);
                context.pop();
                return;
            }
        }
    }

    private static void renderPageGroupPreview(EmiDrawContext context, EmiFavoriteGroups.Group group, int mouseX, int mouseY) {
        List<EmiFavorite> members = group.members();
        if (members.isEmpty()) {
            return;
        }
        int count = Math.min(63, members.size());
        int columns = Math.min(20, count);
        int rows = (count + columns - 1) / columns;
        int width = columns * 18;
        int height = rows * 18;
        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int startX = mouseX + 10;
        if (startX + width > screenWidth - 4) {
            startX = mouseX - width - 10;
        }
        startX = Math.max(4, Math.min(startX, Math.max(4, screenWidth - width - 4)));
        int startY = mouseY - 8;
        startY = Math.max(4, Math.min(startY, Math.max(4, screenHeight - height - 4)));
        context.push();
        context.matrices().translate(0, 0, 240);
        for (int i = 0; i < count; i++) {
            EmiFavorite favorite = members.get(i);
            EmiIngredient ingredient = concreteDragIngredient(favorite);
            if (ingredient.isEmpty()) {
                continue;
            }
            int x = startX + (i % columns) * 18;
            int y = startY + (i / columns) * 18;
            context.drawStack(ingredient, x + 1, y + 1, EmiIngredient.RENDER_ICON);
        }
        context.pop();
    }

    private static void renderPageGroupDropTargets(EmiDrawContext context, Layout layout, EmiFavoriteGroups.Group group,
            int mouseX, int mouseY, float delta) {
        if (group == null || (layout.space != null && layout.space.contains(mouseX, mouseY))) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.screen == null) {
            return;
        }
        for (EmiFavorite favorite : group.members()) {
            EmiIngredient ingredient = concreteDragIngredient(favorite);
            if (!ingredient.isEmpty()) {
                EmiDragDropHandlers.render(client.screen, ingredient, context.raw(), mouseX, mouseY, delta);
                return;
            }
        }
    }

    private static void renderPageDropTargets(EmiDrawContext context, Layout layout, int mouseX, int mouseY) {
        if (layout.panel == null || layout.space == null || pressedPageGroup == null) {
            return;
        }
        EmiFavorite first = pressedPageGroup.firstMember();
        if (first == null) {
            return;
        }
        int sourcePage = EmiFavorites.getFavoritePage(first);
        if (sourcePage > 0) {
            drawPageDropTarget(context, pageArrowBounds(layout, false), mouseX, mouseY);
        }
        drawPageDropTarget(context, pageArrowBounds(layout, true), mouseX, mouseY);
    }

    private static void drawPageDropTarget(EmiDrawContext context, Bounds bounds, int mouseX, int mouseY) {
        int color = bounds.contains(mouseX, mouseY) ? 0xFFFF55FF : 0xFFAA55AA;
        context.fill(bounds.x(), bounds.y(), bounds.width(), 1, color);
        context.fill(bounds.x(), bounds.bottom() - 1, bounds.width(), 1, color);
        context.fill(bounds.x(), bounds.y(), 1, bounds.height(), color);
        context.fill(bounds.right() - 1, bounds.y(), 1, bounds.height(), color);
    }

    private static Bounds pageArrowBounds(Layout layout, boolean right) {
        if (layout.space == null) {
            return Bounds.EMPTY;
        }
        int x = right ? layout.space.tx + layout.space.tw * 18 - 16 : layout.space.tx;
        return new Bounds(x, layout.space.ty - 18, 16, 16);
    }

    private static int pageDropTarget(Layout layout, EmiFavoriteGroups.Group group, int mouseX, int mouseY) {
        if (layout.panel == null || layout.space == null || group == null) {
            return -1;
        }
        EmiFavorite first = group.firstMember();
        if (first == null) {
            return -1;
        }
        int sourcePage = EmiFavorites.getFavoritePage(first);
        if (sourcePage > 0 && pageArrowBounds(layout, false).contains(mouseX, mouseY)) {
            return sourcePage - 1;
        }
        if (pageArrowBounds(layout, true).contains(mouseX, mouseY)) {
            return sourcePage + 1;
        }
        return -1;
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        Layout layout = layout();
        GroupBox box = hoveredHandle(layout, (int) mouseX, (int) mouseY);
        if (box != null) {
            if (button == 0 && EmiInput.isControlDown() && !EmiInput.isShiftDown() && !EmiInput.isAltDown()) {
                Minecraft client = Minecraft.getInstance();
                client.setScreen(new BookmarkTreeScreen(client.screen, box.group));
                playSound();
                return true;
            }
            if (button == 0 && EmiInput.isShiftDown() && !EmiInput.isControlDown() && !EmiInput.isAltDown()) {
                clearDrag();
                clearPageGroupDrag();
                pressedPageGroup = box.group;
                pageGroupDragging = false;
                return true;
            }
            if (button == 0) {
                if (EmiInput.isAltDown()) {
                    EmiFavoriteGroups.toggleCollapsed(box.group);
                } else {
                    EmiFavoriteGroups.toggleGroupMode(box.group);
                }
                playSound();
                return true;
            }
            if (button == 1) {
                EmiFavoriteGroups.toggleCraftingChain(box.group);
                playSound();
                return true;
            }
        }
        VisibleSlot slot = favoriteAt(layout, (int) mouseX, (int) mouseY);
        if (slot != null && (button == 0 || button == 1)) {
            pressedFavorite = slot.favorite;
            dragFavorite = slot.favorite;
            pressedButton = button;
            dragging = false;
        } else {
            clearDrag();
        }
        return false;
    }

    public static boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (pressedPageGroup != null && button == 0) {
            pageGroupDragging = true;
            updatePageGroupMovePreview((int) mouseX, (int) mouseY);
            return true;
        }
        if (pressedFavorite == null || button != pressedButton || (button != 0 && button != 1)) {
            return false;
        }
        if (button == 0 && (reorderDragging || externalFavoriteDragging || (!includeGroupDragging && !EmiInput.isAltDown()
                && !EmiInput.isShiftDown() && !EmiInput.isControlDown()))) {
            if (externalFavoriteDragging) {
                return false;
            }
            Layout current = layout();
            if (current.space == null || !current.space.contains((int) mouseX, (int) mouseY)) {
                externalFavoriteDragging = true;
                reorderDragging = false;
                dragFavorite = pressedFavorite;
                dragging = false;
                return false;
            }
            reorderDragging = true;
            VisibleSlot slot = favoriteAt(current, (int) mouseX, (int) mouseY);
            if (slot != null
                    && EmiFavorites.getFavoritePage(slot.favorite) == EmiFavorites.getFavoritePage(pressedFavorite)
                    && EmiFavoriteGroups.groupFor(slot.favorite) == EmiFavoriteGroups.groupFor(pressedFavorite)) {
                dragFavorite = slot.favorite;
            }
            dragging = true;
            return true;
        }
        if (button == 0 && (includeGroupDragging || (EmiInput.isAltDown()
                && !EmiInput.isShiftDown() && !EmiInput.isControlDown()))) {
            includeGroupDragging = true;
        }
        VisibleSlot slot = favoriteAt(layout(), (int) mouseX, (int) mouseY);
        if (slot != null) {
            dragFavorite = slot.favorite;
            if (dragFavorite != pressedFavorite) {
                dragging = true;
            }
        }
        return dragging;
    }

    public static boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (pressedPageGroup != null && button == 0) {
            try {
                if (!pageGroupDragging) {
                    return true;
                }
                Layout current = layout();
                int targetPage = pageDropTarget(current, pressedPageGroup, (int) mouseX, (int) mouseY);
                if (targetPage >= 0) {
                    if (activeJob(pressedPageGroup) != null) {
                        autoCraftJob = null;
                    }
                    EmiFavoriteGroups.clearGroupMovePreview();
                    if (EmiFavoriteGroups.moveGroupToFavoritePage(pressedPageGroup, targetPage)) {
                        if (current.space != null) {
                            current.space.batcher.repopulate();
                        }
                        if (current.panel != null && current.space != null) {
                            int maxPage = Math.max(0, EmiFavorites.getFavoritePageCount() - 1);
                            int actualPage = Math.max(0, Math.min(targetPage, maxPage));
                            current.panel.page = EmiFavoriteGroups.firstSidebarPageForNamespace(current.space, actualPage);
                        }
                        playSound();
                    }
                    return true;
                }
                int insertion = updatePageGroupMovePreview((int) mouseX, (int) mouseY);
                if (insertion >= 0) {
                    if (activeJob(pressedPageGroup) != null) {
                        autoCraftJob = null;
                    }
                    EmiFavoriteGroups.clearGroupMovePreview();
                    EmiFavoriteGroups.moveGroup(pressedPageGroup, insertion);
                    if (current.space != null) {
                        current.space.batcher.repopulate();
                    }
                    playSound();
                    return true;
                }
                if (dropPageGroupToScreen(pressedPageGroup, (int) mouseX, (int) mouseY)) {
                    playSound();
                }
                return true;
            } finally {
                clearPageGroupDrag();
            }
        }
        if (externalFavoriteDragging && pressedFavorite != null && button == 0 && pressedButton == 0) {
            clearDrag();
            return false;
        }
        if (reorderDragging && pressedFavorite != null && button == 0 && pressedButton == 0) {
            try {
                if (dragging && dragFavorite != null && dragFavorite != pressedFavorite) {
                    int start = rawIndex(pressedFavorite);
                    int end = rawIndex(dragFavorite);
                    if (start >= 0 && end >= 0
                            && EmiFavoriteGroups.moveFavoriteRelative(pressedFavorite, dragFavorite, end > start)) {
                        playSound();
                    }
                }
                return true;
            } finally {
                clearDrag();
            }
        }
        try {
            if (!dragging && pressedFavorite != null && button == 0 && pressedButton == 0
                    && !EmiInput.isShiftDown() && !EmiInput.isControlDown() && !EmiInput.isAltDown()) {
                VisibleSlot released = favoriteAt(layout(), (int) mouseX, (int) mouseY);
                if (released != null && released.favorite == pressedFavorite
                        && EmiFavoriteGroups.groupFor(pressedFavorite) != null) {
                    EmiRecipe recipe = pressedFavorite.getRecipe();
                    if (recipe != null) {
                        EmiApi.displayRecipe(recipe);
                        return true;
                    }
                }
            }
            if (!dragging || pressedFavorite == null || dragFavorite == null || button != pressedButton) {
                return false;
            }
            int start = rawIndex(pressedFavorite);
            int end = rawIndex(dragFavorite);
            if (start < 0 || end < 0) {
                return true;
            }
            if (button == 0 && EmiInput.isShiftDown()) {
                EmiFavoriteGroups.Group group = EmiFavoriteGroups.groupFor(pressedFavorite);
                if (group != null) {
                    int insertion = end > start ? end + 1 : end;
                    EmiFavoriteGroups.moveGroup(group, insertion);
                }
            } else if (button == 0) {
                EmiFavoriteGroups.createOrInclude(start, end);
            } else if (button == 1) {
                EmiFavoriteGroups.excludeRange(start, end);
            }
            playSound();
            return true;
        } finally {
            clearDrag();
        }
    }

    public static boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        boolean control = EmiInput.isControlDown();
        boolean shift = EmiInput.isShiftDown();
        if ((!control && !shift) || amount == 0) {
            return false;
        }
        Layout current = layout();
        VisibleSlot slot = favoriteAt(current, (int) mouseX, (int) mouseY);
        EmiFavoriteGroups.Group group = slot == null ? null : EmiFavoriteGroups.groupFor(slot.favorite);
        GroupBox box = groupAtPoint(current, (int) mouseX, (int) mouseY);
        if (group == null && box != null) {
            group = box.group;
        }
        if (group == null) {
            return false;
        }
        int direction = amount > 0 ? 1 : -1;
        EmiRecipe recipe = slot == null ? null : slot.favorite.getRecipe();
        long step = 1L;
        if (EmiInput.isAltDown()) {
            step = control && shift ? groupOutputStackStep(group) : outputStackStep(group, recipe);
        }
        if (control && shift) {
            EmiFavoriteGroups.adjustQuantity(group, direction, step);
            return true;
        }
        if (recipe != null) {
            EmiFavoriteGroups.adjustRecipeQuantity(group, recipe, direction, step);
            return true;
        }
        EmiFavoriteGroups.adjustQuantity(group, direction, step);
        return true;
    }

    private static long groupOutputStackStep(EmiFavoriteGroups.Group group) {
        List<EmiRecipe> recipes = new ArrayList<>();
        Set<ResourceLocation> seen = new HashSet<>();
        for (EmiFavorite favorite : group.members()) {
            EmiRecipe recipe = favorite.getRecipe();
            if (recipe != null && recipe.getId() != null && favorite.getRole() != Role.ITEM && seen.add(recipe.getId())) {
                recipes.add(recipe);
            }
        }
        List<EmiRecipe> roots = rootRecipes(recipes);
        return roots.isEmpty() ? 1L : outputStackStep(group, roots.get(0));
    }

    private static long outputStackStep(EmiFavoriteGroups.Group group, EmiRecipe recipe) {
        EmiRecipe target = recipe;
        if (target == null) {
            for (EmiFavorite favorite : group.members()) {
                if (favorite.getRole() == Role.RESULT && favorite.getRecipe() != null) {
                    target = favorite.getRecipe();
                    break;
                }
            }
        }
        if (target == null) {
            return 1L;
        }
        for (EmiStack output : target.getOutputs()) {
            if (output == null || output.isEmpty() || output.getItemStack().isEmpty()) {
                continue;
            }
            long outputAmount = Math.max(1L, output.getAmount());
            long maxStack = Math.max(1, output.getItemStack().getMaxStackSize());
            if (outputAmount >= maxStack) {
                return 1L;
            }
            return Math.max(1L, (maxStack + outputAmount - 1L) / outputAmount);
        }
        return 1L;
    }

    public static boolean keyPressed(int mouseX, int mouseY, int keyCode, int modifiers) {
        GroupBox box = groupAtPoint(layout(), mouseX, mouseY);
        if (box == null) {
            return false;
        }
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (shift && keyCode == GLFW.GLFW_KEY_C && (activeJob(box.group) != null || activeMeJob(box.group) != null)) {
            autoCraftJob = null;
            meAutoCraftJob = null;
            playSound();
            return true;
        }
        if (shift && keyCode == GLFW.GLFW_KEY_P && activePullJob(box.group) != null) {
            cancelPullJob();
            playSound();
            return true;
        }
        if (shift && keyCode == GLFW.GLFW_KEY_A) {
            if (activeJob(box.group) != null) {
                autoCraftJob = null;
            }
            if (activeMeJob(box.group) != null) {
                meAutoCraftJob = null;
            }
            if (activePullJob(box.group) != null) {
                cancelPullJob();
            }
            EmiFavoriteGroups.removeGroup(box.group);
            return true;
        }
        if (control && keyCode == GLFW.GLFW_KEY_L) {
            sendGroupToChat(box.group);
            return true;
        }
        if (control && shift && keyCode == GLFW.GLFW_KEY_C) {
            return craftMissing(box.group);
        }
        if (control && shift && keyCode == GLFW.GLFW_KEY_P) {
            return pullItems(box.group, true);
        }
        if (shift && !control && keyCode == GLFW.GLFW_KEY_P) {
            return pullItems(box.group, false);
        }
        if (shift && !control && keyCode == GLFW.GLFW_KEY_C) {
            return craftItems(box.group);
        }
        return false;
    }

    private static void appendAutoCraftTooltip(List<ClientTooltipComponent> tooltip, AutoCraftJob job) {
        int total = Math.max(1, job.steps.size());
        int current = Math.min(total, job.index + 1);
        tooltip.add(line((job.missingOnly ? PlannerText.tr("bookmark.status.craft_missing", "Craft Missing: ") : PlannerText.tr("bookmark.status.crafting", "Crafting: ")) + current + "/" + total, ChatFormatting.GOLD));
        if (job.index < job.steps.size()) {
            CraftStep step = job.steps.get(job.index);
            tooltip.add(line(PlannerText.tr("bookmark.status.current", "Current: ") + ingredientName(step.output), ChatFormatting.GRAY));
        }
        tooltip.add(line(PlannerText.tr("bookmark.status.cancel_crafting", "SHIFT + C - Cancel Crafting"), ChatFormatting.YELLOW));
    }

    private static AutoCraftJob activeJob(EmiFavoriteGroups.Group group) {
        AutoCraftJob job = autoCraftJob;
        return job != null && job.group == group ? job : null;
    }

    private static void appendMeAutoCraftTooltip(List<ClientTooltipComponent> tooltip, MeAutoCraftJob job) {
        int total = Math.max(1, job.steps.size());
        int current = Math.min(total, job.index + 1);
        tooltip.add(line((job.missingOnly ? PlannerText.tr("bookmark.status.me_craft_missing", "ME Craft Missing: ") : PlannerText.tr("bookmark.status.me_crafting", "ME Crafting: ")) + current + "/" + total, ChatFormatting.GOLD));
        if (job.index < job.steps.size()) {
            CraftStep step = job.steps.get(job.index);
            tooltip.add(line(PlannerText.tr("bookmark.status.current", "Current: ") + ingredientName(step.output), ChatFormatting.GRAY));
        }
        tooltip.add(line(PlannerText.tr("bookmark.status.cancel_crafting", "SHIFT + C - Cancel Crafting"), ChatFormatting.YELLOW));
    }

    private static MeAutoCraftJob activeMeJob(EmiFavoriteGroups.Group group) {
        MeAutoCraftJob job = meAutoCraftJob;
        return job != null && job.group == group ? job : null;
    }

    private static void appendPullTooltip(List<ClientTooltipComponent> tooltip, PullJob job) {
        int total = Math.max(1, job.steps.size());
        int current = Math.min(total, job.index + 1);
        tooltip.add(line((job.missingOnly ? PlannerText.tr("bookmark.status.pull_missing", "Pull Missing: ") : PlannerText.tr("bookmark.status.pulling", "Pulling: ")) + current + "/" + total, ChatFormatting.GOLD));
        if (job.index < job.steps.size()) {
            PullStep step = job.steps.get(job.index);
            tooltip.add(line(PlannerText.tr("bookmark.status.current", "Current: ") + ingredientName(step.ingredient), ChatFormatting.GRAY));
        }
        tooltip.add(line(PlannerText.tr("bookmark.status.cancel_pulling", "SHIFT + P - Cancel Pulling"), ChatFormatting.YELLOW));
    }

    private static PullJob activePullJob(EmiFavoriteGroups.Group group) {
        PullJob job = pullJob;
        return job != null && job.group == group ? job : null;
    }

    private static void appendChainTooltip(List<ClientTooltipComponent> tooltip, EmiFavoriteGroups.Group group) {
        Minecraft client = Minecraft.getInstance();
        EmiPlayerInventory inventory = client.player == null ? EmiScreenManager.lastPlayerInventory : EmiPlayerInventory.of(client.player);
        ChainPlan plan = EmiFavoriteGroups.calculatePlan(group, inventory);
        MissingCraftPlan missingPlan = buildMissingCraftPlan(group, inventory, false);
        tooltip.add(line(PlannerText.tr("bookmark.chain.title", "Crafting Chain"), ChatFormatting.AQUA));
        appendAmounts(tooltip, PlannerText.tr("bookmark.chain.results", "Results:"), plan.results, ChatFormatting.GRAY);
        if (!missingPlan.missing.isEmpty()) {
            appendAmounts(tooltip, PlannerText.tr("bookmark.chain.missing_items", "Missing Items:"), missingPlan.missing, ChatFormatting.RED);
        }
        List<AmountEntry> requiredCrafts = requiredCrafts(group, plan, missingPlan.steps);
        if (!requiredCrafts.isEmpty()) {
            appendAmounts(tooltip, PlannerText.tr("bookmark.chain.required_crafts", "Required Crafts:"), requiredCrafts, ChatFormatting.BLUE);
        }
    }

    private static void appendAmounts(List<ClientTooltipComponent> tooltip, String title, List<AmountEntry> entries, ChatFormatting color) {
        tooltip.add(line(title, color));
        if (entries.isEmpty()) {
            tooltip.add(line("  -", ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(new AmountGridTooltipComponent(entries));
    }

    private static List<AmountEntry> requiredCrafts(EmiFavoriteGroups.Group group, ChainPlan plan, List<CraftStep> missingSteps) {
        LinkedHashMap<ResourceLocation, EmiRecipe> unique = new LinkedHashMap<>();
        for (EmiFavorite favorite : group.members()) {
            EmiRecipe recipe = favorite.getRecipe();
            if (recipe != null && recipe.getId() != null && favorite.getRole() != Role.ITEM) {
                unique.putIfAbsent(recipe.getId(), recipe);
            }
        }
        List<EmiRecipe> recipes = new ArrayList<>(unique.values());
        if (group.craftingChain) {
            List<AmountEntry> result = new ArrayList<>();
            for (CraftStep step : missingSteps) {
                if (step.recipe == null || step.amount <= 0L || !isIntermediateRecipe(step.recipe, recipes)) {
                    continue;
                }
                result.add(new AmountEntry(normalized(step.output), step.amount));
            }
            return result;
        }
        List<AmountEntry> result = new ArrayList<>();
        for (EmiRecipe recipe : craftingOrder(recipes)) {
            if (plan.batchesFor(recipe) <= 0L || !isIntermediateRecipe(recipe, recipes)) {
                continue;
            }
            EmiFavorite favorite = resultFavorite(group, recipe);
            if (favorite != null && !favorite.isEmpty()) {
                long amount = plan.requiredFavorites.getOrDefault(favorite, Math.max(1L, favorite.getAmount()));
                result.add(new AmountEntry(normalized(favorite.getStack()), amount));
                continue;
            }
            for (EmiStack output : recipe.getOutputs()) {
                if (output != null && !output.isEmpty()) {
                    long amount = safeMultiply(Math.max(1L, output.getAmount()), plan.batchesFor(recipe));
                    result.add(new AmountEntry(normalized(output), amount));
                    break;
                }
            }
        }
        return result;
    }

    private static boolean isIntermediateRecipe(EmiRecipe recipe, List<EmiRecipe> recipes) {
        for (EmiRecipe consumer : recipes) {
            if (consumer == recipe || consumer.getId() == null || recipe.getId() == null || consumer.getId().equals(recipe.getId())) {
                continue;
            }
            for (EmiIngredient input : consumer.getInputs()) {
                if (input == null || input.isEmpty()) {
                    continue;
                }
                for (EmiStack output : recipe.getOutputs()) {
                    if (accepts(input, output)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void sendGroupToChat(EmiFavoriteGroups.Group group) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        ChainPlan plan = EmiFavoriteGroups.calculatePlan(group, null);
        StringBuilder builder = new StringBuilder(PlannerText.tr("bookmark.share.title", "Bookmark Group: "));
        if (!plan.results.isEmpty()) {
            for (int i = 0; i < plan.results.size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                AmountEntry entry = plan.results.get(i);
                builder.append(entry.amount()).append("x ").append(ingredientName(entry.ingredient()));
            }
        } else {
            builder.append(PlannerText.tr("bookmark.share.count", "%s bookmarks", group.members().size()));
        }
        String message = builder.substring(0, Math.min(240, builder.length()));
        client.player.connection.sendChat(message);
    }

    private static boolean pullItems(EmiFavoriteGroups.Group group, boolean missingOnly) {
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof AbstractContainerScreen<?> handled) || client.player == null || client.gameMode == null) {
            return false;
        }
        List<AmountEntry> entries = pullEntries(group, client.player, missingOnly);
        List<PullStep> steps = new ArrayList<>();
        for (AmountEntry entry : entries) {
            if (entry == null || entry.ingredient() == null || entry.ingredient().isEmpty() || entry.amount() <= 0L) {
                continue;
            }
            long amount = pullAmount(entry.ingredient(), entry.amount());
            if (amount > 0L) {
                steps.add(new PullStep(normalized(entry.ingredient()), amount));
            }
        }
        if (steps.isEmpty()) {
            return false;
        }
        boolean mePull = isMePullContext(handled);
        if (mePull && !handled.getMenu().getCarried().isEmpty()) {
            return false;
        }
        autoCraftJob = null;
        meAutoCraftJob = null;
        cancelPullJob();
        pullJob = new PullJob(group, missingOnly, handled, handled.getMenu().containerId, steps, mePull);
        playSound();
        return true;
    }

    private static List<AmountEntry> pullEntries(EmiFavoriteGroups.Group group, net.minecraft.world.entity.player.Player player, boolean missingOnly) {
        boolean hasRecipe = false;
        for (EmiFavorite favorite : group.members()) {
            if (favorite.getRecipe() != null && favorite.getRole() != Role.ITEM) {
                hasRecipe = true;
                break;
            }
        }
        if (hasRecipe) {
            EmiPlayerInventory inventory;
            if (missingOnly) {
                inventory = playerOnlyInventory(player);
            } else {
                inventory = new EmiPlayerInventory(List.of());
                inventory.inventory.clear();
            }
            return buildMissingCraftPlan(group, inventory, false).missing;
        }
        ChainPlan plan = EmiFavoriteGroups.calculatePlan(group, null);
        if (!missingOnly) {
            return plan.results;
        }
        List<AmountEntry> missing = new ArrayList<>();
        for (AmountEntry entry : plan.results) {
            long amount = Math.max(0L, entry.amount() - available(entry.ingredient()));
            if (amount > 0L) {
                missing.add(new AmountEntry(entry.ingredient(), amount));
            }
        }
        return List.copyOf(missing);
    }

    private static EmiPlayerInventory playerOnlyInventory(net.minecraft.world.entity.player.Player player) {
        List<EmiStack> stacks = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            if (stack != null && !stack.isEmpty()) {
                stacks.add(EmiStack.of(stack.copy()));
            }
        }
        return new EmiPlayerInventory(stacks);
    }

    private static long pullAmount(EmiIngredient ingredient, long amount) {
        for (EmiStack option : ingredient.getEmiStacks()) {
            if (EmiCraftingToolCompat.isReusable(option)) {
                return 1L;
            }
        }
        return Math.max(0L, amount);
    }

    private static boolean craftItems(EmiFavoriteGroups.Group group) {
        return startCraft(group, false);
    }

    private static boolean craftMissing(EmiFavoriteGroups.Group group) {
        return startCraft(group, true);
    }

    private static boolean startCraft(EmiFavoriteGroups.Group group, boolean missingOnly) {
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof AbstractContainerScreen<?> handled) || client.player == null) {
            return false;
        }
        List<CraftStep> fullSteps = buildCraftSteps(group);
        if (fullSteps.isEmpty()) {
            return false;
        }
        boolean meContext = isMeCraftContext(fullSteps.get(0).recipe, handled);
        List<CraftStep> steps;
        if (missingOnly && meContext) {
            var handler = EmiRecipeFiller.getFirstValidHandler(fullSteps.get(0).recipe, (AbstractContainerScreen) handled);
            if (handler == null) {
                return false;
            }
            EmiPlayerInventory inventory = handler.getInventory((AbstractContainerScreen) handled);
            steps = buildMissingCraftSteps(group, inventory);
        } else if (missingOnly) {
            steps = buildMissingCraftSteps(group, EmiPlayerInventory.of(client.player));
        } else {
            steps = fullSteps;
        }
        if (steps.isEmpty()) {
            return false;
        }
        if (meContext) {
            for (CraftStep step : steps) {
                if (firstHandler(step.recipe, handled) == null) {
                    return false;
                }
            }
            cancelPullJob();
            autoCraftJob = null;
            meAutoCraftJob = new MeAutoCraftJob(group, missingOnly, handled, handled.getMenu().containerId, steps);
            playSound();
            return true;
        }
        for (CraftStep step : steps) {
            if (!canAutoCraft(step.recipe, handled)) {
                return false;
            }
        }
        cancelPullJob();
        meAutoCraftJob = null;
        autoCraftJob = new AutoCraftJob(group, missingOnly, handled, handled.getMenu().containerId, steps);
        playSound();
        return true;
    }

    public static void tick() {
        if (meAutoCraftJob != null) {
            tickMeAutoCraftJob();
            return;
        }
        AutoCraftJob job = autoCraftJob;
        if (job == null) {
            tickPullJob();
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof AbstractContainerScreen<?> handled) || handled != job.screen
                || handled.getMenu().containerId != job.syncId || client.player == null) {
            autoCraftJob = null;
            return;
        }
        if (job.waitTicks > 0) {
            job.waitTicks--;
            return;
        }
        if (job.index >= job.steps.size()) {
            autoCraftJob = null;
            return;
        }
        CraftStep step = job.steps.get(job.index);
        long available = available(step.output);
        if (step.target < 0L) {
            step.target = safeAdd(available, step.amount);
        }
        if (available >= step.target) {
            advance(job);
            return;
        }
        if (step.phase == 1) {
            Slot output = outputSlot(step.recipe, handled);
            if (output != null && !output.getItem().isEmpty() && matchesOutput(step.output, EmiStack.of(output.getItem()))) {
                long shown = Math.max(1L, output.getItem().getCount());
                if (step.outputPerBatch <= 0L) {
                    step.outputPerBatch = step.pendingBatches == 1 ? shown : Math.max(1L, shown / Math.max(1, step.pendingBatches));
                }
                if (takeOutput(step.recipe, handled)) {
                    step.phase = 2;
                    step.pendingTicks = 0;
                    job.waitTicks = 1;
                    return;
                }
            }
            step.pendingTicks++;
            if (step.pendingTicks >= 40) {
                autoCraftJob = null;
            }
            return;
        }
        if (step.phase == 2) {
            if (available > step.pendingAvailable) {
                long produced = available - step.pendingAvailable;
                if (step.outputPerBatch <= 0L) {
                    step.outputPerBatch = Math.max(1L, produced / Math.max(1, step.pendingBatches));
                }
                step.phase = 0;
                step.pendingTicks = 0;
                step.failures = 0;
                if (available >= step.target) {
                    advance(job);
                } else {
                    job.waitTicks = 1;
                }
                return;
            }
            step.pendingTicks++;
            if (step.pendingTicks >= 40) {
                autoCraftJob = null;
            }
            return;
        }
        long missing = Math.max(1L, step.target - available);
        long batches = step.outputPerBatch > 0L ? ceilDiv(missing, step.outputPerBatch) : 1L;
        int amount = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, batches));
        if (performFill(step.recipe, handled, amount)) {
            step.phase = 1;
            step.pendingAvailable = available;
            step.pendingBatches = amount;
            step.pendingTicks = 0;
            job.waitTicks = 2;
            return;
        }
        step.failures++;
        if (step.failures >= 3) {
            autoCraftJob = null;
        } else {
            job.waitTicks = 2;
        }
    }

    private static void tickMeAutoCraftJob() {
        MeAutoCraftJob job = meAutoCraftJob;
        if (job == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof AbstractContainerScreen<?> handled) || handled != job.screen
                || handled.getMenu().containerId != job.syncId || client.player == null) {
            meAutoCraftJob = null;
            return;
        }
        if (job.waitTicks > 0) {
            job.waitTicks--;
            return;
        }
        if (job.index >= job.steps.size()) {
            meAutoCraftJob = null;
            return;
        }

        CraftStep step = job.steps.get(job.index);
        long available = available(step.output);
        if (step.target < 0L) {
            step.target = safeAdd(available, step.amount);
        }
        if (step.phase == 2) {
            tickMeGridSettle(job, step, handled);
            return;
        }

        if (step.phase == 1) {
            long expectedProduced = safeMultiply(Math.max(1L, step.outputPerBatch), Math.max(1, step.pendingBatches));
            long expectedAvailable = safeAdd(step.pendingAvailable, expectedProduced);
            if (available >= expectedAvailable) {
                step.phase = 0;
                step.pendingTicks = 0;
                step.failures = 0;
                if (available >= step.target) {
                    finishMeStep(job, step, handled);
                } else {
                    job.waitTicks = 1;
                }
                return;
            }

            Slot output = outputSlot(step.recipe, handled);
            if (output != null && !output.getItem().isEmpty()
                    && matchesOutput(step.output, EmiStack.of(output.getItem())) && takeOutput(step.recipe, handled)) {
                job.waitTicks = 2;
                step.pendingTicks++;
                return;
            }

            step.pendingTicks++;
            if (step.pendingTicks >= 100) {
                meAutoCraftJob = null;
            }
            return;
        }

        if (available >= step.target) {
            finishMeStep(job, step, handled);
            return;
        }

        long missing = Math.max(1L, step.target - available);
        long perBatch = step.outputPerBatch > 0L ? step.outputPerBatch : matchingOutputAmount(step.recipe, step.output);
        perBatch = Math.max(1L, perBatch);
        long batches = ceilDiv(missing, perBatch);
        int amount = (int) Math.min(ME_BURST_LIMIT, Math.max(1L, batches));
        int submitted = performMeFill(step.recipe, handled, amount);
        if (submitted > 0) {
            step.outputPerBatch = perBatch;
            step.phase = 1;
            step.pendingAvailable = available;
            step.pendingBatches = submitted;
            step.pendingTicks = 0;
            job.waitTicks = 2;
            return;
        }
        step.failures++;
        if (step.failures >= 3) {
            meAutoCraftJob = null;
        } else {
            job.waitTicks = 2;
        }
    }

    private static void finishMeStep(MeAutoCraftJob job, CraftStep step, AbstractContainerScreen<?> handled) {
        if (needsMeGridSettle(job, step)) {
            step.phase = 2;
            step.settleTicks = 0;
            step.settleStableTicks = 0;
            tickMeGridSettle(job, step, handled);
        } else {
            advanceMe(job);
        }
    }

    private static boolean needsMeGridSettle(MeAutoCraftJob job, CraftStep step) {
        if (job.index + 1 >= job.steps.size()) {
            return false;
        }
        return recipesShareReusableInput(step.recipe, job.steps.get(job.index + 1).recipe);
    }

    private static boolean recipesShareReusableInput(EmiRecipe first, EmiRecipe second) {
        if (first == null || second == null) {
            return false;
        }
        for (EmiIngredient a : first.getInputs()) {
            if (a == null || a.isEmpty()) {
                continue;
            }
            for (EmiStack left : a.getEmiStacks()) {
                if (!isReusable(left)) {
                    continue;
                }
                for (EmiIngredient b : second.getInputs()) {
                    if (b == null || b.isEmpty()) {
                        continue;
                    }
                    for (EmiStack right : b.getEmiStacks()) {
                        if (isReusable(right) && (EmiCraftingToolCompat.matches(left, right)
                                || EmiCraftingToolCompat.matches(right, left))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static void tickMeGridSettle(MeAutoCraftJob job, CraftStep step, AbstractContainerScreen<?> handled) {
        step.settleTicks++;
        if (step.settleTicks >= ME_GRID_SETTLE_LIMIT) {
            meAutoCraftJob = null;
            return;
        }
        boolean empty = clearMeCraftingGrid(step.recipe, handled);
        if (!empty) {
            step.settleStableTicks = 0;
            job.waitTicks = 1;
            return;
        }
        step.settleStableTicks++;
        if (step.settleStableTicks < ME_GRID_SETTLE_STABLE_TICKS) {
            job.waitTicks = 1;
            return;
        }
        CraftStep next = job.index + 1 < job.steps.size() ? job.steps.get(job.index + 1) : null;
        if (next != null && !meReusableInputsReady(next.recipe, handled)) {
            job.waitTicks = 1;
            return;
        }
        step.phase = 0;
        step.settleTicks = 0;
        step.settleStableTicks = 0;
        advanceMe(job);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean clearMeCraftingGrid(EmiRecipe recipe, AbstractContainerScreen<?> handled) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null) {
            return false;
        }
        var handler = EmiRecipeFiller.getFirstValidHandler(recipe, (AbstractContainerScreen) handled);
        if (!(handler instanceof StandardRecipeHandler standard)) {
            return true;
        }
        List<Slot> slots = standard.getCraftingSlots(recipe, handled.getMenu());
        boolean empty = true;
        for (Slot slot : slots) {
            if (slot == null || slot.getItem().isEmpty()) {
                continue;
            }
            empty = false;
            client.gameMode.handleInventoryMouseClick(handled.getMenu().containerId, slot.index, 0, ClickType.QUICK_MOVE, client.player);
        }
        return empty;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean meReusableInputsReady(EmiRecipe recipe, AbstractContainerScreen<?> handled) {
        var handler = EmiRecipeFiller.getFirstValidHandler(recipe, (AbstractContainerScreen) handled);
        if (handler == null) {
            return false;
        }
        EmiPlayerInventory inventory = handler.getInventory((AbstractContainerScreen) handled);
        if (inventory == null) {
            return false;
        }

        List<EmiIngredient> reusable = new ArrayList<>();
        for (EmiIngredient ingredient : recipe.getInputs()) {
            if (ingredient != null && !ingredient.isEmpty() && hasReusableOption(ingredient)) {
                reusable.add(ingredient);
            }
        }
        if (reusable.isEmpty()) {
            return true;
        }
        return reserveMeReusableInputs(reusable, 0, inventory, new IdentityHashMap<>());
    }

    private static boolean reserveMeReusableInputs(List<EmiIngredient> ingredients, int index,
            EmiPlayerInventory inventory, Map<EmiStack, Long> reserved) {
        if (index >= ingredients.size()) {
            return true;
        }
        EmiIngredient ingredient = ingredients.get(index);
        for (EmiStack expected : ingredient.getEmiStacks()) {
            if (expected == null || expected.isEmpty() || !isReusable(expected)) {
                continue;
            }
            long requiredInstances = Math.max(1L, expected.getAmount());
            for (EmiStack candidate : inventory.inventory.values()) {
                if (candidate == null || candidate.isEmpty() || !EmiCraftingToolCompat.matches(expected, candidate)) {
                    continue;
                }
                long alreadyReserved = reserved.getOrDefault(candidate, 0L);
                if (candidate.getAmount() - alreadyReserved < requiredInstances) {
                    continue;
                }
                if (EmiCraftingToolCompat.isGtTool(candidate)
                        && EmiCraftingToolCompat.getSafeCraftingUses(candidate) <= 0L) {
                    continue;
                }

                reserved.put(candidate, safeAdd(alreadyReserved, requiredInstances));
                if (reserveMeReusableInputs(ingredients, index + 1, inventory, reserved)) {
                    return true;
                }
                if (alreadyReserved == 0L) {
                    reserved.remove(candidate);
                } else {
                    reserved.put(candidate, alreadyReserved);
                }
            }
        }
        return false;
    }

    private static void advanceMe(MeAutoCraftJob job) {
        job.index++;
        job.waitTicks = 1;
        if (job.index >= job.steps.size()) {
            meAutoCraftJob = null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int performMeFill(EmiRecipe recipe, AbstractContainerScreen<?> handled, int amount) {
        var handler = EmiRecipeFiller.getFirstValidHandler(recipe, (AbstractContainerScreen) handled);
        if (handler == null) {
            return 0;
        }
        EmiPlayerInventory inventory = handler.getInventory((AbstractContainerScreen) handled);
        boolean ae2Handler = isAe2UseCraftingHandler(handler);
        int requests = ae2Handler ? Math.max(1, amount) : 1;
        int contextAmount = requests > 1 ? 1 : Math.max(1, amount);
        int submitted = 0;
        for (int i = 0; i < requests; i++) {
            EmiCraftContext context = new EmiCraftContext((AbstractContainerScreen) handled, inventory,
                    EmiCraftContext.Type.CRAFTABLE, EmiCraftContext.Destination.INVENTORY, contextAmount);
            boolean crafted;
            if (ae2Handler) {
                crafted = handler.craft(recipe, context);
            } else if (handler instanceof StandardRecipeHandler standard) {
                List<ItemStack> stacks = EmiRecipeFiller.getStacks(standard, recipe, (AbstractContainerScreen) handled, contextAmount);
                if (stacks == null) {
                    break;
                }
                crafted = standard.craft(recipe, context);
            } else {
                crafted = handler.craft(recipe, context);
            }
            if (!crafted) {
                break;
            }
            submitted++;
        }
        return submitted;
    }

    private static boolean isAe2UseCraftingHandler(Object handler) {
        if (handler == null) {
            return false;
        }
        String name = handler.getClass().getName();
        return name.equals("appeng.integration.modules.emi.EmiUseCraftingRecipeHandler")
                || name.endsWith(".EmiUseCraftingRecipeHandler");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object firstHandler(EmiRecipe recipe, AbstractContainerScreen<?> handled) {
        return EmiRecipeFiller.getFirstValidHandler(recipe, (AbstractContainerScreen) handled);
    }

    private static boolean isMeCraftContext(EmiRecipe recipe, AbstractContainerScreen<?> handled) {
        return isMeLikeClass(firstHandler(recipe, handled)) || isMeLikeClass(handled) || isMeLikeClass(handled.getMenu());
    }

    private static boolean isMeLikeClass(Object object) {
        if (object == null) {
            return false;
        }
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            if (isMeLikeClassName(type.getName())) {
                return true;
            }
            for (Class<?> iface : type.getInterfaces()) {
                if (isMeLikeClassName(iface.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isMeLikeClassName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("appeng.")) {
            return true;
        }
        return lower.startsWith("com.gtocore.") && lower.contains("terminal")
                && (lower.contains("ae") || lower.contains("me") || lower.contains("storage"));
    }


    private static void tickPullJob() {
        PullJob job = pullJob;
        if (job == null) {
            return;
        }
        if (job.mePull) {
            tickMePullJob(job);
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof AbstractContainerScreen<?> handled) || handled != job.screen
                || handled.getMenu().containerId != job.syncId || client.player == null || client.gameMode == null) {
            pullJob = null;
            return;
        }
        if (job.waitTicks > 0) {
            job.waitTicks--;
            return;
        }
        if (job.index >= job.steps.size()) {
            pullJob = null;
            return;
        }
        PullStep step = job.steps.get(job.index);
        long available = available(step.ingredient);
        if (step.target < 0L) {
            step.target = safeAdd(available, step.amount);
        }
        if (available >= step.target) {
            job.index++;
            job.waitTicks = 1;
            return;
        }
        if (step.waiting) {
            if (available > step.pendingAvailable) {
                step.waiting = false;
                step.pendingTicks = 0;
                step.failures = 0;
                job.waitTicks = 1;
                return;
            }
            step.pendingTicks++;
            if (step.pendingTicks >= 30) {
                step.waiting = false;
                step.pendingTicks = 0;
                step.failures++;
            }
            if (step.failures >= 3) {
                pullJob = null;
            }
            return;
        }
        long remaining = Math.max(1L, step.target - available);
        Slot source = findPullSlot(handled, step.ingredient, remaining);
        if (source == null) {
            pullJob = null;
            return;
        }
        step.pendingAvailable = available;
        if (!pullFromSlot(handled, source, remaining)) {
            pullJob = null;
            return;
        }
        step.waiting = true;
        step.pendingTicks = 0;
        job.waitTicks = 1;
    }


    private static void tickMePullJob(PullJob job) {
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof AbstractContainerScreen<?> handled) || handled != job.screen
                || handled.getMenu().containerId != job.syncId || client.player == null || client.gameMode == null) {
            pullJob = null;
            return;
        }
        if (job.waitTicks > 0) {
            job.waitTicks--;
            return;
        }
        if (job.index >= job.steps.size()) {
            pullJob = null;
            return;
        }

        PullStep step = job.steps.get(job.index);
        long playerAmount = available(step.ingredient);
        if (step.target < 0L) {
            step.target = safeAdd(playerAmount, step.amount);
        }

        if (step.mePhase == ME_PULL_IDLE) {
            tickMePullIdle(job, handled, step, playerAmount);
        } else if (step.mePhase == ME_PULL_WAIT_SHIFT) {
            tickMePullShift(job, step, playerAmount);
        } else if (step.mePhase == ME_PULL_WAIT_CURSOR) {
            tickMePullCursor(job, handled, step, playerAmount, client);
        } else if (step.mePhase == ME_PULL_WAIT_INSERT) {
            tickMePullInsert(job, handled, step, playerAmount);
        } else {
            pullJob = null;
        }
    }

    private static void tickMePullIdle(PullJob job, AbstractContainerScreen<?> handled, PullStep step, long playerAmount) {
        if (playerAmount >= step.target) {
            job.index++;
            job.waitTicks = 1;
            return;
        }

        long remaining = Math.max(1L, step.target - playerAmount);
        MePullEntry entry = findMePullEntry(handled, step.ingredient);
        if (entry == null || entry.storedAmount <= 0L) {
            pullJob = null;
            return;
        }

        ItemStack sample = entry.stack.isEmpty() ? firstItemStack(step.ingredient) : entry.stack;
        if (sample.isEmpty() || playerCapacity(handled, sample) <= 0L) {
            pullJob = null;
            return;
        }

        int maxStack = Math.max(1, sample.getMaxStackSize());
        if (remaining >= maxStack) {
            step.pendingAvailable = playerAmount;
            if (!sendMeInteraction(handled, entry.serial, "SHIFT_CLICK")) {
                pullJob = null;
                return;
            }
            step.mePhase = ME_PULL_WAIT_SHIFT;
            step.pendingTicks = 0;
            job.waitTicks = 1;
            return;
        }

        if (!handled.getMenu().getCarried().isEmpty()) {
            pullJob = null;
            return;
        }

        int destinationCapacity = bestPlayerSlotCapacity(handled, sample);
        int count = (int) Math.min(Math.min(remaining, entry.storedAmount), Math.min(maxStack, destinationCapacity));
        if (count <= 0) {
            pullJob = null;
            return;
        }

        int sent = 0;
        for (int i = 0; i < count; i++) {
            if (!sendMeInteraction(handled, entry.serial, "PICKUP_SINGLE")) {
                break;
            }
            sent++;
        }
        if (sent <= 0) {
            pullJob = null;
            return;
        }

        step.mePhase = ME_PULL_WAIT_CURSOR;
        step.meCursorExpected = sent;
        step.meCursorLastCount = 0;
        step.meCursorStableTicks = 0;
        step.pendingTicks = 0;
        job.waitTicks = 1;
    }

    private static void tickMePullShift(PullJob job, PullStep step, long playerAmount) {
        if (playerAmount > step.pendingAvailable) {
            resetMePullPhase(step);
            job.waitTicks = 1;
            return;
        }
        if (++step.pendingTicks >= ME_PULL_TIMEOUT_TICKS) {
            if (++step.failures >= 3) {
                pullJob = null;
            } else {
                resetMePullPhase(step);
            }
        }
    }

    private static void tickMePullCursor(PullJob job, AbstractContainerScreen<?> handled, PullStep step, long playerAmount,
            Minecraft client) {
        ItemStack cursor = handled.getMenu().getCarried();
        if (!cursor.isEmpty()) {
            if (!matchesOutput(step.ingredient, EmiStack.of(cursor))) {
                pullJob = null;
                return;
            }

            int count = cursor.getCount();
            if (count == step.meCursorLastCount) {
                step.meCursorStableTicks++;
            } else {
                step.meCursorLastCount = count;
                step.meCursorStableTicks = 0;
            }

            if (count >= step.meCursorExpected || step.meCursorStableTicks >= ME_PULL_CURSOR_STABLE_TICKS) {
                Slot destination = findPlayerDestinationSlot(handled, cursor, count);
                if (destination == null) {
                    returnMeCursorToNetwork(handled);
                    pullJob = null;
                    return;
                }
                step.pendingAvailable = playerAmount;
                client.gameMode.handleInventoryMouseClick(handled.getMenu().containerId, destination.index, 0,
                        ClickType.PICKUP, client.player);
                step.mePhase = ME_PULL_WAIT_INSERT;
                step.pendingTicks = 0;
                job.waitTicks = 1;
                return;
            }
        }

        if (++step.pendingTicks >= ME_PULL_TIMEOUT_TICKS) {
            if (!handled.getMenu().getCarried().isEmpty()) {
                returnMeCursorToNetwork(handled);
            }
            pullJob = null;
        }
    }

    private static void tickMePullInsert(PullJob job, AbstractContainerScreen<?> handled, PullStep step, long playerAmount) {
        if (handled.getMenu().getCarried().isEmpty() && playerAmount > step.pendingAvailable) {
            resetMePullPhase(step);
            job.waitTicks = 1;
            return;
        }
        if (++step.pendingTicks >= ME_PULL_TIMEOUT_TICKS) {
            if (!handled.getMenu().getCarried().isEmpty()) {
                returnMeCursorToNetwork(handled);
            }
            pullJob = null;
        }
    }

    private static void resetMePullPhase(PullStep step) {
        step.mePhase = ME_PULL_IDLE;
        step.pendingTicks = 0;
        step.failures = 0;
        step.meCursorExpected = 0;
        step.meCursorLastCount = 0;
        step.meCursorStableTicks = 0;
    }

    private static boolean isMePullContext(AbstractContainerScreen<?> handled) {
        if (handled == null || (!isMeLikeClass(handled) && !isMeLikeClass(handled.getMenu()))) {
            return false;
        }
        Object menu = handled.getMenu();
        return findMethod(menu.getClass(), "getClientRepo", 0) != null && findMethod(menu.getClass(), "handleInteraction", 2) != null;
    }

    private static MePullEntry findMePullEntry(AbstractContainerScreen<?> handled, EmiIngredient ingredient) {
        try {
            Object menu = handled.getMenu();
            Object repo = invokeNoArgs(menu, "getClientRepo");
            if (repo == null) {
                return null;
            }
            Ingredient vanilla = toVanillaIngredient(ingredient);
            if (vanilla == null || vanilla.isEmpty()) {
                return null;
            }
            Method method = findCompatibleMethod(repo.getClass(), "getByIngredient", vanilla);
            if (method == null) {
                return null;
            }
            Object raw = invoke(method, repo, vanilla);
            MePullEntry best = null;
            for (Object entry : iterableValues(raw)) {
                long stored = reflectedLong(entry, "getStoredAmount", "storedAmount");
                long serial = reflectedLong(entry, "getSerial", "serial");
                if (stored <= 0L || serial < 0L) {
                    continue;
                }
                ItemStack stack = itemStackFromMeEntry(entry);
                if (stack.isEmpty()) {
                    stack = firstItemStack(ingredient);
                }
                if (best == null || stored > best.storedAmount) {
                    best = new MePullEntry(serial, stored, stack);
                }
            }
            return best;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Ingredient toVanillaIngredient(EmiIngredient ingredient) {
        List<ItemStack> stacks = new ArrayList<>();
        if (ingredient != null) {
            for (EmiStack option : ingredient.getEmiStacks()) {
                ItemStack stack = option.getItemStack();
                if (stack != null && !stack.isEmpty()) {
                    ItemStack copy = stack.copy();
                    copy.setCount(1);
                    stacks.add(copy);
                }
            }
        }
        if (stacks.isEmpty()) {
            return null;
        }
        return Ingredient.of(stacks.toArray(ItemStack[]::new));
    }

    private static ItemStack firstItemStack(EmiIngredient ingredient) {
        if (ingredient == null) {
            return ItemStack.EMPTY;
        }
        for (EmiStack option : ingredient.getEmiStacks()) {
            ItemStack stack = option.getItemStack();
            if (stack != null && !stack.isEmpty()) {
                ItemStack copy = stack.copy();
                copy.setCount(1);
                return copy;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack itemStackFromMeEntry(Object entry) {
        Object value = invokeNoArgs(entry, "getWhat", "what", "getStack", "getItemStack");
        if (value instanceof ItemStack stack) {
            ItemStack copy = stack.copy();
            copy.setCount(1);
            return copy;
        }
        if (value != null) {
            Object stack = invokeNoArgs(value, "toStack", "getItemStack");
            if (stack instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                ItemStack copy = itemStack.copy();
                copy.setCount(1);
                return copy;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean sendMeInteraction(AbstractContainerScreen<?> handled, long serial, String actionName) {
        try {
            Object menu = handled.getMenu();
            for (Method method : menu.getClass().getMethods()) {
                if (!method.getName().equals("handleInteraction") || method.getParameterCount() != 2) {
                    continue;
                }
                Class<?>[] types = method.getParameterTypes();
                if (types[0] != long.class && types[0] != Long.class) {
                    continue;
                }
                Object action = enumValue(types[1], actionName);
                if (action == null) {
                    continue;
                }
                invoke(method, menu, serial, action);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void returnMeCursorToNetwork(AbstractContainerScreen<?> handled) {
        if (!handled.getMenu().getCarried().isEmpty()) {
            sendMeInteraction(handled, -1L, "PICKUP_OR_SET_DOWN");
        }
    }

    private static void cancelPullJob() {
        PullJob job = pullJob;
        if (job != null && job.mePull) {
            Minecraft client = Minecraft.getInstance();
            if (client.screen == job.screen) {
                PullStep step = job.index >= 0 && job.index < job.steps.size() ? job.steps.get(job.index) : null;
                if (!job.screen.getMenu().getCarried().isEmpty() || step != null && step.mePhase == ME_PULL_WAIT_CURSOR) {
                    sendMeInteraction(job.screen, -1L, "PICKUP_OR_SET_DOWN");
                }
            }
        }
        pullJob = null;
    }

    private static Object enumValue(Class<?> type, String name) {
        if (type == null || !type.isEnum()) {
            return null;
        }
        for (Object value : type.getEnumConstants()) {
            if (value instanceof Enum<?> e && e.name().equals(name)) {
                return value;
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) {
        if (type == null) {
            return null;
        }
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        return null;
    }

    private static Method findCompatibleMethod(Class<?> type, String name, Object argument) {
        if (type == null || argument == null) {
            return null;
        }
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(argument.getClass())) {
                return method;
            }
        }
        return null;
    }

    private static Object invokeNoArgs(Object target, String... names) {
        if (target == null) {
            return null;
        }
        for (String name : names) {
            Method method = findMethod(target.getClass(), name, 0);
            if (method == null) {
                continue;
            }
            try {
                return invoke(method, target);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object invoke(Method method, Object target, Object... arguments) throws Exception {
        if (!method.canAccess(target)) {
            method.trySetAccessible();
        }
        return method.invoke(target, arguments);
    }

    private static long reflectedLong(Object target, String... names) {
        Object value = invokeNoArgs(target, names);
        return value instanceof Number number ? number.longValue() : -1L;
    }

    private static List<Object> iterableValues(Object value) {
        List<Object> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                result.add(entry);
            }
        } else if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                result.add(Array.get(value, i));
            }
        }
        return result;
    }

    private static void advance(AutoCraftJob job) {
        job.index++;
        job.waitTicks = 1;
        if (job.index >= job.steps.size()) {
            autoCraftJob = null;
        }
    }

    private static List<CraftStep> buildCraftSteps(EmiFavoriteGroups.Group group) {
        LinkedHashMap<ResourceLocation, EmiRecipe> unique = new LinkedHashMap<>();
        for (EmiFavorite favorite : group.members()) {
            EmiRecipe recipe = favorite.getRecipe();
            if (recipe != null && recipe.getId() != null && favorite.getRole() != Role.ITEM) {
                unique.putIfAbsent(recipe.getId(), recipe);
            }
        }
        if (unique.isEmpty()) {
            return List.of();
        }
        List<EmiRecipe> recipes = new ArrayList<>(unique.values());
        List<EmiRecipe> order = craftingOrder(recipes);
        ChainPlan plan = group.craftingChain ? EmiFavoriteGroups.calculatePlan(group, null) : ChainPlan.EMPTY;
        List<CraftStep> steps = new ArrayList<>();
        for (EmiRecipe recipe : order) {
            if (group.craftingChain && plan.batchesFor(recipe) <= 0L) {
                continue;
            }
            if (!group.craftingChain && EmiFavoriteGroups.recipeQuantity(group, recipe) <= 0L) {
                continue;
            }
            EmiFavorite resultFavorite = resultFavorite(group, recipe);
            EmiIngredient output;
            if (resultFavorite != null && !resultFavorite.isEmpty()) {
                output = normalized(resultFavorite.getStack());
            } else if (!recipe.getOutputs().isEmpty() && recipe.getOutputs().get(0) != null
                    && !recipe.getOutputs().get(0).isEmpty()) {
                output = normalized(recipe.getOutputs().get(0));
            } else {
                continue;
            }
            long amount;
            if (resultFavorite != null) {
                if (group.craftingChain) {
                    amount = Math.max(1L, plan.requiredFavorites.getOrDefault(resultFavorite, resultFavorite.getAmount()));
                } else {
                    amount = Math.max(0L, resultFavorite.getAmount());
                }
            } else {
                long batches = group.craftingChain ? plan.batchesFor(recipe)
                        : safeMultiply(Math.max(1L, group.quantity), EmiFavoriteGroups.recipeQuantity(group, recipe));
                long perBatch = 1L;
                for (EmiStack candidate : recipe.getOutputs()) {
                    if (accepts(output, candidate)) {
                        perBatch = Math.max(perBatch, candidate.getAmount());
                    }
                }
                amount = safeMultiply(perBatch, Math.max(0L, batches));
            }
            if (amount <= 0L) {
                continue;
            }
            steps.add(new CraftStep(recipe, output, amount));
        }
        return steps;
    }

    private static List<CraftStep> buildMissingCraftSteps(EmiFavoriteGroups.Group group, EmiPlayerInventory inventory) {
        return buildMissingCraftPlan(group, inventory, true).steps;
    }

    private static MissingCraftPlan buildMissingCraftPlan(EmiFavoriteGroups.Group group, EmiPlayerInventory inventory, boolean strict) {
        LinkedHashMap<ResourceLocation, EmiRecipe> unique = new LinkedHashMap<>();
        for (EmiFavorite favorite : group.members()) {
            EmiRecipe recipe = favorite.getRecipe();
            if (recipe != null && recipe.getId() != null && favorite.getRole() != Role.ITEM) {
                unique.putIfAbsent(recipe.getId(), recipe);
            }
        }
        if (unique.isEmpty()) {
            return MissingCraftPlan.EMPTY;
        }
        List<CraftStep> fullSteps = buildCraftSteps(group);
        if (fullSteps.isEmpty()) {
            return MissingCraftPlan.EMPTY;
        }
        List<EmiRecipe> recipes = new ArrayList<>();
        for (CraftStep step : fullSteps) {
            if (step.recipe != null && step.recipe.getId() != null) {
                recipes.add(step.recipe);
            }
        }
        MissingCraftPlanner planner = new MissingCraftPlanner(recipes, inventory, strict);
        for (EmiRecipe root : rootRecipes(recipes)) {
            CraftStep full = stepFor(fullSteps, root);
            if (full == null || full.amount <= 0L) {
                continue;
            }
            long remaining = planner.inventory.consume(full.output, full.amount);
            if (remaining <= 0L) {
                continue;
            }
            long perBatch = matchingOutputAmount(root, full.output);
            long batches = ceilDiv(remaining, perBatch);
            if (!planner.plan(root, batches) && strict) {
                return MissingCraftPlan.EMPTY;
            }
            long extra = safeMultiply(perBatch, batches) - remaining;
            if (extra > 0L) {
                planner.inventory.addMatchingOutput(root, full.output, extra);
            }
        }
        List<CraftStep> steps = new ArrayList<>();
        for (EmiRecipe recipe : craftingOrder(recipes)) {
            long batches = planner.batches.getOrDefault(recipe.getId(), 0L);
            if (batches <= 0L) {
                continue;
            }
            EmiIngredient output = recipeOutput(group, recipe);
            if (output == null || output.isEmpty()) {
                continue;
            }
            long amount = safeMultiply(matchingOutputAmount(recipe, output), batches);
            if (amount > 0L) {
                steps.add(new CraftStep(recipe, output, amount));
            }
        }
        return new MissingCraftPlan(List.copyOf(steps), planner.missingEntries());
    }

    private static List<EmiRecipe> rootRecipes(List<EmiRecipe> recipes) {
        List<EmiRecipe> roots = new ArrayList<>();
        for (EmiRecipe candidate : recipes) {
            boolean consumed = false;
            for (EmiRecipe other : recipes) {
                if (candidate == other || candidate.getId() == null || other.getId() == null
                        || candidate.getId().equals(other.getId())) {
                    continue;
                }
                for (EmiIngredient input : other.getInputs()) {
                    for (EmiStack output : candidate.getOutputs()) {
                        if (accepts(input, output)) {
                            consumed = true;
                            break;
                        }
                    }
                    if (consumed) {
                        break;
                    }
                }
                if (consumed) {
                    break;
                }
            }
            if (!consumed) {
                roots.add(candidate);
            }
        }
        if (roots.isEmpty() && !recipes.isEmpty()) {
            roots.add(recipes.get(0));
        }
        return roots;
    }

    private static CraftStep stepFor(List<CraftStep> steps, EmiRecipe recipe) {
        if (recipe == null || recipe.getId() == null) {
            return null;
        }
        for (CraftStep step : steps) {
            if (step.recipe != null && step.recipe.getId() != null && step.recipe.getId().equals(recipe.getId())) {
                return step;
            }
        }
        return null;
    }

    private static EmiIngredient recipeOutput(EmiFavoriteGroups.Group group, EmiRecipe recipe) {
        EmiFavorite favorite = resultFavorite(group, recipe);
        if (favorite != null && !favorite.isEmpty()) {
            return normalized(favorite.getStack());
        }
        for (EmiStack output : recipe.getOutputs()) {
            if (output != null && !output.isEmpty()) {
                return normalized(output);
            }
        }
        return EmiStack.EMPTY;
    }

    private static long matchingOutputAmount(EmiRecipe recipe, EmiIngredient ingredient) {
        long amount = 0L;
        for (EmiStack output : recipe.getOutputs()) {
            if (output != null && !output.isEmpty() && accepts(ingredient, output)) {
                amount = safeAdd(amount, Math.max(1L, output.getAmount()));
            }
        }
        return Math.max(1L, amount);
    }

    private static List<EmiRecipe> craftingOrder(List<EmiRecipe> recipes) {
        List<EmiRecipe> result = new ArrayList<>();
        Set<ResourceLocation> visiting = new HashSet<>();
        Set<ResourceLocation> done = new HashSet<>();
        for (EmiRecipe recipe : recipes) {
            visitRecipe(recipe, recipes, result, visiting, done);
        }
        return result;
    }

    private static void visitRecipe(EmiRecipe recipe, List<EmiRecipe> recipes, List<EmiRecipe> result,
            Set<ResourceLocation> visiting, Set<ResourceLocation> done) {
        if (recipe == null || recipe.getId() == null || done.contains(recipe.getId())) {
            return;
        }
        if (!visiting.add(recipe.getId())) {
            return;
        }
        for (EmiIngredient input : recipe.getInputs()) {
            EmiRecipe producer = findProducer(recipes, input, recipe);
            if (producer != null) {
                visitRecipe(producer, recipes, result, visiting, done);
            }
        }
        visiting.remove(recipe.getId());
        if (done.add(recipe.getId())) {
            result.add(recipe);
        }
    }

    private static EmiRecipe findProducer(List<EmiRecipe> recipes, EmiIngredient input, EmiRecipe consumer) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        for (EmiRecipe candidate : recipes) {
            if (candidate == consumer || candidate.getId() == null || consumer.getId() == null
                    || candidate.getId().equals(consumer.getId())) {
                continue;
            }
            for (EmiStack output : candidate.getOutputs()) {
                if (accepts(input, output)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static boolean accepts(EmiIngredient ingredient, EmiStack stack) {
        if (ingredient == null || stack == null || stack.isEmpty()) {
            return false;
        }
        for (EmiStack option : ingredient.getEmiStacks()) {
            if (EmiCraftingToolCompat.matches(option, stack)) {
                return true;
            }
        }
        return false;
    }

    private static EmiFavorite resultFavorite(EmiFavoriteGroups.Group group, EmiRecipe recipe) {
        if (recipe == null || recipe.getId() == null) {
            return null;
        }
        for (EmiFavorite favorite : group.members()) {
            if (favorite.getRole() == Role.RESULT && recipe.getId().equals(favorite.getRecipeId())) {
                return favorite;
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean canAutoCraft(EmiRecipe recipe, AbstractContainerScreen<?> handled) {
        return EmiRecipeFiller.getFirstValidHandler(recipe, (AbstractContainerScreen) handled) != null && outputSlot(recipe, handled) != null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean performFill(EmiRecipe recipe, AbstractContainerScreen<?> handled, int amount) {
        var handler = EmiRecipeFiller.getFirstValidHandler(recipe, (AbstractContainerScreen) handled);
        if (handler == null) {
            return false;
        }
        EmiPlayerInventory inventory = handler.getInventory((AbstractContainerScreen) handled);
        EmiCraftContext context = new EmiCraftContext((AbstractContainerScreen) handled, inventory,
                EmiCraftContext.Type.CRAFTABLE, EmiCraftContext.Destination.NONE, amount);

        if (handler instanceof StandardRecipeHandler standard) {
            List<ItemStack> stacks = EmiRecipeFiller.getStacks(standard, recipe, (AbstractContainerScreen) handled, amount);
            if (stacks == null) {
                return false;
            }
            return standard.craft(recipe, context);
        }

        if (!handler.canCraft(recipe, context)) {
            return false;
        }
        return handler.craft(recipe, context);
    }

    private static boolean pullFromSlot(AbstractContainerScreen<?> handled, Slot source, long remaining) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null || source == null) {
            return false;
        }
        ItemStack sourceStack = source.getItem().copy();
        if (sourceStack.isEmpty()) {
            return false;
        }
        long desired = Math.min(remaining, sourceStack.getCount());
        if (desired <= 0L) {
            return false;
        }
        if (sourceStack.getCount() <= desired) {
            client.gameMode.handleInventoryMouseClick(handled.getMenu().containerId, source.index, 0, ClickType.QUICK_MOVE, client.player);
            return true;
        }
        if (!handled.getMenu().getCarried().isEmpty() || !source.mayPlace(sourceStack)) {
            return false;
        }
        if (playerCapacity(handled, sourceStack) < desired) {
            return false;
        }
        client.gameMode.handleInventoryMouseClick(handled.getMenu().containerId, source.index, 0, ClickType.PICKUP, client.player);
        long moved = 0L;
        while (moved < desired) {
            Slot destination = findPlayerDestinationSlot(handled, sourceStack);
            if (destination == null) {
                client.gameMode.handleInventoryMouseClick(handled.getMenu().containerId, source.index, 0, ClickType.PICKUP, client.player);
                return false;
            }
            client.gameMode.handleInventoryMouseClick(handled.getMenu().containerId, destination.index, 1, ClickType.PICKUP, client.player);
            moved++;
        }
        client.gameMode.handleInventoryMouseClick(handled.getMenu().containerId, source.index, 0, ClickType.PICKUP, client.player);
        return handled.getMenu().getCarried().isEmpty();
    }

    private static long playerCapacity(AbstractContainerScreen<?> handled, ItemStack stack) {
        long capacity = 0L;
        for (Slot slot : handled.getMenu().slots) {
            if (slot == null || !(slot.container instanceof Inventory) || !slot.mayPlace(stack)) {
                continue;
            }
            ItemStack current = slot.getItem();
            int max = Math.min(stack.getMaxStackSize(), slot.getMaxStackSize());
            if (current.isEmpty()) {
                capacity = safeAdd(capacity, max);
            } else if (ItemStack.isSameItemSameTags(current, stack)) {
                capacity = safeAdd(capacity, Math.max(0, max - current.getCount()));
            }
        }
        return capacity;
    }

    private static Slot findPlayerDestinationSlot(AbstractContainerScreen<?> handled, ItemStack stack) {
        Slot empty = null;
        for (Slot slot : handled.getMenu().slots) {
            if (slot == null || !(slot.container instanceof Inventory) || !slot.mayPlace(stack)) {
                continue;
            }
            ItemStack current = slot.getItem();
            int max = Math.min(stack.getMaxStackSize(), slot.getMaxStackSize());
            if (!current.isEmpty() && ItemStack.isSameItemSameTags(current, stack) && current.getCount() < max) {
                return slot;
            }
            if (current.isEmpty() && empty == null) {
                empty = slot;
            }
        }
        return empty;
    }


    private static Slot findPlayerDestinationSlot(AbstractContainerScreen<?> handled, ItemStack stack, int amount) {
        Slot empty = null;
        for (Slot slot : handled.getMenu().slots) {
            if (slot == null || !(slot.container instanceof Inventory) || !slot.mayPlace(stack)) {
                continue;
            }
            ItemStack current = slot.getItem();
            int max = Math.min(stack.getMaxStackSize(), slot.getMaxStackSize());
            if (!current.isEmpty() && ItemStack.isSameItemSameTags(current, stack) && max - current.getCount() >= amount) {
                return slot;
            }
            if (current.isEmpty() && max >= amount && empty == null) {
                empty = slot;
            }
        }
        return empty;
    }

    private static int bestPlayerSlotCapacity(AbstractContainerScreen<?> handled, ItemStack stack) {
        int best = 0;
        for (Slot slot : handled.getMenu().slots) {
            if (slot == null || !(slot.container instanceof Inventory) || !slot.mayPlace(stack)) {
                continue;
            }
            ItemStack current = slot.getItem();
            int max = Math.min(stack.getMaxStackSize(), slot.getMaxStackSize());
            if (current.isEmpty()) {
                best = Math.max(best, max);
            } else if (ItemStack.isSameItemSameTags(current, stack)) {
                best = Math.max(best, Math.max(0, max - current.getCount()));
            }
        }
        return best;
    }

    private static Slot findPullSlot(AbstractContainerScreen<?> handled, EmiIngredient ingredient, long remaining) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return null;
        }
        Slot fallback = null;
        long fallbackAmount = Long.MAX_VALUE;
        for (Slot slot : handled.getMenu().slots) {
            if (slot == null || slot.container instanceof Inventory || slot instanceof ResultSlot
                    || !slot.mayPickup(client.player)) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !matchesOutput(ingredient, EmiStack.of(stack))) {
                continue;
            }
            long count = stack.getCount();
            if (count <= remaining) {
                return slot;
            }
            if (count < fallbackAmount) {
                fallbackAmount = count;
                fallback = slot;
            }
        }
        return fallback;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Slot outputSlot(EmiRecipe recipe, AbstractContainerScreen<?> handled) {
        var handler = EmiRecipeFiller.getFirstValidHandler(recipe, (AbstractContainerScreen) handled);
        if (handler instanceof StandardRecipeHandler standard) {
            Slot output = standard.getOutputSlot(handled.getMenu());
            if (output != null) {
                return output;
            }
        }
        for (Slot slot : handled.getMenu().slots) {
            if (slot instanceof ResultSlot) {
                return slot;
            }
        }
        return null;
    }

    private static boolean takeOutput(EmiRecipe recipe, AbstractContainerScreen<?> handled) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null) {
            return false;
        }
        Slot output = outputSlot(recipe, handled);
        if (output == null || output.getItem().isEmpty()) {
            return false;
        }
        client.gameMode.handleInventoryMouseClick(handled.getMenu().containerId, output.index, 0, ClickType.QUICK_MOVE, client.player);
        return true;
    }

    private static boolean matchesOutput(EmiIngredient expected, EmiStack actual) {
        if (expected == null || actual == null || expected.isEmpty() || actual.isEmpty()) {
            return false;
        }
        for (EmiStack option : expected.getEmiStacks()) {
            if (matchesOutput(option, actual)) {
                return true;
            }
        }
        return false;
    }

    private static long available(EmiIngredient ingredient) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || ingredient == null || ingredient.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (var itemStack : client.player.getInventory().items) {
            if (itemStack.isEmpty()) {
                continue;
            }
            EmiStack actual = EmiStack.of(itemStack);
            for (EmiStack option : ingredient.getEmiStacks()) {
                if (matchesOutput(option, actual)) {
                    total = safeAdd(total, itemStack.getCount());
                    break;
                }
            }
        }
        return total;
    }

    private static boolean matchesOutput(EmiStack expected, EmiStack actual) {
        if (expected == null || actual == null || expected.isEmpty() || actual.isEmpty()) {
            return false;
        }
        if (EmiCraftingToolCompat.matches(expected, actual)) {
            return true;
        }
        if (!expected.getKey().equals(actual.getKey())) {
            return false;
        }
        var nbt = expected.getNbt();
        return nbt == null || nbt.isEmpty();
    }

    private static EmiIngredient normalized(EmiIngredient ingredient) {
        try {
            return ingredient.copy().setAmount(1).setChance(1);
        } catch (Throwable ignored) {
            return ingredient;
        }
    }

    private static long ceilDiv(long value, long divisor) {
        if (value <= 0L) {
            return 0L;
        }
        return 1L + (value - 1L) / Math.max(1L, divisor);
    }

    private static long safeMultiply(long a, long b) {
        if (a <= 0L || b <= 0L) {
            return 0L;
        }
        if (a > Long.MAX_VALUE / b) {
            return Long.MAX_VALUE;
        }
        return a * b;
    }

    private static long safeAdd(long a, long b) {
        if (b > 0L && a > Long.MAX_VALUE - b) {
            return Long.MAX_VALUE;
        }
        if (b < 0L && a < Long.MIN_VALUE - b) {
            return Long.MIN_VALUE;
        }
        return a + b;
    }

    private static ClientTooltipComponent line(String text, ChatFormatting formatting) {
        return ClientTooltipComponent.create(EmiPort.literal(text).withStyle(formatting).getVisualOrderText());
    }

    private static String ingredientName(EmiIngredient ingredient) {
        if (ingredient == null || ingredient.getEmiStacks().isEmpty()) {
            return "?";
        }
        for (EmiStack stack : ingredient.getEmiStacks()) {
            if (isReusable(stack)) {
                return stack.getName().getString();
            }
        }
        return ingredient.getEmiStacks().get(0).getName().getString();
    }

    private static boolean hasReusableGtToolOption(EmiIngredient ingredient) {
        if (ingredient == null) {
            return false;
        }
        for (EmiStack stack : ingredient.getEmiStacks()) {
            if (isReusable(stack) && EmiCraftingToolCompat.isGtTool(stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasReusableOption(EmiIngredient ingredient) {
        if (ingredient == null) {
            return false;
        }
        for (EmiStack stack : ingredient.getEmiStacks()) {
            if (isReusable(stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isReusable(EmiStack stack) {
        return EmiCraftingToolCompat.isReusable(stack);
    }

    private static Layout layout() {
        EmiScreenManager.SidebarPanel panel = EmiScreenManager.getPanelFor(SidebarType.FAVORITES);
        if (panel == null || panel.space == null || !panel.isVisible()) {
            return Layout.EMPTY;
        }
        EmiScreenManager.ScreenSpace space = panel.space;
        int pageStart = panel.page * space.pageSize;
        int pageEnd = pageStart + space.pageSize;
        List<GroupBox> boxes = new ArrayList<>();
        List<VisibleSlot> slots = new ArrayList<>();
        List<? extends EmiIngredient> sidebar = space.getStacks();
        for (int global = pageStart; global < Math.min(pageEnd, sidebar.size()); global++) {
            EmiIngredient ingredient = sidebar.get(global);
            if (!(ingredient instanceof EmiFavorite favorite) || favorite.isEmpty() || EmiFavoriteGroups.isSidebarSpacer(favorite)) {
                continue;
            }
            int local = global - pageStart;
            Bounds bounds = new Bounds(space.getRawX(local), space.getRawY(local), EmiScreenManager.ENTRY_SIZE, EmiScreenManager.ENTRY_SIZE);
            slots.add(new VisibleSlot(favorite, global, bounds));
        }
        for (EmiFavoriteGroups.Group group : EmiFavoriteGroups.groups()) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (VisibleSlot slot : slots) {
                if (!containsIdentity(group.members(), slot.favorite)) {
                    continue;
                }
                minX = Math.min(minX, slot.bounds.x());
                minY = Math.min(minY, slot.bounds.y());
                maxX = Math.max(maxX, slot.bounds.right());
                maxY = Math.max(maxY, slot.bounds.bottom());
            }
            if (minX == Integer.MAX_VALUE) {
                continue;
            }
            int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            boolean bracketOnRight = panel.side == SidebarSide.LEFT;
            int bx = bracketOnRight ? maxX + 2 : minX - 10;
            if (bx < 0 || bx + 8 > screenWidth) {
                bracketOnRight = !bracketOnRight;
                bx = bracketOnRight ? maxX + 2 : minX - 10;
            }
            bx = Math.max(0, Math.min(screenWidth - 8, bx));
            Bounds area = new Bounds(minX, minY, Math.max(1, maxX - minX), Math.max(1, maxY - minY));
            Bounds bracket = new Bounds(bx, minY, 8, Math.max(8, maxY - minY));
            int handleY = Math.max(bracket.y(), Math.min(bracket.bottom() - 8, bracket.y() + bracket.height() / 2 - 4));
            Bounds handle = new Bounds(bracket.x(), handleY, 8, 8);
            boxes.add(new GroupBox(group, panel, area, bracket, handle, bracketOnRight));
        }
        return new Layout(panel, space, pageStart, List.copyOf(boxes), List.copyOf(slots));
    }

    private static GroupBox hoveredHandle(Layout layout, int mouseX, int mouseY) {
        for (GroupBox box : layout.boxes) {
            if (box.handle.contains(mouseX, mouseY)) {
                return box;
            }
        }
        return null;
    }

    private static GroupBox groupAtPoint(Layout layout, int mouseX, int mouseY) {
        for (GroupBox box : layout.boxes) {
            if (box.handle.contains(mouseX, mouseY) || box.area.contains(mouseX, mouseY)) {
                return box;
            }
        }
        return null;
    }

    private static VisibleSlot favoriteAt(Layout layout, int mouseX, int mouseY) {
        for (VisibleSlot slot : layout.slots) {
            if (slot.bounds.contains(mouseX, mouseY)) {
                return slot;
            }
        }
        return null;
    }

    private static int rawIndex(EmiFavorite favorite) {
        for (int i = 0; i < EmiFavorites.favorites.size(); i++) {
            if (EmiFavorites.favorites.get(i) == favorite) {
                return i;
            }
        }
        return -1;
    }

    private static boolean containsIdentity(List<EmiFavorite> list, EmiFavorite target) {
        for (EmiFavorite favorite : list) {
            if (favorite == target) {
                return true;
            }
        }
        return false;
    }

    private static void clearDrag() {
        pressedFavorite = null;
        dragFavorite = null;
        pressedButton = -1;
        dragging = false;
        reorderDragging = false;
        externalFavoriteDragging = false;
        includeGroupDragging = false;
    }

    private static int updatePageGroupMovePreview(int mouseX, int mouseY) {
        pageGroupInsertion = -1;
        EmiFavoriteGroups.clearGroupMovePreview();
        if (pressedPageGroup == null) {
            return -1;
        }
        Layout base = layout();
        if (base.panel == null || base.space == null) {
            return -1;
        }
        if (pageDropTarget(base, pressedPageGroup, mouseX, mouseY) >= 0) {
            return -1;
        }
        Bounds grid = new Bounds(base.space.tx, base.space.ty, base.space.tw * EmiScreenManager.ENTRY_SIZE,
                base.space.th * EmiScreenManager.ENTRY_SIZE);
        if (!grid.contains(mouseX, mouseY)) {
            return -1;
        }
        int sidebarEdge = Math.min(base.pageStart + base.space.getClosestEdge(mouseX, mouseY), base.space.getStacks().size());
        int rawInsertion = EmiFavoriteGroups.rawInsertionIndexForSidebarEdge(sidebarEdge);
        int insertion = EmiFavoriteGroups.snapGroupInsertion(pressedPageGroup, rawInsertion);
        if (insertion < 0) {
            return -1;
        }
        pageGroupInsertion = insertion;
        EmiFavoriteGroups.setGroupMovePreview(pressedPageGroup, insertion);
        return insertion;
    }

    private static boolean dropPageGroupToScreen(EmiFavoriteGroups.Group group, int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen == null) {
            return false;
        }
        boolean patternTerminal = isAe2PatternTerminal(client.screen);
        List<EmiIngredient> stacks = new ArrayList<>();
        for (EmiFavorite favorite : group.members()) {
            if (stacks.size() >= 63) {
                break;
            }
            if (patternTerminal && favorite.getRole() != Role.INGREDIENT) {
                continue;
            }
            EmiIngredient ingredient = concreteDragIngredient(favorite);
            if (ingredient.isEmpty() || containsEquivalent(stacks, ingredient)) {
                continue;
            }
            stacks.add(ingredient);
        }
        return !stacks.isEmpty() && EmiDragDropHandlers.dropStacks(client.screen, stacks, mouseX, mouseY);
    }

    private static boolean isAe2PatternTerminal(Screen screen) {
        if (screen == null) {
            return false;
        }
        if (isAe2PatternTerminalType(screen.getClass())) {
            return true;
        }
        return screen instanceof AbstractContainerScreen<?> handled
                && isAe2PatternTerminalType(handled.getMenu().getClass());
    }

    private static boolean isAe2PatternTerminalType(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            String name = current.getName().toLowerCase(Locale.ROOT);
            String simple = current.getSimpleName().toLowerCase(Locale.ROOT);
            boolean ae2Like = name.startsWith("appeng.")
                    || name.startsWith("com.gtocore.")
                    || name.contains("ae2");
            if (!ae2Like || simple.contains("access")) {
                continue;
            }
            if (simple.contains("patternencodingterm")
                    || simple.contains("patternterminal")
                    || simple.contains("patterntermscreen")
                    || simple.contains("patterntermmenu")) {
                return true;
            }
        }
        return false;
    }

    private static EmiIngredient concreteDragIngredient(EmiFavorite favorite) {
        if (favorite == null || favorite.getStack() == null || favorite.getStack().getEmiStacks().isEmpty()) {
            return EmiStack.EMPTY;
        }
        try {
            return favorite.getStack().getEmiStacks().get(0).copy().setAmount(1).setChance(1);
        } catch (Throwable ignored) {
            return favorite.getStack().getEmiStacks().get(0);
        }
    }

    private static boolean containsEquivalent(List<EmiIngredient> stacks, EmiIngredient candidate) {
        if (candidate == null || candidate.isEmpty() || candidate.getEmiStacks().isEmpty()) {
            return true;
        }
        EmiStack target = candidate.getEmiStacks().get(0);
        for (EmiIngredient existing : stacks) {
            if (existing != null && !existing.isEmpty() && !existing.getEmiStacks().isEmpty()
                    && existing.getEmiStacks().get(0).isEqual(target, EmiPort.compareStrict())) {
                return true;
            }
        }
        return false;
    }

    private static void clearPageGroupDrag() {
        pressedPageGroup = null;
        pageGroupDragging = false;
        pageGroupInsertion = -1;
        EmiFavoriteGroups.clearGroupMovePreview();
    }

    private static void playSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private static final class GroupHeaderTooltipComponent implements EmiTooltipComponent {
        private static final Component ALT = EmiPort.literal("[alt]").withStyle(ChatFormatting.YELLOW);

        private static Component title() {
            return EmiPort.literal(PlannerText.tr("bookmark.group.title", "Bookmarks Group")).withStyle(ChatFormatting.WHITE);
        }

        @Override
        public int getHeight() {
            return 20;
        }

        @Override
        public int getWidth(Font textRenderer) {
            Component title = title();
            return Math.max(textRenderer.width(title), textRenderer.width(ALT));
        }

        @Override
        public void drawTooltipText(TextRenderData text) {
            text.draw(title(), 0, 0, 0xFFFFFFFF, false);
            text.draw(ALT, 0, 10, 0xFFFFFFFF, false);
        }
    }

    private static final class AmountGridTooltipComponent implements EmiTooltipComponent {
        private static final int WRAP_WIDTH = 288;
        private static final int GAP = 8;
        private static final int ROW_HEIGHT = 20;
        private static final int MAX_ENTRIES = 64;
        private final List<AmountIcon> icons = new ArrayList<>();
        private int width;
        private int height;

        private AmountGridTooltipComponent(List<AmountEntry> entries) {
            int x = 0;
            int y = 0;
            int count = 0;
            for (AmountEntry entry : entries) {
                if (count++ >= MAX_ENTRIES || entry == null || entry.ingredient() == null || entry.ingredient().isEmpty()) {
                    break;
                }
                EmiIngredient ingredient = displayIngredient(entry);
                long amount = hasReusableOption(entry.ingredient()) ? 1L : Math.max(1L, entry.amount());
                Component amountText = amountText(ingredient, amount);
                int iconWidth = 16 + EmiRenderHelper.getAmountOverflow(amountText);
                if (x > 0 && x + iconWidth > WRAP_WIDTH) {
                    x = 0;
                    y += ROW_HEIGHT;
                }
                icons.add(new AmountIcon(ingredient, amountText, x, y));
                width = Math.max(width, x + iconWidth);
                height = Math.max(height, y + ROW_HEIGHT);
                x += iconWidth + GAP;
            }
        }

        @Override
        public int getHeight() {
            return height;
        }

        @Override
        public int getWidth(Font textRenderer) {
            return width;
        }

        @Override
        public void drawTooltip(EmiDrawContext context, TooltipRenderData render) {
            for (AmountIcon icon : icons) {
                context.drawStack(icon.ingredient, icon.x, icon.y, ~EmiIngredient.RENDER_AMOUNT);
                EmiRenderHelper.renderAmount(context, icon.x, icon.y, icon.amount);
            }
        }

        private static Component amountText(EmiIngredient ingredient, long amount) {
            return EmiRenderHelper.getAmountText(ingredient, amount);
        }

        private static EmiIngredient displayIngredient(AmountEntry entry) {
            for (EmiStack stack : entry.ingredient().getEmiStacks()) {
                if (isReusable(stack)) {
                    return stack.copy().setAmount(1).setChance(1);
                }
            }
            try {
                return entry.ingredient().copy().setAmount(Math.max(1L, entry.amount())).setChance(1);
            } catch (Throwable ignored) {
                return entry.ingredient();
            }
        }
    }

    private record AmountIcon(EmiIngredient ingredient, Component amount, int x, int y) {
    }

    private static final class PullJob {
        private final EmiFavoriteGroups.Group group;
        private final boolean missingOnly;
        private final AbstractContainerScreen<?> screen;
        private final int syncId;
        private final List<PullStep> steps;
        private final boolean mePull;
        private int index;
        private int waitTicks;

        private PullJob(EmiFavoriteGroups.Group group, boolean missingOnly, AbstractContainerScreen<?> screen, int syncId,
                List<PullStep> steps, boolean mePull) {
            this.group = group;
            this.missingOnly = missingOnly;
            this.screen = screen;
            this.syncId = syncId;
            this.steps = steps;
            this.mePull = mePull;
        }
    }

    private static final class PullStep {
        private final EmiIngredient ingredient;
        private final long amount;
        private long target = -1L;
        private long pendingAvailable;
        private int pendingTicks;
        private int failures;
        private boolean waiting;
        private int mePhase;
        private int meCursorExpected;
        private int meCursorLastCount;
        private int meCursorStableTicks;

        private PullStep(EmiIngredient ingredient, long amount) {
            this.ingredient = ingredient;
            this.amount = amount;
        }
    }

    private record MePullEntry(long serial, long storedAmount, ItemStack stack) {
    }

    private record MissingCraftPlan(List<CraftStep> steps, List<AmountEntry> missing) {
        private static final MissingCraftPlan EMPTY = new MissingCraftPlan(List.of(), List.of());
    }

    private static final class MeAutoCraftJob {
        private final EmiFavoriteGroups.Group group;
        private final boolean missingOnly;
        private final AbstractContainerScreen<?> screen;
        private final int syncId;
        private final List<CraftStep> steps;
        private int index;
        private int waitTicks;

        private MeAutoCraftJob(EmiFavoriteGroups.Group group, boolean missingOnly, AbstractContainerScreen<?> screen, int syncId,
                List<CraftStep> steps) {
            this.group = group;
            this.missingOnly = missingOnly;
            this.screen = screen;
            this.syncId = syncId;
            this.steps = steps;
        }
    }

    private static final class AutoCraftJob {
        private final EmiFavoriteGroups.Group group;
        private final boolean missingOnly;
        private final AbstractContainerScreen<?> screen;
        private final int syncId;
        private final List<CraftStep> steps;
        private int index;
        private int waitTicks;

        private AutoCraftJob(EmiFavoriteGroups.Group group, boolean missingOnly, AbstractContainerScreen<?> screen, int syncId, List<CraftStep> steps) {
            this.group = group;
            this.missingOnly = missingOnly;
            this.screen = screen;
            this.syncId = syncId;
            this.steps = steps;
        }
    }

    private static final class MissingCraftPlanner {
        private final List<EmiRecipe> recipes;
        private final InventoryLedger inventory;
        private final boolean strict;
        private final LinkedHashMap<ResourceLocation, Long> batches = new LinkedHashMap<>();
        private final LinkedHashMap<EmiIngredient, Long> missing = new LinkedHashMap<>();
        private final Set<ResourceLocation> path = new HashSet<>();

        private MissingCraftPlanner(List<EmiRecipe> recipes, EmiPlayerInventory inventory, boolean strict) {
            this.recipes = recipes;
            this.inventory = new InventoryLedger(inventory);
            this.strict = strict;
        }

        private boolean plan(EmiRecipe recipe, long amount) {
            if (recipe == null || recipe.getId() == null || amount <= 0L) {
                return true;
            }
            if (!path.add(recipe.getId())) {
                return !strict;
            }
            try {
                Set<ToolInstance> reservedTools = new HashSet<>();
                for (EmiIngredient input : recipe.getInputs()) {
                    if (input == null || input.isEmpty()) {
                        continue;
                    }
                    if (hasReusableGtToolOption(input) && inventory.consumeGtToolSlot(input, amount, reservedTools)) {
                        continue;
                    }
                    long needed = safeMultiply(Math.max(1L, input.getAmount()), amount);
                    long remaining = inventory.consume(input, needed);
                    if (remaining <= 0L) {
                        continue;
                    }
                    EmiRecipe producer = findProducer(recipes, input, recipe);
                    if (producer == null || producer.getId() == null || path.contains(producer.getId())) {
                        if (strict) {
                            return false;
                        }
                        addMissing(input, remaining);
                        continue;
                    }
                    long perBatch = matchingOutputAmount(producer, input);
                    long producerBatches = ceilDiv(remaining, perBatch);
                    if (!plan(producer, producerBatches)) {
                        if (strict) {
                            return false;
                        }
                        addMissing(input, remaining);
                        continue;
                    }
                    long extra = safeMultiply(perBatch, producerBatches) - remaining;
                    if (extra > 0L) {
                        inventory.addMatchingOutput(producer, input, extra);
                    }
                }
            } finally {
                path.remove(recipe.getId());
            }
            batches.merge(recipe.getId(), amount, FavoriteGroupSidebar::safeAdd);
            return true;
        }

        private void addMissing(EmiIngredient ingredient, long amount) {
            if (ingredient == null || ingredient.isEmpty() || amount <= 0L) {
                return;
            }
            EmiIngredient key = normalized(ingredient);
            for (var entry : missing.entrySet()) {
                if (EmiIngredient.areEqual(entry.getKey(), key)) {
                    entry.setValue(safeAdd(entry.getValue(), amount));
                    return;
                }
            }
            missing.put(key, amount);
        }

        private List<AmountEntry> missingEntries() {
            List<AmountEntry> result = new ArrayList<>();
            for (var entry : missing.entrySet()) {
                if (entry.getValue() > 0L) {
                    result.add(new AmountEntry(entry.getKey(), entry.getValue()));
                }
            }
            return List.copyOf(result);
        }
    }

    private static final class InventoryLedger {
        private final List<LedgerEntry> entries = new ArrayList<>();
        private final List<ToolInstance> tools = new ArrayList<>();

        private InventoryLedger(EmiPlayerInventory inventory) {
            if (inventory == null) {
                return;
            }
            for (EmiStack stack : inventory.inventory.values()) {
                if (stack != null && !stack.isEmpty() && stack.getAmount() > 0L) {
                    addStack(stack, stack.getAmount());
                }
            }
        }

        private long consume(EmiIngredient ingredient, long amount) {
            long remaining = Math.max(0L, amount);
            if (remaining <= 0L || ingredient == null || ingredient.isEmpty()) {
                return remaining;
            }
            List<LedgerEntry> returned = new ArrayList<>();
            for (LedgerEntry entry : entries) {
                if (entry.amount <= 0L) {
                    continue;
                }
                EmiStack option = matchingOption(ingredient, entry.stack);
                if (option == null) {
                    continue;
                }
                if (isReusable(option)) {
                    if (EmiCraftingToolCompat.isGtTool(option) || EmiCraftingToolCompat.isGtTool(entry.stack)) {
                        continue;
                    }
                    return 0L;
                }
                long used = Math.min(remaining, entry.amount);
                entry.amount -= used;
                remaining -= used;
                addRemainder(returned, option, used);
                if (remaining <= 0L) {
                    break;
                }
            }
            for (LedgerEntry entry : returned) {
                addStack(entry.stack, entry.amount);
            }
            return remaining;
        }


        private boolean consumeGtToolSlot(EmiIngredient ingredient, long crafts, Set<ToolInstance> reserved) {
            ToolInstance best = null;
            for (ToolInstance tool : tools) {
                if (tool == null || reserved.contains(tool) || tool.uses < crafts) {
                    continue;
                }
                boolean matches = false;
                for (EmiStack option : ingredient.getEmiStacks()) {
                    if (option != null && !option.isEmpty() && isReusable(option)
                            && EmiCraftingToolCompat.isGtTool(option) && EmiCraftingToolCompat.matches(option, tool.stack)) {
                        matches = true;
                        break;
                    }
                }
                if (matches && (best == null || tool.uses > best.uses)) {
                    best = tool;
                }
            }
            if (best == null) {
                return false;
            }
            best.uses -= Math.max(1L, crafts);
            reserved.add(best);
            return true;
        }

        private void addRemainder(List<LedgerEntry> returned, EmiStack option, long used) {
            if (used <= 0L || option == null || option.isEmpty()) {
                return;
            }
            EmiStack remainder;
            try {
                remainder = option.getRemainder();
            } catch (Throwable ignored) {
                return;
            }
            if (remainder == null || remainder.isEmpty()) {
                return;
            }
            long returnedAmount = safeMultiply(Math.max(1L, remainder.getAmount()), used);
            if (returnedAmount <= 0L) {
                return;
            }
            EmiStack stack = remainder.copy().setAmount(1);
            for (LedgerEntry entry : returned) {
                if (matchesOutput(stack, entry.stack)) {
                    entry.amount = safeAdd(entry.amount, returnedAmount);
                    return;
                }
            }
            returned.add(new LedgerEntry(stack, returnedAmount));
        }

        private void addMatchingOutput(EmiRecipe recipe, EmiIngredient ingredient, long amount) {
            if (amount <= 0L) {
                return;
            }
            for (EmiStack output : recipe.getOutputs()) {
                if (output != null && !output.isEmpty() && accepts(ingredient, output)) {
                    addStack(output, amount);
                    return;
                }
            }
        }

        private void addStack(EmiStack stack, long amount) {
            if (stack == null || stack.isEmpty() || amount <= 0L) {
                return;
            }
            EmiStack normalized = stack.copy().setAmount(1);
            if (EmiCraftingToolCompat.isGtTool(normalized)) {
                long uses = EmiCraftingToolCompat.getSafeCraftingUses(normalized);
                long count = Math.min(amount, 4096L);
                for (long i = 0; i < count; i++) {
                    tools.add(new ToolInstance(normalized.copy(), uses));
                }
                return;
            }
            for (LedgerEntry entry : entries) {
                if (matchesOutput(normalized, entry.stack)) {
                    entry.amount = safeAdd(entry.amount, amount);
                    return;
                }
            }
            entries.add(new LedgerEntry(normalized, amount));
        }

        private EmiStack matchingOption(EmiIngredient ingredient, EmiStack actual) {
            for (EmiStack option : ingredient.getEmiStacks()) {
                if (matchesOutput(option, actual)) {
                    return option;
                }
            }
            return null;
        }
    }

    private static final class LedgerEntry {
        private final EmiStack stack;
        private long amount;

        private LedgerEntry(EmiStack stack, long amount) {
            this.stack = stack;
            this.amount = amount;
        }
    }

    private static final class ToolInstance {
        private final EmiStack stack;
        private long uses;

        private ToolInstance(EmiStack stack, long uses) {
            this.stack = stack;
            this.uses = uses < 0L ? 0L : uses;
        }
    }

    private static final class CraftStep {
        private final EmiRecipe recipe;
        private final EmiIngredient output;
        private final long amount;
        private long outputPerBatch;
        private long target = -1L;
        private long pendingAvailable = -1L;
        private int pendingBatches;
        private int pendingTicks;
        private int failures;
        private int phase;
        private int settleTicks;
        private int settleStableTicks;

        private CraftStep(EmiRecipe recipe, EmiIngredient output, long amount) {
            this.recipe = recipe;
            this.output = output;
            this.amount = amount;
        }
    }

    private record VisibleSlot(EmiFavorite favorite, int visibleIndex, Bounds bounds) {
    }

    private record GroupBox(EmiFavoriteGroups.Group group, EmiScreenManager.SidebarPanel panel, Bounds area, Bounds bracket,
            Bounds handle, boolean bracketOnRight) {
    }

    private record Layout(EmiScreenManager.SidebarPanel panel, EmiScreenManager.ScreenSpace space, int pageStart,
            List<GroupBox> boxes, List<VisibleSlot> slots) {
        private static final Layout EMPTY = new Layout(null, null, 0, List.of(), List.of());
    }
}
