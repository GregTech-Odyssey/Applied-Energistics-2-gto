package gto_ae.client.gui.widgets.expandable;

import org.jetbrains.annotations.Nullable;

public class ExpandableGroup {
    @Nullable
    private IExpandable currentlyExpanded;

    public ExpandableGroup(IExpandable... expandables) {
        for (IExpandable e : expandables) {
            e.setGroup(this);
        }
    }

    public ExpandableGroup add(IExpandable... expandables) {
        for (IExpandable e : expandables) {
            e.setGroup(this);
        }
        return this;
    }

    public void onExpand(IExpandable iExpandable) {
        if (currentlyExpanded != null && currentlyExpanded != iExpandable) {
            currentlyExpanded.toggleCollapsed();
        }
        currentlyExpanded = iExpandable.isCollapsed() ? null : iExpandable;
    }

    public void onCollapse(IExpandable iExpandable) {
        if (currentlyExpanded == iExpandable) {
            currentlyExpanded = null;
        }
    }
}
