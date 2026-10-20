package dev.emi.emi.screen;

import com.google.common.collect.Lists;
import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.RecipeFillButtonWidget;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.SidebarSide;
import dev.emi.emi.input.EmiInput;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiHistory;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.screen.widget.ResolutionButtonWidget;
import dev.emi.emi.screen.widget.SizedButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

public class RecipeScreen extends Screen {
    private static final ResourceLocation TEXTURE = EmiPort.id("emi", "textures/gui/background.png");
    private static final long RECIPE_FILTER_DEBOUNCE_MS = 140L;
    private static final ScheduledExecutorService RECIPE_FILTER_EXECUTOR = Executors
            .newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "EMI-RecipeFilter");
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            });
    public static @Nullable EmiIngredient resolve = null;
    private Map<EmiRecipeCategory, List<EmiRecipe>> recipes;
    public AbstractContainerScreen<?> old;
    private List<RecipeTab> tabs = Lists.newArrayList();
    private int tabPageSize = 6;
    private int tabPage = 0, tab = 0, page = 0;
    private List<SizedButtonWidget> arrows;
    private List<WidgetGroup> currentPage = Lists.newArrayList();
    private int buttonOff = 0, tabOff = 0;
    private Widget hoveredWidget = null, pressedSlot = null;
    private ResolutionButtonWidget resolutionButton;
    private double scrollAcc = 0;
    private final EmiRecipeSearchIndex recipeSearchIndex = new EmiRecipeSearchIndex();
    private EditBox recipeFilterField;
    private String recipeFilterQuery = "";
    private boolean recipeFilterActive;
    private boolean recipeFilterHelpPinned;
    private boolean recipeFilterFiltering;
    private boolean recipeFilterSuppressNextHashCharacter;
    private volatile long recipeFilterGeneration;
    private ScheduledFuture<?> pendingRecipeFilter;
    private EmiRecipeCategory recipeFilterCategory;
    private EmiRecipeCategory recipeFilterAppliedCategory;
    private int recipeFilterTotalCount;
    private int recipeFilterCount;
    private int recipeFilterToggleX;
    private int recipeFilterToggleY;
    private int recipeFilterX;
    private int recipeFilterY;
    private int recipeFilterWidth;
    private int recipeFilterHelpX;
    private int recipeFilterHelpY;
    private final String[] recipeFilterHelpLines = {
            "",
            I18n.get("emi.recipe_filter.syntax.text"),
            I18n.get("emi.recipe_filter.syntax.inputs"),
            I18n.get("emi.recipe_filter.syntax.outputs"),
            I18n.get("emi.recipe_filter.syntax.mod"),
            I18n.get("emi.recipe_filter.syntax.tooltip"),
            I18n.get("emi.recipe_filter.syntax.tag"),
            I18n.get("emi.recipe_filter.syntax.id"),
            I18n.get("emi.recipe_filter.syntax.formula"),
            I18n.get("emi.recipe_filter.syntax.voltage"),
            I18n.get("emi.recipe_filter.syntax.exclude"),
            I18n.get("emi.recipe_filter.syntax.or"),
            I18n.get("emi.recipe_filter.syntax.phrase")
    };
    private final String recipeFilterSearchingText = I18n.get("emi.recipe_filter.searching");
    private int minimumWidth = 176;
    int backgroundWidth = minimumWidth;
    int backgroundHeight = 200;
    int x = (this.width - backgroundWidth) / 2;
    int y = (this.height - backgroundHeight) / 2;

    public RecipeScreen(AbstractContainerScreen<?> old, Map<EmiRecipeCategory, List<EmiRecipe>> recipes) {
        super(EmiPort.translatable("screen.emi.recipe"));
        this.old = old;
        arrows = List.of(
                new SizedButtonWidget(x + 2, y - 18, 12, 12, 0, 0,
                        () -> tabs.size() > tabPageSize, w -> setPage(tabPage - 1, tab, page)),
                new SizedButtonWidget(x + backgroundWidth - 14, y - 18, 12, 12, 12, 0,
                        () -> tabs.size() > tabPageSize, w -> setPage(tabPage + 1, tab, page)),
                new SizedButtonWidget(x + 5, y + 5, 12, 12, 0, 0,
                        () -> tabs.size() > 1, w -> setPage(tabPage, tab - 1, 0)),
                new SizedButtonWidget(x + backgroundWidth - 17, y + 5, 12, 12, 12, 0,
                        () -> tabs.size() > 1, w -> setPage(tabPage, tab + 1, 0)),
                new SizedButtonWidget(x + 5, y + 18, 12, 12, 0, 0,
                        () -> tabs.get(tab).getPageCount() > 1, w -> setPage(tabPage, tab, page - 1)),
                new SizedButtonWidget(x + backgroundWidth - 17, y + 18, 12, 12, 12, 0,
                        () -> tabs.get(tab).getPageCount() > 1, w -> setPage(tabPage, tab, page + 1)));
        resolve = null;
        this.recipes = recipes;
    }

    @Override
    protected void init() {
        super.init();
        minimumWidth = Math.max(EmiConfig.minimumRecipeScreenWidth, 56);
        backgroundWidth = minimumWidth;
        backgroundHeight = Math.min(EmiConfig.maximumRecipeScreenHeight, height - 52 - EmiConfig.verticalMargin);
        x = (this.width - backgroundWidth) / 2;
        y = (this.height - backgroundHeight) / 2 + 1;
        this.tabPageSize = (minimumWidth - 32) / 24;

        for (SizedButtonWidget widget : arrows) {
            addRenderableWidget(widget);
        }
        EmiScreenManager.addWidgets(this);
        if (resolve != null) {
            resolutionButton = new ResolutionButtonWidget(x - 18, y + 10, 18, 18, resolve, () -> hoveredWidget);
            this.addRenderableWidget(resolutionButton);
        }
        if (recipes != null) {
            EmiRecipe current = null;
            if (tab < tabs.size() && page < tabs.get(tab).getPageCount() && tabs.get(tab).getPage(page).size() > 0) {
                current = tabs.get(tab).getPage(page).get(0).recipe;
            }
            tabs.clear();
            if (!recipes.isEmpty()) {
                var categories = EmiApi.getRecipeManager().getCategories();
                var order = new Reference2IntOpenHashMap<EmiRecipeCategory>(categories.size());
                order.defaultReturnValue(Integer.MAX_VALUE);
                for (int i = 0; i < categories.size(); i++)
                    order.putIfAbsent(categories.get(i), i);
                var sortedCategories = new ObjectArrayList<>(recipes.keySet());
                sortedCategories.sort((left, right) -> Integer.compare(order.getInt(left), order.getInt(right)));
                for (var category : sortedCategories) {
                    List<EmiRecipe> set = recipes.get(category);
                    if (!set.isEmpty()) {
                        RecipeTab tab = new RecipeTab(category, set);
                        tab.bakePages(backgroundHeight);
                        tabs.add(tab);
                    }
                }

                tab = -1;
                setPage(tabPage, 0, 0);
            }
            // setPages(recipes);
            if (current != null) {
                focusRecipe(current);
            }
        }
        setRecipePageWidth(backgroundWidth);
        recipeFilterAppliedCategory = null;
        recipeFilterCategory = null;
        layoutRecipeFilter();
        if (recipeFilterActive && !recipeFilterQuery.isBlank()) {
            syncRecipeFilterCategory();
        }
    }

    private void setRecipePageWidth(int width) {
        if ((width & 1) == 1) {
            width++;
        }
        this.backgroundWidth = width;
        this.x = (this.width - backgroundWidth) / 2;
        this.buttonOff = (backgroundWidth - minimumWidth) / 2;
        int tabExtra = (minimumWidth - 32) % 24 / 2;
        this.tabOff = buttonOff + tabExtra;
        this.arrows.get(0).x = this.x + 2 + buttonOff + tabExtra;
        this.arrows.get(1).x = this.x + minimumWidth - 14 + buttonOff - tabExtra;
        this.arrows.get(2).x = this.x + 5 + buttonOff;
        this.arrows.get(3).x = this.x + minimumWidth - 17 + buttonOff;
        this.arrows.get(4).x = this.x + 5 + buttonOff;
        this.arrows.get(5).x = this.x + minimumWidth - 17 + buttonOff;

        this.arrows.get(0).y = this.y - 18;
        this.arrows.get(1).y = this.y - 18;
        this.arrows.get(2).y = this.y + 5;
        this.arrows.get(3).y = this.y + 5;
        this.arrows.get(4).y = this.y + 19;
        this.arrows.get(5).y = this.y + 19;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void render(GuiGraphics raw, int mouseX, int mouseY, float delta) {
        EmiDrawContext context = EmiDrawContext.wrap(raw);
        this.renderBackground(context.raw());
        context.resetColor();
        EmiRenderHelper.drawNinePatch(context, TEXTURE, x, y, backgroundWidth, backgroundHeight, 0, 0, 4, 1);

        int tp = tabPage * tabPageSize;
        int off = 0;
        for (int i = tp; i < tabs.size() && i < tp + tabPageSize; i++) {
            RecipeTab tab = tabs.get(i);
            int sOff = (i == this.tab ? 2 : 0);
            EmiRenderHelper.drawNinePatch(context, TEXTURE, x + tabOff + off * 24 + 16, y - 24 - sOff, 24, 27 + sOff,
                    i == this.tab ? 9 : 18, 0, 4, 1);
            tab.category.render(context.raw(), x + tabOff + off++ * 24 + 20, y - 20 - (i == this.tab ? 2 : 0), delta);
        }

        EmiRenderHelper.drawNinePatch(context, TEXTURE, x + 19 + buttonOff, y + 5, minimumWidth - 38, 12, 0, 16, 3, 6);
        // EmiRenderHelper.drawScroll(context, x + 19 + buttonOff, y + 5 + 10, minimumWidth - 38, 2, tab, tabs.size(),
        // -1);
        EmiRenderHelper.drawNinePatch(context, TEXTURE, x + 19 + buttonOff, y + 19, minimumWidth - 38, 12, 0, 16, 3, 6);
        // EmiRenderHelper.drawScroll(context, x + 19 + buttonOff, y + 19 + 10, minimumWidth - 38, 2, page,
        // tabs.get(tab).getPageCount(), -1);

        boolean categoryHovered = mouseX >= x + 19 + buttonOff && mouseY >= y + 5
                && mouseX < x + minimumWidth + buttonOff - 19 && mouseY < y + 5 + 12;
        int categoryNameColor = categoryHovered ? 0x22ffff : 0xffffff;

        RecipeTab tab = tabs.get(this.tab);
        Component text = tab.category.getName();
        if (minecraft.font.width(text) > minimumWidth - 40) {
            int extraWidth = minecraft.font.width("...");
            text = EmiPort
                    .literal(minecraft.font.substrByWidth(text, (minimumWidth - 40) - extraWidth).getString() + "...");
        }
        context.drawCenteredTextWithShadow(text, x + backgroundWidth / 2, y + 7, categoryNameColor);
        context.drawCenteredTextWithShadow(
                EmiRenderHelper.getPageText(this.page + 1, tab.getPageCount(), minimumWidth - 40),
                x + backgroundWidth / 2, y + 21, 0xffffff);

        List<EmiIngredient> workstations = EmiApi.getRecipeManager().getWorkstations(tab.category);
        int workstationAmount = Math.min(workstations.size(), getMaxWorkstations());
        if (workstationAmount > 0 || resolve != null) {
            Bounds bounds = getWorkstationBounds(-1);
            int offset = getResolveOffset();
            if (workstationAmount <= 0) {
                offset = 18;
            }
            int columns = Math.max(1, (workstationAmount + getWorkstationRows() - 1) / getWorkstationRows());
            int sideHeight = 10 + 18 * Math.min(workstationAmount, getWorkstationRows()) + offset;
            if (EmiConfig.workstationLocation == SidebarSide.LEFT) {
                EmiRenderHelper.drawNinePatch(context, TEXTURE, bounds.x() - 5 - (columns - 1) * 18, bounds.y() - 5,
                        28 + (columns - 1) * 18, sideHeight, 36, 0, 5, 1);
            } else if (EmiConfig.workstationLocation == SidebarSide.RIGHT) {
                EmiRenderHelper.drawNinePatch(context, TEXTURE, bounds.x() - 5, bounds.y() - 5, 28 + (columns - 1) * 18,
                        sideHeight, 47, 0, 5, 1);
            } else if (EmiConfig.workstationLocation == SidebarSide.BOTTOM) {
                EmiRenderHelper.drawNinePatch(context, TEXTURE, bounds.x() - 5, bounds.y() - 5,
                        10 + 18 * workstationAmount + offset, 28, 58, 0, 5, 1);
            }
        }
        for (WidgetGroup group : currentPage) {
            int mx = mouseX - group.x();
            int my = mouseY - group.y();
            context.push();
            context.matrices().translate(group.x(), group.y(), 0);
            EmiPort.applyModelViewMatrix();
            try {
                for (Widget widget : group.widgets) {
                    widget.render(context.raw(), mx, my, delta);
                }
            } catch (Throwable e) {
                EmiLog.error("Error rendering widget", e);
                group.error(e);
            }
            for (Widget widget : group.widgets) {
                if (widget instanceof RecipeFillButtonWidget) {
                    if (widget.getBounds().contains(mx, my)) {
                        AbstractContainerScreen hs = EmiApi.getHandledScreen();
                        EmiRecipeHandler handler = EmiRecipeFiller.getFirstValidHandler(group.recipe, hs);
                        if (handler != null) {
                            handler.render(group.recipe,
                                    new EmiCraftContext(hs, handler.getInventory(hs), EmiCraftContext.Type.FILL_BUTTON),
                                    group.widgets, context.raw());
                        } else if (EmiScreenManager.lastPlayerInventory != null) {
                            StandardRecipeHandler.renderMissing(group.recipe, EmiScreenManager.lastPlayerInventory,
                                    group.widgets, context.raw());
                        }
                        break;
                    }
                }
            }
            context.pop();
            EmiPort.applyModelViewMatrix();
        }
        EmiScreenManager.drawBackground(context, mouseX, mouseY, delta);
        EmiScreenManager.render(context, mouseX, mouseY, delta);
        EmiScreenManager.drawForeground(context, mouseX, mouseY, delta);
        super.render(context.raw(), mouseX, mouseY, delta);
        if (categoryHovered) {
            context.raw().renderComponentTooltip(minecraft.font, List.of(
                    tab.category.getName(),
                    EmiPort.translatable("emi.view_all_recipes")), mouseX, mouseY);
        }
        hoveredWidget = null;
        outer: for (WidgetGroup group : currentPage) {
            try {
                int mx = mouseX - group.x();
                int my = mouseY - group.y();
                for (Widget widget : group.widgets) {
                    if (widget.getBounds().contains(mx, my)) {
                        List<ClientTooltipComponent> tooltip = widget.getTooltip(mx, my);
                        if (!tooltip.isEmpty()) {
                            EmiRenderHelper.drawTooltip(this, context, tooltip, mouseX, mouseY);
                            hoveredWidget = widget;
                            break outer;
                        }
                    }
                }
            } catch (Throwable e) {
                EmiLog.error("Error rendering widget group", e);
                group.error(e);
            }
        }

        RecipeTab rTab = getTabAt(mouseX, mouseY);
        if (rTab != null) {
            EmiRenderHelper.drawTooltip(this, context, rTab.category.getTooltip(), mouseX, mouseY);
        }
        renderRecipeFilter(raw, mouseX, mouseY, delta);
    }

    public EmiIngredient getHoveredStack() {
        if (hoveredWidget instanceof SlotWidget slot) {
            return slot.getStack();
        }
        return EmiStack.EMPTY;
    }

    public RecipeTab getTabAt(int mx, int my) {
        if (mx >= x + 16 + tabOff && mx < x + backgroundWidth && my >= y - 24 && my < y) {
            int n = (mx - x - 16 - tabOff) / 24 + tabPage * tabPageSize;
            if (n < tabs.size() && n >= tabPage * tabPageSize && n < (tabPage + 1) * tabPageSize) {
                return tabs.get(n);
            }
        }
        return null;
    }

    public int getMaxWorkstations() {
        return EmiConfig.workstationLocation == SidebarSide.LEFT || EmiConfig.workstationLocation == SidebarSide.RIGHT
                || EmiConfig.workstationLocation == SidebarSide.BOTTOM ? Integer.MAX_VALUE : 0;
    }

    private int getWorkstationRows() {
        return Math.max(1, (backgroundHeight - getResolveOffset() - 18) / 18);
    }

    public int getResolveOffset() {
        return resolve == null ? 0 : 23;
    }

    public Bounds getWorkstationBounds(int i) {
        int offset = i == -1 ? -getResolveOffset() : 0;
        i = Math.max(0, i);
        int column = i / getWorkstationRows();
        int row = i % getWorkstationRows();
        if (EmiConfig.workstationLocation == SidebarSide.LEFT) {
            return new Bounds(x - (column + 1) * 18, y + 9 + getResolveOffset() + row * 18 + offset, 18, 18);
        } else if (EmiConfig.workstationLocation == SidebarSide.RIGHT) {
            return new Bounds(x + backgroundWidth + column * 18, y + 9 + getResolveOffset() + row * 18 + offset, 18,
                    18);
        } else if (EmiConfig.workstationLocation == SidebarSide.BOTTOM) {
            return new Bounds(x + 5 + getResolveOffset() + i * 18 + offset, y + backgroundHeight - 23, 18, 18);
        }
        return Bounds.EMPTY;
    }

    public EmiRecipeCategory getFocusedCategory() {
        return tabs.get(tab).category;
    }

    public void focusCategory(EmiRecipeCategory category) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).category == category) {
                setPage(tabPage, i, 0);
                return;
            }
        }
    }

    public void focusRecipe(EmiRecipe recipe) {
        for (int i = 0; i < tabs.size(); i++) {
            RecipeTab tab = tabs.get(i);
            for (int j = 0; j < tab.getPageCount(); j++) {
                for (RecipeDisplay d : tab.getPage(j)) {
                    if (d.recipe == recipe) {
                        setPage(tabPage, i, j);
                        return;
                    }
                }
            }
        }
    }

    public void setPage(int tp, int t, int p) {
        currentPage.clear();
        if (tabs.isEmpty()) {
            return;
        }
        boolean snapTabPage = tp == tabPage && t != tab;
        tab = wrap(t, tabs.size());
        if (snapTabPage) {
            tp = (tab) / tabPageSize;
        }
        tabPage = wrap(tp, (tabs.size() - 1) / tabPageSize + 1);
        RecipeTab tab = tabs.get(this.tab);
        page = wrap(p, tab.getPageCount());
        if (page < tab.getPageCount()) {
            int width = Math.max(minimumWidth - 16, tab.getWidth());
            setRecipePageWidth(width + 16);
            currentPage = Lists.newArrayList();
            currentPage.addAll(tab.constructWidgets(page, x, y, backgroundWidth, backgroundHeight));
            List<EmiIngredient> workstations = EmiApi.getRecipeManager().getWorkstations(tab.category);
            if (!workstations.isEmpty()) {
                WidgetGroup widgets = new WidgetGroup(null, 0, 0, 0, 0);
                int maxWorkstations = getMaxWorkstations();
                for (int i = 0; i < workstations.size() && i < maxWorkstations; i++) {
                    Bounds bounds = getWorkstationBounds(i);
                    if (i == maxWorkstations - 1 && workstations.size() > maxWorkstations) {
                        EmiIngredient ingredient = EmiIngredient.of(workstations.subList(i, workstations.size()));
                        widgets.add(new SlotWidget(ingredient, bounds.x(), bounds.y()));
                    } else {
                        widgets.add(new SlotWidget(workstations.get(i), bounds.x(), bounds.y()));
                    }
                }
                currentPage.add(widgets);
            }
        }
        if (resolve != null && resolutionButton != null) {
            Bounds bounds = getWorkstationBounds(-1);
            resolutionButton.x = bounds.x();
            resolutionButton.y = bounds.y();
        }
    }

    public int wrap(int value, int size) {
        if (value >= size) {
            return 0;
        } else if (value < 0) {
            return size - 1;
        }
        return value;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        pressedSlot = null;
        layoutRecipeFilter();
        if (isRecipeFilterToggleHovered(mouseX, mouseY)) {
            toggleRecipeFilter();
            return true;
        }
        if (recipeFilterActive) {
            if (isRecipeFilterHelpHovered(mouseX, mouseY)) {
                recipeFilterHelpPinned = !recipeFilterHelpPinned;
                return true;
            }
            if (recipeFilterField != null && recipeFilterField.mouseClicked(mouseX, mouseY, button)) {
                EmiPort.focus(recipeFilterField, true);
                return true;
            }
            if (!recipeFilterFiltering && recipeFilterCount == 0 && !recipeFilterQuery.isBlank()
                    && isRecipeFilterAreaHovered(mouseX, mouseY)) {
                return true;
            }
            if (recipeFilterField != null) {
                EmiPort.focus(recipeFilterField, false);
            }
        }
        if (mouseX >= x + 19 + buttonOff && mouseY >= y + 5 && mouseX < x + minimumWidth + buttonOff - 19
                && mouseY <= y + 5 + 12) {
            EmiApi.displayAllRecipes();
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            return true;
        }
        for (WidgetGroup group : currentPage) {
            try {
                int ox = mx - group.x();
                int oy = my - group.y();
                boolean groupHovered = new Bounds(group.x(), group.y(), group.getWidth(), group.getHeight())
                        .contains(mx, my);
                for (Widget widget : group.widgets) {
                    if (widget.getBounds().contains(ox, oy)) {
                        if (widget instanceof SlotWidget slot) {
                            if (pressedSlot == null) {
                                pressedSlot = widget;
                            }
                        } else {
                            if (widget.mouseClicked(ox, oy, button)) {
                                return true;
                            }
                        }
                        groupHovered = true;
                    }
                }
                if (groupHovered
                        && EmiScreenManager.recipeInteraction(group.recipe, bind -> bind.matchesMouse(button))) {
                    return true;
                }
            } catch (Throwable e) {
                EmiLog.error("Error handling widget input", e);
                group.error(e);
            }
        }
        if (EmiScreenManager.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        RecipeTab rTab = getTabAt(mx, my);
        if (rTab != null) {
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            focusCategory(rTab.category);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (EmiScreenManager.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (pressedSlot instanceof SlotWidget slot) {
            WidgetGroup group = getGroup(slot);
            if (group != null) {
                try {
                    int ox = ((int) mouseX) - group.x();
                    int oy = ((int) mouseY) - group.y();
                    if (slot.getBounds().contains(ox, oy)) {
                        if (slot.mouseClicked(ox, oy, button)) {
                            return true;
                        }
                    }
                } catch (Throwable e) {
                    EmiLog.error("Error handling widget input", e);
                    group.error(e);
                }
            }
            pressedSlot = null;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (EmiScreenManager.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        if (pressedSlot instanceof SlotWidget slot) {
            WidgetGroup group = getGroup(slot);
            if (group != null) {
                int ox = ((int) mouseX) - group.x();
                int oy = ((int) mouseY) - group.y();
                if (!slot.getBounds().contains(ox, oy) && button == 0) {
                    EmiIngredient stack = slot.getStack();
                    if (slot.getRecipe() != null) {
                        stack = new EmiFavorite(stack, slot.getRecipe());
                    }
                    EmiScreenManager.pressedStack = stack;
                    EmiScreenManager.draggedStack = stack;
                    pressedSlot = null;
                }
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (EmiScreenManager.mouseScrolled(mouseX, mouseY, amount)) {
            return true;
        } else if (mouseX > x && mouseX < x + backgroundWidth && mouseY < x + backgroundHeight) {
            scrollAcc += amount;
            int sa = (int) scrollAcc;
            scrollAcc %= 1;
            if (EmiInput.isShiftDown() || mouseY < this.y) {
                setPage(tabPage, tab - sa, 0);
            } else {
                setPage(tabPage, tab, page - sa);
            }
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (recipeFilterActive && recipeFilterField != null && recipeFilterField.isFocused()) {
            if (recipeFilterSuppressNextHashCharacter) {
                recipeFilterSuppressNextHashCharacter = false;
                if (chr == '#' || chr == '№') {
                    return true;
                }
            }
            if (chr == '№') {
                recipeFilterField.insertText("#");
                return true;
            }
            recipeFilterField.charTyped(chr, modifiers);
            return true;
        }
        if (EmiScreenManager.search.charTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (recipeFilterActive && recipeFilterField != null && recipeFilterField.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeRecipeFilter();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                EmiPort.focus(recipeFilterField, false);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_3 && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                recipeFilterField.insertText("#");
                recipeFilterSuppressNextHashCharacter = true;
                return true;
            }
            recipeFilterField.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        } else if (EmiScreenManager.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        } else if (this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }

        for (WidgetGroup group : currentPage) {
            try {
                int mx = EmiScreenManager.lastMouseX - group.x();
                int my = EmiScreenManager.lastMouseY - group.y();
                boolean groupHovered = new Bounds(group.x(), group.y(), group.getWidth(), group.getHeight())
                        .contains(EmiScreenManager.lastMouseX, EmiScreenManager.lastMouseY);
                for (Widget widget : group.widgets) {
                    if (widget.getBounds().contains(mx, my)) {
                        if (widget.keyPressed(keyCode, scanCode, modifiers)) {
                            return true;
                        }
                        groupHovered = true;
                    }
                }
                if (groupHovered && EmiScreenManager.recipeInteraction(group.recipe,
                        bind -> bind.matchesKey(keyCode, scanCode))) {
                    return true;
                }
            } catch (Throwable e) {
                EmiLog.error("Error handling widget input", e);
                group.error(e);
            }
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            setPage(tabPage, tab - 1, 0);
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            setPage(tabPage, tab + 1, 0);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void layoutRecipeFilter() {
        int newToggleX = x + buttonOff + 20;
        int newToggleY = y + 19;
        int newHelpX = x + buttonOff + minimumWidth - 32;
        int newHelpY = y + 19;
        int newX = newToggleX + 15;
        int newY = y + 18;
        int newWidth = Math.max(54, newHelpX - newX - 3);

        if (recipeFilterField != null
                && newToggleX == recipeFilterToggleX
                && newToggleY == recipeFilterToggleY
                && newHelpX == recipeFilterHelpX
                && newHelpY == recipeFilterHelpY
                && newX == recipeFilterX
                && newY == recipeFilterY
                && newWidth == recipeFilterWidth) {
            return;
        }

        boolean focused = recipeFilterActive && recipeFilterField != null && recipeFilterField.isFocused();
        recipeFilterToggleX = newToggleX;
        recipeFilterToggleY = newToggleY;
        recipeFilterHelpX = newHelpX;
        recipeFilterHelpY = newHelpY;
        recipeFilterX = newX;
        recipeFilterY = newY;
        recipeFilterWidth = newWidth;

        EditBox field = new EditBox(minecraft.font, recipeFilterX, recipeFilterY,
                recipeFilterWidth, 14, EmiPort.translatable("emi.recipe_filter.title"));
        field.setMaxLength(256);
        field.setSuggestion(recipeFilterQuery.isEmpty() ? I18n.get("emi.recipe_filter.hint") : "");
        field.setValue(recipeFilterQuery);
        updateRecipeFilterColor(field);
        field.setResponder(this::onRecipeFilterChanged);
        EmiPort.focus(field, focused);
        recipeFilterField = field;
    }

    private void renderRecipeFilter(GuiGraphics raw, int mouseX, int mouseY, float delta) {
        if (tabs.isEmpty()) {
            return;
        }
        layoutRecipeFilter();
        syncRecipeFilterCategory();
        renderRecipeFilterToggle(raw, mouseX, mouseY);
        if (!recipeFilterActive || recipeFilterField == null) {
            return;
        }

        if (!recipeFilterFiltering && recipeFilterCount == 0 && !recipeFilterQuery.isBlank()) {
            renderNoRecipeFilterMatches(raw);
        }

        recipeFilterField.render(raw, mouseX, mouseY, delta);
        renderRecipeFilterHelpButton(raw, mouseX, mouseY);
        if (recipeFilterHelpPinned || isRecipeFilterHelpHovered(mouseX, mouseY)) {
            renderRecipeFilterHelp(raw);
        }
    }

    private void renderRecipeFilterToggle(GuiGraphics raw, int mouseX, int mouseY) {
        boolean hovered = isRecipeFilterToggleHovered(mouseX, mouseY);
        int bg = hovered || recipeFilterActive || !recipeFilterQuery.isBlank() ? 0xE0808080 : 0xD0404040;
        int sx = recipeFilterToggleX;
        int sy = recipeFilterToggleY;
        raw.fill(sx, sy, sx + 12, sy + 12, bg);
        int color = 0xFFFFFFFF;
        raw.fill(sx + 3, sy + 3, sx + 8, sy + 4, color);
        raw.fill(sx + 3, sy + 4, sx + 4, sy + 8, color);
        raw.fill(sx + 7, sy + 4, sx + 8, sy + 8, color);
        raw.fill(sx + 4, sy + 7, sx + 8, sy + 8, color);
        raw.fill(sx + 8, sy + 8, sx + 9, sy + 9, color);
        raw.fill(sx + 9, sy + 9, sx + 11, sy + 11, color);
    }

    private void renderRecipeFilterHelpButton(GuiGraphics raw, int mouseX, int mouseY) {
        boolean hovered = isRecipeFilterHelpHovered(mouseX, mouseY);
        int bg = hovered || recipeFilterHelpPinned ? 0xE0808080 : 0xD0404040;
        raw.fill(recipeFilterHelpX, recipeFilterHelpY, recipeFilterHelpX + 12, recipeFilterHelpY + 12, bg);
        EmiDrawContext.wrap(raw).drawCenteredTextWithShadow(EmiPort.literal("?"), recipeFilterHelpX + 6,
                recipeFilterHelpY + 2, 0xFFFFFF);
    }

    private void renderNoRecipeFilterMatches(GuiGraphics raw) {
        int left = x + 4;
        int top = y + 34;
        int right = x + backgroundWidth - 4;
        int bottom = y + backgroundHeight - 4;
        if (right <= left || bottom <= top) {
            return;
        }
        raw.fill(left, top, right, bottom, 0xEE101010);
        EmiDrawContext.wrap(raw).drawCenteredTextWithShadow(
                EmiPort.translatable("emi.recipe_filter.no_matches"),
                (left + right) / 2,
                top + Math.max(8, (bottom - top) / 2 - 4),
                0xFF7777);
    }

    private void renderRecipeFilterHelp(GuiGraphics raw) {
        String[] lines = recipeFilterHelpLines;
        lines[0] = recipeFilterFiltering ? recipeFilterSearchingText
                : I18n.get("emi.recipe_filter.count", recipeFilterCount, recipeFilterTotalCount);

        int width = 0;
        for (String line : lines) {
            width = Math.max(width, minecraft.font.width(line));
        }
        width += 12;
        int helpHeight = lines.length * 11 + 8;
        int helpX = Math.min(recipeFilterX, this.width - width - 4);
        int helpY = recipeFilterY + 16;
        if (helpY + helpHeight > this.height - 4) {
            helpY = Math.max(4, recipeFilterY - helpHeight - 2);
        }

        raw.pose().pushPose();
        raw.pose().translate(0, 0, 500);
        raw.fill(helpX, helpY, helpX + width, helpY + helpHeight, 0xEE101010);
        raw.fill(helpX, helpY, helpX + width, helpY + 1, 0xFF7F7F7F);
        raw.fill(helpX, helpY + helpHeight - 1, helpX + width, helpY + helpHeight, 0xFF7F7F7F);
        EmiDrawContext context = EmiDrawContext.wrap(raw);
        int ty = helpY + 5;
        for (int i = 0; i < lines.length; i++) {
            int color = i == 0 ? 0xFFFFFF : recipeFilterSyntaxColor(lines[i]);
            context.drawText(EmiPort.literal(lines[i]), helpX + 6, ty, color);
            ty += 11;
        }
        raw.pose().popPose();
    }

    private int recipeFilterSyntaxColor(String line) {
        if (line.startsWith("<"))
            return 0x55FF55;
        if (line.startsWith(">"))
            return 0xFFAA55;
        if (line.startsWith("@"))
            return 0xFF55FF;
        if (line.startsWith("#"))
            return 0xFFFF55;
        if (line.startsWith("$"))
            return 0x55FFFF;
        if (line.startsWith("&"))
            return 0x55AAFF;
        if (line.startsWith("="))
            return 0xAAFFAA;
        if (line.startsWith("%"))
            return 0xFFAA55;
        if (line.startsWith("-"))
            return 0xFF7777;
        return 0xDDDDDD;
    }

    private boolean isRecipeFilterToggleHovered(double mouseX, double mouseY) {
        return mouseX >= recipeFilterToggleX && mouseX < recipeFilterToggleX + 12
                && mouseY >= recipeFilterToggleY && mouseY < recipeFilterToggleY + 12;
    }

    private boolean isRecipeFilterHelpHovered(double mouseX, double mouseY) {
        return recipeFilterActive
                && mouseX >= recipeFilterHelpX && mouseX < recipeFilterHelpX + 12
                && mouseY >= recipeFilterHelpY && mouseY < recipeFilterHelpY + 12;
    }

    private boolean isRecipeFilterAreaHovered(double mouseX, double mouseY) {
        int top = y + 34;
        return mouseX >= x && mouseX < x + backgroundWidth
                && mouseY >= top && mouseY < y + backgroundHeight;
    }

    private void toggleRecipeFilter() {
        if (recipeFilterActive) {
            closeRecipeFilter();
            return;
        }
        recipeFilterActive = true;
        recipeFilterHelpPinned = false;
        syncRecipeFilterCategory();
        layoutRecipeFilter();
        if (recipeFilterField != null) {
            EmiPort.focus(recipeFilterField, true);
        }
    }

    private void closeRecipeFilter() {
        recipeFilterActive = false;
        recipeFilterHelpPinned = false;
        recipeFilterSuppressNextHashCharacter = false;
        cancelPendingRecipeFilter();
        if (recipeFilterField != null) {
            EmiPort.focus(recipeFilterField, false);
            if (!recipeFilterQuery.isBlank()) {
                recipeFilterField.setValue("");
                return;
            }
        }
        recipeFilterGeneration++;
        recipeFilterFiltering = false;
        restoreRecipeFilterAppliedCategory(true);
    }

    private void syncRecipeFilterCategory() {
        if (tabs.isEmpty() || tab < 0 || tab >= tabs.size()) {
            return;
        }
        EmiRecipeCategory category = tabs.get(tab).category;
        if (recipeFilterCategory == category) {
            if (recipeFilterQuery.isBlank()) {
                List<EmiRecipe> source = originalRecipeFilterRecipes(category);
                recipeFilterTotalCount = source.size();
                recipeFilterCount = source.size();
            }
            return;
        }

        if (recipeFilterAppliedCategory != null && recipeFilterAppliedCategory != category) {
            restoreRecipeFilterAppliedCategory(false);
        }
        recipeFilterCategory = category;
        List<EmiRecipe> source = originalRecipeFilterRecipes(category);
        recipeFilterTotalCount = source.size();
        recipeFilterCount = source.size();
        if (recipeFilterActive && !recipeFilterQuery.isBlank()) {
            scheduleRecipeFilter(category, recipeFilterQuery);
        }
    }

    private void onRecipeFilterChanged(String value) {
        recipeFilterQuery = value == null ? "" : value;
        recipeFilterSuppressNextHashCharacter = false;
        if (recipeFilterField != null) {
            recipeFilterField.setSuggestion(recipeFilterQuery.isEmpty() ? I18n.get("emi.recipe_filter.hint") : "");
        }
        syncRecipeFilterCategory();
        if (recipeFilterQuery.isBlank()) {
            recipeFilterGeneration++;
            recipeFilterFiltering = false;
            cancelPendingRecipeFilter();
            restoreRecipeFilterAppliedCategory(true);
            List<EmiRecipe> source = originalRecipeFilterRecipes(recipeFilterCategory);
            recipeFilterTotalCount = source.size();
            recipeFilterCount = source.size();
            updateRecipeFilterColor(recipeFilterField);
            return;
        }
        if (recipeFilterCategory != null) {
            scheduleRecipeFilter(recipeFilterCategory, recipeFilterQuery);
        }
    }

    private void scheduleRecipeFilter(EmiRecipeCategory category, String querySnapshot) {
        cancelPendingRecipeFilter();
        long generation = ++recipeFilterGeneration;
        recipeFilterFiltering = true;
        updateRecipeFilterColor(recipeFilterField);
        pendingRecipeFilter = RECIPE_FILTER_EXECUTOR.schedule(
                () -> computeRecipeFilter(category, querySnapshot, generation),
                RECIPE_FILTER_DEBOUNCE_MS,
                TimeUnit.MILLISECONDS);
    }

    private void computeRecipeFilter(EmiRecipeCategory category, String querySnapshot, long generation) {
        if (generation != recipeFilterGeneration) {
            return;
        }
        List<EmiRecipe> source = originalRecipeFilterRecipes(category);
        RecipeFilterQuery parsed = RecipeFilterQuery.parse(querySnapshot);
        List<EmiRecipe> accepted = Lists.newArrayList();
        if (parsed.isEmpty()) {
            accepted.addAll(source);
        } else {
            for (int i = 0; i < source.size(); i++) {
                if ((i & 127) == 0 && generation != recipeFilterGeneration) {
                    return;
                }
                EmiRecipe recipe = source.get(i);
                if (parsed.test(recipeSearchIndex.document(recipe))) {
                    accepted.add(recipe);
                }
            }
        }
        if (generation != recipeFilterGeneration) {
            return;
        }
        List<EmiRecipe> result = List.copyOf(accepted);
        minecraft.execute(() -> applyRecipeFilter(category, querySnapshot, generation, source.size(), result));
    }

    private void applyRecipeFilter(EmiRecipeCategory category, String querySnapshot, long generation,
            int sourceCount, List<EmiRecipe> result) {
        if (minecraft.screen != this
                || generation != recipeFilterGeneration
                || recipeFilterCategory != category
                || !recipeFilterQuery.equals(querySnapshot)) {
            return;
        }
        recipeFilterFiltering = false;
        pendingRecipeFilter = null;
        recipeFilterTotalCount = sourceCount;
        recipeFilterCount = result.size();
        updateRecipeFilterColor(recipeFilterField);
        if (result.isEmpty()) {
            return;
        }
        if (recipeFilterAppliedCategory != null && recipeFilterAppliedCategory != category) {
            restoreRecipeFilterAppliedCategory(false);
        }
        if (replaceRecipeFilterTab(category, result, true)) {
            recipeFilterAppliedCategory = category;
        }
    }

    private boolean replaceRecipeFilterTab(EmiRecipeCategory category, List<EmiRecipe> newRecipes, boolean focus) {
        int index = -1;
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).category == category || tabs.get(i).category.equals(category)) {
                index = i;
                break;
            }
        }
        if (index < 0 && recipeFilterCategory == category && tab >= 0 && tab < tabs.size()) {
            index = tab;
        }
        if (index < 0) {
            return false;
        }
        RecipeTab replacement = new RecipeTab(category, newRecipes);
        replacement.bakePages(backgroundHeight);
        tabs.set(index, replacement);
        if (focus) {
            setPage(tabPage, index, 0);
        }
        return true;
    }

    private void restoreRecipeFilterAppliedCategory(boolean focusIfCurrent) {
        if (recipeFilterAppliedCategory == null) {
            return;
        }
        EmiRecipeCategory category = recipeFilterAppliedCategory;
        recipeFilterAppliedCategory = null;
        List<EmiRecipe> source = originalRecipeFilterRecipes(category);
        if (source.isEmpty()) {
            return;
        }
        boolean focus = focusIfCurrent && recipeFilterCategory == category;
        replaceRecipeFilterTab(category, source, focus);
    }

    private List<EmiRecipe> originalRecipeFilterRecipes(EmiRecipeCategory category) {
        if (recipes == null || category == null) {
            return List.of();
        }
        List<EmiRecipe> source = recipes.get(category);
        return source == null ? List.of() : source;
    }

    private void updateRecipeFilterColor(EditBox field) {
        if (field == null) {
            return;
        }
        int color;
        if (recipeFilterFiltering) {
            color = 0xFFFFAA;
        } else if (!recipeFilterQuery.isBlank() && recipeFilterCount == 0) {
            color = 0xFF5555;
        } else {
            color = 0xE0E0E0;
        }
        field.setTextColor(color);
        field.setTextColorUneditable(color);
    }

    private void cancelPendingRecipeFilter() {
        ScheduledFuture<?> future = pendingRecipeFilter;
        pendingRecipeFilter = null;
        if (future != null) {
            future.cancel(false);
        }
    }

    public WidgetGroup getGroup(Widget widget) {
        for (WidgetGroup group : currentPage) {
            if (group.widgets.contains(widget)) {
                return group;
            }
        }
        return null;
    }

    @Override
    public void onClose() {
        cancelPendingRecipeFilter();
        EmiHistory.popUntil(s -> !(s instanceof RecipeScreen), old);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public Bounds getBounds() {
        int top = y - 26;
        int bottom = y + backgroundHeight;
        int left = x;
        int right = x + backgroundWidth;
        if (EmiConfig.workstationLocation == SidebarSide.LEFT) {
            left -= 22;
        } else if (EmiConfig.workstationLocation == SidebarSide.RIGHT) {
            right += 22;
        }
        return new Bounds(left, top, right - left, bottom - top);
    }
}
