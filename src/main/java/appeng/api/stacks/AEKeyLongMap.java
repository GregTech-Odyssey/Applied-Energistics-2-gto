package appeng.api.stacks;

import org.jetbrains.annotations.UnmodifiableView;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;

import appeng.api.identity.IdentityKeyLongMap;

public final class AEKeyLongMap<K extends AEKey> extends IdentityKeyLongMap<K>
        implements Iterable<Reference2LongMap.Entry<K>> {

    @UnmodifiableView
    public static final AEKeyLongMap<AEKey> EMPTY = new AEKeyLongMap<>(0);

    public AEKeyLongMap() {
        super(DEFAULT_INITIAL_SIZE, DEFAULT_LOAD_FACTOR);
    }

    public AEKeyLongMap(int size) {
        super(size, DEFAULT_LOAD_FACTOR);
    }

    public AEKeyLongMap(AEKeyBigMap<K> map) {
        super(map.size(), DEFAULT_LOAD_FACTOR);
        map.fastForEachLong(this::set);
    }

    public AEKeyLongMap(AEKeyLongMap<K> map) {
        super(map.size, DEFAULT_LOAD_FACTOR);
        map.fastForEach(this::set);
    }

    public AEKeyLongMap(Reference2LongOpenHashMap<K> map) {
        super(map.size(), DEFAULT_LOAD_FACTOR);
        map.reference2LongEntrySet().fastForEach(e -> set(e.getKey(), e.getLongValue()));
    }

    public AEKeyLongMap(Object2LongOpenHashMap<K> map) {
        super(map.size(), DEFAULT_LOAD_FACTOR);
        map.object2LongEntrySet().fastForEach(e -> set(e.getKey(), e.getLongValue()));
    }

    @SuppressWarnings("all")
    @UnmodifiableView
    public static <T extends AEKey> AEKeyLongMap<T> empty() {
        return (AEKeyLongMap<T>) EMPTY;
    }

    @Override
    public AEKeyLongMap<K> clone() {
        return (AEKeyLongMap<K>) super.clone();
    }
}
