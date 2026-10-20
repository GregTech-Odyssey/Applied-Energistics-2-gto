package appeng.mixins.emi;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import io.github.prismwork.emitrades.util.TradeEntityCache;
import io.github.prismwork.emitrades.util.TradeEntitySnapshot;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

@Mixin(ClientLevel.class)
public abstract class TradeClientLevelMixin implements TradeEntityCache {
    @Unique
    private final Reference2ObjectOpenHashMap<TradeEntitySnapshot, Entity> emitrades$displayEntities = new Reference2ObjectOpenHashMap<>(
            16);

    @Override
    public Reference2ObjectOpenHashMap<TradeEntitySnapshot, Entity> emitrades$getDisplayEntities() {
        return emitrades$displayEntities;
    }
}
