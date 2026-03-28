package gto_ae.client.gui.widgets.expandable;

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
}
