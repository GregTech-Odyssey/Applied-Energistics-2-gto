package appeng.client.gui.me.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;

import appeng.api.stacks.AEKey;
import appeng.core.sync.BasePacket;
import appeng.core.sync.packets.CraftingJobStatusPacket;

class CraftingJobStatusPacketTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void transportsExactQuantitiesElapsedTimeAndFollowToTheClient() throws ReflectiveOperationException {
        var jobId = UUID.randomUUID();
        var key = mock(AEKey.class);
        var status = CraftingJobStatusPacket.Status.FINISHED;
        long elapsedNanos = 176_523_000_000_000L;

        // Isolate the registry-dependent key codec; exercise the packet's actual buffer and client routing.
        try (var keys = mockStatic(AEKey.class); var pending = mockStatic(PendingCraftingJobs.class)) {
            keys.when(() -> AEKey.writeKey(any(), eq(key))).thenAnswer(invocation -> {
                ((FriendlyByteBuf) invocation.getArgument(0)).writeInt(42);
                return null;
            });
            keys.when(() -> AEKey.readKey(any())).thenAnswer(invocation -> {
                assertEquals(42, ((FriendlyByteBuf) invocation.getArgument(0)).readInt());
                return key;
            });
            var outgoing = new CraftingJobStatusPacket(jobId, key, Long.MAX_VALUE, 0, status, elapsedNanos, true);
            var field = BasePacket.class.getDeclaredField("p");
            field.setAccessible(true);
            var buffer = (FriendlyByteBuf) field.get(outgoing);
            try {
                assertEquals(outgoing.getPacketID(), buffer.readInt());
                var incoming = new CraftingJobStatusPacket(buffer);
                assertFalse(buffer.isReadable());

                incoming.clientPacketData(null);

                pending.verify(() -> PendingCraftingJobs.jobStatus(jobId, key, Long.MAX_VALUE, 0, status,
                        elapsedNanos, true));
            } finally {
                buffer.release();
            }
        }
    }
}
