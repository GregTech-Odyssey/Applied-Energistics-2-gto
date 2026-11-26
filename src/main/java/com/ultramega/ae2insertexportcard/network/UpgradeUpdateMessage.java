package com.ultramega.ae2insertexportcard.network;

import java.util.function.Supplier;

import com.ultramega.ae2insertexportcard.container.UpgradeContainerMenu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

public class UpgradeUpdateMessage {
    private final int type;
    private final int[] selectedInventorySlots;

    public UpgradeUpdateMessage(int type, int[] selectedInventorySlots) {
        this.type = type;
        this.selectedInventorySlots = selectedInventorySlots;
    }

    public static UpgradeUpdateMessage decode(FriendlyByteBuf buf) {
        return new UpgradeUpdateMessage(buf.readInt(), buf.readVarIntArray());
    }

    public static void encode(UpgradeUpdateMessage message, FriendlyByteBuf buf) {
        buf.writeInt(message.type);
        buf.writeVarIntArray(message.selectedInventorySlots);
    }

    public static void handle(UpgradeUpdateMessage message, Supplier<NetworkEvent.Context> ctx) {
        Player player = ctx.get().getSender();
        if (player != null && player.containerMenu instanceof UpgradeContainerMenu containerMenu) {
            ctx.get().enqueueWork(
                    () -> containerMenu.getUpgradeHost().setSelectedInventorySlots(message.selectedInventorySlots));
        }

        ctx.get().setPacketHandled(true);
    }
}
