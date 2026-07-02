/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package appeng.crafting.execution;

import java.util.Set;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;

/**
 * Stores the crafting logic of a crafting CPU.
 */
public abstract class CraftingCpuLogic {
    public final CraftingCPUCluster cluster;

    protected final ListCraftingInventory inventory = new ListCraftingInventory(this::postChange);

    protected boolean cantStoreItems = false;

    protected long lastModifiedOnTick = TickHandler.INSTANCE.getCurrentTick();

    public CraftingCpuLogic(CraftingCPUCluster cluster) {
        this.cluster = cluster;
    }

    public abstract ICraftingSubmitResult trySubmitJob(IGrid grid, ICraftingPlan plan, IActionSource src,
            @Nullable ICraftingRequester requester);

    public abstract void tickCraftingLogic(IEnergyService eg, CraftingService cc);

    /**
     * Called by the CraftingService with an Integer.MAX_VALUE priority to inject items that are being waited for.
     *
     * @return Consumed amount.
     */
    public abstract long insert(AEKey what, long amount, Actionable type);

    /**
     * Cancel the current job.
     */
    public abstract void cancel();

    protected abstract void postChange(AEKey what);

    public long getLastModifiedOnTick() {
        return lastModifiedOnTick;
    }

    public abstract boolean hasJob();

    @Nullable
    public abstract GenericStack getFinalJobOutput();

    public abstract ElapsedTimeTracker getElapsedTimeTracker();

    public abstract void readFromNBT(CompoundTag data);

    public abstract void writeToNBT(CompoundTag data);

    public abstract ICraftingLink getLastLink();

    public ListCraftingInventory getInventory() {
        return this.inventory;
    }

    /**
     * Register a listener that will receive stacks when either the stored items, await items or pending outputs change.
     * This is only used by the menu. Make sure to remove it by calling {@link #removeListener}.
     */
    public abstract void addListener(Consumer<AEKey> listener);

    public abstract void removeListener(Consumer<AEKey> listener);

    public long getStored(AEKey template) {
        return this.inventory.extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
    }

    public abstract long getWaitingFor(AEKey template);

    public abstract void getAllWaitingFor(Set<AEKey> waitingFor);

    public abstract long getPendingOutputs(AEKey template);

    /**
     * Used by the menu to gather all the kinds of stored items.
     */
    public abstract void getAllItems(KeyCounter out);

    public boolean isCantStoreItems() {
        return cantStoreItems;
    }
}
