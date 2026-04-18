package com.example.oxyarena.event;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class ZeroReverseRewindHelper {
    private static final int REWIND_TICKS = 80;
    private static final int BUFFER_TICKS = 120;
    private static final int COOLDOWN_TICKS = 900;

    private static final Map<UUID, ArrayDeque<PlayerSnapshot>> SNAPSHOTS_BY_PLAYER = new HashMap<>();

    private ZeroReverseRewindHelper() {
    }

    public static int cooldownTicks() {
        return COOLDOWN_TICKS;
    }

    public static boolean activate(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel serverLevel) || !player.isAlive()) {
            return false;
        }

        PlayerSnapshot snapshot = findRewindSnapshot(player, serverLevel.getServer().getTickCount());
        if (snapshot == null) {
            return false;
        }

        Vec3 origin = player.position();
        serverLevel.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                origin.x,
                origin.y + player.getBbHeight() * 0.5D,
                origin.z,
                36,
                0.45D,
                0.7D,
                0.45D,
                0.02D);

        player.teleportTo(
                serverLevel,
                snapshot.x,
                snapshot.y,
                snapshot.z,
                snapshot.yRot,
                snapshot.xRot);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.setHealth(Mth.clamp(snapshot.health, 1.0F, player.getMaxHealth()));

        FoodData foodData = player.getFoodData();
        foodData.setFoodLevel(snapshot.foodLevel);
        foodData.setSaturation(snapshot.saturationLevel);
        foodData.setExhaustion(snapshot.exhaustionLevel);

        player.removeAllEffects();
        for (MobEffectInstance effect : snapshot.effects) {
            player.addEffect(new MobEffectInstance(effect));
        }
        player.setRemainingFireTicks(snapshot.remainingFireTicks);

        serverLevel.sendParticles(
                ParticleTypes.PORTAL,
                snapshot.x,
                snapshot.y + player.getBbHeight() * 0.5D,
                snapshot.z,
                48,
                0.55D,
                0.8D,
                0.55D,
                0.08D);
        serverLevel.playSound(
                null,
                snapshot.x,
                snapshot.y,
                snapshot.z,
                SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS,
                0.9F,
                0.8F);

        return true;
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int currentTick = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive()) {
                continue;
            }

            recordSnapshot(player, currentTick);
        }
    }

    public static void clearPlayer(ServerPlayer player) {
        SNAPSHOTS_BY_PLAYER.remove(player.getUUID());
    }

    public static void clearAll() {
        SNAPSHOTS_BY_PLAYER.clear();
    }

    private static void recordSnapshot(ServerPlayer player, int currentTick) {
        ArrayDeque<PlayerSnapshot> snapshots = SNAPSHOTS_BY_PLAYER.computeIfAbsent(
                player.getUUID(),
                ignored -> new ArrayDeque<>());
        FoodData foodData = player.getFoodData();
        snapshots.addLast(new PlayerSnapshot(
                player.level().dimension(),
                currentTick,
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot(),
                player.getHealth(),
                foodData.getFoodLevel(),
                foodData.getSaturationLevel(),
                foodData.getExhaustionLevel(),
                player.getRemainingFireTicks(),
                copyActiveEffects(player)));

        int oldestAllowedTick = currentTick - BUFFER_TICKS;
        while (!snapshots.isEmpty() && snapshots.peekFirst().tick < oldestAllowedTick) {
            snapshots.removeFirst();
        }
    }

    private static PlayerSnapshot findRewindSnapshot(ServerPlayer player, int currentTick) {
        ArrayDeque<PlayerSnapshot> snapshots = SNAPSHOTS_BY_PLAYER.get(player.getUUID());
        if (snapshots == null || snapshots.isEmpty()) {
            return null;
        }

        int targetTick = currentTick - REWIND_TICKS;
        PlayerSnapshot best = null;
        int bestDistance = Integer.MAX_VALUE;
        ResourceKey<Level> currentDimension = player.level().dimension();
        for (Iterator<PlayerSnapshot> iterator = snapshots.descendingIterator(); iterator.hasNext();) {
            PlayerSnapshot snapshot = iterator.next();
            if (snapshot.tick > targetTick || snapshot.dimension != currentDimension) {
                continue;
            }

            int distance = targetTick - snapshot.tick;
            if (distance < bestDistance) {
                best = snapshot;
                bestDistance = distance;
            }
        }

        return best;
    }

    private record PlayerSnapshot(
            ResourceKey<Level> dimension,
            int tick,
            double x,
            double y,
            double z,
            float yRot,
            float xRot,
            float health,
            int foodLevel,
            float saturationLevel,
            float exhaustionLevel,
            int remainingFireTicks,
            List<MobEffectInstance> effects) {
    }

    private static List<MobEffectInstance> copyActiveEffects(ServerPlayer player) {
        List<MobEffectInstance> effects = new ArrayList<>();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            effects.add(new MobEffectInstance(effect));
        }
        return List.copyOf(effects);
    }
}
