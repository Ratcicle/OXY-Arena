package com.example.oxyarena.entity.projectile;

import java.util.List;

import javax.annotation.Nullable;

import com.example.oxyarena.registry.ModEntityTypes;
import com.example.oxyarena.registry.ModItems;
import com.example.oxyarena.registry.ModMobEffects;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public final class AntiHealPotionProjectile extends ThrowableItemProjectile {
    private static final int BASE_DURATION_TICKS = 20 * 20;
    private static final int MIN_DURATION_TICKS = 20;
    private static final double SPLASH_RADIUS = 4.0D;
    private static final double SPLASH_RADIUS_SQR = SPLASH_RADIUS * SPLASH_RADIUS;
    private static final int SPLASH_COLOR = 0x4B244D;

    public AntiHealPotionProjectile(EntityType<? extends AntiHealPotionProjectile> entityType, Level level) {
        super(entityType, level);
    }

    public AntiHealPotionProjectile(Level level, LivingEntity shooter) {
        super(ModEntityTypes.ANTI_HEAL_POTION.get(), shooter, level);
    }

    public AntiHealPotionProjectile(Level level, double x, double y, double z) {
        super(ModEntityTypes.ANTI_HEAL_POTION.get(), x, y, z, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.ANTI_HEAL_POTION.get();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);

        if (!this.level().isClientSide) {
            Entity directHit = result instanceof EntityHitResult entityHitResult
                    ? entityHitResult.getEntity()
                    : null;
            applySplash(directHit);
            this.level().levelEvent(2002, this.blockPosition(), SPLASH_COLOR);
            this.discard();
        }
    }

    private void applySplash(@Nullable Entity directHit) {
        AABB splashArea = this.getBoundingBox().inflate(SPLASH_RADIUS, 2.0D, SPLASH_RADIUS);
        List<Player> players = this.level().getEntitiesOfClass(Player.class, splashArea, player ->
                player.isAlive() && !player.isSpectator() && player.isAffectedByPotions());
        Entity source = this.getEffectSource();

        for (Player player : players) {
            double distanceSqr = this.distanceToSqr(player);
            if (distanceSqr >= SPLASH_RADIUS_SQR) {
                continue;
            }

            double strength = player == directHit ? 1.0D : 1.0D - Math.sqrt(distanceSqr) / SPLASH_RADIUS;
            int duration = (int)(strength * BASE_DURATION_TICKS + 0.5D);
            if (duration <= MIN_DURATION_TICKS) {
                continue;
            }

            player.addEffect(
                    new MobEffectInstance(ModMobEffects.ANTI_HEAL, duration, 0, false, true, true),
                    source);
        }
    }
}
