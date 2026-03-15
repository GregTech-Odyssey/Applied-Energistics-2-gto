package gto_ae.api.util;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record DirectionalGlobalPos(GlobalPos pos, @Nullable Direction side) {
    public DirectionalGlobalPos(GlobalPos pos, Direction side) {
        this.pos = pos;
        this.side = side;
    }

    public DirectionalGlobalPos(ResourceKey<Level> dimension, BlockPos pos, @Nullable Direction side) {
        this(GlobalPos.of(dimension, pos), side);
    }

    public static void writeToBuffer(FriendlyByteBuf buf, DirectionalGlobalPos pos) {
        buf.writeGlobalPos(pos.pos);
        buf.writeBoolean(pos.side != null);
        if (pos.side != null) {
            buf.writeEnum(pos.side);
        }
    }

    public static DirectionalGlobalPos readFromBuffer(FriendlyByteBuf buf) {
        GlobalPos pos = buf.readGlobalPos();
        Direction side = null;
        if (buf.readBoolean()) {
            side = buf.readEnum(Direction.class);
        }
        return new DirectionalGlobalPos(pos, side);
    }

    public static void writeToTag(CompoundTag tag, String key, DirectionalGlobalPos pos) {
        CompoundTag posTag = new CompoundTag();
        posTag.put("pos", NbtUtils.writeBlockPos(pos.blockPos()));
        posTag.putString("dimension", pos.dimension().location().toString());
        if (pos.side != null) {
            posTag.putInt("side", pos.side.ordinal());
        }
        tag.put(key, posTag);
    }

    public static DirectionalGlobalPos readFromTag(CompoundTag tag, String key) {
        CompoundTag posTag = tag.getCompound(key);
        BlockPos blockPos = NbtUtils.readBlockPos(posTag.getCompound("pos"));
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation(posTag.getString("dimension")));
        Direction side = null;
        if (posTag.contains("side")) {
            side = Direction.values()[posTag.getInt("side")];
        }
        return new DirectionalGlobalPos(dimension, blockPos, side);
    }

    public BlockPos blockPos() {
        return pos.pos();
    }

    public ResourceKey<Level> dimension() {
        return pos.dimension();
    }
}
