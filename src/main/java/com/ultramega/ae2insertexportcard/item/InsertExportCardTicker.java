package com.ultramega.ae2insertexportcard.item;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.ultramega.ae2insertexportcard.AE2InsertExportCard;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageHelper;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.definitions.AEItems;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.me.helpers.ChannelPowerSrc;
import appeng.me.helpers.MachineSource;
import appeng.me.helpers.PlayerSource;
import appeng.util.ConfigInventory;

public final class InsertExportCardTicker {

    private InsertExportCardTicker() {
    }

    @Nullable
    public static Future<ICraftingPlan> tick(WirelessTerminalItem terminalItem, ItemStack stack, Level level,
            Entity entity, int slotId, @Nullable Future<ICraftingPlan> craftingJob) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer player)) {
            return craftingJob;
        }
        CompoundTag rootTag = stack.getTag();
        if (rootTag == null || !rootTag.contains("upgrades", Tag.TAG_LIST)) {
            return craftingJob;
        }

        ListTag tagList = rootTag.getList("upgrades", Tag.TAG_COMPOUND);
        if (tagList.isEmpty()) {
            return craftingJob;
        }

        // 仅在存在输入/输出卡时才解析网络、宿主与范围检查，避免普通无线终端每 tick 产生额外开销。
        IGrid grid = null;
        WirelessTerminalMenuHost host = null;
        boolean resolved = false;

        for (int i = 0; i < tagList.size(); i++) {
            CompoundTag entry = tagList.getCompound(i);
            String id = entry.getString("id");
            boolean isInsertUpgrade = AE2InsertExportCard.INSERT_CARD_ID.equals(id);
            boolean isExportUpgrade = !isInsertUpgrade && AE2InsertExportCard.EXPORT_CARD_ID.equals(id);
            if (!isInsertUpgrade && !isExportUpgrade) {
                continue;
            }
            if (!(entry.get("tag") instanceof CompoundTag cardTag)) {
                continue;
            }
            int[] selectedInventorySlots = cardTag.getIntArray(UpgradeHost.NBT_SELECTED_INVENTORY_SLOTS);
            if (selectedInventorySlots.length == 0) {
                continue;
            }

            if (!resolved) {
                resolved = true;
                grid = terminalItem.getLinkedGrid(stack, level, player);
                if (grid == null || grid.getStorageService() == null) {
                    return craftingJob;
                }
                if (!(terminalItem.getMenuHost(player, slotId, stack,
                        null) instanceof WirelessTerminalMenuHost menuHost)
                        || !menuHost.rangeCheck()) {
                    return craftingJob;
                }
                host = menuHost;
            }

            var node = host.getActionableNode();
            if (node == null) {
                return craftingJob;
            }

            int upgradeSlot = entry.getInt("Slot");
            ItemStack upgrade = host.getUpgrades().getStackInSlot(upgradeSlot);
            IUpgradeInventory upgrades = UpgradeInventories.forItem(upgrade, 3, null);
            boolean invertFilter = upgrades.isInstalled(AEItems.INVERTER_CARD);
            boolean fuzzyInstalled = upgrades.isInstalled(AEItems.FUZZY_CARD);
            boolean speedInstalled = upgrades.isInstalled(AEItems.SPEED_CARD);
            boolean craftingInstalled = upgrades.isInstalled(AEItems.CRAFTING_CARD);
            FuzzyMode fuzzyMode = parseFuzzyMode(upgrade.getOrCreateTag().getString("fuzzy_mode"));

            ConfigInventory filterConfig = ConfigInventory.configTypes(18, null);
            filterConfig.readFromChildTag(cardTag, "filterConfig");

            var storage = grid.getStorageService().getInventory();
            var powerSrc = new ChannelPowerSrc(node, grid.getEnergyService());
            IActionSource source = new PlayerSource(player);
            IItemHandler playerInventory = isExportUpgrade
                    ? player.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null)
                    : null;

            for (int j = 0; j < selectedInventorySlots.length; j++) {
                int selected = selectedInventorySlots[j];
                if (selected < 1) {
                    continue;
                }
                ItemStack itemInInventory = player.getInventory().getItem(j);
                if (itemInInventory == stack) {
                    continue;
                }

                if (isInsertUpgrade) {
                    if (itemInInventory.isEmpty()) {
                        continue;
                    }
                    AEKey what = AEItemKey.of(itemInInventory);
                    if (what == null) {
                        continue;
                    }

                    // Import Fluids
                    for (int index = 0; index < filterConfig.size(); index++) {
                        GenericStack filter = filterConfig.getStack(index);
                        if (filter != null && filter.what() instanceof AEFluidKey) {
                            var fluidCap = itemInInventory.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM);
                            if (fluidCap.isPresent()) {
                                var fluidItem = fluidCap.orElseThrow(IllegalStateException::new);
                                FluidStack fluidStack = fluidItem.drain(Integer.MAX_VALUE,
                                        IFluidHandler.FluidAction.SIMULATE);
                                if (!fluidStack.isEmpty()) {
                                    AEFluidKey aeFluidKey = AEFluidKey.of(fluidStack);
                                    if (aeFluidKey != null) {
                                        long amount = StorageHelper.poweredInsert(powerSrc, storage, aeFluidKey,
                                                fluidStack.getAmount(), source, Actionable.SIMULATE);
                                        if (amount > 0) {
                                            fluidItem.drain((int) amount, IFluidHandler.FluidAction.EXECUTE);
                                            StorageHelper.poweredInsert(powerSrc, storage, aeFluidKey, amount, source,
                                                    Actionable.MODULATE);
                                            player.containerMenu.broadcastChanges();
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Import Items
                    boolean matched = matchesFilter(filterConfig, what, fuzzyInstalled, fuzzyMode);
                    if (invertFilter != matched) {
                        long amount = StorageHelper.poweredInsert(powerSrc, storage, what, itemInInventory.getCount(),
                                source, Actionable.SIMULATE);
                        if (amount > 0) {
                            StorageHelper.poweredInsert(powerSrc, storage, what, itemInInventory.getCount(), source,
                                    Actionable.MODULATE);
                            player.getInventory().setItem(j, ItemStack.EMPTY);
                            player.containerMenu.broadcastChanges();
                        }
                    }
                } else if (playerInventory != null) {
                    int filterIndex = selected - 1;
                    if (filterIndex >= filterConfig.size()) {
                        continue;
                    }
                    GenericStack filter = filterConfig.getStack(filterIndex);
                    if (filter == null) {
                        continue;
                    }

                    var fluidCap = itemInInventory.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM);
                    boolean acceptsFluid = fluidCap.isPresent();
                    AEItemKey what = itemInInventory.isEmpty() ? null : AEItemKey.of(itemInInventory.getItem());
                    if (!acceptsFluid && what != null
                            && !(fuzzyInstalled ? what.fuzzyEquals(filter.what(), fuzzyMode)
                                    : what.equals(filter.what()))) {
                        continue;
                    }

                    AEKey toExportKey;
                    if (fuzzyInstalled) {
                        toExportKey = findFirstFuzzy(grid, filter.what(), fuzzyMode);
                    } else {
                        toExportKey = filter.what();
                    }
                    if (toExportKey == null) {
                        continue;
                    }

                    if (!acceptsFluid && toExportKey instanceof AEItemKey fuzzyItem) {
                        int extractAmount = Math.min(itemInInventory.getMaxStackSize() - itemInInventory.getCount(),
                                itemInInventory.getMaxStackSize());
                        int stackInteractionSize = speedInstalled ? 64 : 1;
                        int size = Math.min(stackInteractionSize, extractAmount);
                        if (size <= 0) {
                            continue;
                        }

                        long extracted = StorageHelper.poweredExtraction(powerSrc, storage, toExportKey, size, source,
                                Actionable.MODULATE);
                        if (extracted <= 0) {
                            if (craftingInstalled) {
                                craftingJob = tryRequestCraft(grid, level, filter.what(), size, craftingJob);
                            }
                            continue;
                        }

                        playerInventory.insertItem(j, fuzzyItem.toStack((int) extracted), false);
                        player.containerMenu.broadcastChanges();
                    } else if (acceptsFluid && toExportKey instanceof AEFluidKey fuzzyFluid) {
                        int stackInteractionSize = speedInstalled ? AEFluidKey.AMOUNT_BUCKET * 64
                                : AEFluidKey.AMOUNT_BUCKET;
                        long extracted = StorageHelper.poweredExtraction(powerSrc, storage, toExportKey,
                                stackInteractionSize, source, Actionable.SIMULATE);
                        if (extracted <= 0) {
                            continue;
                        }
                        var fluidItem = fluidCap.orElseThrow(IllegalStateException::new);
                        int amount = fluidItem.fill(fuzzyFluid.toStack((int) extracted),
                                IFluidHandler.FluidAction.SIMULATE);
                        if (amount > 0) {
                            StorageHelper.poweredExtraction(powerSrc, storage, toExportKey, amount, source,
                                    Actionable.MODULATE);
                            fluidItem.fill(fuzzyFluid.toStack(amount), IFluidHandler.FluidAction.EXECUTE);
                            player.containerMenu.broadcastChanges();
                        }
                    }
                }
            }
        }
        return craftingJob;
    }

    private static FuzzyMode parseFuzzyMode(String name) {
        if (name.isEmpty()) {
            return FuzzyMode.IGNORE_ALL;
        }
        for (var mode : FuzzyMode.values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return FuzzyMode.IGNORE_ALL;
    }

    private static boolean matchesFilter(ConfigInventory filterConfig, AEKey what, boolean fuzzyInstalled,
            FuzzyMode fuzzyMode) {
        for (var entry : filterConfig.getAvailableStacks().findFuzzy(what, fuzzyMode)) {
            AEKey key = entry.getKey();
            if (fuzzyInstalled ? what.fuzzyEquals(key, fuzzyMode) : what.equals(key)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static AEKey findFirstFuzzy(IGrid grid, AEKey filterKey, FuzzyMode fuzzyMode) {
        for (var entry : grid.getStorageService().getCachedInventory().findFuzzy(filterKey, fuzzyMode)) {
            return entry.getKey();
        }
        return null;
    }

    @Nullable
    private static Future<ICraftingPlan> tryRequestCraft(IGrid grid, Level level, AEKey what, int size,
            @Nullable Future<ICraftingPlan> craftingJob) {
        var craftingService = grid.getCraftingService();
        if (!craftingService.isCraftable(what) || craftingService.getRequestedAmount(what) > 0) {
            return craftingJob;
        }
        var src = new MachineSource(grid::getPivot);
        if (craftingJob != null && craftingJob.isDone()) {
            try {
                ICraftingPlan job = craftingJob.get();
                if (job != null) {
                    craftingService.submitJob(job, null, null, false, src);
                    craftingJob = null;
                }
            } catch (InterruptedException | ExecutionException ignored) {
                // 合成计划计算失败时丢弃旧任务并在下方重新发起计算。
                craftingJob = null;
            }
        }
        return craftingService.beginCraftingCalculation(level, () -> src, what, size, CalculationStrategy.CRAFT_LESS);
    }
}
