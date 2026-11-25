package com.ultramega.ae2insertexportcard.container;

import org.jetbrains.annotations.NotNull;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class CardPlayerSlot extends Slot {
    private boolean cancelPickup = false;

    public CardPlayerSlot(Container inventory, int invSlot) {
        super(inventory, invSlot, 0, 0);
    }

    public boolean mayPlace(@NotNull ItemStack stack) {
        return true;
    }

    public boolean mayPickup(@NotNull Player player) {
        if (cancelPickup) {
            cancelPickup = false;
            return false;
        } else {
            return true;
        }
    }

    public void setCancelPickup(boolean cancelPickup) {
        this.cancelPickup = cancelPickup;
    }
}
