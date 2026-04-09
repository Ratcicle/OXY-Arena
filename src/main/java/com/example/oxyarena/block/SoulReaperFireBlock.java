package com.example.oxyarena.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class SoulReaperFireBlock extends BaseFireBlock {
    public static final MapCodec<SoulReaperFireBlock> CODEC = simpleCodec(SoulReaperFireBlock::new);

    public SoulReaperFireBlock(BlockBehaviour.Properties properties) {
        super(properties, 0.0F);
    }

    @Override
    public MapCodec<SoulReaperFireBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction facing,
            BlockState facingState,
            LevelAccessor level,
            BlockPos currentPos,
            BlockPos facingPos) {
        return this.canSurvive(state, level, currentPos)
                ? this.defaultBlockState()
                : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos supportPos = pos.below();
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, Direction.UP);
    }

    @Override
    protected boolean canBurn(BlockState state) {
        return false;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
    }
}
