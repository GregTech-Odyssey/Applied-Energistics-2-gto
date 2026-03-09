package gto_ae.core.sync.packets;

import io.netty.buffer.Unpooled;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import gto_ae.client.gui.me.facility_management.FacilityManagementScreen;

import appeng.core.sync.BasePacket;

public class FacilityClearPacket extends BasePacket {

    public FacilityClearPacket() {
        FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.buffer(32));
        data.writeInt(this.getPacketID());
        this.configureWrite(data);

    }

    public FacilityClearPacket(FriendlyByteBuf ignored) {

    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void clientPacketData(Player player) {
        if (Minecraft.getInstance().screen instanceof FacilityManagementScreen<?> screen) {
            screen.clear();
        }
    }
}
