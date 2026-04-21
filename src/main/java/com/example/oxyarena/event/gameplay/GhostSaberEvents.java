package com.example.oxyarena.event.gameplay;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.example.oxyarena.animation.ModPlayerAnimations;
import com.example.oxyarena.entity.effect.GhostSaberEchoEntity;
import com.example.oxyarena.network.PlayerAnimationPlayPayload;
import com.example.oxyarena.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class GhostSaberEvents {
    private static final boolean ECHO_TEST_DISABLED = false;
    private static final int DASH_DURATION_TICKS = 6;
    private static final int ECHO_DELAY_TICKS = GhostSaberEchoEntity.ECHO_DELAY_TICKS;
    private static final int ECHO_DURATION_TICKS = GhostSaberEchoEntity.ECHO_DURATION_TICKS;
    private static final int ECHO_LINGER_TICKS = 6;
    private static final double DASH_RANGE = 6.0D;
    private static final double MIN_DASH_DISTANCE_SQR = 0.36D;
    private static final double PATH_STEP = 0.25D;
    private static final double HIT_RADIUS = 1.05D;
    private static final float DASH_DAMAGE = 7.5F;
    private static final float ECHO_DAMAGE = 5.0F;

    private static final List<DashState> ACTIVE_DASHES = new ArrayList<>();
    private static final List<PendingEcho> PENDING_ECHOES = new ArrayList<>();
    private static final List<ActiveEcho> ACTIVE_ECHOES = new ArrayList<>();
    private static final List<LingeringEcho> LINGERING_ECHOES = new ArrayList<>();
    private static final Set<UUID> GHOST_SABER_DAMAGE_ATTACKERS = new HashSet<>();

    private GhostSaberEvents() {
    }

    public static boolean activate(ServerPlayer player) {
        if (!player.isAlive() || player.getServer() == null || hasActiveDash(player)) {
            return false;
        }

        Vec3 origin = player.position();
        Vec3 direction = getDashDirection(player);
        Vec3 target = findDashTarget(player, origin, direction);
        if (target.distanceToSqr(origin) < MIN_DASH_DISTANCE_SQR) {
            return false;
        }

        int currentTick = player.getServer().getTickCount();
        UUID echoEntityId = null;
        if (!ECHO_TEST_DISABLED) {
            GhostSaberEchoEntity echoEntity = GhostSaberEchoEntity.spawn(
                    player.serverLevel(),
                    player.getUUID(),
                    origin,
                    target,
                    player.getYRot(),
                    player.getXRot());
            echoEntityId = echoEntity.getUUID();
        }
        DashState state = new DashState(
                player.getUUID(),
                player.level().dimension(),
                origin,
                target,
                player.getYRot(),
                player.getXRot(),
                echoEntityId,
                currentTick,
                player.isNoGravity());
        ACTIVE_DASHES.add(state);
        if (!ECHO_TEST_DISABLED) {
            PENDING_ECHOES.add(new PendingEcho(
                    player.getUUID(),
                    player.level().dimension(),
                    origin,
                    target,
                    player.getYRot(),
                    player.getXRot(),
                    echoEntityId,
                    currentTick + ECHO_DELAY_TICKS));
        }

        player.setNoGravity(true);
        player.resetFallDistance();
        playActivationFeedback(player);
        playPhantomSaberAnimation(player);
        return true;
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int currentTick = server.getTickCount();
        tickDashes(server, currentTick);
        tickPendingEchoes(server, currentTick);
        tickActiveEchoes(server, currentTick);
        tickLingeringEchoes(server, currentTick);
    }

    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || !isDashing(player)) {
            return;
        }

        event.setCanceled(true);
        player.resetFallDistance();
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || event.getEntity() == player
                || !GHOST_SABER_DAMAGE_ATTACKERS.contains(player.getUUID())) {
            return;
        }

        resetCooldown(player);
    }

    public static void clearPlayer(ServerPlayer player) {
        UUID playerId = player.getUUID();
        for (Iterator<DashState> iterator = ACTIVE_DASHES.iterator(); iterator.hasNext();) {
            DashState dash = iterator.next();
            if (playerId.equals(dash.ownerId())) {
                restoreGravity(player, dash);
                iterator.remove();
            }
        }

        for (Iterator<PendingEcho> iterator = PENDING_ECHOES.iterator(); iterator.hasNext();) {
            PendingEcho echo = iterator.next();
            if (playerId.equals(echo.ownerId())) {
                echo.discard(serverLevel(player.getServer(), echo.dimension()));
                iterator.remove();
            }
        }
        for (Iterator<ActiveEcho> iterator = ACTIVE_ECHOES.iterator(); iterator.hasNext();) {
            ActiveEcho echo = iterator.next();
            if (playerId.equals(echo.ownerId())) {
                echo.discard(serverLevel(player.getServer(), echo.dimension()));
                iterator.remove();
            }
        }
        for (Iterator<LingeringEcho> iterator = LINGERING_ECHOES.iterator(); iterator.hasNext();) {
            LingeringEcho echo = iterator.next();
            if (playerId.equals(echo.ownerId())) {
                echo.discard(serverLevel(player.getServer(), echo.dimension()));
                iterator.remove();
            }
        }
        GHOST_SABER_DAMAGE_ATTACKERS.remove(playerId);
    }

    public static void clearAll(MinecraftServer server) {
        for (DashState dash : ACTIVE_DASHES) {
            ServerPlayer player = server.getPlayerList().getPlayer(dash.ownerId());
            if (player != null) {
                restoreGravity(player, dash);
            }
        }
        ACTIVE_DASHES.clear();
        for (PendingEcho echo : PENDING_ECHOES) {
            echo.discard(serverLevel(server, echo.dimension()));
        }
        PENDING_ECHOES.clear();
        for (ActiveEcho echo : ACTIVE_ECHOES) {
            echo.discard(serverLevel(server, echo.dimension()));
        }
        ACTIVE_ECHOES.clear();
        for (LingeringEcho echo : LINGERING_ECHOES) {
            echo.discard(serverLevel(server, echo.dimension()));
        }
        LINGERING_ECHOES.clear();
        GHOST_SABER_DAMAGE_ATTACKERS.clear();
    }

    private static void tickDashes(MinecraftServer server, int currentTick) {
        for (Iterator<DashState> iterator = ACTIVE_DASHES.iterator(); iterator.hasNext();) {
            DashState dash = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(dash.ownerId());
            if (player == null || !player.isAlive() || !player.level().dimension().equals(dash.dimension())) {
                if (player != null) {
                    restoreGravity(player, dash);
                }
                iterator.remove();
                continue;
            }

            int elapsedTicks = Math.max(0, currentTick - dash.startTick() + 1);
            double progress = Math.min(1.0D, (double)elapsedTicks / DASH_DURATION_TICKS);
            Vec3 previousPosition = player.position();
            Vec3 nextPosition = dash.origin().lerp(dash.target(), smoothstep(progress));
            Vec3 movement = nextPosition.subtract(previousPosition);
            player.move(MoverType.SELF, movement);
            player.setDeltaMovement(movement);
            player.hasImpulse = true;
            player.hurtMarked = true;
            player.resetFallDistance();
            damageAlongSegment(player, previousPosition, player.position(), DASH_DAMAGE, dash.hitTargets());
            if (dash.entityId() != null) {
                retargetPendingEcho(dash.entityId(), player.position(), server);
            }

            if (progress >= 1.0D) {
                restoreGravity(player, dash);
                iterator.remove();
            }
        }
    }

    private static void tickPendingEchoes(MinecraftServer server, int currentTick) {
        for (Iterator<PendingEcho> iterator = PENDING_ECHOES.iterator(); iterator.hasNext();) {
            PendingEcho echo = iterator.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(echo.ownerId());
            ServerLevel level = serverLevel(server, echo.dimension());
            Entity existingEcho = level == null ? null : level.getEntity(echo.entityId());
            if (owner == null
                    || !owner.isAlive()
                    || level == null
                    || !owner.level().dimension().equals(echo.dimension())
                    || !(existingEcho instanceof GhostSaberEchoEntity ghostEcho)) {
                if (existingEcho != null) {
                    existingEcho.discard();
                }
                iterator.remove();
                continue;
            }

            ghostEcho.setPos(echo.origin().x, echo.origin().y, echo.origin().z);
            ghostEcho.setYRot(echo.yaw());
            ghostEcho.setXRot(echo.pitch());
            ghostEcho.setMoving(false);
            ghostEcho.setMoveProgress(0.0F);
            ghostEcho.hasImpulse = true;

            if (echo.executeAtTick() > currentTick) {
                continue;
            }

            ACTIVE_ECHOES.add(new ActiveEcho(
                    echo.ownerId(),
                    echo.dimension(),
                    echo.origin(),
                    echo.target(),
                    echo.yaw(),
                    echo.pitch(),
                    echo.entityId(),
                    currentTick,
                    new HashSet<>()));
            level.playSound(
                    null,
                    echo.origin().x,
                    echo.origin().y,
                    echo.origin().z,
                    SoundEvents.ILLUSIONER_CAST_SPELL,
                    SoundSource.PLAYERS,
                    0.55F,
                    1.75F);
            iterator.remove();
        }
    }

    private static void tickActiveEchoes(MinecraftServer server, int currentTick) {
        for (Iterator<ActiveEcho> iterator = ACTIVE_ECHOES.iterator(); iterator.hasNext();) {
            ActiveEcho echo = iterator.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(echo.ownerId());
            ServerLevel level = serverLevel(server, echo.dimension());
            Entity echoEntity = level == null ? null : level.getEntity(echo.entityId());
            if (owner == null
                    || !owner.isAlive()
                    || level == null
                    || !owner.level().dimension().equals(echo.dimension())
                    || !(echoEntity instanceof GhostSaberEchoEntity ghostEcho)) {
                if (echoEntity != null) {
                    echoEntity.discard();
                }
                iterator.remove();
                continue;
            }

            int elapsedTicks = Math.max(0, currentTick - echo.startTick() + 1);
            double progress = Math.min(1.0D, (double)elapsedTicks / ECHO_DURATION_TICKS);
            Vec3 previousPosition = ghostEcho.position();
            Vec3 nextPosition = echo.origin().lerp(echo.target(), smoothstep(progress));
            Vec3 movement = nextPosition.subtract(previousPosition);
            ghostEcho.move(MoverType.SELF, movement);
            ghostEcho.setDeltaMovement(movement);
            ghostEcho.setMoving(true);
            ghostEcho.setMoveProgress((float)progress);
            ghostEcho.setYRot(echo.yaw());
            ghostEcho.setXRot(echo.pitch());
            ghostEcho.hasImpulse = true;
            damageAlongSegment(owner, previousPosition, ghostEcho.position(), ECHO_DAMAGE, echo.hitTargets());

            if (progress >= 1.0D) {
                ghostEcho.setPos(echo.target().x, echo.target().y, echo.target().z);
                ghostEcho.setDeltaMovement(Vec3.ZERO);
                ghostEcho.setMoving(true);
                ghostEcho.setMoveProgress(1.0F);
                ghostEcho.hasImpulse = true;
                LINGERING_ECHOES.add(new LingeringEcho(
                        echo.ownerId(),
                        echo.dimension(),
                        echo.entityId(),
                        currentTick + ECHO_LINGER_TICKS));
                iterator.remove();
            }
        }
    }

    private static void tickLingeringEchoes(MinecraftServer server, int currentTick) {
        for (Iterator<LingeringEcho> iterator = LINGERING_ECHOES.iterator(); iterator.hasNext();) {
            LingeringEcho echo = iterator.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(echo.ownerId());
            ServerLevel level = serverLevel(server, echo.dimension());
            Entity echoEntity = level == null ? null : level.getEntity(echo.entityId());
            if (owner == null
                    || !owner.isAlive()
                    || level == null
                    || !owner.level().dimension().equals(echo.dimension())
                    || !(echoEntity instanceof GhostSaberEchoEntity ghostEcho)) {
                if (echoEntity != null) {
                    echoEntity.discard();
                }
                iterator.remove();
                continue;
            }

            ghostEcho.setMoving(true);
            ghostEcho.setMoveProgress(1.0F);
            ghostEcho.setDeltaMovement(Vec3.ZERO);
            if (currentTick >= echo.discardAtTick()) {
                ghostEcho.discard();
                iterator.remove();
            }
        }
    }

    private static void retargetPendingEcho(UUID entityId, Vec3 target, MinecraftServer server) {
        for (int index = 0; index < PENDING_ECHOES.size(); index++) {
            PendingEcho echo = PENDING_ECHOES.get(index);
            if (!entityId.equals(echo.entityId())) {
                continue;
            }

            ServerLevel level = serverLevel(server, echo.dimension());
            if (level != null && level.getEntity(echo.entityId()) instanceof GhostSaberEchoEntity ghostEcho) {
                ghostEcho.retarget(target);
            }
            PENDING_ECHOES.set(index, new PendingEcho(
                    echo.ownerId(),
                    echo.dimension(),
                    echo.origin(),
                    target,
                    echo.yaw(),
                    echo.pitch(),
                    echo.entityId(),
                    echo.executeAtTick()));
            return;
        }
    }

    private static boolean hasActiveDash(ServerPlayer player) {
        UUID playerId = player.getUUID();
        for (DashState dash : ACTIVE_DASHES) {
            if (playerId.equals(dash.ownerId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDashing(ServerPlayer player) {
        return hasActiveDash(player);
    }

    private static Vec3 getDashDirection(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() < 1.0E-6D) {
            return new Vec3(0.0D, 0.0D, 1.0D);
        }
        return horizontalLook.normalize();
    }

    private static Vec3 findDashTarget(ServerPlayer player, Vec3 origin, Vec3 direction) {
        Vec3 lastSafe = origin;
        int steps = (int)Math.ceil(DASH_RANGE / PATH_STEP);
        for (int step = 1; step <= steps; step++) {
            double distance = Math.min(DASH_RANGE, step * PATH_STEP);
            Vec3 candidate = origin.add(direction.scale(distance));
            if (!canOccupy(player, candidate)) {
                break;
            }
            lastSafe = candidate;
        }
        return lastSafe;
    }

    private static boolean canOccupy(ServerPlayer player, Vec3 position) {
        ServerLevel level = player.serverLevel();
        BlockPos blockPos = BlockPos.containing(position);
        if (!level.isLoaded(blockPos)) {
            return false;
        }

        AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(position).deflate(1.0E-7D);
        return level.noCollision(player, box);
    }

    private static void damageAlongSegment(
            Player attacker,
            Vec3 from,
            Vec3 to,
            float damage,
            Set<UUID> hitTargets) {
        if (!(attacker.level() instanceof ServerLevel level) || damage <= 0.0F) {
            return;
        }

        AABB searchBox = new AABB(from, to).inflate(HIT_RADIUS, 1.0D, HIT_RADIUS);
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class,
                searchBox,
                target -> target.isAlive()
                        && target != attacker
                        && !hitTargets.contains(target.getUUID())
                        && distanceToSegmentSqr(target.getBoundingBox().getCenter(), from, to) <= HIT_RADIUS * HIT_RADIUS);

        UUID attackerId = attacker.getUUID();
        GHOST_SABER_DAMAGE_ATTACKERS.add(attackerId);
        try {
            for (LivingEntity target : targets) {
                hitTargets.add(target.getUUID());
                target.invulnerableTime = 0;
                target.hurtTime = 0;
                target.hurt(attacker.damageSources().playerAttack(attacker), damage);
                if (!target.isAlive() || target.isDeadOrDying()) {
                    resetCooldown(attacker);
                }
            }
        } finally {
            GHOST_SABER_DAMAGE_ATTACKERS.remove(attackerId);
        }
    }

    private static double distanceToSegmentSqr(Vec3 point, Vec3 from, Vec3 to) {
        Vec3 segment = to.subtract(from);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-6D) {
            return point.distanceToSqr(from);
        }

        double progress = point.subtract(from).dot(segment) / lengthSqr;
        progress = Math.max(0.0D, Math.min(1.0D, progress));
        Vec3 closestPoint = from.add(segment.scale(progress));
        return point.distanceToSqr(closestPoint);
    }

    private static double smoothstep(double progress) {
        double clampedProgress = Math.max(0.0D, Math.min(1.0D, progress));
        return clampedProgress * clampedProgress * (3.0D - 2.0D * clampedProgress);
    }

    private static void resetCooldown(Player player) {
        player.getCooldowns().removeCooldown(ModItems.GHOST_SABER.get());
    }

    private static void playActivationFeedback(ServerPlayer player) {
        player.level().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS,
                0.8F,
                1.55F);
    }

    private static void playPhantomSaberAnimation(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new PlayerAnimationPlayPayload(player.getUUID(), ModPlayerAnimations.GHOST_SABER_PHANTOM_SABER_SLASH));
    }

    private static void restoreGravity(ServerPlayer player, DashState dash) {
        player.setNoGravity(dash.previousNoGravity());
        player.setDeltaMovement(Vec3.ZERO);
        player.hasImpulse = true;
        player.hurtMarked = true;
    }

    private static ServerLevel serverLevel(MinecraftServer server, ResourceKey<Level> dimension) {
        return server == null ? null : server.getLevel(dimension);
    }

    private record DashState(
            UUID ownerId,
            ResourceKey<Level> dimension,
            Vec3 origin,
            Vec3 target,
            float yaw,
            float pitch,
            UUID entityId,
            int startTick,
            boolean previousNoGravity,
            Set<UUID> hitTargets) {
        private DashState(
                UUID ownerId,
                ResourceKey<Level> dimension,
                Vec3 origin,
                Vec3 target,
                float yaw,
                float pitch,
                UUID entityId,
                int startTick,
                boolean previousNoGravity) {
            this(ownerId, dimension, origin, target, yaw, pitch, entityId, startTick, previousNoGravity, new HashSet<>());
        }
    }

    private record PendingEcho(
            UUID ownerId,
            ResourceKey<Level> dimension,
            Vec3 origin,
            Vec3 target,
            float yaw,
            float pitch,
            UUID entityId,
            int executeAtTick) {
        private void discard(ServerLevel level) {
            if (level != null && level.getEntity(this.entityId) instanceof GhostSaberEchoEntity echo) {
                echo.discard();
            }
        }
    }

    private record ActiveEcho(
            UUID ownerId,
            ResourceKey<Level> dimension,
            Vec3 origin,
            Vec3 target,
            float yaw,
            float pitch,
            UUID entityId,
            int startTick,
            Set<UUID> hitTargets) {
        private void discard(ServerLevel level) {
            if (level != null && level.getEntity(this.entityId) instanceof GhostSaberEchoEntity echo) {
                echo.discard();
            }
        }
    }

    private record LingeringEcho(
            UUID ownerId,
            ResourceKey<Level> dimension,
            UUID entityId,
            int discardAtTick) {
        private void discard(ServerLevel level) {
            if (level != null && level.getEntity(this.entityId) instanceof GhostSaberEchoEntity echo) {
                echo.discard();
            }
        }
    }
}
