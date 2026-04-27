package gto_ae.client.gui.widgets.expandable;

import net.minecraft.client.renderer.Rect2i;

public interface IExpandable {
    void toggleCollapsed();

    boolean isCollapsed();

    void setGroup(ExpandableGroup group);

    ExpandableGroup getGroup();

    default void onExpand() {
        if (getGroup() != null) {
            getGroup().onExpand(this);
        }
    }

    default void onCollapse() {
        if (getGroup() != null) {
            getGroup().onCollapse(this);
        }
    }

    Rect2i getExpandedBound();
}
