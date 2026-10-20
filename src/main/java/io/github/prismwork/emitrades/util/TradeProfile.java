/*
 * Adapted from EMI Trades by Prismwork and Flamarine.
 * Copyright (c) 2022-2023 Prismwork. Licensed under MIT; see LICENSE-EMITrades.
 * Modified for the GTO AE fork (Mojang mappings and world-independent display data).
 */
package io.github.prismwork.emitrades.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/** Registration metadata; never retains a world, entity or mod-provided trade factory. */
public record TradeProfile(ResourceLocation profession, int level, TradeEntitySnapshot entity, boolean wandering) {
    public ResourceLocation recipeId(int index) {
        String path = profession.getNamespace().equals("minecraft") ? profession.getPath()
                : profession.getNamespace() + "/" + profession.getPath();
        return new ResourceLocation("emitrades", "villager_trades/" + path + "_" + index);
    }

    public MutableComponent title() {
        if (wandering)
            return Component.translatable("emi.emitrades.placeholder.wandering_trader");
        String professionName = profession.getNamespace().equals("minecraft") ? profession.getPath()
                : profession.getNamespace() + "." + profession.getPath();
        return Component.translatable("entity.minecraft.villager." + professionName)
                .append(" - ").append(Component.translatable("emi.emitrades.profession.lvl." + level));
    }
}
