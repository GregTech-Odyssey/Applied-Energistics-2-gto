package gto_ae.menu.implementations;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.google.common.collect.Sets;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import gto_ae.api.util.DirectionalGlobalPos;
import gto_ae.client.gui.me.facility_management.FacilityManagementScreen;
import gto_ae.core.sync.packets.FacilityManagementPacket;
import gto_ae.helpers.facility_management.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import appeng.api.config.YesNo;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.FakeSlot;

/**
 * 与隔壁样板管理终端的区别在于这个菜单对于ME设备是非即时的，可以保存快照状态
 * 
 * @see FacilityManagementScreen
 */
public class FacilityManagementMenu extends AEBaseMenu {
    private static final IntOpenHashSet clearView = new IntOpenHashSet();
    private final IFacilityManagement manager;
    private boolean broadcastFacility = true;

    private IntOpenHashSet lastSeenMachines = null;
    private final Int2ObjectOpenHashMap<FrozenMachineStatus> id2MachineSnapshot = new Int2ObjectOpenHashMap<>();
    private IntOpenHashSet frozenView = new IntOpenHashSet();
    private boolean savingView = false;
    private boolean loadingView = true;

    @GuiSync(947)
    public WorkingStatus filterStatus = WorkingStatus.NONE;
    @GuiSync(948)
    public YesNo filterHasCpuJobs = YesNo.UNDECIDED;
    @GuiSync(949)
    public IO filterIO = IO.NONE;

    @GuiSync(325)
    public boolean viewFrozen = false;

    public final FakeSlot ioFilterSlot;
    public final FakeSlot iconFilterSlot;

    private static final String actionFreezeView = "freeze_view";
    private static final String actionOpenGui = "open_gui";

    public static final MenuType<FacilityManagementMenu> TYPE = MenuTypeBuilder
            .create((i, inv, host) -> new FacilityManagementMenu(i, inv, host),
                    IFacilityManagementHost.class)
            .build("facility_management_menu");

    public FacilityManagementMenu(int id, Inventory playerInventory, IFacilityManagementHost host) {
        this(TYPE, id, playerInventory, host);

    }

    public FacilityManagementMenu(MenuType<?> menuType, int id, Inventory playerInventory,
            IFacilityManagementHost host) {
        super(menuType, id, playerInventory, host);
        this.manager = host.getLogic();
        this.createPlayerInventorySlots(playerInventory);
        ioFilterSlot = new FakeSlot(host.getLogic().getIoFilterInv().createMenuWrapper(), 0);
        iconFilterSlot = new FakeSlot(host.getLogic().getFacilityIconFilterInv().createMenuWrapper(), 0);
        addSlot(ioFilterSlot);
        addSlot(iconFilterSlot);

        registerClientAction(actionFreezeView, IntOpenHashSet.class, this::freezeView);
        registerClientAction(actionOpenGui, Integer.class, this::openGui);
    }

    @Nullable
    private IGrid getGrid() {
        IActionHost host = this.getActionHost();
        if (host != null) {
            final IGridNode agn = host.getActionableNode();
            if (agn != null && agn.isActive()) {
                return agn.getGrid();
            }
        }
        return null;
    }

    @Override
    public void broadcastChanges() {
        if (isClientSide()) {
            return;
        }
        var settingsHolder = manager.getHost();
        var filterStatus = settingsHolder.getWorkingFilter();
        var filterHasCpuJobs = settingsHolder.getCraftingJobsFilter();
        var filterIO = settingsHolder.getIOModeFilter();
        broadcastFacility = filterStatus != this.filterStatus
                || filterHasCpuJobs != this.filterHasCpuJobs
                || filterIO != this.filterIO;
        this.filterStatus = filterStatus;
        this.filterHasCpuJobs = filterHasCpuJobs;
        this.filterIO = filterIO;
        viewFrozen = !frozenView.isEmpty();
        super.broadcastChanges();
        if (getPlayer().getCommandSenderWorld().getGameTime() % 20 == 0) {
            broadcastFacility = true;
        }

        refreshFacilities();
    }

