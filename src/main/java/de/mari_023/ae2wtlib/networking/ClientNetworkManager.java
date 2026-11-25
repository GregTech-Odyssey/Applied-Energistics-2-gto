package de.mari_023.ae2wtlib.networking;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

public class ClientNetworkManager {
    static final Object2ObjectOpenHashMap<String, ServerNetworkManager.PacketDeserializer> CLIENT_PACKETS = new Object2ObjectOpenHashMap<>();

    public static void registerClientBoundPacket(String name, ServerNetworkManager.PacketDeserializer deserializer) {
        CLIENT_PACKETS.put(name, deserializer);
    }

    public static void sendToServer(AE2wtlibPacket packet) {
        ServerNetworkManager.CHANNEL.sendToServer(
                new ServerNetworkManager.Envelope(packet.getPacketName(), packet.getPacketBuffer()));
    }

    static Player getClientPlayer() {
        return Minecraft.getInstance().player;
    }
}
