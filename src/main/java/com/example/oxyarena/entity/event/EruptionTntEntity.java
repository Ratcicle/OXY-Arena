package com.example.oxyarena.entity.event;

import javax.annotation.Nullable;

import com.example.oxyarena.registry.ModEntityTypes;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class EruptionTntEntity extends PrimedTnt {
    private static final float EXPLOSION_RADIUS = 4.0F;
    private static final float MIN_DISTANCE_DAMAGE_FACTOR = 0.55F;
    private static final ExplosionDamageCalculator ONLY_LIVING_DAMAGE_CALCULATOR = new ExplosionDamageCalculator() {
        @Override
        public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return entity instanceof LivingEntity;
        }

        @Override
        public float getKnockbackMultiplier(Entity entity) {
            return entity instanceof LivingEntity ? super.getKnockbackMultiplier(entity) : 0.0F;
        }

        @Override
        public float getEntityDamageAmount(Explosion explosion, Entity entity) {
            float diameter = explosion.radius() * 2.0F;
            Vec3 center = explosion.center();
            double normalizedDistance = Math.sqrt(entity.distanceToSqr(center)) / (double)diameter;
            double exposure = Explosion.getSeenPercent(center, entity);
            float impact = Mth.clamp((float)((1.0D - normalizedDistance) * exposure), 0.0F, 1.0F);
            float softenedImpact = impact * (MIN_DISTANCE_DAMAGE_FACTOR + (1.0F - MIN_DISTANCE_DAMAGE_FACTOR) * impact);
            return (softenedImpact * softenedImpact + softenedImpact) / 2.0F * 7.0F * diameter + 1.0F;
        }
    };

    @Nullable
    private LivingEntity oxyOwner;

    public EruptionTntEntity(EntityType<? extends EruptionTntEntity> entityType, Level level) {
        super(entityType, level);
    }

    public EruptionTntEntity(Level level, double x, double y, double z, @Nullable LivingEntity owner) {
        this(ModEntityTypes.ERUPTION_TNT.get(), level);
        this.oxyOwner = owner;
        this.setPos(x, y, z);
        this.xo = x;
        this.yo = y;
        this.zo = z;
    }

    public void initializeFuseAndLaunch(int fuse, Vec3 launchVelocity) {
        this.setFuse(fuse);
        this.setDeltaMovement(launchVelocity);
    }

    @Nullable
    @Override
    public LivingEntity getOwner() {
        return this.oxyOwner;
    }

    @Override
    public void restoreFrom(Entity entity) {
        super.restoreFrom(entity);
        if (entity instanceof EruptionTntEntity eruptionTnt) {
            this.oxyOwner = eruptionTnt.oxyOwner;
        }
    }

    @Override
    protected void explode() {
        this.level().explode(
                this,
                Explosion.getDefaultDamageSource(this.level(), this),
                ONLY_LIVING_DAMAGE_CALCULATOR,
                this.getX(),
                this.getY(0.0625D),
                this.getZ(),
                EXPLOSION_RADIUS,
                false,
                Level.ExplosionInteraction.NONE);
    }
}
