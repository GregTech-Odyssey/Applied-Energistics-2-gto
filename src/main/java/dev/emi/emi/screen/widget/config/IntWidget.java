package dev.emi.emi.screen.widget.config;

import dev.emi.emi.screen.ConfigScreen.Mutator;
import dev.emi.emi.screen.widget.config.ConfigEntryWidget;
import dev.emi.emi.screen.widget.config.IntEdit;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

public class IntWidget extends ConfigEntryWidget {
    public final dev.emi.emi.screen.widget.config.IntEdit edit;

    public IntWidget(Component name, List<ClientTooltipComponent> tooltip, Supplier<String> search, Mutator<Integer> mutator) {
        super(name, tooltip, search, 20);
        this.edit = new IntEdit(150, () -> mutator.get(), i -> mutator.set(i));
        this.setChildren(List.of(edit.text, edit.up, edit.down));
    }

    @Override
    public void update(int y, int x, int width, int height) {
        int right = x + width;
        edit.setPosition(right - 150, y);
    }
}
