package appeng.api.stacks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

class AEKeyCollectionsTest {

    private static final int OPS = 200_000;

    private static TestKey[] keys(int count) {
        var keys = new TestKey[count];
        for (int i = 0; i < count; i++) {
            keys[i] = new TestKey();
        }
        return keys;
    }

    private static TestKey pick(Random random, TestKey[] keys) {
        int i = random.nextInt(keys.length + 1);
        return i == keys.length ? null : keys[i];
    }

    @Test
    void hashInvariant() {
        for (var key : keys(50_000)) {
            int identity = System.identityHashCode(key);
            assertEquals(identity, key.hashCode());
            assertEquals(HashCommon.mix(identity), key.mix);
        }
    }

    @Test
    void intMapMatchesFastutil() {
        var random = new Random(1);
        var keys = keys(3000);
        var map = new AEKeyIntMap<TestKey>();
        var ref = new Reference2IntOpenHashMap<TestKey>();
        map.defaultReturnValue(-7);
        ref.defaultReturnValue(-7);
        for (int i = 0; i < OPS; i++) {
            var k = pick(random, keys);
            int v = random.nextInt(1000) - 500;
            switch (random.nextInt(11)) {
                case 0, 1 -> assertEquals(ref.put(k, v), map.put(k, v));
                case 2 -> assertEquals(ref.addTo(k, v), map.addTo(k, v));
                case 3, 4 -> assertEquals(ref.removeInt(k), map.removeInt(k));
                case 5 -> assertEquals(ref.getInt(k), map.getInt(k));
                case 6 -> assertEquals(ref.containsKey(k), map.containsKey(k));
                case 7 -> assertEquals(ref.getOrDefault(k, v), map.getOrDefault(k, v));
                case 8 -> assertEquals(ref.putIfAbsent(k, v), map.putIfAbsent(k, v));
                case 9 -> assertEquals(ref.computeIfAbsent(k, x -> v), map.computeIfAbsent(k, x -> v));
                default -> mutateInherited(random, ref, map, k, v);
            }
            assertEquals(ref.size(), map.size());
            if (i % 20_000 == 19_999) {
                drain(random, ref, map, keys);
            }
        }
        assertEquals(new HashMap<>(ref), new HashMap<>(map));
    }

    private static void mutateInherited(Random random, Reference2IntOpenHashMap<TestKey> ref,
            AEKeyIntMap<TestKey> map, TestKey k, int v) {
        if (random.nextBoolean()) {
            assertEquals(ref.merge(k, v, Integer::sum), map.merge(k, v, Integer::sum));
        } else {
            var refIt = ref.reference2IntEntrySet().iterator();
            var mapIt = map.reference2IntEntrySet().iterator();
            var removed = new HashSet<TestKey>();
            while (refIt.hasNext()) {
                var e = refIt.next();
                if ((e.getIntValue() & 3) == 0) {
                    removed.add(e.getKey());
                    refIt.remove();
                }
            }
            while (mapIt.hasNext()) {
                if (removed.contains(mapIt.next().getKey())) {
                    mapIt.remove();
                }
            }
        }
    }

    private static void drain(Random random, Reference2IntOpenHashMap<TestKey> ref, AEKeyIntMap<TestKey> map,
            TestKey[] keys) {
        for (var k : keys) {
            if (random.nextInt(10) != 0) {
                assertEquals(ref.removeInt(k), map.removeInt(k));
            }
        }
        assertEquals(new HashMap<>(ref), new HashMap<>(map));
    }

