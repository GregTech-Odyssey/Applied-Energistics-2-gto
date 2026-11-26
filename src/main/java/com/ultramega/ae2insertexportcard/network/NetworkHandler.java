package com.ultramega.ae2insertexportcard.network;

import com.ultramega.ae2insertexportcard.AE2InsertExportCard;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private final String protocolVersion = Integer.toString(1);
    private final ResourceLocation channel = new ResourceLocation(AE2InsertExportCard.MOD_ID, "main_channel");
    private final SimpleChannel handler = NetworkRegistry.ChannelBuilder
            .named(channel)
            .clientAcceptedVersions(protocolVersion::equals)
            .serverAcceptedVersions(protocolVersion::equals)
            .networkProtocolVersion(() -> protocolVersion)
            .simpleChannel();

    public void register() {
        int id = 0;
        this.handler.registerMessage(id++, UpgradeUpdateMessage.class, UpgradeUpdateMessage::encode,
                UpgradeUpdateMessage::decode, UpgradeUpdateMessage::handle);
        this.handler.registerMessage(id++, LockSlotUpdateMessage.class, LockSlotUpdateMessage::encode,
                LockSlotUpdateMessage::decode, LockSlotUpdateMessage::handle);
    }

    public void sendToServer(Object message) {
        this.handler.sendToServer(message);
    }
}
