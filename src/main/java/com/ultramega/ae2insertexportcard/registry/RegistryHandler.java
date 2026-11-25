package com.ultramega.ae2insertexportcard.registry;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

public class RegistryHandler {
    public static void init() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModCreativeTabs.TABS.register(bus);
        ModItems.ITEMS.register(bus);
        bus.addListener((RegisterEvent event) -> {
            if (event.getRegistryKey().equals(ForgeRegistries.MENU_TYPES.getRegistryKey())) {
                ModMenuTypes.init();
            }
        });
    }
}
