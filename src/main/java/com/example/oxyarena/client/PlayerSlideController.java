package com.example.oxyarena.client;

import org.lwjgl.glfw.GLFW;

import com.example.oxyarena.network.PlayerSlideInputPayload;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PlayerSlideController {
    private static final int CLIENT_SLIDE_POSE_TICKS = 14;
    private static final KeyMapping SLIDE_KEY = new KeyMapping(
            "key.oxyarena.slide",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            "key.categories.gameplay");

    private static boolean lastSentSlideDown;
    private static boolean lastSentMovementDown;
    private static int predictedSlidePoseTicks;
    private static boolean forcingLocalPose;

    private PlayerSlideController() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerSlideController::onClientTickPost);
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SLIDE_KEY);
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean slideKeyDown = minecraft.player != null
                && minecraft.level != null
                && minecraft.screen == null
                && SLIDE_KEY.isDown();
        boolean movementKeyDown = slideKeyDown && isMovementKeyDown(minecraft);
        if (minecraft.player == null || minecraft.level == null) {
            lastSentSlideDown = false;
            lastSentMovementDown = false;
            predictedSlidePoseTicks = 0;
            forcingLocalPose = false;
            return;
        }

        if (slideKeyDown == lastSentSlideDown && movementKeyDown == lastSentMovementDown) {
            tickLocalPose(minecraft.player, slideKeyDown);
            return;
        }

        if (slideKeyDown && !lastSentSlideDown && minecraft.player.isSprinting() && minecraft.player.onGround()) {
            predictedSlidePoseTicks = CLIENT_SLIDE_POSE_TICKS;
        }

        lastSentSlideDown = slideKeyDown;
        lastSentMovementDown = movementKeyDown;
        PacketDistributor.sendToServer(new PlayerSlideInputPayload(slideKeyDown, movementKeyDown));
        tickLocalPose(minecraft.player, slideKeyDown);
    }

    private static boolean isMovementKeyDown(Minecraft minecraft) {
        return minecraft.options.keyUp.isDown()
                || minecraft.options.keyDown.isDown()
                || minecraft.options.keyLeft.isDown()
                || minecraft.options.keyRight.isDown();
    }

    private static void tickLocalPose(LocalPlayer player, boolean keyDown) {
        if (predictedSlidePoseTicks > 0) {
            predictedSlidePoseTicks--;
        }

        boolean shouldForcePose = keyDown || predictedSlidePoseTicks > 0 || (forcingLocalPose && !canStandUp(player));
        if (shouldForcePose) {
            forceLocalPose(player);
            return;
        }

        if (forcingLocalPose) {
            player.setForcedPose(null);
            player.refreshDimensions();
            forcingLocalPose = false;
        }
    }

    private static void forceLocalPose(LocalPlayer player) {
        player.setForcedPose(Pose.SWIMMING);
        if (!player.hasPose(Pose.SWIMMING)) {
            player.setPose(Pose.SWIMMING);
            player.refreshDimensions();
        }
        forcingLocalPose = true;
    }

    private static boolean canStandUp(LocalPlayer player) {
        Pose targetPose = player.isShiftKeyDown() ? Pose.CROUCHING : Pose.STANDING;
        AABB box = player.getDimensions(targetPose).makeBoundingBox(player.position()).deflate(1.0E-7D);
        return player.level().noCollision(player, box);
    }
}
