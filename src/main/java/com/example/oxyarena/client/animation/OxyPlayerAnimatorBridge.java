package com.example.oxyarena.client.animation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.animation.ModPlayerAnimations;
import com.example.oxyarena.network.PlayerAnimationPlayPayload;
import com.example.oxyarena.network.PlayerAnimationStopPayload;

import dev.kosmx.playerAnim.api.IPlayable;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.api.layered.IActualAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public final class OxyPlayerAnimatorBridge {
    private static final int OXY_SKILL_LAYER_PRIORITY = 2500;
    private static final Map<UUID, ResourceLocation> ACTIVE_ANIMATIONS = new HashMap<>();
    private static final FirstPersonConfiguration FIRST_PERSON_CONFIGURATION = new FirstPersonConfiguration(
            true,
            true,
            true,
            true);

    private OxyPlayerAnimatorBridge() {
    }

    public static void handlePlayPayload(PlayerAnimationPlayPayload payload) {
        play(payload.playerId(), payload.animationId());
    }

    public static void handleStopPayload(PlayerAnimationStopPayload payload) {
        stop(payload.playerId());
    }

    public static void play(UUID playerId, ResourceLocation animationId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Player levelPlayer = minecraft.level.getPlayerByUUID(playerId);
        if (!(levelPlayer instanceof AbstractClientPlayer player)) {
            OXYArena.LOGGER.debug("Ignoring OXY PlayerAnimator animation {} for unloaded player {}", animationId, playerId);
            return;
        }

        IPlayable playable = PlayerAnimationRegistry.getAnimation(animationId);
        if (playable == null) {
            OXYArena.LOGGER.warn("Ignoring unknown PlayerAnimator animation {}", animationId);
            return;
        }

        IActualAnimation<?> animation = playable.playAnimation();
        animation.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
        animation.setFirstPersonConfiguration(FIRST_PERSON_CONFIGURATION);

        try {
            AnimationStack animationStack = PlayerAnimationAccess.getPlayerAnimLayer(player);
            animationStack.removeLayer(OXY_SKILL_LAYER_PRIORITY);
            animationStack.addAnimLayer(OXY_SKILL_LAYER_PRIORITY, animation);
            ACTIVE_ANIMATIONS.put(playerId, animationId);
        } catch (IllegalArgumentException exception) {
            OXYArena.LOGGER.warn(
                    "Failed to play PlayerAnimator animation {} for player {}",
                    animationId,
                    player.getGameProfile().getName(),
                    exception);
        }
    }

    public static void stop(UUID playerId) {
        ACTIVE_ANIMATIONS.remove(playerId);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Player levelPlayer = minecraft.level.getPlayerByUUID(playerId);
        if (!(levelPlayer instanceof AbstractClientPlayer player)) {
            OXYArena.LOGGER.debug("Ignoring OXY PlayerAnimator stop for unloaded player {}", playerId);
            return;
        }

        try {
            AnimationStack animationStack = PlayerAnimationAccess.getPlayerAnimLayer(player);
            animationStack.removeLayer(OXY_SKILL_LAYER_PRIORITY);
        } catch (IllegalArgumentException exception) {
            OXYArena.LOGGER.warn(
                    "Failed to stop PlayerAnimator animation for player {}",
                    player.getGameProfile().getName(),
                    exception);
        }
    }

    public static boolean suppressesVanillaSwimming(Player player) {
        ResourceLocation animationId = ACTIVE_ANIMATIONS.get(player.getUUID());
        return ModPlayerAnimations.PLAYER_SLIDE.equals(animationId)
                || ModPlayerAnimations.PLAYER_CRAWL.equals(animationId);
    }
}
