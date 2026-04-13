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

import com.google.common.collect.Iterators;

import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.objects.*;

import appeng.api.config.FuzzyMode;

/**
 * Associates a generic value of type T with AE keys and makes key/value pairs searchable with fuzzy mode semantics.
 */
public final class KeyCounter implements Iterable<Reference2LongMap.Entry<AEKey>> {
    // First map contains a mapping from AEKey#primaryKey
    private Reference2ObjectMap<Object, VariantCounter> lists = new Reference2ObjectOpenHashMap<>();

    public Collection<Object2LongMap.Entry<AEKey>> findFuzzy(AEKey key, FuzzyMode fuzzy) {
        var subIndex = getSubIndexOrNull(key);
        return subIndex == null ? Collections.emptyList() : subIndex.findFuzzy(key, fuzzy);
    }

    public void removeZeros() {
        if (lists == null)
            return;
        var iterator = lists.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var variantList = entry.getValue();
            variantList.removeZeros();
            if (variantList.isEmpty()) {
                iterator.remove();
            }
        }
    }

    public void removeEmptySubmaps() {
        if (lists == null)
            return;
        lists.values().removeIf(VariantCounter::isEmpty);
    }

    public void addAll(KeyCounter other) {
        if (lists == null)
            lists = new Reference2ObjectOpenHashMap<>();
        for (var entry : other.lists.entrySet()) {
            var ourSubIndex = lists.get(entry.getKey());
            if (ourSubIndex == null) {
                lists.put(entry.getKey(), entry.getValue().copy());
            } else {
                ourSubIndex.addAll(entry.getValue());
            }
        }
    }

    public void removeAll(KeyCounter other) {
        if (lists == null)
            lists = new Reference2ObjectOpenHashMap<>();
        for (var entry : other.lists.entrySet()) {
            var ourSubIndex = lists.get(entry.getKey());
            if (ourSubIndex == null) {
                var copied = entry.getValue().copy();
                copied.invert();
                lists.put(entry.getKey(), copied);
            } else {
                ourSubIndex.removeAll(entry.getValue());
            }
        }
    }

    public void add(AEKey key, long amount) {
        Objects.requireNonNull(key, "key");
        if (lists == null)
            lists = new Reference2ObjectOpenHashMap<>();
        getSubIndex(key).add(key, amount);
    }

    /**
     * Subtracts the given amount from the value associated with the given key.
     */
    public void remove(AEKey key, long amount) {
        add(key, -amount);
    }

    /**
     * Removes the given key from this counter, and returns the old value (or 0).
     */
    public long remove(AEKey key) {
        if (lists == null)
            return 0;
        var subIndex = getSubIndex(key);
        var ret = subIndex.remove(key);
        if (subIndex.isEmpty()) {
            lists.remove(key.getPrimaryKey());
        }
        return ret;
    }

    public void set(AEKey key, long amount) {
        if (lists == null)
            lists = new Reference2ObjectOpenHashMap<>();
        getSubIndex(key).set(key, amount);
    }

    public long get(AEKey key) {
        if (lists == null)
            return 0;
        Objects.requireNonNull(key);
        var subIndex = lists.get(key.getPrimaryKey());
        if (subIndex == null) {
            return 0;
        }
        return subIndex.get(key);
    }

    public void reset() {
        if (lists == null)
            return;
        for (var list : lists.values()) {
            list.reset();
        }
    }

    public void clear() {
        if (lists == null)
            return;
        for (var list : lists.values()) {
            list.clear();
        }
    }

    public boolean isEmpty() {
        if (lists == null)
            return true;
        for (var list : lists.values()) {
            if (!list.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public int size() {
        if (lists == null)
            return 0;
        int tot = 0;
        for (var list : lists.values()) {
            tot += list.size();
        }
        return tot;
    }

    @Override
    public Iterator<Reference2LongMap.Entry<AEKey>> iterator() {
        if (lists == null)
            return Collections.emptyIterator();
        return Iterators.transform(Iterators.concat(
                Iterators.transform(lists.values().iterator(), VariantCounter::iterator)),
                i -> new Reference2LongMap.Entry<>() {

                    @Override
                    public AEKey getKey() {
                        return i.getKey();
                    }

                    @Override
                    public long getLongValue() {
                        return i.getLongValue();
                    }

                    @Override
                    public long setValue(long value) {
                        return i.setValue(value);
                    }
                });
    }

    private VariantCounter getSubIndex(AEKey key) {
        // We check before the call to computeIfAbsent, otherwise we'd need a capturing lambda.
        if (key.getFuzzySearchMaxValue() > 0) {
            return lists.computeIfAbsent(key.getPrimaryKey(), k -> new VariantCounter.FuzzyVariantMap());
        } else {
            return lists.computeIfAbsent(key.getPrimaryKey(), k -> new VariantCounter.UnorderedVariantMap());
        }
    }

    @Nullable
    private VariantCounter getSubIndexOrNull(AEKey key) {
        return lists.get(key.getPrimaryKey());
    }

    @Nullable
    public AEKey getFirstKey() {
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
        if (lists == null)
            return null;
        for (var value : lists.values()) {
            var it = value.iterator();
            if (it.hasNext()) {
                return it.next();
            }
        }
        return null;
    }

    @Nullable
    public <T extends AEKey> Object2LongMap.Entry<AEKey> getFirstEntry(Class<T> keyClass) {
        if (lists == null)
            return null;
        for (var value : lists.values()) {
            var it = value.iterator();
            if (it.hasNext()) {
                var entry = it.next();
                if (keyClass.isInstance(entry.getKey())) {
                    return entry;
                }
            }
        }
        return null;
    }

    public Set<AEKey> keySet() {
        if (lists == null)
            return Collections.emptySet();
        var keys = new ReferenceOpenHashSet<AEKey>(size());
        for (var list : lists.values()) {
            for (var entry : list) {
                keys.add(entry.getKey());
            }
        }
        return keys;
    }

    public Set<GenericStack> entrySet() {
        if (lists == null)
            return Collections.emptySet();
        var keys = new HashSet<GenericStack>(size());
        for (var list : lists.values()) {
            for (var entry : list) {
                keys.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
        }
        return keys;
    }
}
