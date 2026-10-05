package appeng.api.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.StorageTargetResolver.Tier;

class StorageTargetResolverTest {

    @BeforeAll
    static void capabilities() {
        try (var manager = mockStatic(CapabilityManager.class)) {
            manager.when(() -> CapabilityManager.get(any())).thenAnswer(invocation -> mock(Capability.class));
            assertNotNull(ForgeCapabilities.ITEM_HANDLER);
        }
    }

    private static BlockEntity host(MEStorage storage) {
        return host(storage, -1);
    }

    private static BlockEntity host(MEStorage storage, int epoch) {
        var be = mock(BlockEntity.class, withSettings().extraInterfaces(MEStorageHost.class));
        when(((MEStorageHost) be).getMEStorage(Direction.UP)).thenReturn(storage);
        when(((MEStorageHost) be).storageEpoch()).thenReturn(epoch);
        return be;
    }

    @Test
    void removedOrMissingBlockEntityResolvesToNone() {
        var storage = mock(MEStorage.class);
        var be = host(storage);
        var resolver = new StorageTargetResolver();
        assertEquals(Tier.STORAGE, resolver.resolveAll(be, Direction.UP, StorageAccess.FULL));
        assertSame(storage, resolver.raw());
        when(be.isRemoved()).thenReturn(true);
        assertEquals(Tier.NONE, resolver.resolveAll(be, Direction.UP, StorageAccess.FULL));
        assertEquals(Tier.NONE, resolver.tier());
        assertNull(resolver.raw());
        assertEquals(Tier.NONE, resolver.resolveAll(null, Direction.UP, StorageAccess.FULL));
        verify((MEStorageHost) be, times(1)).getMEStorage(Direction.UP);
    }

    @Test
    void hostIsAskedDirectlyWithoutCapabilities() {
        var storage = mock(MEStorage.class);
        var be = host(storage);
        var resolver = new StorageTargetResolver();
        for (int i = 0; i < 3; i++) {
            assertEquals(Tier.STORAGE, resolver.resolveAll(be, Direction.UP, StorageAccess.FULL));
            assertSame(storage, resolver.raw());
        }
        when(((MEStorageHost) be).getMEStorage(Direction.UP)).thenReturn(null);
        assertEquals(Tier.NONE, resolver.resolveAll(be, Direction.UP, StorageAccess.FULL));
        assertNull(resolver.raw());
        verify(be, never()).getCapability(any(), any());
    }

    @Test
    void extractSkipsForeignStorage() {
        var storage = mock(MEStorage.class);
        var typed = mock(KeyTypedStorage.class);
        var be = host(storage);
        var resolver = new StorageTargetResolver();
        assertEquals(Tier.NONE, resolver.resolveAll(be, Direction.UP, StorageAccess.EXTRACT));
        when(((MEStorageHost) be).getMEStorage(Direction.UP)).thenReturn(typed);
        assertEquals(Tier.STORAGE, resolver.resolveAll(be, Direction.UP, StorageAccess.EXTRACT));
        assertSame(typed, resolver.raw());
    }

