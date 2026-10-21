/*
 * GTO AE adaptation of EMI Trades display entity lifetime.
 * Licensed under MIT; see LICENSE-EMITrades.
 */
package io.github.prismwork.emitrades.util;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/** Recipe indexes retain appearance data, never a live entity or its level. */
public final class TradeEntitySnapshot {
    private final EntityType<?> type;
    private final ResourceLocation id;
    private final CompoundTag tag;
    private final Component name;
    private final String modId;

    private TradeEntitySnapshot(Entity entity) {
        type = entity.getType();
        id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        tag = entity.saveWithoutId(new CompoundTag());
        name = entity.getName();
        modId = entity instanceof Villager villager
                ? BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession())
                        .getNamespace()
                : id.getNamespace();
    }

    private TradeEntitySnapshot(EntityType<?> type, CompoundTag appearance) {
        this.type = type;
        id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        tag = appearance;
        name = type.getDescription();
        modId = id.getNamespace();
    }

    /** Build a display snapshot without creating an entity on EMI's background loading thread. */
    public static TradeEntitySnapshot ofType(EntityType<?> type, CompoundTag appearance) {
        return new TradeEntitySnapshot(type, appearance);
    }

    public static TradeEntitySnapshot of(Entity entity) {
        return new TradeEntitySnapshot(entity);
    }

    public ResourceLocation id() {
        return id;
    }

    public CompoundTag tag() {
        return tag;
    }

    public Component name() {
        return name;
    }

    public String modId() {
        return modId;
    }

    public @Nullable Entity entity() {
        var level = Minecraft.getInstance().level;
        if (level == null)
            return null;
        var entities = ((TradeEntityCache) level).emitrades$getDisplayEntities();
        var entity = entities.get(this);
        if (entity == null) {
            entity = type.create(level);
            if (entity != null) {
                entity.load(tag);
                entities.put(this, entity);
            }
        }
        return entity;
    }
}
