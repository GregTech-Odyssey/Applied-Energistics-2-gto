package gto_ae.hooks.gui.menu;

import org.jetbrains.annotations.NotNull;

import net.minecraft.world.item.ItemStack;

import appeng.menu.me.common.GridInventoryEntry;

public interface IRepoSlot {

    GridInventoryEntry getEntry();

    long getStoredAmount();

    boolean isCraftable();

    @NotNull
    ItemStack getItem();

    boolean hasItem();
}
