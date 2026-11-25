package de.mari_023.ae2wtlib.networking;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import de.mari_023.ae2wtlib.AE2wtlib;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

public class ServerNetworkManager {
    private static final String PROTOCOL = "1";
    static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AE2wtlib.MOD_NAME, "network"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private static final Object2ObjectOpenHashMap<String, PacketDeserializer> SERVER_PACKETS = new Object2ObjectOpenHashMap<>();

    static {
        CHANNEL.registerMessage(0, Envelope.class, Envelope::encode, Envelope::decode, Envelope::handle);
    }

    public static void registerServerBoundPacket(String name, PacketDeserializer deserializer) {
        SERVER_PACKETS.put(name, deserializer);
    }

    public static void sendToClient(ServerPlayer player, AE2wtlibPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new Envelope(packet.getPacketName(), packet.getPacketBuffer()));
    }

    @FunctionalInterface
    public interface PacketDeserializer {
        AE2wtlibPacket create(FriendlyByteBuf buf);
    }

    record Envelope(String name, FriendlyByteBuf payload) {
        static void encode(Envelope msg, FriendlyByteBuf out) {
            out.writeUtf(msg.name);
            int len = msg.payload.readableBytes();
            out.writeVarInt(len);
            out.writeBytes(msg.payload, msg.payload.readerIndex(), len);
        }

        static Envelope decode(FriendlyByteBuf in) {
            String name = in.readUtf();
            int len = in.readVarInt();
            return new Envelope(name, new FriendlyByteBuf(in.readBytes(len)));
        }

        static void handle(Envelope msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            var ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                try {
                    boolean server = ctx.getDirection().getReceptionSide().isServer();
                    Player player = server ? ctx.getSender() : ClientNetworkManager.getClientPlayer();
                    if (player == null) {
                        return;
                    }
                    var deserializer = server ? SERVER_PACKETS.get(msg.name)
                            : ClientNetworkManager.CLIENT_PACKETS.get(msg.name);
                    if (deserializer != null) {
                        deserializer.create(msg.payload).processPacketData(player);
                    }
                } finally {
                    msg.payload.release();
                }
            });
            ctx.setPacketHandled(true);
        }
    }
}