    @Test
    void longMapMatchesFastutil() {
        var random = new Random(2);
        var keys = keys(3000);
        var map = new AEKeyLongMap<TestKey>();
        var ref = new Reference2LongOpenHashMap<TestKey>();
        for (int i = 0; i < OPS; i++) {
            var k = pick(random, keys);
            long v = random.nextInt(1_000_000) + 1;
            switch (random.nextInt(12)) {
                case 0, 1 -> assertEquals(k == null ? 0 : ref.put(k, v), map.put(k, v));
                case 2 -> assertEquals(k == null ? 0 : ref.addTo(k, v), map.addTo(k, v));
                case 3, 4 -> assertEquals(k == null ? 0 : ref.removeLong(k), map.removeLong(k));
                case 5 -> assertEquals(k == null ? 0 : ref.getLong(k), map.getLong(k));
                case 6 -> assertEquals(k != null && ref.containsKey(k), map.containsKey(k));
                case 7 -> assertEquals(k == null ? -1 : ref.getOrDefault(k, -1), map.getOrDefault(k, -1));
                case 8 -> assertEquals(k == null ? 0 : ref.addTo(k, -v), map.removeTo(k, v));
                case 9 -> {
                    if (k != null) {
                        ref.addTo(k, v);
                    }
                    map.insert(k, v);
                }
                case 10 -> {
                    long expected = 0;
                    if (k != null && ref.containsKey(k)) {
                        long old = ref.getLong(k);
                        if (old > v) {
                            ref.put(k, old - v);
                            expected = v;
                        } else {
                            expected = ref.removeLong(k);
                        }
                    }
                    assertEquals(expected, map.extract(k, v));
                }
                default -> {
                    if (k != null) {
                        ref.put(k, v);
                    }
                    map.set(k, v);
                }
            }
            assertEquals(ref.size(), map.size());
            assertEquals(k != null && ref.containsKey(k), map.contains(k));
            assertEquals(k == null ? 0 : ref.getLong(k), map.getAmount(k));
            if (i % 20_000 == 19_999) {
                for (var key : keys) {
                    if (random.nextInt(10) != 0) {
                        assertEquals(ref.removeLong(key), map.removeLong(key));
                    }
                }
            }
        }
        assertEquals(new HashMap<>(ref), new HashMap<>(map));
        var seen = new HashMap<TestKey, Long>();
        map.fastForEach(seen::put);
        assertEquals(new HashMap<>(ref), seen);
    }

    @Test
    void longMapSaturates() {
        var k = new TestKey();
        var map = new AEKeyLongMap<TestKey>();
        map.set(k, Long.MAX_VALUE - 1);
        map.addTo(k, 10);
        assertEquals(Long.MAX_VALUE, map.getLong(k));
        map.insert(k, 10);
        assertEquals(Long.MAX_VALUE, map.getLong(k));
        map.set(k, -Long.MAX_VALUE + 1);
        map.removeTo(k, 10);
        assertEquals(-Long.MAX_VALUE, map.getLong(k));
        assertEquals(0, map.put(null, 5));
        assertFalse(map.containsKey(null));
    }

    @Test
    void objectMapMatchesFastutil() {
        var random = new Random(3);
        var keys = keys(3000);
        var map = new AEKeyObjectMap<TestKey, Integer>();
        var ref = new Reference2ObjectOpenHashMap<TestKey, Integer>();
        for (int i = 0; i < OPS; i++) {
            var k = pick(random, keys);
            Integer v = random.nextInt(1000);
            switch (random.nextInt(9)) {
                case 0, 1 -> assertEquals(ref.put(k, v), map.put(k, v));
                case 2, 3 -> assertEquals(ref.remove(k), map.remove(k));
                case 4 -> assertEquals(ref.get(k), map.get(k));
                case 5 -> assertEquals(ref.containsKey(k), map.containsKey(k));
                case 6 -> assertEquals(ref.getOrDefault(k, -1), map.getOrDefault(k, -1));
                case 7 -> assertEquals(ref.putIfAbsent(k, v), map.putIfAbsent(k, v));
                default -> assertEquals(ref.computeIfAbsent(k, x -> v), map.computeIfAbsent(k, x -> v));
            }
            assertEquals(ref.size(), map.size());
            if (i % 20_000 == 19_999) {
                for (var key : keys) {
                    if (random.nextInt(10) != 0) {
                        assertEquals(ref.remove(key), map.remove(key));
                    }
                }
            }
        }
        assertEquals(new HashMap<>(ref), new HashMap<>(map));
    }