    @Test
    void cachedForgeHandlerIsTrustedFromSecondSightingUntilInvalidated() {
        var be = mock(BlockEntity.class);
        var handler = mock(IItemHandler.class);
        var optional = LazyOptional.of(() -> handler);
        doReturn(optional).when(be).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP);
        var resolver = new StorageTargetResolver();
        for (int i = 0; i < 10; i++) {
            assertEquals(Tier.FORGE, resolver.resolveHandler(be, Direction.UP, AEKeyTypes.ITEMS));
            assertSame(handler, resolver.raw());
        }
        verify(be, times(2)).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP);

        var replacementHandler = mock(IItemHandler.class);
        var replacement = LazyOptional.of(() -> replacementHandler);
        doReturn(replacement).when(be).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP);
        optional.invalidate();
        assertEquals(Tier.FORGE, resolver.resolveHandler(be, Direction.UP, AEKeyTypes.ITEMS));
        assertSame(replacementHandler, resolver.raw());
        verify(be, times(3)).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP);
    }

    @Test
    void freshOptionalPerCallIsNeverTrusted() {
        var be = mock(BlockEntity.class);
        var handler = mock(IItemHandler.class);
        when(be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP))
                .thenAnswer(invocation -> LazyOptional.of(() -> handler));
        var resolver = new StorageTargetResolver();
        for (int i = 0; i < 5; i++) {
            assertEquals(Tier.FORGE, resolver.resolveHandler(be, Direction.UP, AEKeyTypes.ITEMS));
            assertSame(handler, resolver.raw());
        }
        verify(be, times(5)).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP);
    }

    @Test
    void missingHandlerIsRecheckedPeriodically() {
        var be = mock(BlockEntity.class);
        doReturn(LazyOptional.empty()).when(be).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP);
        var resolver = new StorageTargetResolver();
        for (int i = 0; i < 64; i++) {
            assertEquals(Tier.NONE, resolver.resolveHandler(be, Direction.UP, AEKeyTypes.FLUIDS));
        }
        verify(be, times(1)).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP);
        assertEquals(Tier.NONE, resolver.resolveHandler(be, Direction.UP, AEKeyTypes.FLUIDS));
        verify(be, times(2)).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP);
    }

    @Test
    void switchingKeyTypeRequeries() {
        var be = mock(BlockEntity.class);
        var handler = mock(IItemHandler.class);
        var optional = LazyOptional.of(() -> handler);
        doReturn(optional).when(be).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP);
        doReturn(LazyOptional.empty()).when(be).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP);
        var resolver = new StorageTargetResolver();
        assertEquals(Tier.FORGE, resolver.resolveHandler(be, Direction.UP, AEKeyTypes.ITEMS));
        assertEquals(Tier.NONE, resolver.resolveHandler(be, Direction.UP, AEKeyTypes.FLUIDS));
        assertEquals(Tier.FORGE, resolver.resolveHandler(be, Direction.UP, AEKeyTypes.ITEMS));
        verify(be, times(2)).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP);
    }

    @Test
    void trackedHostIsReusedUntilEpochChanges() {
        var storage = mock(MEStorage.class);
        var be = host(storage, 3);
        var host = (MEStorageHost) be;
        var resolver = new StorageTargetResolver();
        for (int i = 0; i < 5; i++) {
            assertEquals(Tier.STORAGE, resolver.resolve(be, Direction.UP, AEKeyTypes.ITEMS, StorageAccess.INSERT));
            assertSame(storage, resolver.raw());
        }
        verify(host, times(1)).getMEStorage(Direction.UP);

        var replacement = mock(MEStorage.class);
        when(host.getMEStorage(Direction.UP)).thenReturn(replacement);
        assertEquals(Tier.STORAGE, resolver.resolve(be, Direction.UP, AEKeyTypes.ITEMS, StorageAccess.INSERT));
        assertSame(storage, resolver.raw());
        when(host.storageEpoch()).thenReturn(4);
        assertEquals(Tier.STORAGE, resolver.resolve(be, Direction.UP, AEKeyTypes.ITEMS, StorageAccess.INSERT));
        assertSame(replacement, resolver.raw());
        assertEquals(Tier.STORAGE, resolver.resolve(be, Direction.UP, AEKeyTypes.ITEMS, StorageAccess.INSERT));
        verify(host, times(2)).getMEStorage(Direction.UP);

        when(host.getMEStorage(Direction.UP)).thenReturn(null);
        when(host.storageEpoch()).thenReturn(5);
        assertEquals(Tier.NONE, resolver.resolveAll(be, Direction.UP, StorageAccess.INSERT));
        assertNull(resolver.raw());
        assertEquals(Tier.NONE, resolver.resolveAll(be, Direction.UP, StorageAccess.INSERT));
        verify(host, times(3)).getMEStorage(Direction.UP);
    }

    @Test
    void epochReuseIsKeyedBySideTypeAndAccess() {
        var typed = mock(KeyTypedStorage.class);
        var items = mock(MEStorage.class);
        var fluids = mock(MEStorage.class);
        when(typed.forKeyType(AEKeyTypes.ITEMS)).thenReturn(items);
        when(typed.forKeyType(AEKeyTypes.FLUIDS)).thenReturn(fluids);
        var be = host(typed, 0);
        var host = (MEStorageHost) be;
        when(host.getMEStorage(Direction.DOWN)).thenReturn(typed);
        var resolver = new StorageTargetResolver();
        assertEquals(Tier.STORAGE, resolver.resolve(be, Direction.UP, AEKeyTypes.ITEMS, StorageAccess.INSERT));
        assertSame(items, resolver.raw());
        assertEquals(Tier.STORAGE, resolver.resolve(be, Direction.UP, AEKeyTypes.ITEMS, StorageAccess.INSERT));
        assertSame(items, resolver.raw());
        assertEquals(Tier.STORAGE, resolver.resolve(be, Direction.UP, AEKeyTypes.FLUIDS, StorageAccess.INSERT));
        assertSame(fluids, resolver.raw());
        assertEquals(Tier.STORAGE, resolver.resolve(be, Direction.DOWN, AEKeyTypes.FLUIDS, StorageAccess.INSERT));
        assertEquals(Tier.STORAGE, resolver.resolve(be, Direction.DOWN, AEKeyTypes.FLUIDS, StorageAccess.EXTRACT));
        assertSame(fluids, resolver.raw());
        assertEquals(Tier.STORAGE, resolver.resolveAll(be, Direction.DOWN, StorageAccess.EXTRACT));
        assertSame(typed, resolver.raw());
        assertEquals(Tier.STORAGE, resolver.resolveAll(be, Direction.DOWN, StorageAccess.EXTRACT));
        assertSame(typed, resolver.raw());
        verify(host, times(2)).getMEStorage(Direction.UP);
        verify(host, times(3)).getMEStorage(Direction.DOWN);
    }

    @Test
    void untrackedHostIsAskedEveryTime() {
        var storage = mock(MEStorage.class);
        var be = host(storage);
        var resolver = new StorageTargetResolver();
        for (int i = 0; i < 4; i++) {
            assertEquals(Tier.STORAGE, resolver.resolve(be, Direction.UP, AEKeyTypes.ITEMS, StorageAccess.INSERT));
            assertSame(storage, resolver.raw());
        }
        verify((MEStorageHost) be, times(4)).getMEStorage(Direction.UP);
    }

    @Test
    void trackedHostWithoutStorageGoesStraightToHandler() {
        var be = host(null, 7);
        var host = (MEStorageHost) be;
        var handler = mock(IItemHandler.class);
        var optional = LazyOptional.of(() -> handler);
        doReturn(optional).when(be).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP);
        var resolver = new StorageTargetResolver();
        for (int i = 0; i < 5; i++) {
            assertEquals(Tier.FORGE, resolver.resolve(be, Direction.UP, AEKeyTypes.ITEMS, StorageAccess.INSERT));
            assertSame(handler, resolver.raw());
        }
        verify(host, times(1)).getMEStorage(Direction.UP);
        verify(be, times(2)).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP);

        var storage = mock(MEStorage.class);
        when(host.getMEStorage(Direction.UP)).thenReturn(storage);
        when(host.storageEpoch()).thenReturn(8);
        assertEquals(Tier.STORAGE, resolver.resolve(be, Direction.UP, AEKeyTypes.ITEMS, StorageAccess.INSERT));
        assertSame(storage, resolver.raw());
    }

    @Test
    void typedHostWithoutTheTypeStaysNoneWithoutHandlerFallback() {
        var typed = mock(KeyTypedStorage.class);
        var be = host(typed, 2);
        var resolver = new StorageTargetResolver();
        for (int i = 0; i < 3; i++) {
            assertEquals(Tier.NONE, resolver.resolve(be, Direction.UP, AEKeyTypes.FLUIDS, StorageAccess.INSERT));
            assertNull(resolver.raw());
        }
        verify((MEStorageHost) be, times(1)).getMEStorage(Direction.UP);
        verify(typed, times(1)).forKeyType(AEKeyTypes.FLUIDS);
        verify(be, never()).getCapability(any(), any());
    }

    @Test
    void forgetAndRemovalDropEpochMemory() {
        var storage = mock(MEStorage.class);
        var be = host(storage, 1);
        var host = (MEStorageHost) be;
        var resolver = new StorageTargetResolver();
        assertEquals(Tier.STORAGE, resolver.resolveAll(be, Direction.UP, StorageAccess.FULL));
        assertEquals(Tier.STORAGE, resolver.resolveAll(be, Direction.UP, StorageAccess.FULL));
        verify(host, times(1)).getMEStorage(Direction.UP);
        resolver.forget();
        assertEquals(Tier.STORAGE, resolver.resolveAll(be, Direction.UP, StorageAccess.FULL));
        verify(host, times(2)).getMEStorage(Direction.UP);

        when(be.isRemoved()).thenReturn(true);
        assertEquals(Tier.NONE, resolver.resolveAll(be, Direction.UP, StorageAccess.FULL));
        assertNull(resolver.raw());
        when(be.isRemoved()).thenReturn(false);
        assertEquals(Tier.STORAGE, resolver.resolveAll(be, Direction.UP, StorageAccess.FULL));
        assertSame(storage, resolver.raw());
        verify(host, times(3)).getMEStorage(Direction.UP);
    }

    @Test
    void epochReuseSurvivesInterleavedResolution() {
        var storage = mock(MEStorage.class);
        var be = host(storage, 9);
        var other = mock(BlockEntity.class);
        var handler = mock(IItemHandler.class);
        doReturn(LazyOptional.of(() -> handler)).when(other).getCapability(ForgeCapabilities.ITEM_HANDLER,
                Direction.UP);
        var resolver = new StorageTargetResolver();
        assertEquals(Tier.STORAGE, resolver.resolve(be, Direction.UP, AEKeyTypes.ITEMS, StorageAccess.INSERT));
        assertEquals(Tier.FORGE, resolver.resolveHandler(other, Direction.UP, AEKeyTypes.ITEMS));
        assertSame(handler, resolver.raw());
        assertEquals(Tier.STORAGE, resolver.resolve(be, Direction.UP, AEKeyTypes.ITEMS, StorageAccess.INSERT));
        assertSame(storage, resolver.raw());
        verify((MEStorageHost) be, times(1)).getMEStorage(Direction.UP);
    }
}
