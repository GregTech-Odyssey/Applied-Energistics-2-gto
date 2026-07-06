package gto_ae.core.sync.packets;

import io.netty.buffer.Unpooled;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.objects.Reference2LongMaps;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyMap;
import appeng.core.sync.BasePacket;

import gto_ae.api.util.DirectionalGlobalPos;
import gto_ae.client.gui.me.facility_management.FacilityManagementScreen;
import gto_ae.helpers.facility_management.FrozenMachineStatus;
import gto_ae.helpers.facility_management.IStatusTracked;
import gto_ae.helpers.facility_management.ThroughputCounter;
import gto_ae.helpers.facility_management.WorkingStatus;

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

            AEKeyMap<AEKey> configuredSetting = new AEKeyMap<>();
            int settingSize = stream.readInt();
            for (int i = 0; i < settingSize; i++) {
                var key = AEKey.readKey(stream);
                var value = stream.readLong();
                configuredSetting.set(key, value);
            }

            this.status = new FrozenMachineStatus(
                    pos, status,
                    jobCount,
                    throughputCounter,
                    group,
                    facilityUid,
                    configuredSetting);
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

        data.writeInt(tracked.getConfiguredSetting().size());
        for (var setting : Reference2LongMaps.fastIterable(tracked.getConfiguredSetting())) {
            AEKey.writeKey(data, setting.getKey());
            data.writeLong(setting.getLongValue());
        }

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
