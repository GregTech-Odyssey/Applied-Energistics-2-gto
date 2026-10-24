package appeng.client.gui.me.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.core.AEConfig;
import appeng.core.sync.packets.CraftingJobStatusPacket.Status;

class PendingCraftingJobsTest {
    private MockedStatic<Minecraft> minecraftStatic;
    private MockedStatic<AEConfig> configStatic;
    private Minecraft minecraft;
    private ToastComponent toasts;
    private AEConfig config;
    private AEKey key;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void setUp() {
        minecraft = mock(Minecraft.class);
        minecraft.player = mock(LocalPlayer.class);
        toasts = mock(ToastComponent.class);
        when(minecraft.getToasts()).thenReturn(toasts);
        config = mock(AEConfig.class);
        minecraftStatic = mockStatic(Minecraft.class);
        minecraftStatic.when(Minecraft::getInstance).thenReturn(minecraft);
        configStatic = mockStatic(AEConfig.class);
        configStatic.when(AEConfig::instance).thenReturn(config);
        key = mock(AEKey.class);
        when(key.getDisplayName()).thenReturn(Component.literal("Silicon"));
        when(key.getAmountPerUnit()).thenReturn(1);
        when(key.getType()).thenReturn(mock(AEKeyType.class));
        PendingCraftingJobs.clearPendingJobs();
    }

    @AfterEach
    void tearDown() {
        PendingCraftingJobs.clearPendingJobs();
        configStatic.close();
        minecraftStatic.close();
    }

    @ParameterizedTest
    @CsvSource({ "true,false,1", "false,true,1", "true,true,1", "false,false,0" })
    void chatsWhenEnabledOrFollowedButAlwaysShowsToast(boolean enabled, boolean followed, int messages) {
        when(config.isNotifyForFinishedCraftingJobs()).thenReturn(enabled);
        minecraft.screen = mock(MEStorageScreen.class);

        PendingCraftingJobs.jobStatus(UUID.randomUUID(), key, 100, 0, Status.FINISHED, 23_000_000_000L, followed);

        verify(minecraft.player, times(messages)).displayClientMessage(any(Component.class), eq(false));
        verify(toasts).addToast(any(FinishedJobToast.class));
        verify(minecraft.player, never()).getInventory();
    }

    @Test
    void ignoresStatusForUnknownKeys() {
        when(config.isNotifyForFinishedCraftingJobs()).thenReturn(true);

        PendingCraftingJobs.jobStatus(UUID.randomUUID(), null, 100, 0, Status.FINISHED, 23_000_000_000L, true);

        verify(minecraft.player, never()).displayClientMessage(any(), anyBoolean());
        verify(toasts, never()).addToast(any());
    }

    @Test
    void cancellationRemovesThePendingJobWithoutCompletionChatOrToast() {
        var id = UUID.randomUUID();
        when(config.isNotifyForFinishedCraftingJobs()).thenReturn(true);
        PendingCraftingJobs.jobStatus(id, key, 100, 100, Status.STARTED, 0, true);
        assertTrue(PendingCraftingJobs.hasPendingJob(key));

        PendingCraftingJobs.jobStatus(id, key, 100, 50, Status.CANCELLED, 23_000_000_000L, true);

        assertFalse(PendingCraftingJobs.hasPendingJob(key));
        verify(minecraft.player, never()).displayClientMessage(any(), anyBoolean());
        verify(toasts, never()).addToast(any());
    }

    @Test
    void successfulCompletionRemovesThePendingJobAndNotifies() {
        var id = UUID.randomUUID();
        PendingCraftingJobs.jobStatus(id, key, 100, 100, Status.STARTED, 0, true);

        PendingCraftingJobs.jobStatus(id, key, 100, 0, Status.FINISHED, 23_000_000_000L, true);

        assertFalse(PendingCraftingJobs.hasPendingJob(key));
        verify(minecraft.player).displayClientMessage(any(), eq(false));
        verify(toasts).addToast(any(FinishedJobToast.class));
    }
}
