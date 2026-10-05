package appeng.me.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.hooks.ticking.TickHandler;

class CompositeStorageTest {

    private static final AEKeyType MANA = mock(AEKeyType.class);
    private static final AEKeyType SOURCE = mock(AEKeyType.class);

    @Test
    void dispatchesOtherKeyTypesAndListsLiveUntilTicked() {
        TickHandler.instance().tickCounter = 5;
        var mana = new Counting();
        var composite = new CompositeStorage(new AEKeyType[] { MANA, SOURCE });
        composite.setOther(0, mana);
        var key = new TestKey(MANA);

        assertEquals(5, composite.insert(key, 5, Actionable.MODULATE, IActionSource.empty()));
        assertEquals(0, composite.insert(new TestKey(SOURCE), 5, Actionable.MODULATE, IActionSource.empty()));
        assertSame(mana, composite.forKeyType(MANA));
        assertNull(composite.forKeyType(SOURCE));
        assertTrue(composite.containsAny(Set.of(key)));
        assertFalse(composite.isEmpty());

        mana.stored.add(key, 3);
        assertEquals(8, composite.getAvailableStacks().get(key));

        assertEquals(TickRateModulation.URGENT, composite.onTick());
        mana.stored.add(key, 1);
        assertEquals(8, composite.getAvailableStacks().get(key));
        assertEquals(TickRateModulation.URGENT, composite.onTick());
        assertEquals(9, composite.getAvailableStacks().get(key));
        assertEquals(TickRateModulation.SLOWER, composite.onTick());

        composite.extract(key, 2, Actionable.MODULATE, IActionSource.empty());
        assertEquals(7, composite.getAvailableStacks().get(key));
    }

    @Test
    void mapConstructorKeepsEveryKeyType() {
        var mana = new Counting();
        var source = new Counting();
        var storages = new IdentityHashMap<AEKeyType, MEStorage>();
        storages.put(MANA, mana);
        storages.put(SOURCE, source);
        var composite = new CompositeStorage(storages);
        var key = new TestKey(SOURCE);
        composite.insert(key, 4, Actionable.MODULATE, IActionSource.empty());
        assertEquals(4, source.stored.get(key));
        assertTrue(composite.supportsKeyType(MANA));
        assertTrue(composite.supportsKeyType(SOURCE));

        composite.setStorages(null, null);
        assertTrue(composite.supportsKeyType(SOURCE));
    }

    private static final class Counting implements MEStorage {
        private final KeyCounter stored = new KeyCounter();

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (mode == Actionable.MODULATE) {
                stored.add(what, amount);
            }
            return amount;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            var extracted = Math.min(amount, stored.get(what));
            if (mode == Actionable.MODULATE) {
                stored.remove(what, extracted);
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            for (var entry : stored) {
                if (entry.getLongValue() > 0) {
                    out.add(entry.getKey(), entry.getLongValue());
                }
            }
        }

        @Override
        public Component getDescription() {
            return Component.empty();
        }
    }

    private static final class TestKey extends AEKey {
        private final AEKeyType type;

        private TestKey(AEKeyType type) {
            this.type = type;
        }

        @Override
        public AEKeyType getType() {
            return type;
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
            return type;
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
