package appeng.api.config;

import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.AEKeyFilter;

/**
 * Configures a type-based filter for terminals and other views.
 */
public enum TypeFilter {
    ALL(AEKeyFilter.none()),
    ITEMS(AEKeyTypes.ITEMS.filter()),
    FLUIDS(AEKeyTypes.FLUIDS.filter());

    private final AEKeyFilter filter;

    TypeFilter(AEKeyFilter filter) {
        this.filter = filter;
    }

    public AEKeyFilter getFilter() {
        return filter;
    }
}
