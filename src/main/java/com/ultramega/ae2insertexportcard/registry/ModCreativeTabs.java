package com.ultramega.ae2insertexportcard.registry;

import com.ultramega.ae2insertexportcard.AE2InsertExportCard;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB,
            AE2InsertExportCard.MOD_ID);

    public static final RegistryObject<CreativeModeTab> TAB_AE2INSERTEXPORTCARD = TABS.register(
            AE2InsertExportCard.MOD_ID,
            () -> CreativeModeTab.builder().title(Component.translatable("itemGroup." + AE2InsertExportCard.MOD_ID))
                    .icon(() -> new ItemStack(ModItems.INSERT_CARD.get())).displayItems((featureFlags, output) -> {
                        output.accept(new ItemStack(ModItems.INSERT_CARD.get()));
                        output.accept(new ItemStack(ModItems.EXPORT_CARD.get()));
                    }).build());
}
