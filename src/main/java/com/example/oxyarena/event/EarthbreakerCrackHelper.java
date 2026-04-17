package com.example.oxyarena.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.example.oxyarena.registry.ModDamageTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class EarthbreakerCrackHelper {
    private static final int MIN_CHARGE_TICKS = 8;
    private static final int COOLDOWN_TICKS = 240;
    private static final int RESTORE_DELAY_TICKS = 120;
    private static final int RESTORE_RETRY_TICKS = 10;
    private static final int MAX_CONSECUTIVE_GROUND_FAILURES = 2;
    private static final int GROUND_SEARCH_BELOW = 3;
    private static final int GROUND_SEARCH_ABOVE = 5;
    private static final double LAYER_DAMAGE_HEIGHT = 2.5D;
    private static final CrackTier[] TIERS = new CrackTier[] {
            new CrackTier(1, 10, 3, 3, 5.0F),
            new CrackTier(2, 17, 5, 6, 8.0F),
            new CrackTier(3, 25, 7, 10, 12.0F)
    };

    private static final List<CrackActivation> ACTIVE_CRACKS = new ArrayList<>();
    private static final Map<TrackedBlockKey, RemovedBlockState> REMOVED_BLOCKS = new HashMap<>();
    private static long nextActivationId = 1L;

    private EarthbreakerCrackHelper() {
    }

    public static int minimumChargeTicks() {
        return MIN_CHARGE_TICKS;
    }

    public static int cooldownTicks() {
        return COOLDOWN_TICKS;
    }

    public static void activate(Player player, int chargeTicks) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        Vec3 look = player.getLookAngle();
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() < 1.0E-4D) {
            Direction direction = player.getDirection();
            horizontalLook = new Vec3(direction.getStepX(), 0.0D, direction.getStepZ());
        }
        horizontalLook = horizontalLook.normalize();

        CrackTier tier = tierForCharge(chargeTicks);
        int expectedGroundY = player.blockPosition().getY() - 1;
        ACTIVE_CRACKS.add(new CrackActivation(
                nextActivationId++,
                level.dimension(),
                player.getUUID(),
                player.position(),
                horizontalLook,
                tier,
                expectedGroundY,
                level.getServer().getTickCount()));

        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS,
                0.45F,
                0.55F);
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        int currentTick = event.getServer().getTickCount();
        tickActiveCracks(event.getServer(), currentTick);
        tickRemovedBlocks(event.getServer(), currentTick);
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        restoreAll(event.getServer());
        ACTIVE_CRACKS.clear();
        REMOVED_BLOCKS.clear();
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE_CRACKS.clear();
        REMOVED_BLOCKS.clear();
    }

    private static void tickActiveCracks(MinecraftServer server, int currentTick) {
        Iterator<CrackActivation> iterator = ACTIVE_CRACKS.iterator();
        while (iterator.hasNext()) {
            CrackActivation activation = iterator.next();
            ServerLevel level = server.getLevel(activation.dimension());
            if (level == null || activation.tick(level, currentTick)) {
                iterator.remove();
            }
        }
    }

    private static void tickRemovedBlocks(MinecraftServer server, int currentTick) {
        Iterator<Map.Entry<TrackedBlockKey, RemovedBlockState>> iterator = REMOVED_BLOCKS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<TrackedBlockKey, RemovedBlockState> entry = iterator.next();
            RemovedBlockState removedBlock = entry.getValue();
            if (currentTick < removedBlock.restoreTick()) {
                continue;
            }

            ServerLevel level = server.getLevel(entry.getKey().dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }

            BlockPos pos = entry.getKey().pos();
            if (!level.isLoaded(pos)) {
                removedBlock.setRestoreTick(currentTick + RESTORE_RETRY_TICKS);
                continue;
            }

            BlockState currentState = level.getBlockState(pos);
            if (!currentState.isAir()) {
                iterator.remove();
                continue;
            }

            if (hasLivingEntityInBlock(level, pos)) {
                removedBlock.setRestoreTick(currentTick + RESTORE_RETRY_TICKS);
                continue;
            }

            level.setBlockAndUpdate(pos, removedBlock.originalState());
            iterator.remove();
        }
    }

    private static void restoreAll(MinecraftServer server) {
        for (Map.Entry<TrackedBlockKey, RemovedBlockState> entry : REMOVED_BLOCKS.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey().dimension());
            if (level == null) {
                continue;
            }

            BlockPos pos = entry.getKey().pos();
            if (level.isLoaded(pos) && level.getBlockState(pos).isAir()) {
                level.setBlockAndUpdate(pos, entry.getValue().originalState());
            }
        }
    }

    private static CrackTier tierForCharge(int chargeTicks) {
        if (chargeTicks >= 40) {
            return TIERS[2];
        }
        if (chargeTicks >= 20) {
            return TIERS[1];
        }
        return TIERS[0];
    }

    private static CrackTier tierForStrength(int strengthLevel) {
        return TIERS[Mth.clamp(strengthLevel, 1, TIERS.length) - 1];
    }

    private static BlockPos findGround(ServerLevel level, int x, int z, int expectedY) {
        BlockPos bestPos = null;
        int bestDistance = Integer.MAX_VALUE;
        boolean bestIsAbove = false;

        int minY = Math.max(level.getMinBuildHeight() + 1, expectedY - GROUND_SEARCH_BELOW);
        int maxY = Math.min(level.getMaxBuildHeight() - 2, expectedY + GROUND_SEARCH_ABOVE);
        for (int y = minY; y <= maxY; y++) {
            BlockPos candidate = new BlockPos(x, y, z);
            if (!isValidGround(level, candidate)) {
                continue;
            }

            int distance = Math.abs(y - expectedY);
            boolean isAbove = y >= expectedY;
            if (distance < bestDistance || distance == bestDistance && isAbove && !bestIsAbove) {
                bestPos = candidate.immutable();
                bestDistance = distance;
                bestIsAbove = isAbove;
            }
        }

        return bestPos;
    }

    private static boolean isValidGround(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !level.isLoaded(pos.above())) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        if (!state.isFaceSturdy(level, pos, Direction.UP) || !canRemoveBlock(level, pos, state)) {
            return false;
        }

        BlockState aboveState = level.getBlockState(pos.above());
        return aboveState.isAir() || aboveState.canBeReplaced();
    }

    private static boolean canRemoveBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()
                || !state.getFluidState().isEmpty()
                || state.hasBlockEntity()) {
            return false;
        }

        float destroySpeed = state.getDestroySpeed(level, pos);
        if (destroySpeed < 0.0F || destroySpeed > 20.0F) {
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

    private static boolean tryRemoveBlock(
            ServerLevel level,
            BlockPos pos,
            long activationId,
            int restoreTick,
            boolean playBreakEffect) {
        if (!level.isLoaded(pos)) {
            return false;
        }

        TrackedBlockKey key = new TrackedBlockKey(level.dimension(), pos.immutable());
        RemovedBlockState existingState = REMOVED_BLOCKS.get(key);
        BlockState currentState = level.getBlockState(pos);
        if (existingState != null && currentState.isAir()) {
            existingState.setOwnerActivationId(activationId);
            existingState.setRestoreTick(Math.max(existingState.restoreTick(), restoreTick));
            return true;
        }

        if (!canRemoveBlock(level, pos, currentState)) {
            return false;
        }

        REMOVED_BLOCKS.put(key, new RemovedBlockState(currentState, activationId, restoreTick));
        if (playBreakEffect) {
            level.levelEvent(2001, pos, Block.getId(currentState));
        }
        level.removeBlock(pos, false);
        return true;
    }

    private static boolean hasLivingEntityInBlock(ServerLevel level, BlockPos pos) {
        AABB bounds = new AABB(pos).deflate(0.01D);
        for (LivingEntity livingEntity : level.getEntitiesOfClass(LivingEntity.class, bounds)) {
            if (livingEntity.isAlive() && !livingEntity.isSpectator()) {
                return true;
            }
        }
        return false;
    }

    private static final class CrackActivation {
        private final long id;
        private final ResourceKey<Level> dimension;
        private final UUID ownerId;
        private final Vec3 origin;
        private final Vec3 forward;
        private final Vec3 right;
        private final CrackTier originalTier;
        private final int startedTick;
        private final Set<UUID> damagedEntities = new HashSet<>();
        private int expectedGroundY;
        private int nextStep = 1;
        private int strengthLevel;
        private int consecutiveGroundFailures;

        private CrackActivation(
                long id,
                ResourceKey<Level> dimension,
                UUID ownerId,
                Vec3 origin,
                Vec3 forward,
                CrackTier originalTier,
                int expectedGroundY,
                int startedTick) {
            this.id = id;
            this.dimension = dimension;
            this.ownerId = ownerId;
            this.origin = origin;
            this.forward = forward;
            this.right = new Vec3(-forward.z, 0.0D, forward.x);
            this.originalTier = originalTier;
            this.expectedGroundY = expectedGroundY;
            this.startedTick = startedTick;
            this.strengthLevel = originalTier.level();
        }

        private ResourceKey<Level> dimension() {
            return this.dimension;
        }

        private boolean tick(ServerLevel level, int currentTick) {
            if (this.nextStep > this.originalTier.range()) {
                return true;
            }

            Vec3 center = this.origin.add(this.forward.scale(this.nextStep));
            BlockPos centerGround = findGround(level, Mth.floor(center.x), Mth.floor(center.z), this.expectedGroundY);
            if (centerGround == null) {
                this.consecutiveGroundFailures++;
                this.strengthLevel--;
                this.nextStep++;
                return this.consecutiveGroundFailures >= MAX_CONSECUTIVE_GROUND_FAILURES || this.strengthLevel <= 0;
            }

            this.consecutiveGroundFailures = 0;
            this.expectedGroundY = centerGround.getY();
            this.openLayer(level, center, centerGround, currentTick);
            this.nextStep++;
            return false;
        }

        private void openLayer(ServerLevel level, Vec3 center, BlockPos centerGround, int currentTick) {
            CrackTier effectiveTier = tierForStrength(this.strengthLevel);
            int radius = this.radiusForCurrentStep(effectiveTier);
            int restoreTick = currentTick + RESTORE_DELAY_TICKS;
            List<BlockPos> openedGroundCells = new ArrayList<>();

            for (int offset = -radius; offset <= radius; offset++) {
                Vec3 lateral = center.add(this.right.scale(offset));
                BlockPos ground = offset == 0
                        ? centerGround
                        : findGround(level, Mth.floor(lateral.x), Mth.floor(lateral.z), centerGround.getY());
                if (ground == null) {
                    continue;
                }

                int cellDepth = Math.max(1, effectiveTier.depth() - Math.abs(offset));
                BlockState aboveState = level.getBlockState(ground.above());
                if (!aboveState.isAir() && aboveState.canBeReplaced()) {
                    tryRemoveBlock(level, ground.above(), this.id, restoreTick, false);
                }

                boolean openedAny = false;
                for (int depth = 0; depth < cellDepth; depth++) {
                    BlockPos removePos = ground.below(depth);
                    openedAny |= tryRemoveBlock(level, removePos, this.id, restoreTick, depth == 0);
                }

                if (openedAny) {
                    openedGroundCells.add(ground);
                }
            }

            if (openedGroundCells.isEmpty()) {
                return;
            }

            spawnLayerParticles(level, openedGroundCells);
            damageEntities(level, openedGroundCells, effectiveTier.damage());
        }

        private int radiusForCurrentStep(CrackTier effectiveTier) {
            int maxRadius = effectiveTier.width() / 2;
            int taperStartStep = Math.max(1, effectiveTier.range() / 2);
            if (this.nextStep <= taperStartStep || maxRadius <= 0) {
                return maxRadius;
            }

            int taperLength = Math.max(1, effectiveTier.range() - taperStartStep);
            int taperProgress = Mth.clamp(this.nextStep - taperStartStep, 0, taperLength);
            float remainingRatio = 1.0F - taperProgress / (float) taperLength;
            return Mth.clamp(Mth.ceil(maxRadius * remainingRatio), 0, maxRadius);
        }

        private void damageEntities(ServerLevel level, List<BlockPos> groundCells, float damage) {
            AABB bounds = null;
            for (BlockPos pos : groundCells) {
                AABB cellBounds = new AABB(pos).expandTowards(0.0D, LAYER_DAMAGE_HEIGHT, 0.0D);
                bounds = bounds == null ? cellBounds : bounds.minmax(cellBounds);
            }

            if (bounds == null) {
                return;
            }

            Entity owner = level.getEntity(this.ownerId);
            for (LivingEntity livingEntity : level.getEntitiesOfClass(LivingEntity.class, bounds.inflate(0.2D))) {
                if (!livingEntity.isAlive()
                        || livingEntity.getUUID().equals(this.ownerId)
                        || !this.damagedEntities.add(livingEntity.getUUID())) {
                    continue;
                }

                livingEntity.invulnerableTime = 0;
                livingEntity.hurtTime = 0;
                if (owner instanceof Player player) {
                    livingEntity.hurt(level.damageSources().source(ModDamageTypes.EARTHBREAKER_CRACK, player), damage);
                } else {
                    livingEntity.hurt(level.damageSources().source(ModDamageTypes.EARTHBREAKER_CRACK), damage);
                }
            }
        }

        private void spawnLayerParticles(ServerLevel level, List<BlockPos> groundCells) {
            BlockPos center = groundCells.get(groundCells.size() / 2);
            BlockState particleState = level.getBlockState(center.below());
            if (particleState.isAir()) {
                particleState = Blocks.DIRT.defaultBlockState();
            }

            level.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, particleState),
                    center.getX() + 0.5D,
                    center.getY() + 0.35D,
                    center.getZ() + 0.5D,
                    12,
                    Math.max(0.2D, groundCells.size() * 0.15D),
                    0.2D,
                    Math.max(0.2D, groundCells.size() * 0.15D),
                    0.08D);

            if ((this.nextStep + this.startedTick) % 3 == 0) {
                level.playSound(
                        null,
                        center,
                        SoundEvents.ANCIENT_DEBRIS_BREAK,
                        SoundSource.BLOCKS,
                        0.5F,
                        0.75F + level.random.nextFloat() * 0.15F);
            }
        }
    }

    private record CrackTier(int level, int range, int width, int depth, float damage) {
    }

    private record TrackedBlockKey(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private static final class RemovedBlockState {
        private final BlockState originalState;
        private long ownerActivationId;
        private int restoreTick;

        private RemovedBlockState(BlockState originalState, long ownerActivationId, int restoreTick) {
            this.originalState = originalState;
            this.ownerActivationId = ownerActivationId;
            this.restoreTick = restoreTick;
        }

        private BlockState originalState() {
            return this.originalState;
        }

        private int restoreTick() {
            return this.restoreTick;
        }

        private void setOwnerActivationId(long ownerActivationId) {
            this.ownerActivationId = ownerActivationId;
        }

        private void setRestoreTick(int restoreTick) {
            this.restoreTick = restoreTick;
        }
    }
}
