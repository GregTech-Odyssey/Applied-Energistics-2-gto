package dev.emi.emi.network;

import dev.emi.emi.network.EmiPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class EmiNetwork {
    public static final ResourceLocation FILL_RECIPE = new ResourceLocation("emi:fill_recipe");
    public static final ResourceLocation CREATE_ITEM = new ResourceLocation("emi:create_item");
    public static final ResourceLocation COMMAND = new ResourceLocation("emi:command");
    public static final ResourceLocation CHESS = new ResourceLocation("emi:chess");
    public static final ResourceLocation PING = new ResourceLocation("emi:ping");
    private static BiConsumer<ServerPlayer, EmiPacket> clientSender;
    private static Consumer<dev.emi.emi.network.EmiPacket> serverSender;

    public static void initServer(BiConsumer<ServerPlayer, EmiPacket> sender) {
        clientSender = sender;
    }

    public static void initClient(Consumer<dev.emi.emi.network.EmiPacket> sender) {
        serverSender = sender;
    }

    public static void sendToClient(ServerPlayer player, EmiPacket packet) {
        clientSender.accept(player, packet);
    }

    public static void sendToServer(EmiPacket packet) {
        serverSender.accept(packet);
    }
}
