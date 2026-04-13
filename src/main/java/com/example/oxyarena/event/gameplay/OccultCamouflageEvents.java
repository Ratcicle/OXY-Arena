package com.example.oxyarena.event.gameplay;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.oxyarena.network.OccultCamouflageSyncPayload;
import com.example.oxyarena.registry.ModItems;
import com.example.oxyarena.registry.ModMobEffects;
import com.example.oxyarena.util.OccultCamouflageTuning;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class OccultCamouflageEvents {
    private static final int SET_PASSIVE_EFFECT_DURATION_TICKS = 10;

    private static final Map<UUID, OccultCamouflageState> OCCULT_CAMOUFLAGE_STATES = new HashMap<>();

    private OccultCamouflageEvents() {
    }

    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0.0F) {
            return;
        }

        if (event.getEntity() instanceof Player damagedPlayer) {
            cancelOccultCamouflage(damagedPlayer);
        }

        if (event.getSource().getEntity() instanceof Player attackingPlayer) {
            cancelOccultCamouflage(attackingPlayer);
        }
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        for (Player player : event.getServer().getPlayerList().getPlayers()) {
            if (player.isSpectator()) {
                clearOccultCamouflageState(player);
                continue;
            }

            tickOccultCamouflage(player);
        }
    }

    public static void cancelOccultCamouflage(Player player) {
        OccultCamouflageState state = OCCULT_CAMOUFLAGE_STATES.computeIfAbsent(
                player.getUUID(),
                ignored -> new OccultCamouflageState(player.position()));
        state.lastPosition = player.position();
        state.stationaryTicks = 0;
        state.progress = 0.0F;
        updateOccultMarkerEffect(player, state.progress);
        syncOccultCamouflageProgress(player, state, false);
    }

    public static void clearOccultCamouflageState(Player player) {
        UUID playerId = player.getUUID();
        OccultCamouflageState state = OCCULT_CAMOUFLAGE_STATES.get(playerId);
        if (state != null) {
            state.progress = 0.0F;
            syncOccultCamouflageProgress(player, state, false);
            OCCULT_CAMOUFLAGE_STATES.remove(playerId);
        }
        player.removeEffect(ModMobEffects.OCCULT_CAMOUFLAGE);
    }

    public static void clearOccultCamouflageTracking() {
        OCCULT_CAMOUFLAGE_STATES.clear();
    }

    public static void syncOccultCamouflageStateTo(ServerPlayer recipient, Player target) {
        if (!(target.level() instanceof ServerLevel) || recipient.server != target.getServer()) {
            return;
        }

        int quantizedProgress = OccultCamouflageTuning.progressToQuantized(getOccultCamouflageProgress(target));
        PacketDistributor.sendToPlayer(recipient, new OccultCamouflageSyncPayload(target.getUUID(), quantizedProgress));
    }

    public static float getOccultCamouflageProgress(Player player) {
        OccultCamouflageState state = OCCULT_CAMOUFLAGE_STATES.get(player.getUUID());
        return state == null ? 0.0F : state.progress;
    }

    private static void tickOccultCamouflage(Player player) {
        if (!(player.level() instanceof ServerLevel)) {
            return;
        }

        if (!hasFullOccultSet(player)) {
            clearOccultCamouflageState(player);
            return;
        }

        OccultCamouflageState state = OCCULT_CAMOUFLAGE_STATES.computeIfAbsent(
                player.getUUID(),
                ignored -> new OccultCamouflageState(player.position()));
        Vec3 currentPosition = player.position();
        double deltaMovementSqr = currentPosition.distanceToSqr(state.lastPosition);
        boolean stationary = deltaMovementSqr <= OccultCamouflageTuning.MOVEMENT_EPSILON_SQR;
        boolean microMovement = OccultCamouflageTuning.isMicroMovement(deltaMovementSqr, player.isCrouching());
        boolean hardBreakMovement = OccultCamouflageTuning.isNormalMovement(deltaMovementSqr, player.isCrouching())
                || player.isSprinting()
                || !player.onGround()
                || player.isUsingItem();

        if (hardBreakMovement) {
            state.stationaryTicks = 0;
            state.progress = 0.0F;
        } else if (stationary) {
            state.stationaryTicks++;
            if (state.stationaryTicks >= OccultCamouflageTuning.ARMING_TICKS) {
                state.progress = Mth.clamp(
                        state.progress + OccultCamouflageTuning.fadeInStep(),
                        0.0F,
                        1.0F);
            } else {
                state.progress = 0.0F;
            }
        } else if (microMovement && state.progress > 0.0F) {
            state.stationaryTicks = OccultCamouflageTuning.ARMING_TICKS;
            state.progress = Math.max(
                    OccultCamouflageTuning.PARTIAL_FLOOR_PROGRESS,
                    state.progress - OccultCamouflageTuning.PARTIAL_DECAY_PER_TICK);
        } else {
            state.stationaryTicks = 0;
            state.progress = 0.0F;
        }

        state.lastPosition = currentPosition;
        updateOccultMarkerEffect(player, state.progress);
        syncOccultCamouflageProgress(player, state, false);
    }

    private static void updateOccultMarkerEffect(Player player, float progress) {
        if (progress <= 0.0F) {
            player.removeEffect(ModMobEffects.OCCULT_CAMOUFLAGE);
            return;
        }

        player.addEffect(new MobEffectInstance(
                ModMobEffects.OCCULT_CAMOUFLAGE,
                SET_PASSIVE_EFFECT_DURATION_TICKS,
                0,
                false,
                false,
                false));
    }

    private static void syncOccultCamouflageProgress(Player player, OccultCamouflageState state, boolean force) {
        if (player.level().isClientSide) {
            return;
        }

        int quantizedProgress = OccultCamouflageTuning.progressToQuantized(state.progress);
        if (!force && quantizedProgress == state.lastSyncedProgress) {
            return;
        }

        state.lastSyncedProgress = quantizedProgress;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new OccultCamouflageSyncPayload(player.getUUID(), quantizedProgress));
    }

    private static boolean hasFullOccultSet(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.OCCULT_HELMET.get())
                && player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.OCCULT_CHESTPLATE.get())
                && player.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.OCCULT_LEGGINGS.get())
                && player.getItemBySlot(EquipmentSlot.FEET).is(ModItems.OCCULT_BOOTS.get());
    }

    private static final class OccultCamouflageState {
        private Vec3 lastPosition;
        private int stationaryTicks;
        private float progress;
        private int lastSyncedProgress;

        private OccultCamouflageState(Vec3 lastPosition) {
            this.lastPosition = lastPosition;
            this.stationaryTicks = 0;
            this.progress = 0.0F;
            this.lastSyncedProgress = -1;
        }
    }
}
