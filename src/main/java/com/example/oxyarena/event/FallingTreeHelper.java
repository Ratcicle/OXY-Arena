package com.example.oxyarena.event;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.oxyarena.Config;
import com.example.oxyarena.OXYArena;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class FallingTreeHelper {
    public static final TagKey<Block> LOGS = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "falling_tree_logs"));
    public static final TagKey<Block> LEAVES = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "falling_tree_leaves"));
    public static final TagKey<Block> BLACKLIST = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "falling_tree_blacklist"));

    private static final int MIN_LEAF_EVIDENCE = 3;
    private static final int LEAF_SCAN_RADIUS = 2;
    private static final Map<MinecraftServer, List<PendingTreeFell>> PENDING_FELLS = new IdentityHashMap<>();
    private static boolean processingTreeFell;

    private FallingTreeHelper() {
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (processingTreeFell || event.isCanceled()) {
            return;
        }

        if (!(event.getPlayer() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        if (!Config.fallingTreeEnabled() || player.isSpectator() || level.dimension() != Level.OVERWORLD) {
            return;
        }

        BlockPos origin = event.getPos();
        BlockState originState = event.getState();
        if (!isEligibleLog(originState)) {
            return;
        }

        if (!player.getMainHandItem().canPerformAction(ItemAbilities.AXE_DIG)) {
            return;
        }

        TreeScanResult scanResult = scanTree(level, origin, originState.getBlock(), Config.fallingTreeMaxLogs());
        if (scanResult == null || scanResult.allLogs().size() <= 1) {
            return;
        }

        List<BlockPos> remainingLogs = new ArrayList<>(scanResult.allLogs());
        remainingLogs.remove(origin);
        remainingLogs.sort(Comparator
                .comparingInt((BlockPos pos) -> pos.getY())
                .thenComparingInt(pos -> horizontalDistanceSquared(pos, origin))
                .thenComparingInt((BlockPos pos) -> pos.getX())
                .thenComparingInt((BlockPos pos) -> pos.getZ()));

        PENDING_FELLS.computeIfAbsent(level.getServer(), unused -> new ArrayList<>())
                .add(new PendingTreeFell(
                        player,
                        level,
                        origin.immutable(),
                        originState.getBlock(),
                        origin.getY(),
                        remainingLogs,
                        level.getServer().getTickCount() + 1,
                        Config.fallingTreeBreakIntervalTicks()));
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        List<PendingTreeFell> pending = PENDING_FELLS.get(event.getServer());
        if (pending == null || pending.isEmpty()) {
            return;
        }

        Iterator<PendingTreeFell> iterator = pending.iterator();
        while (iterator.hasNext()) {
            PendingTreeFell scheduledFell = iterator.next();
            if (scheduledFell.nextBreakTick() > event.getServer().getTickCount()) {
                continue;
            }

            if (advanceScheduledFell(scheduledFell)) {
                iterator.remove();
            }
        }

        if (pending.isEmpty()) {
            PENDING_FELLS.remove(event.getServer());
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        PENDING_FELLS.remove(event.getServer());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING_FELLS.remove(event.getServer());
    }

    private static boolean advanceScheduledFell(PendingTreeFell scheduledFell) {
        ServerPlayer player = scheduledFell.player();
        ServerLevel level = scheduledFell.level();
        if (player.isRemoved() || player.serverLevel() != level || player.isSpectator()) {
            return true;
        }

        if (!level.hasChunkAt(scheduledFell.origin())) {
            return true;
        }

        if (level.getBlockState(scheduledFell.origin()).is(scheduledFell.logBlock())) {
            return true;
        }

        if (!player.getMainHandItem().canPerformAction(ItemAbilities.AXE_DIG)) {
            return true;
        }

        if (!scheduledFell.hasRemainingLogs()) {
            return true;
        }

        processingTreeFell = true;
        try {
            BlockPos targetPos = scheduledFell.pollNextLog();
            if (targetPos == null) {
                return true;
            }

            if (!level.hasChunkAt(targetPos)) {
                return true;
            }

            if (targetPos.getY() >= scheduledFell.baseY()) {
                BlockState targetState = level.getBlockState(targetPos);
                if (targetState.is(scheduledFell.logBlock()) && !targetState.is(BLACKLIST) && player.getMainHandItem().canPerformAction(ItemAbilities.AXE_DIG)) {
                    if (player.gameMode.destroyBlock(targetPos)) {
                        playChainedBreakSound(level, targetPos, targetState);
                    }
                }
            }
        } finally {
            processingTreeFell = false;
        }

        if (!player.getMainHandItem().canPerformAction(ItemAbilities.AXE_DIG)) {
            return true;
        }

        if (!scheduledFell.hasRemainingLogs()) {
            return true;
        }

        scheduledFell.scheduleNextBreak();
        return false;
    }

    private static boolean isEligibleLog(BlockState state) {
        return state.is(LOGS) && !state.is(BLACKLIST);
    }

    private static TreeScanResult scanTree(ServerLevel level, BlockPos origin, Block block, int maxLogs) {
        Set<BlockPos> trunkLogs = scanTrunkLogs(level, origin, block, maxLogs);
        if (trunkLogs == null || trunkLogs.size() <= 1) {
            return null;
        }

        if (!hasLeafEvidence(level, trunkLogs, MIN_LEAF_EVIDENCE, 2)) {
            return null;
        }

        int topY = trunkLogs.stream()
                .mapToInt(BlockPos::getY)
                .max()
                .orElse(origin.getY());

        Set<BlockPos> branchLogs = Config.fallingTreeBranchDetectionEnabled()
                ? scanBranchLogs(level, origin, block, trunkLogs, topY, maxLogs)
                : Set.of();
        if (branchLogs == null) {
            return null;
        }

        Set<BlockPos> allLogs = new HashSet<>(trunkLogs);
        allLogs.addAll(branchLogs);
        if (allLogs.size() > maxLogs) {
            return null;
        }

        return new TreeScanResult(List.copyOf(allLogs), Set.copyOf(trunkLogs), Set.copyOf(branchLogs), topY);
    }

    private static Set<BlockPos> scanTrunkLogs(ServerLevel level, BlockPos origin, Block block, int maxLogs) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> trunkLogs = new HashSet<>();

        queue.add(origin.immutable());
        visited.add(origin.immutable());

        while (!queue.isEmpty()) {
            BlockPos currentPos = queue.removeFirst();
            if (currentPos.getY() < origin.getY()) {
                continue;
            }

            if (!level.hasChunkAt(currentPos)) {
                return null;
            }

            BlockState currentState = level.getBlockState(currentPos);
            if (!currentState.is(block) || !currentState.is(LOGS) || currentState.is(BLACKLIST)) {
                continue;
            }

            trunkLogs.add(currentPos);
            if (trunkLogs.size() > maxLogs) {
                return null;
            }

            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                for (int yOffset = 0; yOffset <= 1; yOffset++) {
                    for (int zOffset = -1; zOffset <= 1; zOffset++) {
                        if (xOffset == 0 && yOffset == 0 && zOffset == 0) {
                            continue;
                        }

                        BlockPos nextPos = currentPos.offset(xOffset, yOffset, zOffset);
                        if (nextPos.getY() < origin.getY()) {
                            continue;
                        }

                        if (visited.add(nextPos)) {
                            queue.addLast(nextPos.immutable());
                        }
                    }
                }
            }
        }

        return trunkLogs;
    }

    private static Set<BlockPos> scanBranchLogs(ServerLevel level, BlockPos origin, Block block, Set<BlockPos> trunkLogs, int topY, int maxLogs) {
        int seedFloorY = topY - 3;
        Set<BlockPos> canopySeeds = new HashSet<>();
        for (BlockPos trunkLog : trunkLogs) {
            if (trunkLog.getY() >= seedFloorY) {
                canopySeeds.add(trunkLog);
            }
        }

        Set<BlockPos> processedBranchLogs = new HashSet<>();
        Set<BlockPos> branchLogs = new HashSet<>();
        int totalLogCount = trunkLogs.size();

        for (BlockPos seedPos : canopySeeds) {
            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                for (int yOffset = -1; yOffset <= 1; yOffset++) {
                    for (int zOffset = -1; zOffset <= 1; zOffset++) {
                        if (xOffset == 0 && yOffset == 0 && zOffset == 0) {
                            continue;
                        }

                        BlockPos candidatePos = seedPos.offset(xOffset, yOffset, zOffset);
                        if (candidatePos.getY() < origin.getY()
                                || trunkLogs.contains(candidatePos)
                                || processedBranchLogs.contains(candidatePos)) {
                            continue;
                        }

                        BranchComponent component = collectBranchComponent(level, candidatePos, block, origin.getY(), trunkLogs, processedBranchLogs);
                        if (component == null || !component.hasLeafEvidence() || component.logs().isEmpty()) {
                            continue;
                        }

                        branchLogs.addAll(component.logs());
                        totalLogCount += component.logs().size();
                        if (totalLogCount > maxLogs) {
                            return null;
                        }
                    }
                }
            }
        }

        return branchLogs;
    }

    private static BranchComponent collectBranchComponent(ServerLevel level, BlockPos startPos, Block block, int baseY, Set<BlockPos> trunkLogs,
            Set<BlockPos> processedBranchLogs) {
        ArrayDeque<BranchNode> queue = new ArrayDeque<>();
        Set<BlockPos> localVisited = new HashSet<>();
        Set<BlockPos> componentLogs = new HashSet<>();
        boolean hasLeafEvidence = false;

        queue.addLast(new BranchNode(startPos.immutable(), 1));

        while (!queue.isEmpty()) {
            BranchNode node = queue.removeFirst();
            BlockPos currentPos = node.pos();
            if (!localVisited.add(currentPos) || processedBranchLogs.contains(currentPos)) {
                continue;
            }

            if (currentPos.getY() < baseY || trunkLogs.contains(currentPos) || !level.hasChunkAt(currentPos)) {
                continue;
            }

            BlockState currentState = level.getBlockState(currentPos);
            if (!currentState.is(block) || !currentState.is(LOGS) || currentState.is(BLACKLIST)) {
                continue;
            }

            componentLogs.add(currentPos);
            if (!hasLeafEvidence && hasLeafEvidence(level, Set.of(currentPos), 1, Config.fallingTreeBranchLeafRadius())) {
                hasLeafEvidence = true;
            }

            if (node.distanceFromCanopy() >= Config.fallingTreeBranchMaxReach()) {
                continue;
            }

            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                for (int yOffset = -1; yOffset <= 1; yOffset++) {
                    for (int zOffset = -1; zOffset <= 1; zOffset++) {
                        if (xOffset == 0 && yOffset == 0 && zOffset == 0) {
                            continue;
                        }

                        BlockPos nextPos = currentPos.offset(xOffset, yOffset, zOffset);
                        if (nextPos.getY() < baseY || trunkLogs.contains(nextPos) || processedBranchLogs.contains(nextPos) || localVisited.contains(nextPos)) {
                            continue;
                        }

                        queue.addLast(new BranchNode(nextPos.immutable(), node.distanceFromCanopy() + 1));
                    }
                }
            }
        }

        processedBranchLogs.addAll(componentLogs);
        return new BranchComponent(componentLogs, hasLeafEvidence);
    }

    private static boolean hasLeafEvidence(ServerLevel level, Set<BlockPos> logs, int minimumLeafCount, int radius) {
        Set<BlockPos> detectedLeaves = new HashSet<>();
        for (BlockPos logPos : logs) {
            for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                for (int yOffset = -radius; yOffset <= radius; yOffset++) {
                    for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                        BlockPos checkPos = logPos.offset(xOffset, yOffset, zOffset);
                        if (logs.contains(checkPos)) {
                            continue;
                        }

                        if (level.getBlockState(checkPos).is(LEAVES) && detectedLeaves.add(checkPos) && detectedLeaves.size() >= minimumLeafCount) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private static int horizontalDistanceSquared(BlockPos first, BlockPos second) {
        int dx = first.getX() - second.getX();
        int dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private static void playChainedBreakSound(ServerLevel level, BlockPos pos, BlockState state) {
        var soundType = state.getSoundType();
        level.playSound(
                null,
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                soundType.getBreakSound(),
                SoundSource.BLOCKS,
                (soundType.getVolume() + 1.0F) / 2.0F,
                soundType.getPitch() * 0.8F);
    }

    private record TreeScanResult(List<BlockPos> allLogs, Set<BlockPos> trunkLogs, Set<BlockPos> branchLogs, int topY) {
    }

    private record BranchComponent(Set<BlockPos> logs, boolean hasLeafEvidence) {
    }

    private record BranchNode(BlockPos pos, int distanceFromCanopy) {
    }

    private static final class PendingTreeFell {
        private final ServerPlayer player;
        private final ServerLevel level;
        private final BlockPos origin;
        private final Block logBlock;
        private final int baseY;
        private final List<BlockPos> remainingLogs;
        private final int breakIntervalTicks;
        private int nextLogIndex;
        private int nextBreakTick;

        private PendingTreeFell(ServerPlayer player, ServerLevel level, BlockPos origin, Block logBlock, int baseY, List<BlockPos> remainingLogs,
                int nextBreakTick, int breakIntervalTicks) {
            this.player = player;
            this.level = level;
            this.origin = origin;
            this.logBlock = logBlock;
            this.baseY = baseY;
            this.remainingLogs = remainingLogs;
            this.nextBreakTick = nextBreakTick;
            this.breakIntervalTicks = breakIntervalTicks;
        }

        private ServerPlayer player() {
            return player;
        }

        private ServerLevel level() {
            return level;
        }

        private BlockPos origin() {
            return origin;
        }

        private Block logBlock() {
            return logBlock;
        }

        private int baseY() {
            return baseY;
        }

        private int nextBreakTick() {
            return nextBreakTick;
        }

        private boolean hasRemainingLogs() {
            return nextLogIndex < remainingLogs.size();
        }

        private BlockPos pollNextLog() {
            if (!hasRemainingLogs()) {
                return null;
            }

            return remainingLogs.get(nextLogIndex++);
        }

        private void scheduleNextBreak() {
            nextBreakTick += breakIntervalTicks;
        }
    }
}
