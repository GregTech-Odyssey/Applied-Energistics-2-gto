package dev.emi.emi.network;

import dev.emi.emi.chess.EmiChess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public abstract class EmiChessPacket implements EmiPacket {
    protected final UUID uuid;
    protected final byte type, start, end;

    public EmiChessPacket(UUID uuid, byte type, byte start, byte end) {
        this.uuid = uuid;
        this.type = type;
        this.start = start;
        this.end = end;
    }

    public EmiChessPacket(FriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readByte(), buf.readByte(), buf.readByte());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(uuid);
        buf.writeByte(type);
        buf.writeByte(start);
        buf.writeByte(end);
    }

    @Override
    public ResourceLocation getId() {
        return EmiNetwork.CHESS;
    }

    public static class S2C extends EmiChessPacket {

        public S2C(UUID uuid, byte type, byte start, byte end) {
            super(uuid, type, start, end);
        }

        public S2C(FriendlyByteBuf buf) {
            super(buf);
        }

        @Override
        public void apply(Player player) {
            EmiChess.receiveNetwork(uuid, type, start, end);
        }
    }

    public static class C2S extends EmiChessPacket {

        public C2S(UUID uuid, byte type, byte start, byte end) {
            super(uuid, type, start, end);
        }

        public C2S(FriendlyByteBuf buf) {
            super(buf);
        }

        @Override
        public void apply(Player player) {
            Player opponent = player.level().getPlayerByUUID(uuid);
            if (opponent instanceof ServerPlayer spe) {
                EmiNetwork.sendToClient(spe, new S2C(player.getUUID(), type, start, end));
            }
        }
    }
}
