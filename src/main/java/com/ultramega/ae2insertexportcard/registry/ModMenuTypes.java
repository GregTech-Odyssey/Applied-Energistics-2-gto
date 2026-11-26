package com.ultramega.ae2insertexportcard.registry;

import com.ultramega.ae2insertexportcard.container.UpgradeContainerMenu;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.ForgeRegistries;

import appeng.core.AppEng;

public class ModMenuTypes {
    public static void init() {
        registerMenuType(UpgradeContainerMenu.INSERT_CARD_ID, UpgradeContainerMenu.TYPE_INSERT);
        registerMenuType(UpgradeContainerMenu.EXPORT_CARD_ID, UpgradeContainerMenu.TYPE_EXPORT);
    }

    public static void registerMenuType(String id, MenuType<?> menuType) {
        ForgeRegistries.MENU_TYPES.register(AppEng.makeId(id), menuType);
    }
}
