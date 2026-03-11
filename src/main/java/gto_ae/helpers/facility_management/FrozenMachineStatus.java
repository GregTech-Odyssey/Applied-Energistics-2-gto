package gto_ae.helpers.facility_management;

import java.util.Objects;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableSet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.networking.crafting.ICraftingLink;

import gto_ae.api.util.DirectionalGlobalPos;

/// A snapshot of a machine's status at the time of packet creation.
/// This is immutable and can be safely shared across threads and used as a key in maps. It can be created from a live machine status
public final class FrozenMachineStatus implements IStatusTracked {
    private WorkingStatus status;
    private int relatedJobsCount;
    private ThroughputCounter throughputCounter;
    private long lastRefreshTime;
    private PatternContainerGroup terminalGroup;
    private final int facilityUid;
    @NotNull
    private final DirectionalGlobalPos directionalGlobalPos;

    @Nullable
    private Consumer<Player> openGuiAction;

    public FrozenMachineStatus(WorkingStatus value, int jobCount, ThroughputCounter entries,
            PatternContainerGroup group, int facilityUid, @NotNull DirectionalGlobalPos directionalGlobalPos) {
        this.status = value;
        this.relatedJobsCount = jobCount;
        this.throughputCounter = entries;
        this.lastRefreshTime = entries.getLastRefreshTime();
        this.facilityUid = facilityUid;
        this.terminalGroup = group;
        this.directionalGlobalPos = directionalGlobalPos;
    }

    /// used by client to update the status of an existing machine.
    /// Returns true if the terminal group has changed, which requires refreshing the search name and re-sorting the
    /// machine in the gui
    public boolean clientUpdate(FrozenMachineStatus other) {
        this.status = other.status;
        this.relatedJobsCount = other.relatedJobsCount;
        this.throughputCounter = other.throughputCounter;
        this.lastRefreshTime = other.lastRefreshTime;
        if (!Objects.equals(this.terminalGroup, other.terminalGroup)) {
            this.terminalGroup = other.terminalGroup;
            return true;
        }
        return false;
    }

    public boolean serverEquals(IStatusTracked other) {
        var lastRefresh = other.getThroughputCounter().getLastRefreshTime() == this.lastRefreshTime;
        return this.status == other.getStatus()
                && this.relatedJobsCount == other.getRequestedJobs().size()
                && lastRefresh
                && Objects.equals(this.terminalGroup, other.getTerminalGroup());
    }

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return ImmutableSet.of();
    }

    @Override
    public @NotNull WorkingStatus getStatus() {
        return status;
    }

    @Override
    public void openGui(Player player) {
        if (openGuiAction != null) {
            openGuiAction.accept(player);
        }
    }

    @Override
    public @NotNull ThroughputCounter getThroughputCounter() {
        return throughputCounter;
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        return terminalGroup;
    }

    public String getSearchName() {
        return terminalGroup.name().getString();
    }

    public boolean noThroughputStats() {
        return throughputCounter == ThroughputCounter.EMPTY;
    }

    @Override
    public int getFacilityUid() {
        return facilityUid;
    }

    @Override
    @NotNull
    public DirectionalGlobalPos getFacilityPosition() {
        return directionalGlobalPos;
    }

    public int getJobCount() {
        return relatedJobsCount;
    }

    public void setOpenGuiAction(@Nullable Consumer<Player> openGuiAction) {
        this.openGuiAction = openGuiAction;
    }
}
