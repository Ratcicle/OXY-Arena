package com.example.oxyarena.entity.projectile;

import com.example.oxyarena.event.gameplay.BlackBladeDamageHelper;
import com.example.oxyarena.registry.ModEntityTypes;
import com.example.oxyarena.registry.ModItems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public final class BlackBladeProjectile extends AbstractArrow {
    private static final int MAX_LIFE_TICKS = 60;

    public BlackBladeProjectile(EntityType<? extends BlackBladeProjectile> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
        this.pickup = Pickup.DISALLOWED;
    }

    public BlackBladeProjectile(Level level, LivingEntity owner, ItemStack weaponStack) {
        super(ModEntityTypes.BLACK_BLADE_PROJECTILE.get(), owner, level, weaponStack.copyWithCount(1), null);
        this.setNoGravity(true);
        this.pickup = Pickup.DISALLOWED;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && (this.tickCount >= MAX_LIFE_TICKS || this.inGround)) {
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        if (target instanceof LivingEntity livingTarget && this.level() instanceof ServerLevel serverLevel) {
            BlackBladeDamageHelper.scheduleProjectileDamage(serverLevel, livingTarget, this.getOwner());
        }

        this.playSound(SoundEvents.WITHER_HURT, 0.6F, 1.65F);
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        this.playSound(SoundEvents.WITHER_HURT, 0.45F, 1.25F);
        this.discard();
    }

    @Override
    public ItemStack getWeaponItem() {
        return this.getPickupItemStackOrigin();
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.BLACK_BLADE.get());
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.WITHER_HURT;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0D;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.pickup = Pickup.DISALLOWED;
        this.setNoGravity(true);
    }
}