    @SuppressWarnings("unchecked")
    private void refreshFacilities() {
        if (broadcastFacility) {
            broadcastFacility = false;
            IGrid grid = getGrid();

            var currentSeen = new IntOpenHashSet();
            Set<DirectionalGlobalPos> savingViews;
            if (savingView) {
                savingViews = new HashSet<>();
            } else {
                savingViews = null;
            }
            if (grid != null) {
                for (var machineClass : grid.getMachineClasses()) {
                    if (IStatusTracked.class.isAssignableFrom(machineClass)) {
                        visitPatternProviderHosts(grid, (Class<? extends IStatusTracked>) machineClass,
                                currentSeen, savingViews);
                    }
                }

            }
            if (savingViews != null) {
                manager.saveView(savingViews);
            }

            if (lastSeenMachines != null) {
                // Machines that disappeared since last refresh
                for (int disappeared : Sets.difference(lastSeenMachines, currentSeen)) {
                    sendPacketToClient(FacilityManagementPacket.forRemoval(disappeared));
                }
            }
            lastSeenMachines = currentSeen;
            if (loadingView && !manager.getSavedView().isEmpty()) {
                frozenView = new IntOpenHashSet(currentSeen);
            }
            savingView = false;
            loadingView = false;
        }
    }

    @SuppressWarnings("RedundantIfStatement")
    private boolean isInvalid(IStatusTracked machine) {
        if (machine.getFacilityPosition() == null) {
            return true;
        }
        if (loadingView && !manager.getSavedView().isEmpty()) {
            if (!manager.getSavedView().contains(machine.getFacilityPosition())) {
                return true;
            }
        }
        if (!frozenView.isEmpty()) {
            return !frozenView.contains(machine.getFacilityUid());
        }
        switch (filterStatus) {
            case BUSY, WORKING, IDLE -> {
                if (machine.getStatus() != filterStatus)
                    return true;
            }
            case NONE -> {
            }
            case WORKING_OR_BUSY -> {
                if (machine.getStatus() == WorkingStatus.IDLE)
                    return true;
            }
        }
        switch (filterHasCpuJobs) {
            case YES -> {
                if (machine.getRequestedJobs().isEmpty())
                    return true;
            }
            case NO -> {
                if (!machine.getRequestedJobs().isEmpty())
                    return true;
            }
            default -> {
            }
        }

        var iconFilter = manager.getFacilityItem();
        if (iconFilter != null && machine.getTerminalGroup().icon() != iconFilter) {
            return true;
        }

        var ioStatistics = machine.getThroughputCounter();
        if (ioStatistics != ThroughputCounter.EMPTY) {
            var ioMatch = switch (filterIO) {
                case IN -> ioStatistics.hasPositiveValues();
                case OUT -> ioStatistics.hasNegativeValues();
                case BOTH -> (ioStatistics.hasPositiveValues() && ioStatistics.hasNegativeValues());
                default -> true;
            };
            if (!ioMatch) {
                return true;
            }

            var contentCheck = manager.getFilter() != null && !ioStatistics.containsKey(manager.getFilter());
            if (contentCheck) {
                return true;
            }

        }
        return false;
    }

    private <T extends IStatusTracked> void visitPatternProviderHosts(
            IGrid grid, Class<T> machineClass, IntSet currentSeen, @Nullable Set<DirectionalGlobalPos> positions) {
        for (T machine : grid.getActiveMachines(machineClass)) {
            if (isInvalid(machine)) {
                continue;
            }
            var pos = Objects.requireNonNull(machine.getFacilityPosition());
            if (positions != null) {
                positions.add(pos);
            }

            var t = this.id2MachineSnapshot.get(machine.getFacilityUid());
            if ((t == null || !t.serverEquals(machine))) {
                var status = new FrozenMachineStatus(
                        machine.getStatus(),
                        machine.getRequestedJobs().size(),
                        machine.getThroughputCounter(),
                        machine.getTerminalGroup(),
                        machine.getFacilityUid(),
                        pos);
                status.setOpenGuiAction(machine::openGui);
                this.id2MachineSnapshot.put(machine.getFacilityUid(), status);
                sendPacketToClient(FacilityManagementPacket.forUpdate(machine));
            }

            currentSeen.add(machine.getFacilityUid());
        }
    }

    public void freezeView(@Nullable IntOpenHashSet toFreeze) {
        if (toFreeze == null) {
            toFreeze = clearView;
        }
        if (isClientSide()) {
            sendClientAction(actionFreezeView, toFreeze);
            return;
        }
        this.frozenView = toFreeze;
        savingView = !toFreeze.isEmpty();
        if (toFreeze.isEmpty()) {
            manager.saveView(Set.of());
        }
        broadcastFacility = true;
        refreshFacilities();
    }

    public void openGui(int facilityUid) {
        if (isClientSide()) {
            sendClientAction(actionOpenGui, facilityUid);
            return;
        }
        var f = id2MachineSnapshot.get(facilityUid);
        if (f != null) {
            f.openGui(getPlayer());
        }
    }
}
