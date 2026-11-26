package com.ultramega.ae2insertexportcard.item;

import com.ultramega.ae2insertexportcard.AE2InsertExportCard;
import com.ultramega.ae2insertexportcard.registry.ModItems;
import com.ultramega.ae2insertexportcard.util.UpgradeType;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.util.ConfigInventory;
import appeng.util.ConfigManager;

public class UpgradeHost implements IConfigurableObject {
    public static final String NBT_SELECTED_INVENTORY_SLOTS = "SelectedInventorySlots";

    public final ConfigInventory filterConfig = ConfigInventory.configTypes(18, this::updateFilter);
    private IConfigManager configManager;
    private IUpgradeInventory upgrades;

    private final UpgradeType type;
    private final ItemStack itemStack;
    private final WirelessTerminalMenuHost host;

    public UpgradeHost(UpgradeType type, int id, Inventory inventory, WirelessTerminalMenuHost host) {
        this.type = type;
        this.itemStack = host.getItemStack();
        this.host = host;
        for (int i = 0; i < host.getUpgrades().size(); i++) {
            ItemStack upgrade = host.getUpgrades().getStackInSlot(i);
            if (upgrade.getItem() == (type == UpgradeType.INSERT ? ModItems.INSERT_CARD.get()
                    : ModItems.EXPORT_CARD.get())) {
                this.configManager = new ConfigManager(
                        (manager, settingName) -> manager.writeToNBT(upgrade.getOrCreateTag()));
                this.upgrades = UpgradeInventories.forItem(upgrade, type == UpgradeType.EXPORT ? 3 : 2, null);

                FuzzyMode fzMode;
                final String fz = upgrade.getOrCreateTag().getString("fuzzy_mode");
                try {
                    fzMode = FuzzyMode.valueOf(fz);
                } catch (Throwable t) {
                    fzMode = FuzzyMode.IGNORE_ALL;
                }
                this.configManager.registerSetting(Settings.FUZZY_MODE, fzMode);
            }
        }
        if (upgrades == null) {
            this.upgrades = UpgradeInventories.empty();
        }

        ListTag tagList = itemStack.getTag().getList("upgrades", Tag.TAG_COMPOUND);
        for (int i = 0; i < tagList.size(); i++) {
            if (isInsertOrExportCard(type, tagList, i)) {
                CompoundTag tag = (CompoundTag) tagList.getCompound(i).get("tag");

                if (tag == null) {
                    tag = new CompoundTag();
                }

                filterConfig.readFromChildTag(tag, "filterConfig");

                tagList.getCompound(i).put("tag", tag);
            }
        }
    }

    private void updateFilter() {
        ListTag tagList = itemStack.getTag().getList("upgrades", Tag.TAG_COMPOUND);
        for (int i = 0; i < tagList.size(); i++) {
            if (isInsertOrExportCard(type, tagList, i)) {
                CompoundTag tag = (CompoundTag) tagList.getCompound(i).get("tag");

                if (tag == null) {
                    tag = new CompoundTag();
                }

                filterConfig.writeToChildTag(tag, "filterConfig");

                tagList.getCompound(i).put("tag", tag);
            }
        }
    }

    public void setSelectedInventorySlots(int[] selectedInventorySlots) {
        ListTag tagList = itemStack.getTag().getList("upgrades", Tag.TAG_COMPOUND);
        for (int i = 0; i < tagList.size(); i++) {
            if (isInsertOrExportCard(type, tagList, i)) {
                CompoundTag tag = (CompoundTag) tagList.getCompound(i).get("tag");

                if (tag == null) {
                    tag = new CompoundTag();
                }

                tag.putIntArray(NBT_SELECTED_INVENTORY_SLOTS, selectedInventorySlots);

                tagList.getCompound(i).put("tag", tag);
            }
        }
    }

    public int[] getSelectedInventorySlots() {
        ListTag tagList = itemStack.getTag().getList("upgrades", Tag.TAG_COMPOUND);
        for (int i = 0; i < tagList.size(); i++) {
            if (isInsertOrExportCard(type, tagList, i)) {
                CompoundTag tag = (CompoundTag) tagList.getCompound(i).get("tag");

                if (tag != null && tag.contains(NBT_SELECTED_INVENTORY_SLOTS)) {
                    return tag.getIntArray(NBT_SELECTED_INVENTORY_SLOTS);
                }
            }
        }

        return new int[36];
    }

    public boolean isInsertOrExportCard(UpgradeType type, ListTag tagList, int index) {
        String tagId = tagList.getCompound(index).getString("id");
        String target = type == UpgradeType.INSERT ? AE2InsertExportCard.INSERT_CARD_ID
                : AE2InsertExportCard.EXPORT_CARD_ID;
        return tagId.equals(target);
    }

    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    @Override
    public IConfigManager getConfigManager() {
        return configManager;
    }
}
