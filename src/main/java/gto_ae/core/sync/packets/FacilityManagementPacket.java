package gto_ae.core.sync.packets;

import io.netty.buffer.Unpooled;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import gto_ae.api.util.DirectionalGlobalPos;
import gto_ae.client.gui.me.facility_management.FacilityManagementScreen;
import gto_ae.helpers.facility_management.FrozenMachineStatus;
import gto_ae.helpers.facility_management.IStatusTracked;
import gto_ae.helpers.facility_management.ThroughputCounter;
import gto_ae.helpers.facility_management.WorkingStatus;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.core.sync.BasePacket;

public class FacilityManagementPacket extends BasePacket {

    // input.
    private boolean addOrUpdate;
    private FrozenMachineStatus status;
    private int facilityUid;

    public FacilityManagementPacket(FriendlyByteBuf stream) {
        addOrUpdate = stream.readBoolean();
        var facilityUid = stream.readInt();

        if (addOrUpdate) {
            var pos = DirectionalGlobalPos.readFromBuffer(stream);
            var status = stream.readEnum(WorkingStatus.class);
            var jobCount = stream.readVarInt();
            var throughputCounter = ThroughputCounter.readFromBuffer(stream);
            var group = PatternContainerGroup.readFromPacket(stream);

            this.status = new FrozenMachineStatus(
                    status,
                    jobCount,
                    throughputCounter,
                    group,
                    facilityUid,
                    pos);
        } else {
            this.facilityUid = facilityUid;
        }

    }

    private FacilityManagementPacket() {
    }

    // api
    public static FacilityManagementPacket forUpdate(IStatusTracked tracked) {
        var packet = new FacilityManagementPacket();
        FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.buffer(2048));
        data.writeInt(packet.getPacketID());

        data.writeBoolean(true);
        data.writeInt(tracked.getFacilityUid());

        DirectionalGlobalPos.writeToBuffer(data, tracked.getFacilityPosition());
        data.writeEnum(tracked.getStatus());
        data.writeVarInt(tracked.getRequestedJobs().size());
        ThroughputCounter.writeToBuffer(data, tracked.getThroughputCounter());
        tracked.getTerminalGroup().writeToPacket(data);

        packet.configureWrite(data);
        return packet;
    }

    // api
    public static FacilityManagementPacket forRemoval(int tracked) {
        var packet = new FacilityManagementPacket();
        FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.buffer(40));
        data.writeInt(packet.getPacketID());

        data.writeBoolean(false);
        data.writeInt(tracked);

        packet.configureWrite(data);
        return packet;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void clientPacketData(Player player) {
        if (Minecraft.getInstance().screen instanceof FacilityManagementScreen<?> screen) {
            if (addOrUpdate) {
                screen.updateFacility(status);
            } else {
                screen.removeFacility(facilityUid);
            }
        }
    }
}
