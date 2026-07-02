package appeng.api.stacks;

import java.util.*;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;

import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongLists;
import it.unimi.dsi.fastutil.objects.*;

import appeng.api.config.FuzzyMode;

/**
 * Tallies a negative or positive amount for sub-variants of a {@link AEKey}.
 * <p>
 * copied From: <a href="https://github.com/gharris1727/Applied-Energistics-2">...</a>
 */
final class VariantCounter implements Iterable<Object2LongMap.Entry<AEKey>> {

    private static final byte EMPTY = 0; // zero types
    private static final byte SINGLE = 1; // one type
    private static final byte GENERIC = 2; // 2+ stacks that do not have durability
    private static final byte FUZZY = 3; // 2+ stacks that have durability

    private byte state;

    // valid IFF state == SINGLE
    private AEKey key;
    // valid IFF state == SINGLE
    private long count;
    // valid IFF state == GENERIC
    private Object2LongOpenHashMap<AEKey> genericRecords;
    // valid IFF state == FUZZY
    private Object2LongAVLTreeMap<AEKey> fuzzyRecords;

    public VariantCounter() {
        state = EMPTY;
    }

    private VariantCounter(byte state, AEKey key, long count, Object2LongOpenHashMap<AEKey> genericRecords,
            Object2LongAVLTreeMap<AEKey> fuzzyRecords) {
        this.state = state;
        this.key = key;
        this.count = count;
        this.genericRecords = genericRecords;
        this.fuzzyRecords = fuzzyRecords;
    }

    public long get(AEKey key) {
        return switch (state) {
            case EMPTY -> 0;
            case SINGLE -> this.key == key ? count : 0;
            case GENERIC -> genericRecords.getOrDefault(key, 0);
            case FUZZY -> fuzzyRecords.getOrDefault(key, 0);
            default -> throw new IllegalStateException("Unexpected value: " + state);
        };
    }

    public void add(AEKey key, long amount) {
        switch (state) {
            case EMPTY -> addEmpty(key, amount);
            case SINGLE -> addSingle(key, amount);
            case GENERIC -> genericRecords.addTo(key, amount);
            case FUZZY -> fuzzyRecords.addTo(key, amount);
        }
    }

    // valid IFF state == EMPTY
    private void addEmpty(AEKey key, long amount) {
        this.key = key;
        this.count = amount;
        state = SINGLE;
    }

    // valid IFF state == SINGLE
    private void addSingle(AEKey key, long amount) {
        if (this.key == key) {
            count += amount;
        } else {
            addSingleDistinct(key, amount);
        }
    }

    // valid IFF state == SINGLE && !this.key.equals(key)
    private void addSingleDistinct(AEKey key, long amount) {
        if (this.key.getFuzzySearchMaxValue() <= 0) {
            genericRecords = new Object2LongOpenHashMap<>();
            genericRecords.put(this.key, this.count);
            genericRecords.put(key, amount);
            this.key = null;
            this.count = 0;
            state = GENERIC;
        } else {
            fuzzyRecords = new Object2LongAVLTreeMap<>(FuzzySearch.COMPARATOR);
            fuzzyRecords.put(this.key, this.count);
            fuzzyRecords.put(key, amount);
            this.key = null;
            this.count = 0;
            state = FUZZY;
        }
    }

    public long set(AEKey key, long amount) {
        return switch (state) {
            case EMPTY -> setEmpty(key, amount);
            case SINGLE -> setSingle(key, amount);
            case GENERIC -> genericRecords.put(key, amount);
            case FUZZY -> fuzzyRecords.put(key, amount);
            default -> throw new IllegalStateException("Unexpected value: " + state);
        };
    }

    // valid IFF state == EMPTY
    private long setEmpty(AEKey key, long amount) {
        this.key = key;
        this.count = amount;
        state = SINGLE;
        return 0;
    }

    // valid IFF state == SINGLE
    private long setSingle(AEKey key, long amount) {
        if (this.key == key) {
            long ret = count;
            count = amount;
            return ret;
        }
        addSingleDistinct(key, amount);
        return 0;
    }

    public long remove(AEKey key) {
        return switch (state) {
            case EMPTY -> 0;
            case SINGLE -> removeSingle(key);
            case GENERIC -> genericRecords.removeLong(key);
            case FUZZY -> fuzzyRecords.removeLong(key);
            default -> throw new IllegalStateException("Unexpected value: " + state);
        };
    }

    // valid IFF state == SINGLE
    private long removeSingle(AEKey key) {
        if (this.key == key) {
            long ret = this.count;
            this.count = 0;
            this.state = EMPTY;
            return ret;
        }
        return 0;
    }

    public void addAll(VariantCounter other) {
        for (var entry : other) {
            add(entry.getKey(), entry.getLongValue());
        }
    }

    public void removeAll(VariantCounter other) {
        for (var entry : other) {
            add(entry.getKey(), -entry.getLongValue());
        }
    }

    public boolean containsFuzzy(AEKey filter, FuzzyMode fuzzy) {
        return switch (state) {
            case EMPTY -> false;
            case SINGLE -> key.fuzzyModeEquals(filter, fuzzy);
            case GENERIC -> !genericRecords.isEmpty();
            case FUZZY -> !FuzzySearch.findFuzzy(fuzzyRecords, filter, fuzzy).isEmpty();
            default -> throw new IllegalStateException("Unexpected value: " + state);
        };
    }

