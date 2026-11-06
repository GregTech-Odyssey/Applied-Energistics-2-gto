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

        // getName() works for every stack type: ItemEmiStack delegates to ItemStack#getHoverName, FluidEmiStack to the
        // fluid name. Going through getItemStack() instead would skip fluids, whose FluidEmiStack inherits the base
        // implementation that always returns an empty stack.
        for (var stack : hovered.getStack().getEmiStacks()) {
            if (!stack.isEmpty()) {
                return stack.getName().getString();
            }
        }

        return null;
    }
}
