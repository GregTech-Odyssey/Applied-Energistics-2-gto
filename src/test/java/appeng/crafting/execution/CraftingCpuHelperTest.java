package appeng.crafting.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Items;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class CraftingCpuHelperTest {
    @Test
    void extractTemplatesDoesNotOverflowRequestedAmount() {
        var key = AEItemKey.of(Items.COBBLESTONE);
        var inventory = new ListCraftingInventory(ignored -> {
        });
        inventory.insert(key, 10, Actionable.MODULATE);

        var extracted = CraftingCpuHelper.extractTemplates(inventory, new GenericStack(key, 2), Long.MAX_VALUE);

        assertEquals(5, extracted);
        assertEquals(0, inventory.list.get(key));
    }
}
