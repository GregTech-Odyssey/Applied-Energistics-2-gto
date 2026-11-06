package appeng.integration.modules.emi;

import com.google.common.base.Strings;

import org.jetbrains.annotations.Nullable;

import dev.emi.emi.api.EmiApi;

import appeng.integration.abstraction.ItemListModAdapter;

class EmiItemListModAdapter implements ItemListModAdapter {
    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getShortName() {
        return "EMI";
    }

    @Override
    public String getSearchText() {
        return Strings.nullToEmpty(EmiApi.getSearchText());
    }

    @Override
    public void setSearchText(String text) {
        EmiApi.setSearchText(Strings.nullToEmpty(text));
    }

    @Override
    public boolean hasSearchFocus() {
        return EmiApi.isSearchFocused();
    }

    @Override
    @Nullable
    public String getHoveredItemName() {
        var hovered = EmiApi.getHoveredStack(false);
        if (hovered.isEmpty()) {
            return null;
        }

        for (var stack : hovered.getStack().getEmiStacks()) {
            var itemStack = stack.getItemStack();
            if (!itemStack.isEmpty()) {
                return itemStack.getHoverName().getString();
            }
        }

        return null;
    }
}
