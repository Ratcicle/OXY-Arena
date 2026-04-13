package com.example.oxyarena.event.gameplay;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.oxyarena.registry.ModItems;
import com.example.oxyarena.registry.ModMobEffects;
import com.example.oxyarena.registry.ModSoundEvents;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class CounterMobilityEvents {
    private static final int STORM_CHARGE_FALL_IMMUNITY_TICKS = 80;
    private static final double STORM_CHARGE_SELF_BOOST_MAX_DISTANCE_SQR = 36.0D;
    private static final int KUSABIMARU_DEFLECT_WINDOW_TICKS = 4;
    private static final int KUSABIMARU_STUN_TICKS = 15;
    private static final int KUSABIMARU_DEFLECT_SOUND_CHAIN_WINDOW_TICKS = 30;
    private static final int KUSABIMARU_DEFLECT_SOUND_COUNT = 6;

    private static final Map<UUID, Integer> KUSABIMARU_DEFLECT_ACTIVE_UNTIL = new HashMap<>();
    private static final Map<UUID, Integer> KUSABIMARU_DEFLECT_SOUND_INDEX = new HashMap<>();
    private static final Map<UUID, Integer> KUSABIMARU_DEFLECT_LAST_SOUND_TICK = new HashMap<>();
    private static final Map<UUID, Integer> STORM_CHARGE_FALL_IMMUNE_UNTIL = new HashMap<>();

    private CounterMobilityEvents() {
    }

    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        handleStormChargeFallImmunity(event);

        if (!(event.getEntity() instanceof Player defender) || !isKusabimaruDeflectActive(defender)) {
            return;
        }

        LivingEntity attacker = getKusabimaruAttacker(event.getSource().getDirectEntity());
        if (attacker == null) {
            return;
        }

        event.setCanceled(true);
        if (event.getSource().getDirectEntity() instanceof Projectile projectile) {
            handleSuccessfulKusabimaruDeflect(defender, attacker, projectile);
            return;
        }

        handleSuccessfulKusabimaruDeflect(defender, attacker, null);
    }

    public static boolean onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getProjectile().level().isClientSide()
                || !(event.getRayTraceResult() instanceof EntityHitResult entityHitResult)
                || !(entityHitResult.getEntity() instanceof Player defender)
                || !isKusabimaruDeflectActive(defender)) {
            return false;
        }

        LivingEntity attacker = getKusabimaruAttacker(event.getProjectile());
        if (attacker == null) {
            return false;
        }

        event.setCanceled(true);
        handleSuccessfulKusabimaruDeflect(defender, attacker, event.getProjectile());
        return true;
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        int currentTick = event.getServer().getTickCount();
        cleanupStormChargeFallImmunity(currentTick);
        tickKusabimaruStunnedPlayers(event);
    }

    public static void grantStormChargeFallImmunity(Player player, Vec3 explosionPos) {
        if (player.getServer() == null || player.distanceToSqr(explosionPos) > STORM_CHARGE_SELF_BOOST_MAX_DISTANCE_SQR) {
            return;
        }

        STORM_CHARGE_FALL_IMMUNE_UNTIL.put(
                player.getUUID(),
                player.getServer().getTickCount() + STORM_CHARGE_FALL_IMMUNITY_TICKS);
    }

    public static void clearStormChargeState(Player player) {
        STORM_CHARGE_FALL_IMMUNE_UNTIL.remove(player.getUUID());
    }

    public static void activateKusabimaruDeflect(Player player) {
        if (player.getServer() == null) {
            return;
        }

        KUSABIMARU_DEFLECT_ACTIVE_UNTIL.put(
                player.getUUID(),
                player.getServer().getTickCount() + KUSABIMARU_DEFLECT_WINDOW_TICKS);
    }

    public static void clearKusabimaruState(Player player) {
        KUSABIMARU_DEFLECT_ACTIVE_UNTIL.remove(player.getUUID());
        KUSABIMARU_DEFLECT_SOUND_INDEX.remove(player.getUUID());
        KUSABIMARU_DEFLECT_LAST_SOUND_TICK.remove(player.getUUID());
    }

    public static boolean isKusabimaruStunned(Player player) {
        return player.hasEffect(ModMobEffects.KUSABIMARU_STUN);
    }

    private static void handleStormChargeFallImmunity(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || player.getServer() == null
                || !event.getSource().is(DamageTypeTags.IS_FALL)) {
            return;
        }

        Integer immuneUntil = STORM_CHARGE_FALL_IMMUNE_UNTIL.get(player.getUUID());
        if (immuneUntil == null) {
            return;
        }

        if (player.getServer().getTickCount() > immuneUntil.intValue()) {
            STORM_CHARGE_FALL_IMMUNE_UNTIL.remove(player.getUUID());
            return;
        }

        event.setCanceled(true);
        STORM_CHARGE_FALL_IMMUNE_UNTIL.remove(player.getUUID());
        player.resetFallDistance();
    }

    private static boolean isKusabimaruDeflectActive(Player player) {
        if (player.getServer() == null || !player.getMainHandItem().is(ModItems.KUSABIMARU.get())) {
            KUSABIMARU_DEFLECT_ACTIVE_UNTIL.remove(player.getUUID());
            return false;
        }

        Integer activeUntilTick = KUSABIMARU_DEFLECT_ACTIVE_UNTIL.get(player.getUUID());
        if (activeUntilTick == null) {
            return false;
        }

        if (player.getServer().getTickCount() >= activeUntilTick.intValue()) {
            KUSABIMARU_DEFLECT_ACTIVE_UNTIL.remove(player.getUUID());
            return false;
        }

        return true;
    }

    private static LivingEntity getKusabimaruAttacker(Entity directEntity) {
        if (directEntity instanceof Projectile projectile) {
            return projectile.getOwner() instanceof LivingEntity livingOwner ? livingOwner : null;
        }

        return directEntity instanceof LivingEntity livingAttacker ? livingAttacker : null;
    }

    private static void handleSuccessfulKusabimaruDeflect(Player defender, LivingEntity attacker, Projectile projectile) {
        if (projectile != null && !projectile.isRemoved()) {
            projectile.deflect(ProjectileDeflection.AIM_DEFLECT, defender, defender, true);
            Vec3 look = defender.getLookAngle().normalize();
            Vec3 origin = defender.getEyePosition().add(look.scale(0.75D));
            projectile.setPos(origin.x, origin.y, origin.z);
        } else if (!(attacker instanceof Player)) {
            double knockbackX = defender.getX() - attacker.getX();
            double knockbackZ = defender.getZ() - attacker.getZ();
            if (Mth.equal((float)knockbackX, 0.0F) && Mth.equal((float)knockbackZ, 0.0F)) {
                Vec3 look = defender.getLookAngle();
                knockbackX = look.x;
                knockbackZ = look.z;
            }

            attacker.knockback(0.9F, knockbackX, knockbackZ);
        }

        ItemStack weapon = defender.getMainHandItem();
        if (!weapon.isEmpty()) {
            weapon.hurtAndBreak(1, defender, EquipmentSlot.MAINHAND);
        }
        defender.getCooldowns().removeCooldown(ModItems.KUSABIMARU.get());

        if (defender.level() instanceof ServerLevel serverLevel) {
            Holder<SoundEvent> deflectSound = getNextKusabimaruDeflectSound(defender, serverLevel.getServer().getTickCount());
            serverLevel.playSound(
                    null,
                    defender.getX(),
                    defender.getY(),
                    defender.getZ(),
                    deflectSound.value(),
                    defender.getSoundSource(),
                    1.0F,
                    1.0F);
        }

        if (attacker instanceof Player attackingPlayer && attackingPlayer != defender) {
            attackingPlayer.addEffect(new MobEffectInstance(
                    ModMobEffects.KUSABIMARU_STUN,
                    KUSABIMARU_STUN_TICKS,
                    0,
                    false,
                    false,
                    true));
        }
    }

    private static void tickKusabimaruStunnedPlayers(ServerTickEvent.Post event) {
        for (Player player : event.getServer().getPlayerList().getPlayers()) {
            if (!isKusabimaruStunned(player)) {
                continue;
            }

            Vec3 movement = player.getDeltaMovement();
            player.stopUsingItem();
            player.setSprinting(false);
            player.setDeltaMovement(0.0D, Math.min(movement.y, 0.0D), 0.0D);
            player.hurtMarked = true;
        }
    }

    private static Holder<SoundEvent> getNextKusabimaruDeflectSound(Player defender, int currentTick) {
        UUID playerId = defender.getUUID();
        int nextIndex = 0;
        Integer lastTick = KUSABIMARU_DEFLECT_LAST_SOUND_TICK.get(playerId);
        if (lastTick != null && currentTick - lastTick.intValue() <= KUSABIMARU_DEFLECT_SOUND_CHAIN_WINDOW_TICKS) {
            nextIndex = (KUSABIMARU_DEFLECT_SOUND_INDEX.getOrDefault(playerId, -1) + 1) % KUSABIMARU_DEFLECT_SOUND_COUNT;
        }

        KUSABIMARU_DEFLECT_SOUND_INDEX.put(playerId, nextIndex);
        KUSABIMARU_DEFLECT_LAST_SOUND_TICK.put(playerId, currentTick);

        return switch (nextIndex) {
            case 1 -> ModSoundEvents.DEFLECT2;
            case 2 -> ModSoundEvents.DEFLECT3;
            case 3 -> ModSoundEvents.DEFLECT4;
            case 4 -> ModSoundEvents.DEFLECT5;
            case 5 -> ModSoundEvents.DEFLECT6;
            default -> ModSoundEvents.DEFLECT;
        };
    }

    private static void cleanupStormChargeFallImmunity(int currentTick) {
        STORM_CHARGE_FALL_IMMUNE_UNTIL.entrySet().removeIf(entry -> entry.getValue().intValue() < currentTick);
    }
}
