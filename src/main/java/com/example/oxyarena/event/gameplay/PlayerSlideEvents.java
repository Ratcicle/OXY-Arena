package com.example.oxyarena.event.gameplay;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.example.oxyarena.animation.ModPlayerAnimations;
import com.example.oxyarena.network.PlayerAnimationPlayPayload;
import com.example.oxyarena.network.PlayerAnimationStopPayload;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PlayerSlideEvents {
    private static final int SLIDE_DURATION_TICKS = 14;
    private static final int SLIDE_COOLDOWN_TICKS = 20;
    private static final int AIR_GRACE_TICKS = 3;
    private static final double SLIDE_INITIAL_SPEED = 0.86D;
    private static final double SLIDE_MIN_SPEED = 0.18D;
    private static final double SLIDE_FRICTION = 0.87D;
    private static final double MIN_DIRECTION_LENGTH_SQR = 0.0025D;
    private static final Map<UUID, SlideState> STATES = new HashMap<>();

    private PlayerSlideEvents() {
    }

    public static void handleInput(ServerPlayer player, boolean slideKeyDown, boolean movementKeyDown) {
        if (player.getServer() == null) {
            return;
        }

        boolean wasTracked = STATES.containsKey(player.getUUID());
        if (!slideKeyDown && !wasTracked) {
            return;
        }

        SlideState state = STATES.computeIfAbsent(player.getUUID(), ignored -> new SlideState());
        state.keyDown = slideKeyDown;
        state.movementKeyDown = movementKeyDown;
        if (!slideKeyDown) {
            if (state.mode == SlideMode.CRAWLING) {
                stopLowMovementAnimation(player, state);
            }
            if (state.mode == SlideMode.CRAWLING && canStandUp(player)) {
                clearPlayer(player);
            }
            return;
        }

        if (!canUseLowMovement(player) || !canFitPose(player, Pose.SWIMMING)) {
            clearPlayer(player);
            return;
        }

        if (state.mode == SlideMode.SLIDING) {
            return;
        }

        if (wasTracked && state.mode == SlideMode.CRAWLING) {
            startCrawling(player, state);
            return;
        }

        int currentTick = player.getServer().getTickCount();
        if (canStartSlide(player, state, currentTick)) {
            startSlide(player, state, currentTick);
            return;
        }

        startCrawling(player, state);
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int currentTick = server.getTickCount();
        Iterator<Map.Entry<UUID, SlideState>> iterator = STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SlideState> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }

            if (!tickPlayer(player, entry.getValue(), currentTick)) {
                iterator.remove();
            }
        }
    }

    public static void clearPlayer(ServerPlayer player) {
        SlideState state = STATES.remove(player.getUUID());
        if (state != null) {
            stopLowMovementAnimation(player, state);
            clearForcedPose(player, state);
        }
    }

    public static void clearAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            SlideState state = STATES.get(player.getUUID());
            if (state != null) {
                stopLowMovementAnimation(player, state);
                clearForcedPose(player, state);
            }
        }
        STATES.clear();
    }

    public static boolean isActive(ServerPlayer player) {
        return STATES.containsKey(player.getUUID());
    }

    private static boolean tickPlayer(ServerPlayer player, SlideState state, int currentTick) {
        if (!canUseLowMovement(player) || !canFitPose(player, Pose.SWIMMING)) {
            stopLowMovementAnimation(player, state);
            clearForcedPose(player, state);
            return false;
        }

        forceLowPose(player, state);
        if (state.mode == SlideMode.SLIDING) {
            tickSlide(player, state, currentTick);
        }

        if (state.mode == SlideMode.CRAWLING && !state.keyDown && canStandUp(player)) {
            stopLowMovementAnimation(player, state);
            clearForcedPose(player, state);
            return false;
        }

        return true;
    }

    private static void tickSlide(ServerPlayer player, SlideState state, int currentTick) {
        if (player.onGround()) {
            state.airborneTicks = 0;
        } else {
            state.airborneTicks++;
        }

        if (currentTick >= state.slideEndTick
                || state.airborneTicks > AIR_GRACE_TICKS
                || state.slideSpeed < SLIDE_MIN_SPEED) {
            finishSlide(player, state);
            return;
        }

        Vec3 currentVelocity = player.getDeltaMovement();
        Vec3 slideVelocity = state.slideDirection.scale(state.slideSpeed);
        player.setDeltaMovement(slideVelocity.x, currentVelocity.y, slideVelocity.z);
        player.hasImpulse = true;
        player.hurtMarked = true;
        state.slideSpeed *= SLIDE_FRICTION;
    }

    private static void finishSlide(ServerPlayer player, SlideState state) {
        Vec3 currentVelocity = player.getDeltaMovement();
        player.setDeltaMovement(currentVelocity.x * 0.45D, currentVelocity.y, currentVelocity.z * 0.45D);
        player.hasImpulse = true;
        player.hurtMarked = true;

        if (state.keyDown || !canStandUp(player)) {
            state.mode = SlideMode.CRAWLING;
            state.slideSpeed = 0.0D;
            state.airborneTicks = 0;
            updateCrawlAnimation(player, state);
            return;
        }

        clearPlayer(player);
    }

    private static void startSlide(ServerPlayer player, SlideState state, int currentTick) {
        state.mode = SlideMode.SLIDING;
        state.slideDirection = getSlideDirection(player);
        state.slideSpeed = SLIDE_INITIAL_SPEED;
        state.slideEndTick = currentTick + SLIDE_DURATION_TICKS;
        state.cooldownUntilTick = currentTick + SLIDE_DURATION_TICKS + SLIDE_COOLDOWN_TICKS;
        state.airborneTicks = 0;
        forceLowPose(player, state);
        playLowMovementAnimation(player, state, ModPlayerAnimations.PLAYER_SLIDE, SlideAnimation.SLIDE);
    }

    private static void startCrawling(ServerPlayer player, SlideState state) {
        state.mode = SlideMode.CRAWLING;
        state.slideSpeed = 0.0D;
        state.airborneTicks = 0;
        forceLowPose(player, state);
        updateCrawlAnimation(player, state);
    }

    private static boolean canStartSlide(ServerPlayer player, SlideState state, int currentTick) {
        return currentTick >= state.cooldownUntilTick
                && player.onGround()
                && player.isSprinting()
                && getSlideDirection(player).lengthSqr() > MIN_DIRECTION_LENGTH_SQR;
    }

    private static boolean canUseLowMovement(ServerPlayer player) {
        return player.isAlive()
                && !player.isSpectator()
                && !player.getAbilities().flying
                && !player.isPassenger()
                && !player.isFallFlying()
                && !player.isInWaterOrBubble()
                && !PlayerMantleEvents.isMantling(player);
    }

    private static boolean canStandUp(ServerPlayer player) {
        Pose targetPose = player.isShiftKeyDown() ? Pose.CROUCHING : Pose.STANDING;
        return canFitPose(player, targetPose);
    }

    private static boolean canFitPose(ServerPlayer player, Pose pose) {
        AABB box = player.getDimensions(pose).makeBoundingBox(player.position()).deflate(1.0E-7D);
        return player.level().noCollision(player, box);
    }

    private static Vec3 getSlideDirection(ServerPlayer player) {
        Vec3 velocity = player.getDeltaMovement();
        Vec3 horizontalVelocity = new Vec3(velocity.x, 0.0D, velocity.z);
        if (horizontalVelocity.lengthSqr() > MIN_DIRECTION_LENGTH_SQR) {
            return horizontalVelocity.normalize();
        }

        Vec3 look = player.getLookAngle();
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() > MIN_DIRECTION_LENGTH_SQR) {
            return horizontalLook.normalize();
        }

        return Vec3.ZERO;
    }

    private static void forceLowPose(ServerPlayer player, SlideState state) {
        if (!state.forcedPose) {
            player.setForcedPose(Pose.SWIMMING);
            state.forcedPose = true;
        }
        if (!player.hasPose(Pose.SWIMMING)) {
            player.setPose(Pose.SWIMMING);
            player.refreshDimensions();
        }
    }

    private static void clearForcedPose(ServerPlayer player, SlideState state) {
        if (!state.forcedPose) {
            return;
        }

        player.setForcedPose(null);
        if (canStandUp(player)) {
            player.setPose(player.isShiftKeyDown() ? Pose.CROUCHING : Pose.STANDING);
            player.refreshDimensions();
        }
        state.forcedPose = false;
    }

    private static void updateCrawlAnimation(ServerPlayer player, SlideState state) {
        if (state.keyDown && state.movementKeyDown) {
            if (state.activeAnimation != SlideAnimation.CRAWL) {
                playLowMovementAnimation(player, state, ModPlayerAnimations.PLAYER_CRAWL, SlideAnimation.CRAWL);
            }
            return;
        }

        if (state.activeAnimation != SlideAnimation.NONE) {
            stopLowMovementAnimation(player, state);
        }
    }

    private static void playLowMovementAnimation(
            ServerPlayer player,
            SlideState state,
            ResourceLocation animationId,
            SlideAnimation activeAnimation) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new PlayerAnimationPlayPayload(player.getUUID(), animationId));
        state.activeAnimation = activeAnimation;
    }

    private static void stopLowMovementAnimation(ServerPlayer player, SlideState state) {
        if (state.activeAnimation == SlideAnimation.NONE) {
            return;
        }

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new PlayerAnimationStopPayload(player.getUUID()));
        state.activeAnimation = SlideAnimation.NONE;
    }

    private enum SlideMode {
        CRAWLING,
        SLIDING
    }

    private enum SlideAnimation {
        NONE,
        SLIDE,
        CRAWL
    }

    private static final class SlideState {
        private boolean keyDown;
        private boolean movementKeyDown;
        private boolean forcedPose;
        private SlideMode mode = SlideMode.CRAWLING;
        private SlideAnimation activeAnimation = SlideAnimation.NONE;
        private int slideEndTick;
        private int cooldownUntilTick;
        private int airborneTicks;
        private double slideSpeed;
        private Vec3 slideDirection = Vec3.ZERO;
    }
}
