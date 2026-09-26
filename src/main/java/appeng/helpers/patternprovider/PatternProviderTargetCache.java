package appeng.helpers.patternprovider;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.config.Actionable;
import appeng.api.config.AdvancedBlockingMode;
import appeng.api.config.Settings;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.capabilities.Capabilities;
import appeng.core.definitions.AEItems;
import appeng.helpers.InterfaceLogicHost;
import appeng.me.storage.CompositeStorage;
import appeng.parts.automation.StackWorldBehaviors;
import appeng.util.BlockApiCache;

class PatternProviderTargetCache {
    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockApiCache<MEStorage> cache;
    private final Direction direction;
    private final IActionSource src;
    private final Map<AEKeyType, ExternalStorageStrategy> strategies;

    PatternProviderTargetCache(ServerLevel l, BlockPos pos, Direction direction, IActionSource src) {
        this.level = l;
        this.pos = pos;
        this.cache = BlockApiCache.create(Capabilities.STORAGE, l, pos);
        this.direction = direction;
        this.src = src;
        this.strategies = StackWorldBehaviors.createExternalStorageStrategies(l, pos, direction);
    }

    @Nullable
    PatternProviderTarget find() {
        // our capability first: allows any storage channel
        var meStorage = cache.find(direction);
        if (meStorage != null) {
            return wrapMeStorage(meStorage, getAdvancedBlockingContext());
        }

        // otherwise fall back to the platform capability
        var externalStorages = new IdentityHashMap<AEKeyType, MEStorage>(2);
        for (var entry : strategies.entrySet()) {
            var wrapper = entry.getValue().createWrapper(false, () -> {
            });
            if (wrapper != null) {
                externalStorages.put(entry.getKey(), wrapper);
            }
        }

        if (externalStorages.size() > 0) {
            return wrapMeStorage(new CompositeStorage(externalStorages), null);
        }

        return null;
    }

    @Nullable
    private AdvancedBlockingContext getAdvancedBlockingContext() {
        var blockEntity = level.getBlockEntity(pos);
        InterfaceLogicHost interfaceHost = null;
        if (blockEntity instanceof InterfaceLogicHost directInterface) {
            interfaceHost = directInterface;
        } else if (blockEntity instanceof CableBusBlockEntity cableBus
                && cableBus.getPart(direction) instanceof InterfaceLogicHost partInterface) {
            interfaceHost = partInterface;
        }

        if (interfaceHost != null && interfaceHost.getUpgrades().isInstalled(AEItems.ADVANCED_BLOCKING_CARD)) {
            var logic = interfaceHost.getInterfaceLogic();
            var networkStorage = logic.getNetworkStorage();
            if (networkStorage != null) {
                return new AdvancedBlockingContext(
                        networkStorage,
                        logic.getConfigManager().getSetting(Settings.ADVANCED_BLOCKING_MODE));
            }
        }
        return null;
    }

    private PatternProviderTarget wrapMeStorage(MEStorage storage, @Nullable AdvancedBlockingContext advancedBlocking) {
        return new PatternProviderTarget() {
            @Override
            public long insert(AEKey what, long amount, Actionable type) {
                return storage.insert(what, amount, type, src);
            }

            @Override
            public boolean containsPatternInput(Set<AEKey> patternInputs) {
                return PatternProviderTarget.isBlocked(
                        storage,
                        patternInputs,
                        advancedBlocking == null ? null : advancedBlocking.storage(),
                        advancedBlocking == null ? AdvancedBlockingMode.DEFAULT : advancedBlocking.mode());
            }
        };
    }

    private record AdvancedBlockingContext(MEStorage storage, AdvancedBlockingMode mode) {
    }
}
