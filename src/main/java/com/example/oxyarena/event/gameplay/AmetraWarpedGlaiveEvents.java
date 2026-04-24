package com.example.oxyarena.event.gameplay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.oxyarena.animation.ModPlayerAnimations;
import com.example.oxyarena.entity.effect.DimensionalRiftEntity;
import com.example.oxyarena.entity.projectile.DimensionalRiftProjectile;
import com.example.oxyarena.network.PlayerAnimationPlayPayload;
import com.example.oxyarena.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class AmetraWarpedGlaiveEvents {
    private static final int MARK_DURATION_TICKS = 200;
    private static final int SLASH_DELAY_TICKS = 10;
    private static final double RIFT_DISTANCE = 2.2D;
    private static final double TELEPORT_FORWARD_DISTANCE = 1.4D;
    private static final double SLASH_RADIUS = 3.0D;
    private static final float SLASH_DAMAGE = 12.0F;
    private static final float PROJECTILE_POWER = 2.75F;
    private static final float PROJECTILE_INACCURACY = 0.0F;

    private static final Map<UUID, UUID> ACTIVE_RIFTS_BY_OWNER = new HashMap<>();
    private static final Map<UUID, MarkState> MARKS_BY_PLAYER = new HashMap<>();
    private static final List<PendingSlash> PENDING_SLASHES = new ArrayList<>();

    private AmetraWarpedGlaiveEvents() {
    }

    public static boolean createRift(ServerPlayer player) {
        if (!player.isAlive() || !isHoldingGlaive(player)) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        discardActiveRift(player.getServer(), player.getUUID());

        Vec3 direction = getHorizontalLookDirection(player);
        Vec3 position = player.position().add(direction.scale(RIFT_DISTANCE));
        DimensionalRiftEntity rift = new DimensionalRiftEntity(level, player, position, player.getYRot());
        if (!level.addFreshEntity(rift)) {
            return false;
        }

        ACTIVE_RIFTS_BY_OWNER.put(player.getUUID(), rift.getUUID());
        playAnimation(player, ModPlayerAnimations.AMETRA_WARPED_GLAIVE_RIFT_CUT);
        player.swing(InteractionHand.MAIN_HAND, true);
        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                position.x,
                position.y + rift.getBbHeight() * 0.5D,
                position.z,
                28,
                0.28D,
                0.55D,
                0.28D,
                0.025D);
        level.playSound(
                null,
                position.x,
                position.y,
                position.z,
                SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS,
                0.8F,
                0.65F);
        return true;
    }

    public static boolean activateMarkedTeleport(ServerPlayer player) {
        if (!player.isAlive() || !isHoldingGlaive(player)) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        int currentTick = level.getServer().getTickCount();
        UUID playerId = player.getUUID();
        MarkState mark = MARKS_BY_PLAYER.get(playerId);
        if (mark == null || mark.expiresAtTick() <= currentTick || !mark.dimension().equals(level.dimension())) {
            MARKS_BY_PLAYER.remove(playerId);
            return false;
        }

        Entity targetEntity = level.getEntity(mark.targetId());
        if (!(targetEntity instanceof LivingEntity target) || !target.isAlive()) {
            MARKS_BY_PLAYER.remove(playerId);
            return false;
        }

        Vec3 teleportPosition = findSafeTeleportPosition(player, target);
        if (teleportPosition == null) {
            return false;
        }

        MARKS_BY_PLAYER.remove(playerId);
        Vec3 origin = player.position();
        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                origin.x,
                origin.y + player.getBbHeight() * 0.5D,
                origin.z,
                32,
                0.4D,
                0.7D,
                0.4D,
                0.03D);

        player.teleportTo(
                level,
                teleportPosition.x,
                teleportPosition.y,
                teleportPosition.z,
                yawToward(teleportPosition, target.getBoundingBox().getCenter()),
                player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.swing(InteractionHand.MAIN_HAND, true);

        level.sendParticles(
                ParticleTypes.PORTAL,
                teleportPosition.x,
                teleportPosition.y + player.getBbHeight() * 0.5D,
                teleportPosition.z,
                48,
                0.45D,
                0.8D,
                0.45D,
                0.08D);
        level.playSound(
                null,
                teleportPosition.x,
                teleportPosition.y,
                teleportPosition.z,
                SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS,
                0.9F,
                1.45F);

        PENDING_SLASHES.add(new PendingSlash(playerId, level.dimension(), currentTick + SLASH_DELAY_TICKS));
        return true;
    }

    public static boolean onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof DimensionalRiftEntity rift)) {
            return false;
        }

        event.setCanceled(true);
        if (!player.isAlive() || player.isSpectator() || !(rift.level() instanceof ServerLevel level)) {
            return true;
        }

        fireProjectile(level, player, rift);
        return true;
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int currentTick = server.getTickCount();
        MARKS_BY_PLAYER.entrySet().removeIf(entry -> entry.getValue().expiresAtTick() <= currentTick);
        tickPendingSlashes(server, currentTick);
    }

    public static void markTarget(ServerLevel level, ServerPlayer player, LivingEntity target) {
        if (target == player || !target.isAlive()) {
            return;
        }

        int currentTick = level.getServer().getTickCount();
        MARKS_BY_PLAYER.put(
                player.getUUID(),
                new MarkState(target.getUUID(), level.dimension(), currentTick + MARK_DURATION_TICKS));
        level.sendParticles(
                ParticleTypes.PORTAL,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.62D,
                target.getZ(),
                24,
                0.3D,
                0.45D,
                0.3D,
                0.04D);
        level.playSound(
                null,
                target.getX(),
                target.getY(),
                target.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS,
                0.65F,
                1.7F);
    }

    public static void unregisterRift(UUID ownerId, UUID riftId) {
        ACTIVE_RIFTS_BY_OWNER.remove(ownerId, riftId);
    }

    public static void clearPlayer(ServerPlayer player) {
        UUID playerId = player.getUUID();
        MARKS_BY_PLAYER.remove(playerId);
        PENDING_SLASHES.removeIf(slash -> playerId.equals(slash.playerId()));
        discardActiveRift(player.getServer(), playerId);
    }

    public static void clearTarget(LivingEntity target) {
        UUID targetId = target.getUUID();
        MARKS_BY_PLAYER.entrySet().removeIf(entry -> targetId.equals(entry.getValue().targetId()));
    }

    public static void clearAll(MinecraftServer server) {
        for (UUID riftId : List.copyOf(ACTIVE_RIFTS_BY_OWNER.values())) {
            discardRift(server, riftId);
        }
        ACTIVE_RIFTS_BY_OWNER.clear();
        MARKS_BY_PLAYER.clear();
        PENDING_SLASHES.clear();
    }

    private static void fireProjectile(ServerLevel level, ServerPlayer player, DimensionalRiftEntity rift) {
        Vec3 direction = player.getLookAngle();
        if (direction.lengthSqr() < 1.0E-7D) {
            direction = getHorizontalLookDirection(player);
        } else {
            direction = direction.normalize();
        }

        Vec3 origin = rift.getBoundingBox().getCenter().add(direction.scale(0.7D));
        DimensionalRiftProjectile projectile = new DimensionalRiftProjectile(level, player, player.getMainHandItem());
        projectile.setPos(origin);
        projectile.shoot(direction.x, direction.y, direction.z, PROJECTILE_POWER, PROJECTILE_INACCURACY);
        level.addFreshEntity(projectile);
        player.swing(InteractionHand.MAIN_HAND, true);

        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                origin.x,
                origin.y,
                origin.z,
                14,
                0.18D,
                0.18D,
                0.18D,
                0.02D);
        level.playSound(
                null,
                origin.x,
                origin.y,
                origin.z,
                SoundEvents.AMETHYST_BLOCK_HIT,
                SoundSource.PLAYERS,
                0.7F,
                1.35F);
    }

    private static void tickPendingSlashes(MinecraftServer server, int currentTick) {
        for (Iterator<PendingSlash> iterator = PENDING_SLASHES.iterator(); iterator.hasNext();) {
            PendingSlash slash = iterator.next();
            if (slash.executeAtTick() > currentTick) {
                continue;
            }

            iterator.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(slash.playerId());
            if (player == null
                    || !player.isAlive()
                    || !player.level().dimension().equals(slash.dimension())
                    || !isHoldingGlaive(player)) {
                continue;
            }

            applyCircularSlash(player);
        }
    }

    private static void applyCircularSlash(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        playAnimation(player, ModPlayerAnimations.AMETRA_WARPED_GLAIVE_CIRCULAR_SLASH);
        level.sendParticles(
                ParticleTypes.SWEEP_ATTACK,
                player.getX(),
                player.getY() + player.getBbHeight() * 0.55D,
                player.getZ(),
                12,
                0.85D,
                0.35D,
                0.85D,
                0.0D);

        AABB area = player.getBoundingBox().inflate(SLASH_RADIUS);
        Vec3 center = player.getBoundingBox().getCenter();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, target -> isSlashTarget(player, target))) {
            if (target.getBoundingBox().getCenter().distanceToSqr(center) > SLASH_RADIUS * SLASH_RADIUS) {
                continue;
            }

            target.invulnerableTime = 0;
            target.hurtTime = 0;
            target.hurt(player.damageSources().playerAttack(player), SLASH_DAMAGE);
        }

        player.sweepAttack();
        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS,
                1.0F,
                0.75F);
    }

    private static boolean isSlashTarget(Player player, LivingEntity target) {
        return target.isAlive()
                && !target.isRemoved()
                && target != player
                && !player.isAlliedTo(target)
                && !(target instanceof ArmorStand armorStand && armorStand.isMarker())
                && (!(target instanceof Player targetPlayer) || !targetPlayer.isSpectator());
    }

    private static Vec3 findSafeTeleportPosition(ServerPlayer player, LivingEntity target) {
        ServerLevel level = player.serverLevel();
        Vec3 forward = getHorizontalTargetDirection(target);
        double[] yawOffsets = new double[] { 0.0D, 25.0D, -25.0D, 50.0D, -50.0D, 75.0D, -75.0D };
        double[] yOffsets = new double[] { 0.0D, 0.5D, 1.0D, -0.5D };

        for (double yawOffset : yawOffsets) {
            Vec3 direction = rotateHorizontal(forward, yawOffset);
            for (double yOffset : yOffsets) {
                Vec3 candidate = new Vec3(
                        target.getX() + direction.x * TELEPORT_FORWARD_DISTANCE,
                        target.getY() + yOffset,
                        target.getZ() + direction.z * TELEPORT_FORWARD_DISTANCE);
                if (canOccupy(level, player, candidate)) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private static boolean canOccupy(ServerLevel level, ServerPlayer player, Vec3 position) {
        BlockPos blockPos = BlockPos.containing(position);
        if (!level.isLoaded(blockPos) || !level.getWorldBorder().isWithinBounds(blockPos)) {
            return false;
        }

        AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(position).deflate(1.0E-7D);
        return level.noCollision(player, box);
    }

    private static Vec3 getHorizontalLookDirection(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() >= 1.0E-7D) {
            return horizontal.normalize();
        }

        float yawRadians = player.getYRot() * ((float)Math.PI / 180.0F);
        return new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians)).normalize();
    }

    private static Vec3 getHorizontalTargetDirection(LivingEntity target) {
        Vec3 look = target.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() >= 1.0E-7D) {
            return horizontal.normalize();
        }

        float yawRadians = target.yBodyRot * ((float)Math.PI / 180.0F);
        return new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians)).normalize();
    }

    private static Vec3 rotateHorizontal(Vec3 direction, double degrees) {
        double radians = degrees * Math.PI / 180.0D;
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(
                direction.x * cos - direction.z * sin,
                0.0D,
                direction.x * sin + direction.z * cos).normalize();
    }

    private static float yawToward(Vec3 origin, Vec3 target) {
        Vec3 toTarget = target.subtract(origin);
        return (float)(Math.atan2(toTarget.x, toTarget.z) * (180.0D / Math.PI));
    }

    private static boolean isHoldingGlaive(Player player) {
        return player.getMainHandItem().is(ModItems.AMETRA_WARPED_GLAIVE.get());
    }

    private static void playAnimation(ServerPlayer player, net.minecraft.resources.ResourceLocation animationId) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new PlayerAnimationPlayPayload(player.getUUID(), animationId));
    }

    private static void discardActiveRift(MinecraftServer server, UUID ownerId) {
        UUID riftId = ACTIVE_RIFTS_BY_OWNER.remove(ownerId);
        if (server == null || riftId == null) {
            return;
        }

        discardRift(server, riftId);
    }

    private static void discardRift(MinecraftServer server, UUID riftId) {
        if (server == null) {
            return;
        }

        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(riftId);
            if (entity instanceof DimensionalRiftEntity rift) {
                rift.discard();
                return;
            }
        }
    }

    private record MarkState(UUID targetId, ResourceKey<Level> dimension, int expiresAtTick) {
    }

    private record PendingSlash(UUID playerId, ResourceKey<Level> dimension, int executeAtTick) {
    }
}
