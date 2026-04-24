package com.example.oxyarena.entity.projectile;

import com.example.oxyarena.entity.effect.DimensionalRiftEntity;
import com.example.oxyarena.event.gameplay.AmetraWarpedGlaiveEvents;
import com.example.oxyarena.registry.ModEntityTypes;
import com.example.oxyarena.registry.ModItems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public final class DimensionalRiftProjectile extends AbstractArrow {
    private static final double BASE_DAMAGE = 6.0D;
    private static final int MAX_LIFE_TICKS = 60;

    public DimensionalRiftProjectile(EntityType<? extends DimensionalRiftProjectile> entityType, Level level) {
        super(entityType, level);
        this.setBaseDamage(BASE_DAMAGE);
        this.setNoGravity(true);
        this.pickup = Pickup.DISALLOWED;
    }

    public DimensionalRiftProjectile(Level level, LivingEntity owner, ItemStack weaponStack) {
        super(ModEntityTypes.DIMENSIONAL_RIFT_PROJECTILE.get(), owner, level, weaponStack.copyWithCount(1), null);
        this.setBaseDamage(BASE_DAMAGE);
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
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target)
                && target != this.getOwner()
                && !(target instanceof DimensionalRiftEntity)
                && !(target instanceof ArmorStand armorStand && armorStand.isMarker())
                && (!(target instanceof Player player) || !player.isSpectator());
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        DamageSource damageSource = this.damageSources().arrow(this, owner);

        target.hurt(damageSource, (float)this.getBaseDamage());
        if (target instanceof LivingEntity livingTarget
                && owner instanceof ServerPlayer serverPlayer
                && this.level() instanceof ServerLevel serverLevel) {
            AmetraWarpedGlaiveEvents.markTarget(serverLevel, serverPlayer, livingTarget);
        }

        this.playSound(SoundEvents.AMETHYST_BLOCK_BREAK, 0.65F, 1.45F);
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        this.playSound(SoundEvents.AMETHYST_BLOCK_HIT, 0.55F, 0.9F);
        this.discard();
    }

    @Override
    public ItemStack getWeaponItem() {
        return this.getPickupItemStackOrigin();
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.AMETRA_WARPED_GLAIVE.get());
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
        this.setBaseDamage(BASE_DAMAGE);
        this.pickup = Pickup.DISALLOWED;
        this.setNoGravity(true);
    }
}
