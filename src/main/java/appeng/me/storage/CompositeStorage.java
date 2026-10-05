package appeng.me.storage;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.KeyTypedStorage;
import appeng.api.storage.MEStorage;
import appeng.core.localization.GuiText;

/**
 * Combines the per-key-type storages one face exposes. Listing is live until the owner starts ticking it as a
 * {@link ITickingMonitor}; from then on it is cached and refreshed per tick.
 */
public class CompositeStorage implements KeyTypedStorage, ITickingMonitor {

    private static final AEKeyType[] NO_TYPES = new AEKeyType[0];
    private static final MEStorage[] NO_STORAGES = new MEStorage[0];

    @Nullable
    private MEStorage items;
    @Nullable
    private MEStorage fluids;
    private AEKeyType[] otherTypes = NO_TYPES;
    private MEStorage[] others = NO_STORAGES;
    @Nullable
    private AvailableStacksCache cache;

    public CompositeStorage(Map<AEKeyType, MEStorage> storages) {
        setStorages(storages);
    }

    public CompositeStorage(@Nullable MEStorage items, @Nullable MEStorage fluids) {
        this.items = items;
        this.fluids = fluids;
    }

    public CompositeStorage(AEKeyType[] otherTypes) {
        if (otherTypes.length > 0) {
            this.otherTypes = otherTypes;
            this.others = new MEStorage[otherTypes.length];
        }
    }

    public void setStorages(Map<AEKeyType, MEStorage> storages) {
        Objects.requireNonNull(storages);
        var otherCount = storages.size();
        var items = storages.get(AEKeyTypes.ITEMS);
        var fluids = storages.get(AEKeyTypes.FLUIDS);
        if (items != null) {
            otherCount--;
        }
        if (fluids != null) {
            otherCount--;
        }
        var otherTypes = NO_TYPES;
        var others = NO_STORAGES;
        if (otherCount > 0) {
            otherTypes = new AEKeyType[otherCount];
            others = new MEStorage[otherCount];
            var i = 0;
            for (var entry : storages.entrySet()) {
                var type = entry.getKey();
                if (type != AEKeyTypes.ITEMS && type != AEKeyTypes.FLUIDS) {
                    otherTypes[i] = type;
                    others[i++] = entry.getValue();
                }
            }
        }
        this.items = items;
        this.fluids = fluids;
        this.otherTypes = otherTypes;
        this.others = others;
        invalidate();
    }

    public void setStorages(@Nullable MEStorage items, @Nullable MEStorage fluids) {
        var changed = false;
        if (this.items != items) {
            this.items = items;
            changed = true;
        }
        if (this.fluids != fluids) {
            this.fluids = fluids;
            changed = true;
        }
        if (changed) {
            invalidate();
        }
    }

    public void setOther(int index, @Nullable MEStorage storage) {
        var others = this.others;
        if (others[index] != storage) {
            others[index] = storage;
            invalidate();
        }
    }

    @Nullable
    private MEStorage storageFor(AEKey what) {
        if (what instanceof AEItemKey) {
            return items;
        }
        if (what instanceof AEFluidKey) {
            return fluids;
        }
        return other(what.getType());
    }

    @Nullable
    private MEStorage other(AEKeyType type) {
        var otherTypes = this.otherTypes;
        for (int i = 0; i < otherTypes.length; i++) {
            if (otherTypes[i] == type) {
                return others[i];
            }
        }
        return null;
    }

    private void invalidate() {
        var cache = this.cache;
        if (cache != null) {
            cache.invalidateCache();
        }
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        var storage = storageFor(what);
        return storage != null && storage.isPreferredStorageFor(what, source);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        var storage = storageFor(what);
        if (storage == null) {
            return 0;
        }
        var inserted = storage.insert(what, amount, mode, source);
        if (inserted > 0 && mode == Actionable.MODULATE) {
            invalidate();
        }
        return inserted;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        var storage = storageFor(what);
        if (storage == null) {
            return 0;
        }
        var extracted = storage.extract(what, amount, mode, source);
        if (extracted > 0 && mode == Actionable.MODULATE) {
            invalidate();
        }
        return extracted;
    }

