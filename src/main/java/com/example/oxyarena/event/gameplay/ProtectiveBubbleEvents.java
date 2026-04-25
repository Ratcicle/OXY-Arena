package com.example.oxyarena.event.gameplay;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.example.oxyarena.network.ProtectiveBubbleSyncPayload;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ProtectiveBubbleEvents {
    private static final Set<UUID> PROTECTED_PLAYERS = new HashSet<>();

    private ProtectiveBubbleEvents() {
    }

    public static boolean activate(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !player.isAlive() || player.isSpectator()) {
            return false;
        }

        if (!PROTECTED_PLAYERS.add(player.getUUID())) {
            return false;
        }

        sync(serverPlayer, true);
        spawnActivationFeedback(serverPlayer);
        return true;
    }

    public static boolean isProtected(Player player) {
        return PROTECTED_PLAYERS.contains(player.getUUID());
    }

    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !PROTECTED_PLAYERS.remove(player.getUUID())) {
            return;
        }

        event.setCanceled(true);
        player.resetFallDistance();
        sync(player, false);
        spawnBreakFeedback(player);
    }

    public static void clearPlayer(Player player) {
        if (PROTECTED_PLAYERS.remove(player.getUUID()) && player instanceof ServerPlayer serverPlayer) {
            sync(serverPlayer, false);
        }
    }

    public static void clearAll(MinecraftServer server) {
        for (UUID playerId : Set.copyOf(PROTECTED_PLAYERS)) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                sync(player, false);
            }
        }

        PROTECTED_PLAYERS.clear();
    }

    public static void syncStateTo(ServerPlayer recipient, Player protectedPlayer) {
        if (PROTECTED_PLAYERS.contains(protectedPlayer.getUUID())) {
            PacketDistributor.sendToPlayer(
                    recipient,
                    new ProtectiveBubbleSyncPayload(protectedPlayer.getUUID(), true));
        }
    }

    private static void sync(ServerPlayer player, boolean active) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new ProtectiveBubbleSyncPayload(player.getUUID(), active));
    }

    private static void spawnActivationFeedback(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.sendParticles(
                ParticleTypes.END_ROD,
                player.getX(),
                player.getY() + 1.0D,
                player.getZ(),
                18,
                0.42D,
                0.55D,
                0.42D,
                0.02D);
        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS,
                0.8F,
                1.35F);
    }

    private static void spawnBreakFeedback(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.sendParticles(
                ParticleTypes.POOF,
                player.getX(),
                player.getY() + 1.0D,
                player.getZ(),
                24,
                0.45D,
                0.55D,
                0.45D,
                0.05D);
        level.sendParticles(
                ParticleTypes.END_ROD,
                player.getX(),
                player.getY() + 1.0D,
                player.getZ(),
                10,
                0.38D,
                0.48D,
                0.38D,
                0.04D);
        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.SHIELD_BLOCK,
                SoundSource.PLAYERS,
                0.9F,
                1.45F);
    }
}