    public Set<AEKey> findFuzzyKey(AEKey filter, FuzzyMode fuzzy) {
        return switch (state) {
            case EMPTY -> Collections.emptySet();
            case SINGLE -> key.fuzzyModeEquals(filter, fuzzy) ? Collections.singleton(key) : Collections.emptySet();
            case GENERIC -> genericRecords.keySet();
            case FUZZY -> FuzzySearch.findFuzzy(fuzzyRecords, filter, fuzzy).keySet();
            default -> throw new IllegalStateException("Unexpected value: " + state);
        };
    }

    public LongCollection findFuzzyValue(AEKey filter, FuzzyMode fuzzy) {
        return switch (state) {
            case EMPTY -> LongLists.EMPTY_LIST;
            case SINGLE -> key.fuzzyModeEquals(filter, fuzzy) ? LongLists.singleton(key) : LongLists.EMPTY_LIST;
            case GENERIC -> genericRecords.values();
            case FUZZY -> FuzzySearch.findFuzzy(fuzzyRecords, filter, fuzzy).values();
            default -> throw new IllegalStateException("Unexpected value: " + state);
        };
    }

    public Set<Object2LongMap.Entry<AEKey>> findFuzzy(AEKey filter, FuzzyMode fuzzy) {
        return switch (state) {
            case EMPTY -> Collections.emptySet();
            case SINGLE ->
                key.fuzzyModeEquals(filter, fuzzy) ? Collections.singleton(singleton()) : Collections.emptySet();
            case GENERIC -> genericRecords.object2LongEntrySet();
            case FUZZY -> FuzzySearch.findFuzzy(fuzzyRecords, filter, fuzzy).object2LongEntrySet();
            default -> throw new IllegalStateException("Unexpected value: " + state);
        };
    }

    // valid IFF state == SINGLE
    private Object2LongMap.Entry<AEKey> singleton() {
        final AEKey keyCapture = key;
        return new Object2LongMap.Entry<>() {
            @Override
            public long getLongValue() {
                return get(keyCapture);
            }

            @Override
            public long setValue(long l) {
                return VariantCounter.this.set(keyCapture, l);
            }

            @Override
            public AEKey getKey() {
                return keyCapture;
            }
        };
    }

    public int size() {
        return switch (state) {
            case EMPTY -> 0;
            case SINGLE -> 1;
            case GENERIC -> genericRecords.size();
            case FUZZY -> fuzzyRecords.size();
            default -> throw new IllegalStateException("Unexpected value: " + state);
        };
    }

    public boolean isEmpty() {
        return switch (state) {
            case EMPTY -> true;
            case SINGLE -> false;
            case GENERIC -> genericRecords.isEmpty();
            case FUZZY -> fuzzyRecords.isEmpty();
            default -> throw new IllegalStateException("Unexpected value: " + state);
        };
    }

    @Override
    public @NotNull Iterator<Object2LongMap.Entry<AEKey>> iterator() {
        return switch (state) {
            case EMPTY -> Collections.emptyIterator();
            case SINGLE -> Collections.singletonList(singleton()).iterator();
            case GENERIC -> genericRecords.object2LongEntrySet().fastIterator();
            case FUZZY -> fuzzyRecords.object2LongEntrySet().iterator();
            default -> throw new IllegalStateException("Unexpected value: " + state);
        };
    }

    @Override
    public void forEach(Consumer<? super Object2LongMap.Entry<AEKey>> action) {
        switch (state) {
            case EMPTY -> {
            }
            case SINGLE -> action.accept(singleton());
            case GENERIC -> genericRecords.object2LongEntrySet().fastForEach(action);
            case FUZZY -> fuzzyRecords.object2LongEntrySet().forEach(action);
        }
    }

    public void reset() {
        switch (state) {
            case EMPTY -> {
            }
            case SINGLE -> count = 0;
            case GENERIC -> genericRecords.replaceAll((key, value) -> 0L);
            case FUZZY -> fuzzyRecords.replaceAll((key, value) -> 0L);
        }

    }

    public void clear() {
        switch (state) {
            case EMPTY -> {
            }
            case SINGLE -> clearSingle();
            case GENERIC -> genericRecords.clear();
            case FUZZY -> fuzzyRecords.clear();
        }
    }

    private void clearSingle() {
        this.key = null;
        this.count = 0;
        this.state = EMPTY;
    }

    public VariantCounter copy() {
        return new VariantCounter(state, key, count, genericRecords != null ? genericRecords.clone() : null,
                fuzzyRecords != null ? fuzzyRecords.clone() : null);
    }

    public void invert() {
        switch (state) {
            case EMPTY -> {
            }
            case SINGLE -> count = -count;
            case GENERIC -> {
                for (var it = genericRecords.object2LongEntrySet().fastIterator(); it.hasNext();) {
                    var e = it.next();
                    e.setValue(-e.getLongValue());
                }
            }
            case FUZZY -> {
                for (var e : fuzzyRecords.object2LongEntrySet()) {
                    e.setValue(-e.getLongValue());
                }
            }
        }
    }

    public void removeZeros() {
        switch (state) {
            case EMPTY -> {
            }
            case SINGLE -> {
                if (count == 0) {
                    clearSingle();
                }
            }
            case GENERIC -> mapRemoveZeros(genericRecords);
            case FUZZY -> mapRemoveZeros(fuzzyRecords);
        }
    }

    private void mapRemoveZeros(Object2LongMap<AEKey> records) {
        var it = records.values().iterator();
        while (it.hasNext()) {
            var entry = it.nextLong();
            if (entry == 0) {
                it.remove();
            }
        }
    }
}
