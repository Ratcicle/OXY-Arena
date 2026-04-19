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

    private static boolean lastSentDown;
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
        boolean shouldSendDown = minecraft.player != null
                && minecraft.level != null
                && minecraft.screen == null
                && SLIDE_KEY.isDown();
        if (minecraft.player == null || minecraft.level == null) {
            lastSentDown = false;
            predictedSlidePoseTicks = 0;
            forcingLocalPose = false;
            return;
        }

        if (shouldSendDown == lastSentDown) {
            tickLocalPose(minecraft.player, shouldSendDown);
            return;
        }

        if (shouldSendDown && minecraft.player.isSprinting() && minecraft.player.onGround()) {
            predictedSlidePoseTicks = CLIENT_SLIDE_POSE_TICKS;
        }

        lastSentDown = shouldSendDown;
        PacketDistributor.sendToServer(new PlayerSlideInputPayload(shouldSendDown));
        tickLocalPose(minecraft.player, shouldSendDown);
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
