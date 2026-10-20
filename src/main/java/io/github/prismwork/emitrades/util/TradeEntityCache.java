package io.github.prismwork.emitrades.util;

import net.minecraft.world.entity.Entity;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

/** Owned by ClientLevel so the display entities are collected together with their world. */
public interface TradeEntityCache {
    Reference2ObjectOpenHashMap<TradeEntitySnapshot, Entity> emitrades$getDisplayEntities();
}
