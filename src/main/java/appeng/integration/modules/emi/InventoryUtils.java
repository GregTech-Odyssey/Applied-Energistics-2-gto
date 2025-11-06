package appeng.integration.modules.emi;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import dev.emi.emi.api.stack.EmiStack;

import appeng.api.stacks.GenericStack;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.MEStorageMenu;

public class InventoryUtils {

    public static <T extends AEBaseMenu> List<EmiStack> getStacks(AbstractContainerScreen<T> menu,
            SlotSemantic semantic) {
        return menu.getMenu().getSlots(semantic).stream()
                .map(Slot::getItem)
                .map(EmiStack::of)
                .filter(Objects::nonNull)
                .toList();
    }

    public static <T extends MEStorageMenu> List<EmiStack> getExistingStacks(T menu) {
        Set<GridInventoryEntry> allEntries = menu.getClientRepo().getAllEntries();
        if (allEntries == null) {
            return List.of();
        }

        return allEntries.stream()
                .filter(it -> it.getWhat() != null)
                .filter(it -> it.getStoredAmount() > 0 || it.getRequestableAmount() > 0)
                .map(it -> new GenericStack(it.getWhat(), Math.max(it.getStoredAmount(), it.getRequestableAmount())))
                .map(EmiStackHelper::toEmiStack)
                .filter(Objects::nonNull)
                .toList();
    }
}
