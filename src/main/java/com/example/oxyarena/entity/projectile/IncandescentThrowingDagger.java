package com.example.oxyarena.entity.projectile;

import javax.annotation.Nullable;

import com.example.oxyarena.registry.ModEntityTypes;
import com.example.oxyarena.registry.ModItems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class IncandescentThrowingDagger extends AbstractArrow {
    private static final double BASE_DAMAGE = 3.0D;
    private static final double GRAVITY = 0.025D;
    private static final float BURN_SECONDS = 4.0F;
    private boolean dealtDamage;

    public IncandescentThrowingDagger(EntityType<? extends IncandescentThrowingDagger> entityType, Level level) {
        super(entityType, level);
        this.setBaseDamage(BASE_DAMAGE);
    }

    public IncandescentThrowingDagger(Level level, LivingEntity owner, ItemStack pickupItemStack) {
        super(
                ModEntityTypes.INCANDESCENT_THROWING_DAGGER.get(),
                owner,
                level,
                pickupItemStack.copyWithCount(1),
                null);
        this.setBaseDamage(BASE_DAMAGE);
    }

    public IncandescentThrowingDagger(Level level, double x, double y, double z, ItemStack pickupItemStack) {
        super(
                ModEntityTypes.INCANDESCENT_THROWING_DAGGER.get(),
                x,
                y,
                z,
                level,
                pickupItemStack.copyWithCount(1),
                null);
        this.setBaseDamage(BASE_DAMAGE);
    }

    @Override
    public void tick() {
        if (this.inGroundTime > 4) {
            this.dealtDamage = true;
        }

        super.tick();
    }

    @Nullable
    @Override
    protected EntityHitResult findHitEntity(Vec3 startVec, Vec3 endVec) {
        return this.dealtDamage ? null : super.findHitEntity(startVec, endVec);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        DamageSource damageSource = this.damageSources().thrown(this, owner == null ? this : owner);
        float damage = (float)this.getBaseDamage();

        if (this.level() instanceof ServerLevel serverLevel) {
            ItemStack weaponItem = this.getWeaponItem();
            if (weaponItem != null) {
                damage = EnchantmentHelper.modifyDamage(serverLevel, weaponItem, target, damageSource, damage);
            }
        }

        this.dealtDamage = true;
        if (target.hurt(damageSource, damage) && target instanceof LivingEntity livingTarget) {
            this.doKnockback(livingTarget, damageSource);
            livingTarget.igniteForSeconds(BURN_SECONDS);

            if (this.level() instanceof ServerLevel serverLevel) {
                ItemStack weaponItem = this.getWeaponItem();
                if (weaponItem != null) {
                    EnchantmentHelper.doPostAttackEffectsWithItemSource(serverLevel, target, damageSource, weaponItem);
                }
            }

            this.doPostHurtEffects(livingTarget);
        }

        this.setDeltaMovement(this.getDeltaMovement().multiply(-0.01D, -0.1D, -0.01D));
        this.playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.2F);
    }

    @Override
    public ItemStack getWeaponItem() {
        return this.getPickupItemStackOrigin();
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.INCANDESCENT_THROWING_DAGGER.get());
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.TRIDENT_HIT_GROUND;
    }

    @Override
    protected double getDefaultGravity() {
        return GRAVITY;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.dealtDamage = compound.getBoolean("DealtDamage");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("DealtDamage", this.dealtDamage);
    }
}
