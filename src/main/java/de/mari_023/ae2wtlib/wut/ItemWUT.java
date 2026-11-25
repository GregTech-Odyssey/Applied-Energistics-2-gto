package de.mari_023.ae2wtlib.wut;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import de.mari_023.ae2wtlib.TextConstants;
import de.mari_023.ae2wtlib.terminal.ItemWT;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.upgrades.Upgrades;
import appeng.api.util.IConfigManager;
import appeng.core.definitions.AEItems;
import appeng.menu.locator.MenuLocator;

public class ItemWUT extends ItemWT implements ICurioItem {
    private static final int FIXED_UPGRADE_SLOTS = 3;
    private static final boolean SINGLE_CURIO_TICK = Boolean.parseBoolean(
            System.getProperty("gtocore.wut.singleCurioTick", "true"));

    @Override
    public InteractionResultHolder<ItemStack> use(final Level w, final Player player, final InteractionHand hand) {
        if (WUTHandler.getCurrentTerminal(player.getItemInHand(hand)).isEmpty()) {
            if (!w.isClientSide())
                player.sendSystemMessage(TextConstants.TERMINAL_EMPTY);
            return new InteractionResultHolder<>(InteractionResult.FAIL, player.getItemInHand(hand));
        }
        /*
         * if(player.isShiftKeyDown()) { if(w.isClientSide()) Minecraft.getInstance().setScreen(new
         * WUTSelectScreen(player.getItemInHand(hand))); return new InteractionResultHolder<>(InteractionResult.SUCCESS,
         * player.getItemInHand(hand)); } else
         */
        return super.use(w, player, hand);
    }

    @Override
    public double getChargeRate(ItemStack stack) {
        return 800d
                * (countInstalledTerminals(stack) + 1 + getUpgrades(stack).getInstalledUpgrades(AEItems.ENERGY_CARD));
    }

    @Override
    public boolean open(final Player player, ItemStack stack, final MenuLocator locator, boolean returningFromSubmenu) {
        return WUTHandler.open(player, locator, returningFromSubmenu);
    }

    @Override
    public MenuType<?> getMenuType(ItemStack stack) {
        return WUTHandler.wirelessTerminals.get(WUTHandler.getCurrentTerminal(stack)).menuType();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(final ItemStack stack, final Level world, final List<Component> lines,
            final TooltipFlag advancedTooltips) {
        lines.add(TextConstants.UNIVERSAL);
        for (var terminal : WUTHandler.wirelessTerminals.entrySet()) {
            if (WUTHandler.hasTerminal(stack, terminal.getKey()))
                lines.add(terminal.getValue().terminalName());
        }
        super.appendHoverText(stack, world, lines, advancedTooltips);
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        // 固定 3 个升级槽位，避免合并多个终端后升级栏无限拉长。
        return UpgradeInventories.forItem(stack, FIXED_UPGRADE_SLOTS, this::onUpgradesChanged);
    }

    public void onUpgradesChanged(ItemStack stack, IUpgradeInventory upgrades) {
        setAEMaxPowerMultiplier(stack,
                countInstalledTerminals(stack) + Upgrades.getEnergyCardMultiplier(upgrades));
    }

    public int countInstalledTerminals(ItemStack stack) {
        int terminals = 0;
        for (String s : WUTHandler.terminalNames) {
            if (WUTHandler.hasTerminal(stack, s))
                terminals++;
        }
        return terminals;
    }

    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int i, boolean bl) {
        for (var terminal : WUTHandler.wirelessTerminals.entrySet()) {
            if (!WUTHandler.hasTerminal(itemStack, terminal.getKey()))
                continue;
            terminal.getValue().item().inventoryTick(itemStack,
                    level, entity, i, bl);
        }
    }

    public IConfigManager getConfigManager(ItemStack target) {
        return WUTHandler.wirelessTerminals.get(WUTHandler.getCurrentTerminal(target)).item().getConfigManager(target);
    }

    public void curioTick(SlotContext slotContext, ItemStack stack) {
        // Curios 已先执行 ItemStack.inventoryTick，此回调只会重复分发所有已安装终端的 tick。
        if (SINGLE_CURIO_TICK) {
            return;
        }
        inventoryTick(stack, slotContext.entity().level(), slotContext.entity(), 0, false);
    }
}
