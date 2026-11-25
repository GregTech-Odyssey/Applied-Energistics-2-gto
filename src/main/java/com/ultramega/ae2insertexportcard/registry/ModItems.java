package com.ultramega.ae2insertexportcard.registry;

import com.ultramega.ae2insertexportcard.AE2InsertExportCard;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import appeng.api.upgrades.Upgrades;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,
            AE2InsertExportCard.MOD_ID);

    public static final RegistryObject<Item> INSERT_CARD = ITEMS.register("insert_card",
            () -> Upgrades.createUpgradeCardItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> EXPORT_CARD = ITEMS.register("export_card",
            () -> Upgrades.createUpgradeCardItem(new Item.Properties().stacksTo(1)));
}
