package appeng.items.tools.powered;

import java.util.function.DoubleSupplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import de.mari_023.ae2wtlib.AE2wtlib;
import de.mari_023.ae2wtlib.terminal.IUniversalWirelessTerminalItem;
import de.mari_023.ae2wtlib.wct.WCTMenu;
import de.mari_023.ae2wtlib.wct.WCTMenuHost;
import de.mari_023.ae2wtlib.wct.magnet_card.MagnetHandler;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.IGrid;
import appeng.menu.locator.MenuLocators;

public class WirelessCraftingTerminalItem extends WirelessTerminalItem
        implements IUniversalWirelessTerminalItem, ICurioItem {
    public WirelessCraftingTerminalItem(DoubleSupplier powerCapacity, Properties props) {
        super(powerCapacity, props);
    }

    @Override
    public MenuType<?> getMenuType() {
        return WCTMenu.TYPE;
    }

    @Override
    public MenuType<?> getMenuType(ItemStack stack) {
        return getMenuType();
    }

    @Override
    protected boolean checkPreconditions(ItemStack item, Player player) {
        return checkUniversalPreconditions(item, player);
    }

    @Nullable
    @Override
    public ItemMenuHost getMenuHost(Player player, int inventorySlot, ItemStack stack, @Nullable BlockPos pos) {
        return new WCTMenuHost(player, inventorySlot, stack,
                (p, subMenu) -> tryOpen(player, MenuLocators.forInventorySlot(inventorySlot), stack, true));
    }

    @Nullable
    @Override
    public IGrid getLinkedGrid(ItemStack item, Level level, @Nullable Player sendMessagesTo) {
        return AE2wtlib.UNIVERSAL_TERMINAL.getLinkedGrid(item, level, sendMessagesTo);
    }

    @Override
    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(itemStack, level, entity, slotId, isSelected);
        if (!level.isClientSide() && entity instanceof ServerPlayer player) {
            MagnetHandler.handle(player, itemStack);
        }
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        inventoryTick(stack, slotContext.entity().level(), slotContext.entity(), 0, false);
    }
}
