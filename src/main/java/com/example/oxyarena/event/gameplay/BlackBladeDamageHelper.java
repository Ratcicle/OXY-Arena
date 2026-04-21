package com.example.oxyarena.event.gameplay;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.example.oxyarena.registry.ModDamageTypes;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class BlackBladeDamageHelper {
    private static final int PULSE_INTERVAL_TICKS = 2;
    private static final int PASSIVE_PULSES = 3;
    private static final int PROJECTILE_PULSES = 5;
    private static final float PULSE_DAMAGE = 1.0F;

    private static final List<ScheduledPulse> SCHEDULED_PULSES = new ArrayList<>();

    private BlackBladeDamageHelper() {
    }

    public static void schedulePassiveDamage(ServerLevel level, LivingEntity target, @Nullable Entity attacker) {
        scheduleDamage(level, target, attacker, PASSIVE_PULSES, ModDamageTypes.BLACK_BLADE_PULSE);
    }

    public static void scheduleProjectileDamage(ServerLevel level, LivingEntity target, @Nullable Entity attacker) {
        scheduleDamage(level, target, attacker, PROJECTILE_PULSES, ModDamageTypes.BLACK_BLADE_PROJECTILE);
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        int currentTick = event.getServer().getTickCount();
        Iterator<ScheduledPulse> iterator = SCHEDULED_PULSES.iterator();
        while (iterator.hasNext()) {
            ScheduledPulse pulse = iterator.next();
            if (pulse.triggerTick() > currentTick) {
                continue;
            }

            ServerLevel level = event.getServer().getLevel(pulse.levelKey());
            if (level == null) {
                iterator.remove();
                continue;
            }

            Entity targetEntity = level.getEntity(pulse.targetId());
            if (!(targetEntity instanceof LivingEntity target) || !target.isAlive()) {
                iterator.remove();
                continue;
            }

            Entity attacker = pulse.attackerId() == null ? null : level.getEntity(pulse.attackerId());
            applyGuaranteedPulse(target, pulse.damageType(), attacker);
            iterator.remove();
        }
    }

    public static void clearAll() {
        SCHEDULED_PULSES.clear();
    }

    private static void scheduleDamage(
            ServerLevel level,
            LivingEntity target,
            @Nullable Entity attacker,
            int pulseCount,
            ResourceKey<DamageType> damageType) {
        if (pulseCount <= 0 || !target.isAlive()) {
            return;
        }

        int currentTick = level.getServer().getTickCount();
        for (int pulseIndex = 1; pulseIndex <= pulseCount; pulseIndex++) {
            SCHEDULED_PULSES.add(new ScheduledPulse(
                    level.dimension(),
                    target.getUUID(),
                    attacker == null ? null : attacker.getUUID(),
                    currentTick + pulseIndex * PULSE_INTERVAL_TICKS,
                    damageType));
        }
    }

    private static void applyGuaranteedPulse(
            LivingEntity target,
            ResourceKey<DamageType> damageType,
            @Nullable Entity attacker) {
        int previousInvulnerableTime = target.invulnerableTime;
        target.invulnerableTime = 0;
        try {
            target.hurt(target.damageSources().source(damageType, null, attacker), PULSE_DAMAGE);
        } finally {
            target.invulnerableTime = previousInvulnerableTime;
        }
    }

    private record ScheduledPulse(
            ResourceKey<net.minecraft.world.level.Level> levelKey,
            UUID targetId,
            @Nullable UUID attackerId,
            int triggerTick,
            ResourceKey<DamageType> damageType) {
    }
}
