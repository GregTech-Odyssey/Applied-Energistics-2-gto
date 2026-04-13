package gto_ae.helpers.facility_management;

import java.util.Collections;

import com.google.common.collect.ImmutableSet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;

import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMaps;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.helpers.IConfigInvHost;
import appeng.parts.AEBasePart;

import gto_ae.api.util.DirectionalGlobalPos;

public interface IStatusTracked {

    ImmutableSet<ICraftingLink> getRequestedJobs();

    @NotNull
    WorkingStatus getStatus();

    void openGui(Player player);

    /**
     * 10秒统计一次，记录最近10秒内工作过的物品
     * <p>
     * 从网络移除物品时使用{@link ThroughputCounter#remove(AEKey, long)}，从网络添加物品时使用{@link ThroughputCounter#add(AEKey, long)}
     * </p>
     */
    @NotNull
    ThroughputCounter getThroughputCounter();

    default PatternContainerGroup getTerminalGroup() {
        return switch (this) {
            case AEBasePart aeBasePart -> IStatusTracked.makeGroup(this, AEItemKey.of(aeBasePart.getPartItem()));
            case AEBaseBlockEntity aeBaseBlockEntity ->
                IStatusTracked.makeGroup(this, AEItemKey.of(aeBaseBlockEntity.getItemFromBlockEntity()));
            default -> throw new IllegalStateException("Unknown IStatusTracked type: " + this.getClass());
        };
    }

    default Reference2LongMap<AEKey> getConfiguredSetting() {
        if (this instanceof IConfigInvHost configHolder) {
            var map = new Reference2LongOpenHashMap<AEKey>();
            for (var e : configHolder.getConfig().getAvailableStacks()) {
                map.put(e.getKey(), e.getLongValue());
            }
            return map;
        }
        return Reference2LongMaps.emptyMap();
    }

    default int getFacilityUid() {
        return FacilityUidManager.uidFor(this);
    }

    static PatternContainerGroup makeGroup(Object mayBeNameable, AEItemKey icon) {

        // Prefer own custom name / icon if player has named it
        if (mayBeNameable instanceof Nameable nameable && nameable.hasCustomName()) {
            var name = nameable.getCustomName();
            return new PatternContainerGroup(
                    icon,
                    name,
                    Collections.emptyList());
        }

        // If nothing is adjacent, just use itself
        return new PatternContainerGroup(
                icon,
                icon.getDisplayName(),
                Collections.emptyList());
    }

    @Nullable
    default DirectionalGlobalPos getFacilityPosition() {
        return switch (this) {
            case AEBasePart aeBasePart -> {
                var level = aeBasePart.getHost().getBlockEntity().getLevel();
                if (level == null) {
                    yield null;
                }
                yield new DirectionalGlobalPos(level.dimension(), aeBasePart.getHost().getBlockEntity().getBlockPos(),
                        aeBasePart.getSide());
            }
            case AEBaseBlockEntity aeBaseBlockEntity -> {
                if (aeBaseBlockEntity.getLevel() == null) {
                    yield null;
                }
                yield new DirectionalGlobalPos(aeBaseBlockEntity.getLevel().dimension(),
                        aeBaseBlockEntity.getBlockPos(),
                        null);
            }
            default -> null;
        };
    }
}
