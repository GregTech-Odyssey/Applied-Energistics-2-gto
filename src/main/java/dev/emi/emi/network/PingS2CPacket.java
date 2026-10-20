package dev.emi.emi.network;

import dev.emi.emi.EmiClient;
import dev.emi.emi.network.EmiNetwork;
import dev.emi.emi.network.EmiPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public class PingS2CPacket implements EmiPacket {

    public PingS2CPacket() {
    }

    public PingS2CPacket(FriendlyByteBuf buf) {
    }

    @Override
    public void write(FriendlyByteBuf buf) {
    }

    @Override
    public void apply(Player player) {
        EmiClient.onServer = true;
    }

    @Override
    public ResourceLocation getId() {
        return EmiNetwork.PING;
    }
}
