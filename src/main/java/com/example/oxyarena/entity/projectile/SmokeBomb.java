package com.example.oxyarena.entity.projectile;

import com.example.oxyarena.entity.effect.SmokeCloud;
import com.example.oxyarena.registry.ModEntityTypes;
import com.example.oxyarena.registry.ModItems;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

public class SmokeBomb extends ThrowableItemProjectile {
    public SmokeBomb(EntityType<? extends SmokeBomb> entityType, Level level) {
        super(entityType, level);
    }

    public SmokeBomb(Level level, LivingEntity shooter) {
        super(ModEntityTypes.SMOKE_BOMB.get(), shooter, level);
    }

    public SmokeBomb(Level level, double x, double y, double z) {
        super(ModEntityTypes.SMOKE_BOMB.get(), x, y, z, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.SMOKE_BOMB.get();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);

        if (!this.level().isClientSide) {
            SmokeCloud smokeCloud = new SmokeCloud(
                    this.level(),
                    result.getLocation().x(),
                    result.getLocation().y(),
                    result.getLocation().z());
            this.level().addFreshEntity(smokeCloud);
            this.level().playSound(
                    null,
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.NEUTRAL,
                    0.8F,
                    1.2F);
            this.discard();
        }
    }
}