    @Override
    public boolean supportsKeyType(AEKeyType type) {
        return forKeyType(type) != null;
    }

    @Nullable
    @Override
    public MEStorage forKeyType(AEKeyType type) {
        if (type == AEKeyTypes.ITEMS) {
            return items;
        }
        if (type == AEKeyTypes.FLUIDS) {
            return fluids;
        }
        return other(type);
    }

    @Override
    public boolean containsAny(Set<AEKey> primaryKeys) {
        if (containsAny(items, primaryKeys) || containsAny(fluids, primaryKeys)) {
            return true;
        }
        for (var other : others) {
            if (containsAny(other, primaryKeys)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(@Nullable MEStorage storage, Set<AEKey> primaryKeys) {
        if (storage == null) {
            return false;
        }
        if (storage instanceof KeyTypedStorage typed) {
            return typed.containsAny(primaryKeys);
        }
        for (var entry : storage.getAvailableStacks()) {
            if (entry.getLongValue() > 0 && primaryKeys.contains(entry.getKey().dropSecondary())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isEmpty() {
        if (!isEmpty(items) || !isEmpty(fluids)) {
            return false;
        }
        for (var other : others) {
            if (!isEmpty(other)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isEmpty(@Nullable MEStorage storage) {
        if (storage == null) {
            return true;
        }
        if (storage instanceof KeyTypedStorage typed) {
            return typed.isEmpty();
        }
        for (var entry : storage.getAvailableStacks()) {
            if (entry.getLongValue() > 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Component getDescription() {
        MutableComponent types = null;
        if (items != null) {
            types = append(null, AEKeyTypes.ITEMS);
        }
        if (fluids != null) {
            types = append(types, AEKeyTypes.FLUIDS);
        }
        var others = this.others;
        for (int i = 0; i < others.length; i++) {
            if (others[i] != null) {
                types = append(types, otherTypes[i]);
            }
        }
        return GuiText.ExternalStorage.text(types == null ? Component.empty() : types);
    }

    private static MutableComponent append(@Nullable MutableComponent types, AEKeyType type) {
        if (types == null) {
            return Component.empty().append(type.getDescription());
        }
        return types.append(", ").append(type.getDescription());
    }

    @Override
    public TickRateModulation onTick() {
        var cache = this.cache;
        if (cache == null) {
            this.cache = cache = new AvailableStacksCache(this::collectAvailableStacks);
            cache.setTickUpdate(false);
        }
        return cache.updateCache() ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    private void collectAvailableStacks(KeyCounter out) {
        var items = this.items;
        if (items != null) {
            items.getAvailableStacks(out);
        }
        var fluids = this.fluids;
        if (fluids != null) {
            fluids.getAvailableStacks(out);
        }
        for (var other : others) {
            if (other != null) {
                other.getAvailableStacks(out);
            }
        }
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        var cache = this.cache;
        if (cache != null) {
            out.addAll(cache.getAvailableStacksCache());
        } else {
            collectAvailableStacks(out);
        }
    }

    @Override
    public KeyCounter getAvailableStacks() {
        var cache = this.cache;
        if (cache != null) {
            return cache.getAvailableStacksCache();
        }
        var out = new KeyCounter();
        collectAvailableStacks(out);
        return out;
    }

    @Override
    public void getAvailableStacks(KeyCounter out, boolean extractableOnly) {
        if (!extractableOnly) {
            getAvailableStacks(out);
            return;
        }
        listExtractable(items, out);
        listExtractable(fluids, out);
        for (var other : others) {
            listExtractable(other, out);
        }
    }

    private static void listExtractable(@Nullable MEStorage storage, KeyCounter out) {
        if (storage instanceof KeyTypedStorage typed) {
            typed.getAvailableStacks(out, true);
        } else if (storage != null) {
            storage.getAvailableStacks(out);
        }
    }
}
