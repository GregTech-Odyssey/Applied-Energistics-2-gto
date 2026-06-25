/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 TeamAppliedEnergistics
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.stacks;

import java.util.*;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;

import appeng.api.config.FuzzyMode;
import appeng.hooks.IUnique;

/**
 * Associates a generic value of type T with AE keys and makes key/value pairs searchable with fuzzy mode semantics.
 */
public final class KeyCounter implements Iterable<Reference2LongMap.Entry<AEKey>> {

    private AEKeyMap<AEKey> map;

    private Int2ObjectOpenHashMap<Object2LongOpenHashMap<AEKey>> fuzzyMap;

    private boolean fuzzyUpdate;

    public AEKeyMap<AEKey> getMap() {
        if (map == null) {
            return AEKeyMap.EMPTY;
        }
        return map;
    }

    @Override
    public void forEach(Consumer<? super Reference2LongMap.Entry<AEKey>> consumer) {
        if (map == null) {
            return;
        }
        map.reference2LongEntrySet().fastForEach(consumer);
    }

    public Collection<Object2LongMap.Entry<AEKey>> findFuzzy(AEKey key, FuzzyMode fuzzy) {
        if (map == null) {
            return Collections.emptyList();
        }
        if (key.getPrimaryKey() instanceof IUnique unique) {
            if (fuzzyUpdate || fuzzyMap == null) {
                fuzzyUpdate = false;
                if (fuzzyMap == null) {
                    fuzzyMap = new Int2ObjectOpenHashMap<>();
                } else {
                    fuzzyMap.values().forEach(Object2LongOpenHashMap::clear);
                }
                map.fastForEach((k, v) -> {
                    if (k.getPrimaryKey() instanceof IUnique u) {
                        fuzzyMap.computeIfAbsent(u.ae2$getUid(), _k -> new Object2LongOpenHashMap<>()).addTo(k, v);
                    }
                });
            }
            var map = fuzzyMap.get(unique.ae2$getUid());
            if (map != null) {
                return map.object2LongEntrySet();
            }
        }
        long value = map.getOrDefault(key, Long.MIN_VALUE);
        if (value > Long.MIN_VALUE) {
            return Collections.singleton(new Entry(value, key));
        }
        return Collections.emptyList();
    }

    public void removeZeros() {
        if (map == null) {
            return;
        }
        var it = map.iterator();
        while (it.hasNext()) {
            if (it.next().getLongValue() == 0) {
                it.remove();
            }
        }
        fuzzyUpdate = true;
    }

    public void removeEmptySubmaps() {
    }

    public void addAll(KeyCounter other) {
        var m = other.getMap();
        if (m.isEmpty()) {
            return;
        }
        if (map == null) {
            map = m.clone();
        } else {
            map.addAll(m);
        }
        fuzzyUpdate = true;
    }

    public void removeAll(KeyCounter other) {
        var m = other.getMap();
        if (m.isEmpty()) {
            return;
        }
        var size = m.size();
        var map = this.map;
        if (map == null) {
            this.map = map = new AEKeyMap<>(size);
        }
        map.removeAll(m);
        fuzzyUpdate = true;
    }

    public void add(AEKey key, long amount) {
        var map = this.map;
        if (map == null) {
            this.map = map = new AEKeyMap<>();
        }
        map.addTo(key, amount);
        fuzzyUpdate = true;
    }

    public void remove(AEKey key, long amount) {
        var map = this.map;
        if (map == null) {
            this.map = map = new AEKeyMap<>();
        }
        map.addTo(key, -amount);
        fuzzyUpdate = true;
    }

    public long remove(AEKey key) {
        var map = this.map;
        if (map == null) {
            return 0;
        }
        fuzzyUpdate = true;
        return map.removeLong(key);
    }

    public void set(AEKey key, long amount) {
        var map = this.map;
        if (map == null) {
            this.map = map = new AEKeyMap<>();
        }
        map.set(key, amount);
        fuzzyUpdate = true;
    }

    public long get(AEKey key) {
        var map = this.map;
        if (map == null) {
            return 0;
        }
        return map.getAmount(key);
    }

    public void reset() {
        var map = this.map;
        if (map == null) {
            return;
        }
        map.reset();
        fuzzyUpdate = true;
    }

    public void clear() {
        var map = this.map;
        if (map == null) {
            return;
        }
        map.clear();
        fuzzyUpdate = true;
    }

    public boolean isEmpty() {
        var map = this.map;
        if (map == null) {
            return true;
        }
        return map.isEmpty();
    }

    public int size() {
        var map = this.map;
        if (map == null) {
            return 0;
        }
        return map.size();
    }

    @Override
    public @NotNull Iterator<Reference2LongMap.Entry<AEKey>> iterator() {
        var map = this.map;
        if (map == null) {
            return Collections.emptyIterator();
        }
        return map.iterator();
    }

    public @Nullable AEKey getFirstKey() {
        var e = getFirstEntry();
        return e != null ? e.getKey() : null;
    }

    @Nullable
    public <T extends AEKey> T getFirstKey(Class<T> keyClass) {
        var e = getFirstEntry(keyClass);
        return e != null ? keyClass.cast(e.getKey()) : null;
    }

    @Nullable
    public Object2LongMap.Entry<AEKey> getFirstEntry() {
        var map = this.map;
        if (map == null) {
            return null;
        }
        for (var e : map) {
            return new Entry(e.getLongValue(), e.getKey());
        }
        return null;
    }

    @Nullable
    public <T extends AEKey> Object2LongMap.Entry<AEKey> getFirstEntry(Class<T> keyClass) {
        var map = this.map;
        if (map == null) {
            return null;
        }
        for (var e : map) {
            if (keyClass.isInstance(e.getKey())) {
                return new Entry(e.getLongValue(), e.getKey());
            }
        }
        return null;
    }

    public Set<AEKey> keySet() {
        var map = this.map;
        if (map == null) {
            return Collections.emptySet();
        }
        return map.keySet();
    }

    public Set<GenericStack> genericStackSet() {
        var map = this.map;
        if (map == null) {
            return Collections.emptySet();
        }
        var keys = new ReferenceOpenHashSet<GenericStack>(map.size());
        map.fastForEach((k, v) -> keys.add(new GenericStack(k, v)));
        return keys;
    }

    public boolean contains(AEKey key) {
        var map = this.map;
        if (map == null) {
            return false;
        }
        return map.containsKey(key);
    }

    public void ensureCapacity(int capacity) {
        var map = this.map;
        if (map == null) {
            this.map = map = new AEKeyMap<>();
        }
        map.ensureCapacity(capacity);
    }

    public void addAll(int size, Consumer<AEKeyMap<AEKey>> consumer) {
        if (size < 1) {
            return;
        }
        var map = this.map;
        if (map == null) {
            this.map = map = new AEKeyMap<>();
        }
        map.ensureCapacity(size);
        consumer.accept(map);
        fuzzyUpdate = true;
    }

    private static final class Entry implements Object2LongMap.Entry<AEKey> {

        private final long value;
        private final AEKey key;

        private Entry(long value, AEKey key) {
            this.value = value;
            this.key = key;
        }

        @Override
        public long getLongValue() {
            return value;
        }

        @Override
        public long setValue(long value) {
            return 0;
        }

        @Override
        public AEKey getKey() {
            return key;
        }
    }
}
