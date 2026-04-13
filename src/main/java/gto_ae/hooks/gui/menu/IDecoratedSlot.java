package gto_ae.hooks.gui.menu;

import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

import gto_ae.hooks.gui.IIcon;

public interface IDecoratedSlot {

    default @Nullable IIcon getIcon() {
        return null;
    }

    default float getOpacityOfIcon() {
        return 1.0f;
    }

    default @NotNull List<Component> getEmptyTooltipMessage() {
        return Collections.emptyList();
    }
}
