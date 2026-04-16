package com.example.oxyarena.combatstatus;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3f;

import com.example.oxyarena.network.CombatStatusSyncPayload;
import com.example.oxyarena.registry.ModDamageTypes;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CombatStatusEvents {
    private static final String MAGIC_DAMAGE_SOURCE = "magic";
    private static final String BLEED_BURST_PARTICLE_STYLE = "bleed_burst";
    private static final Vector3f BLEED_DUST_COLOR = new Vector3f(0.74F, 0.08F, 0.10F);
    private static final float ARMOR_BUILDUP_REDUCTION_PER_POINT = 0.02F;
    private static final float MIN_BUILDUP_MULTIPLIER = 0.45F;

    private static final Map<ResourceKey<net.minecraft.world.level.Level>, Map<UUID, EntityCombatStatusState>> ACTIVE_STATUSES =
            new HashMap<>();

    private CombatStatusEvents() {
    }

    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target)
                || target.level().isClientSide()
                || event.getNewDamage() <= 0.0F
                || !target.isAlive()
                || !(event.getSource().getEntity() instanceof Player attacker)
                || event.getSource().getDirectEntity() != attacker) {
            return;
        }

        int currentTick = target.getServer() != null ? target.getServer().getTickCount() : 0;
        for (CombatStatusApplication application : CombatStatusDataManager.getApplications(attacker.getMainHandItem())) {
            applyStatus(target, application, attacker, currentTick);
        }
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        int currentTick = event.getServer().getTickCount();
        Iterator<Map.Entry<ResourceKey<net.minecraft.world.level.Level>, Map<UUID, EntityCombatStatusState>>> levelIterator =
                ACTIVE_STATUSES.entrySet().iterator();
        while (levelIterator.hasNext()) {
            Map.Entry<ResourceKey<net.minecraft.world.level.Level>, Map<UUID, EntityCombatStatusState>> levelEntry =
                    levelIterator.next();
            ServerLevel level = event.getServer().getLevel(levelEntry.getKey());
            if (level == null) {
                clearHudForUnknownLevel(event, levelEntry.getValue());
                levelIterator.remove();
                continue;
            }

            Iterator<Map.Entry<UUID, EntityCombatStatusState>> targetIterator = levelEntry.getValue().entrySet().iterator();
            while (targetIterator.hasNext()) {
                Map.Entry<UUID, EntityCombatStatusState> targetEntry = targetIterator.next();
                Entity entity = level.getEntity(targetEntry.getKey());
                if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
                    sendZeroIfOnline(event, targetEntry.getKey(), targetEntry.getValue());
                    targetIterator.remove();
                    continue;
                }

                tickTargetStatus(target, targetEntry.getValue(), currentTick);
                if (targetEntry.getValue().statuses().isEmpty()) {
                    targetIterator.remove();
                }
            }

            if (levelEntry.getValue().isEmpty()) {
                levelIterator.remove();
            }
        }
    }

    public static void clearEntity(LivingEntity entity) {
        Map<UUID, EntityCombatStatusState> levelStates = ACTIVE_STATUSES.get(entity.level().dimension());
        if (levelStates == null) {
            return;
        }

        EntityCombatStatusState removedState = levelStates.remove(entity.getUUID());
        if (removedState == null) {
            return;
        }

        if (entity instanceof ServerPlayer serverPlayer) {
            sendZeroForAllStatuses(serverPlayer, removedState);
        }

        if (levelStates.isEmpty()) {
            ACTIVE_STATUSES.remove(entity.level().dimension());
        }
    }

    public static void clearAll() {
        ACTIVE_STATUSES.clear();
    }

    private static void applyStatus(
            LivingEntity target,
            CombatStatusApplication application,
            Player attacker,
            int currentTick) {
        CombatStatusDefinition definition = CombatStatusDataManager.getDefinition(application.statusId());
        if (definition == null) {
            return;
        }

        EntityCombatStatusState entityState = ACTIVE_STATUSES
                .computeIfAbsent(target.level().dimension(), key -> new HashMap<>())
                .computeIfAbsent(target.getUUID(), key -> new EntityCombatStatusState());
        ActiveStatusState activeStatusState = entityState.statuses()
                .computeIfAbsent(application.statusId(), key -> new ActiveStatusState());

        float adjustedBuildup = getArmorAdjustedBuildup(application.buildupPerHit(), target);
        float newBuildup = activeStatusState.currentBuildup() + adjustedBuildup;
        activeStatusState.setLastApplicationTick(currentTick);
        if (newBuildup >= definition.maxBuildup()) {
            procStatus(target, attacker, definition);
            if (definition.resetOnProc()) {
                newBuildup = definition.overflowCarry() ? newBuildup - definition.maxBuildup() : 0.0F;
            } else {
                newBuildup = definition.maxBuildup();
            }
        }

        activeStatusState.setCurrentBuildup(Mth.clamp(newBuildup, 0.0F, definition.maxBuildup()));
        syncStatusIfNeeded(target, application.statusId(), definition, activeStatusState);
    }

    private static void tickTargetStatus(LivingEntity target, EntityCombatStatusState entityState, int currentTick) {
        Iterator<Map.Entry<ResourceLocation, ActiveStatusState>> iterator = entityState.statuses().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ResourceLocation, ActiveStatusState> statusEntry = iterator.next();
            CombatStatusDefinition definition = CombatStatusDataManager.getDefinition(statusEntry.getKey());
            ActiveStatusState activeStatusState = statusEntry.getValue();
            if (definition == null) {
                sendZeroIfPlayerTarget(target, statusEntry.getKey(), activeStatusState);
                iterator.remove();
                continue;
            }

            if (activeStatusState.currentBuildup() > 0.0F
                    && currentTick - activeStatusState.lastApplicationTick() >= definition.decayDelayTicks()) {
                activeStatusState.setCurrentBuildup(Math.max(0.0F, activeStatusState.currentBuildup() - definition.decayPerTick()));
            }

            syncStatusIfNeeded(target, statusEntry.getKey(), definition, activeStatusState);
            if (activeStatusState.currentBuildup() <= 0.0F) {
                iterator.remove();
            }
        }
    }

    private static void syncStatusIfNeeded(
            LivingEntity target,
            ResourceLocation statusId,
            CombatStatusDefinition definition,
            ActiveStatusState activeStatusState) {
        if (!(target instanceof ServerPlayer serverPlayer)) {
            return;
        }

        int quantizedProgress = quantizeProgress(activeStatusState.currentBuildup(), definition.maxBuildup());
        if (quantizedProgress == activeStatusState.lastSentHudProgress()) {
            return;
        }

        PacketDistributor.sendToPlayer(serverPlayer, new CombatStatusSyncPayload(statusId, quantizedProgress));
        activeStatusState.setLastSentHudProgress(quantizedProgress);
    }

    private static void sendZeroIfPlayerTarget(
            LivingEntity target,
            ResourceLocation statusId,
            ActiveStatusState activeStatusState) {
        if (!(target instanceof ServerPlayer serverPlayer) || activeStatusState.lastSentHudProgress() == 0) {
            return;
        }

        PacketDistributor.sendToPlayer(serverPlayer, new CombatStatusSyncPayload(statusId, 0));
        activeStatusState.setLastSentHudProgress(0);
    }

    private static void sendZeroForAllStatuses(ServerPlayer player, EntityCombatStatusState entityState) {
        for (Map.Entry<ResourceLocation, ActiveStatusState> statusEntry : entityState.statuses().entrySet()) {
            if (statusEntry.getValue().lastSentHudProgress() <= 0) {
                continue;
            }

            PacketDistributor.sendToPlayer(player, new CombatStatusSyncPayload(statusEntry.getKey(), 0));
            statusEntry.getValue().setLastSentHudProgress(0);
        }
    }

    private static void sendZeroIfOnline(
            ServerTickEvent.Post event,
            UUID playerId,
            EntityCombatStatusState entityState) {
        ServerPlayer serverPlayer = event.getServer().getPlayerList().getPlayer(playerId);
        if (serverPlayer != null) {
            sendZeroForAllStatuses(serverPlayer, entityState);
        }
    }

    private static void clearHudForUnknownLevel(
            ServerTickEvent.Post event,
            Map<UUID, EntityCombatStatusState> levelStates) {
        for (Map.Entry<UUID, EntityCombatStatusState> stateEntry : levelStates.entrySet()) {
            sendZeroIfOnline(event, stateEntry.getKey(), stateEntry.getValue());
        }
    }

    private static int quantizeProgress(float currentBuildup, float maxBuildup) {
        if (maxBuildup <= 0.0F) {
            return 0;
        }

        return Mth.clamp(Math.round(currentBuildup / maxBuildup * 100.0F), 0, 100);
    }

    private static void procStatus(LivingEntity target, Player attacker, CombatStatusDefinition definition) {
        target.hurt(resolveDamageSource(target, attacker, definition.damageSource()),
                definition.procFlatDamage() + target.getMaxHealth() * definition.procMaxHealthRatio());

        if (target.level() instanceof ServerLevel serverLevel) {
            spawnProcParticles(serverLevel, target, definition.procParticleStyle());
        }
    }

    private static net.minecraft.world.damagesource.DamageSource resolveDamageSource(
            LivingEntity target,
            Player attacker,
            String damageSource) {
        if (MAGIC_DAMAGE_SOURCE.equals(damageSource)) {
            return target.damageSources().source(ModDamageTypes.BLEED_PROC, attacker);
        }

        return target.damageSources().source(ModDamageTypes.BLEED_PROC, attacker);
    }

    private static float getArmorAdjustedBuildup(float baseBuildup, LivingEntity target) {
        float armorValue = Math.max(0.0F, target.getArmorValue());
        float multiplier = Mth.clamp(
                1.0F - armorValue * ARMOR_BUILDUP_REDUCTION_PER_POINT,
                MIN_BUILDUP_MULTIPLIER,
                1.0F);
        return baseBuildup * multiplier;
    }

    private static void spawnProcParticles(ServerLevel level, LivingEntity target, String particleStyle) {
        if (!BLEED_BURST_PARTICLE_STYLE.equals(particleStyle)) {
            return;
        }

        double width = Math.max(0.25D, target.getBbWidth() * 0.35D);
        double height = Math.max(0.2D, target.getBbHeight() * 0.3D);
        level.sendParticles(
                new DustParticleOptions(BLEED_DUST_COLOR, 1.1F),
                target.getX(),
                target.getY(0.6D),
                target.getZ(),
                18,
                width,
                height,
                width,
                0.01D);
        level.sendParticles(
                ParticleTypes.DAMAGE_INDICATOR,
                target.getX(),
                target.getY(0.8D),
                target.getZ(),
                6,
                width * 0.65D,
                height * 0.5D,
                width * 0.65D,
                0.1D);
    }

    private static final class EntityCombatStatusState {
        private final Map<ResourceLocation, ActiveStatusState> statuses = new HashMap<>();

        private Map<ResourceLocation, ActiveStatusState> statuses() {
            return statuses;
        }
    }

    private static final class ActiveStatusState {
        private float currentBuildup;
        private int lastApplicationTick;
        private int lastSentHudProgress = -1;

        private float currentBuildup() {
            return currentBuildup;
        }

        private void setCurrentBuildup(float currentBuildup) {
            this.currentBuildup = currentBuildup;
        }

        private int lastApplicationTick() {
            return lastApplicationTick;
        }

        private void setLastApplicationTick(int lastApplicationTick) {
            this.lastApplicationTick = lastApplicationTick;
        }

        private int lastSentHudProgress() {
            return lastSentHudProgress;
        }

        private void setLastSentHudProgress(int lastSentHudProgress) {
            this.lastSentHudProgress = lastSentHudProgress;
        }
    }
}
