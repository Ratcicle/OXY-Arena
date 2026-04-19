package com.example.oxyarena.event.gameplay;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.example.oxyarena.network.PlayerMantleInputPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class PlayerMantleEvents {
    private static final int MAX_HANG_TICKS = 60;
    private static final int COOLDOWN_TICKS = 10;
    private static final int GROUND_GRACE_TICKS = 4;
    private static final double WALL_SEARCH_MIN_DISTANCE = 0.42D;
    private static final double WALL_SEARCH_MID_DISTANCE = 0.68D;
    private static final double WALL_SEARCH_MAX_DISTANCE = 0.94D;
    private static final double HANG_WALL_OFFSET = 0.83D;
    private static final double HANG_Y_OFFSET = 0.72D;
    private static final double MAX_ANCHOR_DRIFT_SQR = 0.72D;
    private static final double CLIMB_JUMP_UP_SPEED = 0.58D;
    private static final double CLIMB_JUMP_FORWARD_SPEED = 0.34D;
    private static final Map<UUID, MantleState> HANGING = new HashMap<>();
    private static final Map<UUID, Integer> LAST_GROUND_TICK = new HashMap<>();
    private static final Map<UUID, Integer> COOLDOWN_UNTIL = new HashMap<>();

    private PlayerMantleEvents() {
    }

    public static void handleInput(ServerPlayer player, int action) {
        if (player.getServer() == null) {
            return;
        }

        MantleState state = HANGING.get(player.getUUID());
        if (action == PlayerMantleInputPayload.ACTION_SHIFT_PRESSED) {
            if (state != null) {
                release(player, state, true);
            }
            return;
        }

        if (action != PlayerMantleInputPayload.ACTION_JUMP_PRESSED) {
            return;
        }

        if (state != null) {
            launchClimbJump(player, state);
            return;
        }

        tryStartMantle(player);
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int currentTick = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.onGround()) {
                LAST_GROUND_TICK.put(player.getUUID(), currentTick);
            }
        }

        Iterator<Map.Entry<UUID, MantleState>> iterator = HANGING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, MantleState> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !tickMantleState(player, entry.getValue(), currentTick)) {
                if (player != null) {
                    restoreGravity(player, entry.getValue());
                }
                iterator.remove();
            }
        }

        COOLDOWN_UNTIL.entrySet().removeIf(entry -> entry.getValue().intValue() <= currentTick);
        LAST_GROUND_TICK.keySet().removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
    }

    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clearPlayer(player);
        }
    }

    public static boolean isMantling(ServerPlayer player) {
        return HANGING.containsKey(player.getUUID());
    }

    public static void clearPlayer(ServerPlayer player) {
        MantleState state = HANGING.remove(player.getUUID());
        if (state != null) {
            restoreGravity(player, state);
        }
        LAST_GROUND_TICK.remove(player.getUUID());
        COOLDOWN_UNTIL.remove(player.getUUID());
    }

    public static void clearAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            MantleState state = HANGING.get(player.getUUID());
            if (state != null) {
                restoreGravity(player, state);
            }
        }
        HANGING.clear();
        LAST_GROUND_TICK.clear();
        COOLDOWN_UNTIL.clear();
    }

    private static void tryStartMantle(ServerPlayer player) {
        int currentTick = player.getServer().getTickCount();
        if (!canUseMantle(player, currentTick)) {
            return;
        }

        MantleTarget target = findMantleTarget(player);
        if (target == null) {
            return;
        }

        MantleState state = new MantleState(
                target.direction,
                target.wallLowerPos,
                target.wallUpperPos,
                target.landingCenter,
                target.hangCenter,
                target.wallState,
                currentTick + MAX_HANG_TICKS,
                player.isNoGravity());
        HANGING.put(player.getUUID(), state);
        player.setNoGravity(true);
        player.resetFallDistance();
        lockPlayerToAnchor(player, state);
        spawnMantleFeedback(player.serverLevel(), state, 6, 0.03D, 0.7F);
    }

    private static boolean tickMantleState(ServerPlayer player, MantleState state, int currentTick) {
        if (!canStayHanging(player, state, currentTick)) {
            setCooldown(player, currentTick);
            return false;
        }

        lockPlayerToAnchor(player, state);
        return true;
    }

    private static void launchClimbJump(ServerPlayer player, MantleState state) {
        if (!isTargetStillValid(player, state) || !canFitStanding(player, state.landingCenter)) {
            release(player, state, true);
            return;
        }

        HANGING.remove(player.getUUID());
        restoreGravity(player, state);
        player.resetFallDistance();
        player.setDeltaMovement(
                state.direction.getStepX() * CLIMB_JUMP_FORWARD_SPEED,
                CLIMB_JUMP_UP_SPEED,
                state.direction.getStepZ() * CLIMB_JUMP_FORWARD_SPEED);
        player.hasImpulse = true;
        player.hurtMarked = true;
        setCooldown(player, player.getServer().getTickCount());
        spawnMantleFeedback(player.serverLevel(), state, 10, 0.04D, 0.95F);
    }

    private static void release(ServerPlayer player, MantleState state, boolean applyCooldown) {
        HANGING.remove(player.getUUID());
        restoreGravity(player, state);
        player.setDeltaMovement(Vec3.ZERO);
        player.hasImpulse = true;
        player.hurtMarked = true;
        if (applyCooldown && player.getServer() != null) {
            setCooldown(player, player.getServer().getTickCount());
        }
    }

    private static void lockPlayerToAnchor(ServerPlayer player, MantleState state) {
        player.teleportTo(state.hangCenter.x, state.hangCenter.y, state.hangCenter.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.hasImpulse = true;
        player.hurtMarked = true;
    }

    private static boolean canUseMantle(ServerPlayer player, int currentTick) {
        Integer cooldownUntil = COOLDOWN_UNTIL.get(player.getUUID());
        if (cooldownUntil != null && cooldownUntil.intValue() > currentTick) {
            return false;
        }

        Integer lastGroundTick = LAST_GROUND_TICK.get(player.getUUID());
        boolean recentlyGrounded = player.onGround()
                || (lastGroundTick != null && currentTick - lastGroundTick.intValue() <= GROUND_GRACE_TICKS);
        return recentlyGrounded
                && player.isAlive()
                && !player.isSpectator()
                && !player.getAbilities().flying
                && !player.isPassenger()
                && !player.isFallFlying()
                && !player.isInWaterOrBubble()
                && !PlayerSlideEvents.isActive(player);
    }

    private static boolean canStayHanging(ServerPlayer player, MantleState state, int currentTick) {
        return currentTick <= state.expireTick
                && player.isAlive()
                && !player.isSpectator()
                && !player.getAbilities().flying
                && !player.isPassenger()
                && !player.isFallFlying()
                && !player.isInWaterOrBubble()
                && player.position().distanceToSqr(state.hangCenter) <= MAX_ANCHOR_DRIFT_SQR
                && isTargetStillValid(player, state);
    }

    private static boolean isTargetStillValid(ServerPlayer player, MantleState state) {
        ServerLevel level = player.serverLevel();
        return isWallBlock(level, state.wallLowerPos, state.direction, player)
                && isWallBlock(level, state.wallUpperPos, state.direction, player)
                && canFitStanding(player, state.landingCenter);
    }

    private static MantleTarget findMantleTarget(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Direction direction = Direction.getNearest(look.x, 0.0D, look.z);
        if (direction.getAxis() == Direction.Axis.Y) {
            return null;
        }

        double feetY = player.getY() + 0.12D;
        double[] distances = { WALL_SEARCH_MIN_DISTANCE, WALL_SEARCH_MID_DISTANCE, WALL_SEARCH_MAX_DISTANCE };
        for (double distance : distances) {
            BlockPos wallLowerPos = BlockPos.containing(
                    player.getX() + direction.getStepX() * distance,
                    feetY,
                    player.getZ() + direction.getStepZ() * distance);
            MantleTarget target = validateMantleTarget(player, direction, wallLowerPos);
            if (target != null) {
                return target;
            }
        }

        BlockPos directWallPos = player.blockPosition().relative(direction);
        return validateMantleTarget(player, direction, directWallPos);
    }

    private static MantleTarget validateMantleTarget(ServerPlayer player, Direction direction, BlockPos wallLowerPos) {
        ServerLevel level = player.serverLevel();
        BlockPos wallUpperPos = wallLowerPos.above();
        BlockPos landingFeetPos = wallUpperPos.above();
        if (!level.isLoaded(wallLowerPos) || !level.isLoaded(wallUpperPos) || !level.isLoaded(landingFeetPos.above())) {
            return null;
        }

        if (!isWallBlock(level, wallLowerPos, direction, player) || !isWallBlock(level, wallUpperPos, direction, player)) {
            return null;
        }

        Vec3 landingCenter = Vec3.atBottomCenterOf(landingFeetPos);
        if (!canFitStanding(player, landingCenter)) {
            return null;
        }

        BlockState wallState = level.getBlockState(wallUpperPos);
        Vec3 wallCenter = Vec3.atCenterOf(wallLowerPos);
        Vec3 hangCenter = new Vec3(
                wallCenter.x - direction.getStepX() * HANG_WALL_OFFSET,
                wallLowerPos.getY() + HANG_Y_OFFSET,
                wallCenter.z - direction.getStepZ() * HANG_WALL_OFFSET);
        if (!level.noCollision(player, player.getBoundingBox().move(hangCenter.subtract(player.position())))) {
            return null;
        }

        return new MantleTarget(direction, wallLowerPos.immutable(), wallUpperPos.immutable(), landingCenter, hangCenter, wallState);
    }

    private static boolean isWallBlock(ServerLevel level, BlockPos pos, Direction direction, ServerPlayer player) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && state.isFaceSturdy(level, pos, direction.getOpposite())
                && state.isFaceSturdy(level, pos, Direction.UP)
                && !state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean canFitStanding(ServerPlayer player, Vec3 center) {
        AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(center).deflate(1.0E-7D);
        return player.level().noCollision(player, box);
    }

    private static void restoreGravity(ServerPlayer player, MantleState state) {
        player.setNoGravity(state.previousNoGravity);
    }

    private static void setCooldown(ServerPlayer player, int currentTick) {
        COOLDOWN_UNTIL.put(player.getUUID(), currentTick + COOLDOWN_TICKS);
    }

    private static void spawnMantleFeedback(ServerLevel level, MantleState state, int particleCount, double speed, float pitch) {
        Vec3 particlePos = state.hangCenter.add(
                state.direction.getStepX() * 0.18D,
                0.45D,
                state.direction.getStepZ() * 0.18D);
        level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, state.wallState),
                particlePos.x,
                particlePos.y,
                particlePos.z,
                particleCount,
                0.18D,
                0.24D,
                0.18D,
                speed);
        level.playSound(null, BlockPos.containing(particlePos), SoundEvents.LADDER_STEP, SoundSource.PLAYERS, 0.55F, pitch);
    }

    private record MantleTarget(
            Direction direction,
            BlockPos wallLowerPos,
            BlockPos wallUpperPos,
            Vec3 landingCenter,
            Vec3 hangCenter,
            BlockState wallState) {
    }

    private static final class MantleState {
        private final Direction direction;
        private final BlockPos wallLowerPos;
        private final BlockPos wallUpperPos;
        private final Vec3 landingCenter;
        private final Vec3 hangCenter;
        private final BlockState wallState;
        private final int expireTick;
        private final boolean previousNoGravity;

        private MantleState(
                Direction direction,
                BlockPos wallLowerPos,
                BlockPos wallUpperPos,
                Vec3 landingCenter,
                Vec3 hangCenter,
                BlockState wallState,
                int expireTick,
                boolean previousNoGravity) {
            this.direction = direction;
            this.wallLowerPos = wallLowerPos;
            this.wallUpperPos = wallUpperPos;
            this.landingCenter = landingCenter;
            this.hangCenter = hangCenter;
            this.wallState = wallState;
            this.expireTick = expireTick;
            this.previousNoGravity = previousNoGravity;
        }
    }
}
