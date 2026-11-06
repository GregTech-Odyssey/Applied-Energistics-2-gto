package appeng.integration.abstraction;

import org.jetbrains.annotations.Nullable;

public interface ItemListModAdapter {

    boolean isEnabled();

    String getShortName();

    default String getSearchText() {
        return "";
    }

    default void setSearchText(String text) {
    }

    default boolean hasSearchFocus() {
        return false;
    }

    @Nullable
    default String getHoveredItemName() {
        return null;
    }

    static ItemListModAdapter none() {
        return new ItemListModAdapter() {
            @Override
            public boolean isEnabled() {
                return false;
            }

            @Override
            public String getShortName() {
                return "JEI/REI/EMI";
            }
        };
    }

}
