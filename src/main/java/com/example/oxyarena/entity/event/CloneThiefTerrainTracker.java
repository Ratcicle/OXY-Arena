package com.example.oxyarena.entity.event;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class CloneThiefTerrainTracker {
    private final Map<BlockPos, BlockState> placedBlocks = new LinkedHashMap<>();
    private final Map<BlockPos, BlockState> brokenBlocks = new LinkedHashMap<>();

    boolean placeBlock(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState currentState = level.getBlockState(pos);
        if (!currentState.canBeReplaced() || currentState.hasBlockEntity()) {
            return false;
        }

        this.placedBlocks.putIfAbsent(pos.immutable(), state);
        level.setBlockAndUpdate(pos, state);
        return true;
    }

    boolean breakBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!canBreak(level, pos, state)) {
            return false;
        }

        this.brokenBlocks.putIfAbsent(pos.immutable(), state);
        level.levelEvent(2001, pos, Block.getId(state));
        level.removeBlock(pos, false);
        return true;
    }

    void revert(ServerLevel level) {
        for (Map.Entry<BlockPos, BlockState> entry : this.placedBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState placedState = entry.getValue();
            if (level.getBlockState(pos).equals(placedState)) {
                level.removeBlock(pos, false);
            }
        }

        for (Map.Entry<BlockPos, BlockState> entry : this.brokenBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState currentState = level.getBlockState(pos);
            if (currentState.isAir() || this.placedBlocks.containsKey(pos)) {
                level.setBlockAndUpdate(pos, entry.getValue());
            }
        }

        this.placedBlocks.clear();
        this.brokenBlocks.clear();
    }

    boolean hasTrackedChanges() {
        return !this.placedBlocks.isEmpty() || !this.brokenBlocks.isEmpty();
    }

    private static boolean canBreak(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()
                || !state.getFluidState().isEmpty()
                || state.hasBlockEntity()
                || state.getDestroySpeed(level, pos) < 0.0F
                || state.getDestroySpeed(level, pos) > 20.0F) {
            return false;
        }

        return !state.is(Blocks.BEDROCK)
                && !state.is(Blocks.OBSIDIAN)
                && !state.is(Blocks.CRYING_OBSIDIAN)
                && !state.is(Blocks.RESPAWN_ANCHOR)
                && !state.is(Blocks.END_PORTAL_FRAME)
                && !state.is(Blocks.END_PORTAL)
                && !state.is(Blocks.NETHER_PORTAL);
    }
}
