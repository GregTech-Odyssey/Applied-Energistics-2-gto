package com.gto.ae.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.core.AppEng;
import appeng.core.definitions.AEBlockEntities;
import appeng.core.definitions.AEBlocks;

public final class SuspiciousSkyStoneBlock extends BrushableBlock {
    public static final ResourceLocation LOOT_TABLE = AppEng.makeId("archaeology/suspicious_sky_stone");

    public SuspiciousSkyStoneBlock() {
        super(AEBlocks.SKY_STONE_BLOCK.block(), Properties.copy(AEBlocks.SKY_STONE_BLOCK.block()).strength(0.25f),
                SoundEvents.BRUSH_GRAVEL, SoundEvents.BRUSH_GRAVEL_COMPLETED);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 保留刷扫进度回退，陨石不继承沙砾的下落行为。
        ((BrushableBlockEntity) level.getBlockEntity(pos)).checkReset();
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
    }

    @Override
    public BrushableBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    public static final class Entity extends BrushableBlockEntity {
        public Entity(BlockPos pos, BlockState state) {
            super(pos, state);
            // 矿脉直接写入区块方块状态，首次访问时由原版创建实体并绑定战利品。
            setLootTable(LOOT_TABLE, 0);
        }

        @Override
        public BlockEntityType<?> getType() {
            return AEBlockEntities.SUSPICIOUS_SKY_STONE;
        }

        @Override
        public void load(CompoundTag tag) {
            // 原版读取已抽出的物品时不会清空构造默认表，必须先清除以免重抽。
            setLootTable(null, 0);
            super.load(tag);
        }
    }
}
