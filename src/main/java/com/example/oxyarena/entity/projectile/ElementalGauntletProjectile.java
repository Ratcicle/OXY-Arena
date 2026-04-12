package com.example.oxyarena.entity.projectile;

import javax.annotation.Nullable;

import com.example.oxyarena.registry.ModDamageTypes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ElementalGauntletProjectile extends AbstractArrow {
    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(
            ElementalGauntletProjectile.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_KNOCKBACK_BURST = SynchedEntityData.defineId(
            ElementalGauntletProjectile.class,
            EntityDataSerializers.BOOLEAN);

    private static final double BASE_DAMAGE = 2.0D;
    private static final int MAX_LIFE_TICKS = 50;
    private static final double HOMING_SEARCH_RADIUS = 8.0D;
    private static final double HOMING_CONE_DOT = 0.2D;
    private static final double HOMING_TURN_RATE = 0.16D;
    private static final double HOMING_MIN_SPEED = 0.35D;
    private static final double SPLASH_RANGE = 2.6D;
    private static final double SPLASH_CONE_DOT = 0.6D;
    private static final float SPLASH_DAMAGE = 1.0F;
    private static final double KNOCKBACK_STRENGTH = 0.42D;

    public ElementalGauntletProjectile(EntityType<? extends ElementalGauntletProjectile> entityType, Level level) {
        super(entityType, level);
        this.setBaseDamage(BASE_DAMAGE);
        this.setNoGravity(true);
        this.pickup = Pickup.DISALLOWED;
    }

    public ElementalGauntletProjectile(
            EntityType<? extends ElementalGauntletProjectile> entityType,
            Level level,
            LivingEntity owner,
            ItemStack weaponStack,
            int variant,
            boolean knockbackBurst) {
        super(entityType, owner, level, weaponStack.copyWithCount(1), null);
        this.setBaseDamage(BASE_DAMAGE);
        this.setNoGravity(true);
        this.pickup = Pickup.DISALLOWED;
        this.setVariant(variant);
        this.setKnockbackBurst(knockbackBurst);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, 0);
        builder.define(DATA_KNOCKBACK_BURST, false);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            this.updateRotationFromMotion();
            return;
        }

        if (this.tickCount >= MAX_LIFE_TICKS || this.inGround) {
            this.discard();
            return;
        }

        this.applyWeakHoming();
        this.updateRotationFromMotion();
    }

    @Nullable
    @Override
    protected EntityHitResult findHitEntity(Vec3 startVec, Vec3 endVec) {
        return super.findHitEntity(startVec, endVec);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        DamageSource damageSource = this.damageSources().source(
                ModDamageTypes.ELEMENTAL_GAUNTLET_PROJECTILE,
                this,
                owner == null ? this : owner);
        float damage = (float)this.getBaseDamage();
        Vec3 flightDirection = this.getDeltaMovement().lengthSqr() > 1.0E-7D
                ? this.getDeltaMovement().normalize()
                : Vec3.directionFromRotation(this.getXRot(), this.getYRot());

        if (this.level() instanceof ServerLevel serverLevel) {
            ItemStack weaponItem = this.getWeaponItem();
            if (weaponItem != null) {
                damage = EnchantmentHelper.modifyDamage(serverLevel, weaponItem, target, damageSource, damage);
            }
        }

        if (target.hurt(damageSource, damage)) {
            if (target instanceof LivingEntity livingTarget) {
                if (this.isKnockbackBurst()) {
                    livingTarget.knockback(KNOCKBACK_STRENGTH, -flightDirection.x, -flightDirection.z);
                }

                if (this.level() instanceof ServerLevel serverLevel) {
                    ItemStack weaponItem = this.getWeaponItem();
                    if (weaponItem != null) {
                        EnchantmentHelper.doPostAttackEffectsWithItemSource(
                                serverLevel,
                                target,
                                damageSource,
                                weaponItem);
                    }
                }

                this.doPostHurtEffects(livingTarget);
                this.applyRearSplashDamage(livingTarget, damageSource, flightDirection);
            }
        }

        this.playSound(SoundEvents.AMETHYST_BLOCK_HIT, 0.8F, 1.3F);
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        this.playSound(SoundEvents.AMETHYST_BLOCK_HIT, 0.7F, 1.05F);
        this.discard();
    }

    @Override
    public ItemStack getWeaponItem() {
        return this.getPickupItemStackOrigin();
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0D;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setVariant(compound.getInt("Variant"));
        this.setKnockbackBurst(compound.getBoolean("KnockbackBurst"));
        this.pickup = Pickup.DISALLOWED;
        this.setNoGravity(true);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("Variant", this.getVariant());
        compound.putBoolean("KnockbackBurst", this.isKnockbackBurst());
    }

    public int getVariant() {
        return this.getEntityData().get(DATA_VARIANT);
    }

    public boolean isKnockbackBurst() {
        return this.getEntityData().get(DATA_KNOCKBACK_BURST);
    }

    private void setVariant(int variant) {
        this.getEntityData().set(DATA_VARIANT, Math.floorMod(variant, 4));
    }

    private void setKnockbackBurst(boolean knockbackBurst) {
        this.getEntityData().set(DATA_KNOCKBACK_BURST, knockbackBurst);
    }

    private void applyWeakHoming() {
        Vec3 motion = this.getDeltaMovement();
        double speed = motion.length();
        if (speed < HOMING_MIN_SPEED) {
            return;
        }

        LivingEntity target = this.findHomingTarget(motion.normalize());
        if (target == null) {
            return;
        }

        Vec3 desiredDirection = target.getBoundingBox().getCenter().subtract(this.position()).normalize();
        Vec3 adjustedDirection = motion.normalize().lerp(desiredDirection, HOMING_TURN_RATE).normalize();
        this.setDeltaMovement(adjustedDirection.scale(speed));
    }

    @Nullable
    private LivingEntity findHomingTarget(Vec3 forward) {
        LivingEntity bestTarget = null;
        double bestDistanceSqr = Double.MAX_VALUE;

        for (LivingEntity candidate : this.level().getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(HOMING_SEARCH_RADIUS),
                this::isValidHomingTarget)) {
            Vec3 toCandidate = candidate.getBoundingBox().getCenter().subtract(this.position());
            double distanceSqr = toCandidate.lengthSqr();
            if (distanceSqr < 1.0E-6D) {
                continue;
            }

            Vec3 candidateDirection = toCandidate.normalize();
            if (forward.dot(candidateDirection) < HOMING_CONE_DOT || !this.hasClearPath(candidate)) {
                continue;
            }

            if (distanceSqr < bestDistanceSqr) {
                bestDistanceSqr = distanceSqr;
                bestTarget = candidate;
            }
        }

        return bestTarget;
    }

    private boolean isValidHomingTarget(LivingEntity candidate) {
        if (!candidate.isAlive()
                || candidate.isRemoved()
                || candidate == this.getOwner()
                || candidate instanceof ArmorStand armorStand && armorStand.isMarker()) {
            return false;
        }

        if (candidate instanceof Player player && player.isSpectator()) {
            return false;
        }

        Entity owner = this.getOwner();
        return !(owner instanceof LivingEntity livingOwner) || !livingOwner.isAlliedTo(candidate);
    }

    private boolean hasClearPath(LivingEntity target) {
        HitResult hitResult = this.level().clip(new ClipContext(
                this.position(),
                target.getBoundingBox().getCenter(),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this));
        return hitResult.getType() == HitResult.Type.MISS;
    }

    private void applyRearSplashDamage(LivingEntity primaryTarget, DamageSource damageSource, Vec3 flightDirection) {
        if (!(this.level() instanceof ServerLevel)) {
            return;
        }

        Vec3 splashDirection = flightDirection.lengthSqr() > 1.0E-7D
                ? flightDirection.normalize()
                : primaryTarget.getLookAngle().normalize();
        Vec3 primaryCenter = primaryTarget.getBoundingBox().getCenter();
        Entity owner = this.getOwner();

        for (LivingEntity secondaryTarget : this.level().getEntitiesOfClass(
                LivingEntity.class,
                primaryTarget.getBoundingBox().inflate(SPLASH_RANGE),
                this::isValidSplashTarget)) {
            if (secondaryTarget == primaryTarget) {
                continue;
            }

            Vec3 toSecondary = secondaryTarget.getBoundingBox().getCenter().subtract(primaryCenter);
            double distanceSqr = toSecondary.lengthSqr();
            if (distanceSqr < 1.0E-6D || distanceSqr > SPLASH_RANGE * SPLASH_RANGE) {
                continue;
            }

            Vec3 directionToSecondary = toSecondary.normalize();
            if (splashDirection.dot(directionToSecondary) < SPLASH_CONE_DOT) {
                continue;
            }

            if (owner instanceof LivingEntity livingOwner && livingOwner.isAlliedTo(secondaryTarget)) {
                continue;
            }

            secondaryTarget.hurt(damageSource, SPLASH_DAMAGE);
        }
    }

    private boolean isValidSplashTarget(LivingEntity candidate) {
        return candidate.isAlive()
                && !candidate.isRemoved()
                && candidate != this.getOwner()
                && !(candidate instanceof ArmorStand armorStand && armorStand.isMarker())
                && (!(candidate instanceof Player player) || !player.isSpectator());
    }

    private void updateRotationFromMotion() {
        Vec3 motion = this.getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-7D) {
            return;
        }

        double horizontalDistance = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        float targetYaw = (float)(net.minecraft.util.Mth.atan2(motion.x, motion.z) * (180.0D / Math.PI));
        float targetPitch = (float)(net.minecraft.util.Mth.atan2(motion.y, horizontalDistance) * (180.0D / Math.PI));
        this.setYRot(targetYaw);
        this.setXRot(targetPitch);
    }
}