    @Test
    void setMatchesFastutil() {
        var random = new Random(4);
        var keys = keys(3000);
        var set = new AEKeySet<TestKey>();
        var ref = new ReferenceOpenHashSet<TestKey>();
        for (int i = 0; i < OPS; i++) {
            var k = pick(random, keys);
            switch (random.nextInt(4)) {
                case 0, 1 -> assertEquals(ref.add(k), set.add(k));
                case 2 -> assertEquals(ref.remove(k), set.remove(k));
                default -> assertEquals(ref.contains(k), set.contains(k));
            }
            assertEquals(ref.size(), set.size());
            if (i % 20_000 == 19_999) {
                var it = set.iterator();
                while (it.hasNext()) {
                    var key = it.next();
                    if (random.nextInt(10) != 0) {
                        it.remove();
                        assertTrue(ref.remove(key));
                    }
                }
            }
        }
        assertEquals(new HashSet<>(ref), new HashSet<>(set));
        assertEquals(new HashSet<>(ref), new HashSet<>(new AEKeySet<>(ref)));
    }

    @Test
    void bigMapMatchesModel() {
        var random = new Random(5);
        var keys = keys(3000);
        var map = new AEKeyBigMap<TestKey>();
        var ref = new HashMap<TestKey, BigInteger>();
        for (int i = 0; i < OPS; i++) {
            var k = keys[random.nextInt(keys.length)];
            var v = BigInteger.valueOf(random.nextInt(1_000_000) + 1);
            switch (random.nextInt(6)) {
                case 0 -> {
                    ref.put(k, v);
                    map.set(k, v);
                }
                case 1 -> {
                    ref.merge(k, v, BigInteger::add);
                    map.addTo(k, v);
                }
                case 2 -> {
                    var old = ref.remove(k);
                    assertEquals(old == null ? BigInteger.ZERO : old, map.remove(k));
                }
                case 3 -> {
                    var old = ref.get(k);
                    var expected = BigInteger.ZERO;
                    if (old != null) {
                        if (old.compareTo(v) > 0) {
                            ref.put(k, old.subtract(v));
                            expected = v;
                        } else {
                            expected = ref.remove(k);
                        }
                    }
                    assertEquals(expected, map.extract(k, v));
                }
                case 4 -> assertEquals(ref.containsKey(k), map.contains(k));
                default -> assertEquals(ref.getOrDefault(k, BigInteger.ZERO), map.getAmount(k));
            }
            assertEquals(ref.size(), map.size());
        }
        assertEquals(ref, new HashMap<>(map));
    }

    @Test
    void keyCounterPresizeKeepsContent() {
        var random = new Random(6);
        var keys = keys(5000);
        for (int capacity : new int[] { -1, 0, 1, 16, 17, 100, 5000 }) {
            var counter = new KeyCounter();
            counter.ensureCapacity(capacity);
            assertTrue(counter.isEmpty());
            var ref = new HashMap<TestKey, Long>();
            for (int i = 0; i < 20_000; i++) {
                var k = keys[random.nextInt(keys.length)];
                long v = random.nextInt(1000) + 1;
                ref.merge(k, v, Long::sum);
                counter.add(k, v);
                if (i % 997 == 0) {
                    counter.ensureCapacity(random.nextInt(3000));
                }
            }
            assertEquals(ref.size(), counter.size());
            for (var entry : ref.entrySet()) {
                assertEquals((long) entry.getValue(), counter.get(entry.getKey()));
            }
            var copy = new KeyCounter();
            copy.addAll(counter.getMap());
            var bigCopy = new KeyCounter();
            bigCopy.addAll(new AEKeyBigMap<>(counter.getMap()));
            var consumerCopy = new KeyCounter();
            consumerCopy.addAll(counter.size(), map -> counter.getMap().fastForEach(map::insert));
            for (var other : List.of(copy, bigCopy, consumerCopy)) {
                assertEquals(ref.size(), other.size());
                for (var entry : ref.entrySet()) {
                    assertEquals((long) entry.getValue(), other.get(entry.getKey()));
                }
            }
        }
    }

    private static final class TestKey extends AEKey {
        @Override
        public AEKeyType getType() {
            return null;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public CompoundTag toTag() {
            return null;
        }

        @Override
        public Object getPrimaryKey() {
            return this;
        }

        @Override
        public ResourceLocation getId() {
            return null;
        }

        @Override
        public void writeToPacket(FriendlyByteBuf data) {
        }

        @Override
        protected Component computeDisplayName() {
            return null;
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        }
    }

}
