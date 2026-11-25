package com.ultramega.ae2insertexportcard.registry;

import com.ultramega.ae2insertexportcard.container.UpgradeContainerMenu;
import com.ultramega.ae2insertexportcard.screen.UpgradeScreen;
import com.ultramega.ae2insertexportcard.util.UpgradeType;

import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import appeng.init.client.InitScreens;

public class ClientEventHandler {
    public ClientEventHandler() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::init);
    }

    public void init(FMLClientSetupEvent event) {
        InitScreens.<UpgradeContainerMenu, UpgradeScreen>register(
                UpgradeContainerMenu.TYPE_INSERT, (menu, playerInventory, title,
                        style) -> new UpgradeScreen(UpgradeType.INSERT, menu, playerInventory, title, style),
                "/screens/insert_card.json");
        InitScreens.<UpgradeContainerMenu, UpgradeScreen>register(
                UpgradeContainerMenu.TYPE_EXPORT, (menu, playerInventory, title,
                        style) -> new UpgradeScreen(UpgradeType.EXPORT, menu, playerInventory, title, style),
                "/screens/export_card.json");
    }
}
