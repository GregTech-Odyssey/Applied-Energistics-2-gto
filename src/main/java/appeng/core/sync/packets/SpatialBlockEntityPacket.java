package appeng.core.sync.packets;

import io.netty.buffer.Unpooled;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import appeng.core.sync.BasePacket;

/**
 * Applies moved block entities through their chunk-load callback, which may differ from ordinary update handling.
 */
public class SpatialBlockEntityPacket extends BasePacket {
    private BlockPos pos;
    private BlockState state;
    private BlockEntityType<?> type;
    private CompoundTag tag;

    public SpatialBlockEntityPacket(FriendlyByteBuf stream) {
        pos = stream.readBlockPos();
        state = Block.stateById(stream.readVarInt());
        type = BuiltInRegistries.BLOCK_ENTITY_TYPE.byId(stream.readVarInt());
        tag = stream.readNbt();
    }

    public SpatialBlockEntityPacket(BlockEntity blockEntity) {
        var data = new FriendlyByteBuf(Unpooled.buffer());
        data.writeInt(getPacketID());
        data.writeBlockPos(blockEntity.getBlockPos());
        data.writeVarInt(Block.getId(blockEntity.getBlockState()));
        data.writeVarInt(BuiltInRegistries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType()));
        data.writeNbt(blockEntity.getUpdateTag());
        configureWrite(data);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void clientPacketData(Player player) {
        var level = (ClientLevel) player.level();
        var chunk = level.getChunkAt(pos);
        chunk.removeBlockEntity(pos);
        level.setServerVerifiedBlockState(pos, state, Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
        // A pending block prediction may defer the authoritative state write.
        if (chunk.getBlockState(pos) != state) {
            level.setBlock(pos, state, Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
        }
        var blockEntity = chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE);
        if (blockEntity != null && blockEntity.getType() == type) {
            blockEntity.handleUpdateTag(tag);
        }
    }
}
