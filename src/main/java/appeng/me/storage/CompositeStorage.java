package appeng.me.storage;

import java.util.Map;
import java.util.Objects;

import net.minecraft.network.chat.Component;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.core.localization.GuiText;

/**
 * Combines several ME storages that each handle only a given key-space.
 */
public class CompositeStorage implements MEStorage, ITickingMonitor {
    private final AvailableStacksCache cache;

    private Map<AEKeyType, MEStorage> storages;

    public CompositeStorage(Map<AEKeyType, MEStorage> storages) {
        this.storages = storages;
        this.cache = new AvailableStacksCache(out -> {
            for (var storage : storages.values()) {
                storage.getAvailableStacks(out);
            }
        });
    }

    public void setStorages(Map<AEKeyType, MEStorage> storages) {
        this.storages = Objects.requireNonNull(storages);
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        var storage = storages.get(what.getType());
        return storage != null && storage.isPreferredStorageFor(what, source);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        var storage = storages.get(what.getType());
        var inserted = storage != null ? storage.insert(what, amount, mode, source) : 0;

        if (inserted > 0 && mode == Actionable.MODULATE) {
            cache.invalidateCache();
        }

        return inserted;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        var storage = storages.get(what.getType());
        var extracted = storage != null ? storage.extract(what, amount, mode, source) : 0;

        if (extracted > 0 && mode == Actionable.MODULATE) {
            cache.invalidateCache();
        }

        return extracted;
    }

    /**
     * Describes the types of storage represented by this object.
     */
    @Override
    public Component getDescription() {
        var types = Component.literal("");
        boolean first = true;
        for (var keyType : storages.keySet()) {
            if (!first) {
                types.append(", ");
            } else {
                first = false;
            }
            types.append(keyType.getDescription());
        }

        return GuiText.ExternalStorage.text(types);
    }

    @Override
    public TickRateModulation onTick() {
        return TickRateModulation.SLOWER;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        out.addAll(cache.getAvailableStacksCache());
    }

    @Override
    public KeyCounter getAvailableStacks() {
        return cache.getAvailableStacksCache();
    }
}
