package gto_ae.client.gui.widgets;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;

import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.Tooltip;
import appeng.client.gui.widgets.Scrollbar;

public class AEListBox implements ICompositeWidget {

    private int x = 0;
    private int y = 0;
    private int showNum = 3; // Number of items to show at once
    private boolean isVisible = true;
    private boolean catchScrollbar = true; // Whether to capture mouse wheel events for scrolling
    private final Scrollbar scrollbar;
    private final List<ListItem> items = new ArrayList<>();
    private final AEBaseScreen<?> screen;

    public AEListBox(AEBaseScreen<?> screen) {
        this.screen = screen;
        scrollbar = new Scrollbar(Scrollbar.SMALL);
        scrollbar.setCaptureMouseWheel(false);
        scrollbar.setVisible(false);
    }

    @Override
    public void populateScreen(Consumer<AbstractWidget> addWidget, Rect2i bounds, AEBaseScreen<?> screen) {
        for (ListItem item : items) {
            if (item.isVisible())
                item.populateScreen(addWidget, item.getBounds(), screen);
        }
    }

    @Override
    public boolean wantsAllMouseWheelEvents() {
        return catchScrollbar;
    }

    public void setCatchScrollbar(boolean catchScrollbar) {
        this.catchScrollbar = catchScrollbar;
        scrollbar.setCaptureMouseWheel(catchScrollbar);
    }

    public void addItem(ListItem item) {
        items.add(item);
        item.setPosition(new Point(x, y + items.size() * item.getHeight()));
    }

    public void clearItems() {
        items.forEach(ListItem::onRemove);
        items.clear();
    }

    @Override
    public void setPosition(Point position) {
        this.x = position.getX();
        this.y = position.getY();
    }

    @Override
    public void setSize(int width, int height) {
    }

    public int width() {
        return getVisibleItems().stream().mapToInt(ListItem::getWidth).max().orElse(0)
                + scrollbar.getBounds().getWidth();
    }

    public int height() {
        return getVisibleItems().stream().mapToInt(ListItem::getHeight).sum();
    }

    private List<ListItem> getVisibleItems() {
        scrollbar.setRange(0, Math.max(0, items.size() - showNum), 1);
        int start = scrollbar.getCurrentScroll();
        int end = Math.min(start + showNum, items.size());
        return items.subList(start, end);
    }

    @Override
    public Rect2i getBounds() {
        return new Rect2i(x, y, width(), height());
    }

    @Override
    public boolean onMouseWheel(Point mousePos, double delta) {
        scrollbar.onMouseWheel(mousePos, delta);
        return ICompositeWidget.super.onMouseWheel(mousePos, delta);
    }

    @Override
    public @Nullable Tooltip getTooltip(int mouseX, int mouseY) {
        if (!isVisible)
            return null;
        for (ListItem item : getVisibleItems()) {
            Tooltip tooltip = item.getTooltip(mouseX, mouseY);
            if (tooltip != null) {
                return tooltip;
            }
        }
        return null;
    }

    @Override
    public void updateBeforeRender() {
        var visibleItems = getVisibleItems();
        var accumulatedHeight = 0;
        for (ListItem item : items) {
            if (visibleItems.contains(item)) {
                item.setVisible(true);
                item.setPosition(new Point(x, y + accumulatedHeight));
                item.updateBeforeRender();
                accumulatedHeight += item.getHeight();
            } else {
                item.setVisible(false);
            }
        }
        if (items.size() > showNum) {
            scrollbar.setVisible(true);
            scrollbar.setPosition(new Point(x + width() - scrollbar.getBounds().getWidth(), y));
            scrollbar.setSize(scrollbar.getBounds().getWidth(), height());
            scrollbar.updateBeforeRender();
        } else {
            scrollbar.setVisible(false); // No scrolling needed
        }
    }

    @Override
    public boolean onMouseDown(Point mousePos, int button) {
        if (scrollbar.getBounds().contains(mousePos.getX(), mousePos.getY())) {
            return scrollbar.onMouseDown(mousePos, button);
        }
        for (ListItem item : getVisibleItems()) {
            if (item.getBounds().contains(mousePos.getX(), mousePos.getY())) {
                return item.onMouseDown(mousePos, button);
            }
        }
        return false;
    }

    @Override
    public boolean onMouseUp(Point mousePos, int button) {
        if (scrollbar.getBounds().contains(mousePos.getX(), mousePos.getY())) {
            return scrollbar.onMouseUp(mousePos, button);
        }
        for (ListItem item : getVisibleItems()) {
            if (item.getBounds().contains(mousePos.getX(), mousePos.getY())) {
                return item.onMouseUp(mousePos, button);
            }
        }
        return false;
    }

    @Override
    public boolean onMouseDrag(Point mousePos, int button) {
        if (scrollbar.getBounds().contains(mousePos.getX(), mousePos.getY())) {
            return scrollbar.onMouseDrag(mousePos, button);
        }
        for (ListItem item : getVisibleItems()) {
            if (item.getBounds().contains(mousePos.getX(), mousePos.getY())) {
                return item.onMouseDrag(mousePos, button);
            }
        }
        return false;
    }

    @Override
    public void drawBackgroundLayer(GuiGraphics guiGraphics, Rect2i bounds, Point mouse) {
        for (ListItem item : getVisibleItems()) {
            item.drawBackgroundLayer(guiGraphics, item.getBounds(), mouse);
        }
        if (scrollbar.isVisible()) {
            scrollbar.drawBackgroundLayer(guiGraphics, scrollbar.getBounds(), mouse);
        }
    }

    @Override
    public void drawForegroundLayer(GuiGraphics guiGraphics, Rect2i bounds, Point mouse) {
        for (ListItem item : getVisibleItems()) {
            item.drawForegroundLayer(guiGraphics, item.getBounds(), mouse);
        }
        if (scrollbar.isVisible()) {
            scrollbar.drawForegroundLayer(guiGraphics, scrollbar.getBounds(), mouse);
        }
    }

    @Override
    public void addExclusionZones(List<Rect2i> exclusionZones, Rect2i screenBounds) {
        exclusionZones.add(new Rect2i(x + screen.getGuiLeft(), y + screen.getGuiTop(), width(), height()));
    }

    public AEBaseScreen<?> getScreen() {
        return screen;
    }

    public List<ListItem> getItems() {
        return items;
    }

    public Scrollbar getScrollbar() {
        return scrollbar;
    }

    public boolean isCatchScrollbar() {
        return catchScrollbar;
    }

    @Override
    public boolean isVisible() {
        return isVisible;
    }

    public void setVisible(boolean visible) {
        isVisible = visible;
    }

    public int getShowNum() {
        return showNum;
    }

    public void setShowNum(int showNum) {
        this.showNum = showNum;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public interface ListItem extends ICompositeWidget {

        default int getWidth() {
            return getBounds().getWidth(); // Default width, can be overridden
        }

        default int getHeight() {
            return getBounds().getHeight(); // Default height, can be overridden
        }

        @Override
        default void setSize(int width, int height) {
        }

        void setVisible(boolean visible);

        default void onRemove() {
        }
    }
}
