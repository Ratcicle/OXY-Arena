package com.example.oxyarena.entity.effect;

import com.example.oxyarena.registry.ModEntityTypes;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class SmokeCloud extends Entity {
    private static final int DURATION_TICKS = 120;
    private static final double MIN_RADIUS = 4.0D;
    private static final double MAX_RADIUS = 6.0D;
    private static final int MIN_PARTICLES_PER_TICK = 20;
    private static final int MAX_PARTICLES_PER_TICK = 150;

    public SmokeCloud(EntityType<? extends SmokeCloud> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public SmokeCloud(Level level, double x, double y, double z) {
        this(ModEntityTypes.SMOKE_CLOUD.get(), level);
        this.setPos(x, y, z);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            this.spawnSmokeParticles();
        }

        if (this.tickCount >= DURATION_TICKS) {
            this.discard();
        }
    }

    private void spawnSmokeParticles() {
        float progress = Mth.clamp((float) this.tickCount / (float) DURATION_TICKS, 0.0F, 1.0F);
        float density = 1.0F - progress;
        double radius = MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * (1.0D - Math.pow(1.0D - progress, 2.0D));
        int particleCount = MIN_PARTICLES_PER_TICK + Math.round(
                (MAX_PARTICLES_PER_TICK - MIN_PARTICLES_PER_TICK) * density * density);

        for (int i = 0; i < particleCount; i++) {
            double theta = this.random.nextDouble() * Mth.TWO_PI;
            double yDirection = this.random.nextDouble() * 2.0D - 1.0D;
            double horizontalScale = Math.sqrt(1.0D - yDirection * yDirection);
            double distance = radius * Math.cbrt(this.random.nextDouble());
            double offsetX = Math.cos(theta) * horizontalScale * distance;
            double offsetY = yDirection * distance * 0.75D;
            double offsetZ = Math.sin(theta) * horizontalScale * distance;
            double speedScale = 0.012D + this.random.nextDouble() * 0.02D;
            double velocityX = offsetX * speedScale + this.random.triangle(0.0D, 0.004D);
            double velocityY = 0.01D + this.random.nextDouble() * 0.03D + offsetY * 0.01D;
            double velocityZ = offsetZ * speedScale + this.random.triangle(0.0D, 0.004D);

            this.level().addParticle(
                    ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    this.getX() + offsetX,
                    this.getY() + 0.15D + offsetY,
                    this.getZ() + offsetZ,
                    velocityX,
                    velocityY,
                    velocityZ);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
    }
}
