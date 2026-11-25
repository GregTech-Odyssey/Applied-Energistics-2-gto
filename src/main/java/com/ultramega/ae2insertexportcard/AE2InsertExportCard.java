package com.ultramega.ae2insertexportcard;

import com.ultramega.ae2insertexportcard.network.NetworkHandler;
import com.ultramega.ae2insertexportcard.registry.ClientEventHandler;
import com.ultramega.ae2insertexportcard.registry.ModItems;
import com.ultramega.ae2insertexportcard.registry.RegistryHandler;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import de.mari_023.ae2wtlib.AE2wtlib;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;

@Mod(AE2InsertExportCard.MOD_ID)
public class AE2InsertExportCard {
    public static final String MOD_ID = "ae2insertexportcard";
    public static final String INSERT_CARD_ID = MOD_ID + ":insert_card";
    public static final String EXPORT_CARD_ID = MOD_ID + ":export_card";

    public static final NetworkHandler NETWORK_HANDLER = new NetworkHandler();

    public AE2InsertExportCard() {
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientEventHandler::new);

        RegistryHandler.init();

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(FMLCommonSetupEvent event) {
        Upgrades.add(ModItems.INSERT_CARD.get(), AEItems.WIRELESS_TERMINAL, 1);
        Upgrades.add(ModItems.EXPORT_CARD.get(), AEItems.WIRELESS_TERMINAL, 1);
        Upgrades.add(ModItems.INSERT_CARD.get(), AEItems.WIRELESS_CRAFTING_TERMINAL, 1);
        Upgrades.add(ModItems.EXPORT_CARD.get(), AEItems.WIRELESS_CRAFTING_TERMINAL, 1);
        Upgrades.add(ModItems.INSERT_CARD.get(), AE2wtlib.UNIVERSAL_TERMINAL, 1);
        Upgrades.add(ModItems.EXPORT_CARD.get(), AE2wtlib.UNIVERSAL_TERMINAL, 1);

        Upgrades.add(AEItems.FUZZY_CARD, ModItems.INSERT_CARD.get(), 1);
        Upgrades.add(AEItems.FUZZY_CARD, ModItems.EXPORT_CARD.get(), 1);
        Upgrades.add(AEItems.INVERTER_CARD, ModItems.INSERT_CARD.get(), 1);
        Upgrades.add(AEItems.CRAFTING_CARD, ModItems.EXPORT_CARD.get(), 1);
        Upgrades.add(AEItems.SPEED_CARD, ModItems.EXPORT_CARD.get(), 1);

        NETWORK_HANDLER.register();
    }
}
