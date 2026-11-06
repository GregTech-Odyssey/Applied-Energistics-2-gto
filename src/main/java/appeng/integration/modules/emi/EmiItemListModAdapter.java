package appeng.integration.modules.emi;

import com.google.common.base.Strings;

import org.jetbrains.annotations.Nullable;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.FluidEmiStack;
import dev.emi.emi.api.stack.ItemEmiStack;

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

        // EmiStack is extensible, and stacks like GTOCore's research tech nodes or ions also report a name without
        // being
        // something AE can search for. Only items and fluids are accepted, so hovering those keeps the T key's original
        // behaviour. getName() is used rather than getItemStack() because FluidEmiStack inherits a base implementation
        // that always returns an empty stack, which would skip fluids entirely.
        for (var stack : hovered.getStack().getEmiStacks()) {
            if ((stack instanceof ItemEmiStack || stack instanceof FluidEmiStack) && !stack.isEmpty()) {
                return stack.getName().getString();
            }
        }

        return null;
    }
}
