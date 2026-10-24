package appeng.client.gui.me.common;

import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import appeng.api.stacks.AEKey;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.sync.packets.CraftingJobStatusPacket;

/**
 * Tracks pending crafting jobs started by this player.
 */
@OnlyIn(Dist.CLIENT)
public final class PendingCraftingJobs {
    private static final Map<UUID, PendingJob> jobs = new Object2ObjectOpenHashMap<>();

    private PendingCraftingJobs() {
    }

    public static boolean hasPendingJob(AEKey what) {
        return jobs.values().stream().anyMatch(s -> s.what == what);
    }

    public static void clearPendingJobs() {
        jobs.clear();
    }

    public static void jobStatus(UUID id,
            AEKey what,
            long requestedAmount,
            long remainingAmount,
            CraftingJobStatusPacket.Status status) {
        jobStatus(id, what, requestedAmount, remainingAmount, status, 0, false);
    }

    public static void jobStatus(UUID id,
            AEKey what,
            long requestedAmount,
            long remainingAmount,
            CraftingJobStatusPacket.Status status,
            long elapsedTimeNanos,
            boolean followJob) {

        if (what == null) {
            AELog.warn("Received crafting job status for job %s with an unknown key", id);
            return;
        }

        AELog.debug("Crafting job " + id + " for " + requestedAmount
                + "x" + what.getDisplayName().getString() + ". State=" + status);

        var existing = jobs.get(id);
        switch (status) {
            case STARTED -> {
                if (existing == null) {
                    jobs.put(id, new PendingJob(id, what, requestedAmount, remainingAmount));
                }
            }
            case CANCELLED -> jobs.remove(id);
            case FINISHED -> {
                jobs.remove(id);
                var minecraft = Minecraft.getInstance();
                if (minecraft.player != null) {
                    minecraft.getToasts().addToast(new FinishedJobToast(what, requestedAmount));
                    if (AEConfig.instance().isNotifyForFinishedCraftingJobs() || followJob) {
                        minecraft.player.displayClientMessage(
                                FinishedJobNotification.create(what, requestedAmount, elapsedTimeNanos), false);
                    }
                }
            }
        }
    }

    record PendingJob(UUID jobId, AEKey what, long requestedAmount, long remainingAmount) {
    }
}
