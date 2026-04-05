package com.example.oxyarena.entity.projectile;

import javax.annotation.Nullable;

import com.example.oxyarena.registry.ModEntityTypes;
import com.example.oxyarena.registry.ModItems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class ThrownZeusLightning extends AbstractArrow {
    private static final double PROJECTILE_DAMAGE = 5.0D;
    private static final int MAX_OUTBOUND_TICKS = 100;
    private static final double AIR_DRAG_COMPENSATION = 1.0D / 0.99D;
    private static final double RETURN_SPEED = 3.0D;
    private static final double RETURN_COLLECT_DISTANCE_SQ = 4.0D;
    // TODO: Com simulation distance baixa, o retorno ainda pode dessincronizar em arremessos longos
    // e so normaliza quando os chunks voltam a ser simulados. Revisitar essa logica depois.
    private boolean returning;
    private boolean returnToOffhand;
    @Nullable
    private Long forcedChunk;
    @Nullable
    private Long forcedNextChunk;

    public ThrownZeusLightning(EntityType<? extends ThrownZeusLightning> entityType, Level level) {
        super(entityType, level);
        this.setBaseDamage(PROJECTILE_DAMAGE);
        this.setNoGravity(true);
    }

    public ThrownZeusLightning(Level level, LivingEntity owner, ItemStack pickupItemStack, InteractionHand throwHand) {
        super(
                ModEntityTypes.ZEUS_LIGHTNING.get(),
                owner,
                level,
                pickupItemStack.copyWithCount(1),
                null);
        this.setBaseDamage(PROJECTILE_DAMAGE);
        this.setNoGravity(true);
        this.returnToOffhand = throwHand == InteractionHand.OFF_HAND;
    }

    public ThrownZeusLightning(Level level, double x, double y, double z, ItemStack pickupItemStack) {
        super(
                ModEntityTypes.ZEUS_LIGHTNING.get(),
                x,
                y,
                z,
                level,
                pickupItemStack.copyWithCount(1),
                null);
        this.setBaseDamage(PROJECTILE_DAMAGE);
        this.setNoGravity(true);
    }

    @Override
    public void tick() {
        this.updateForcedChunks();

        if (this.returning) {
            this.tickReturnToOwner();
            if (this.isRemoved()) {
                return;
            }
        } else if (!this.inGround && this.tickCount >= MAX_OUTBOUND_TICKS) {
            this.startReturningToOwner();
            if (this.isRemoved()) {
                return;
            }
        }

        super.tick();

        if (!this.returning && !this.inGround && !this.isInWater()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(AIR_DRAG_COMPENSATION));
        }

        if (!this.returning && this.inGround) {
            this.startReturningToOwner();
        }
    }

    @Nullable
    @Override
    protected EntityHitResult findHitEntity(Vec3 startVec, Vec3 endVec) {
        return this.returning ? null : super.findHitEntity(startVec, endVec);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        DamageSource damageSource = this.damageSources().trident(this, owner == null ? this : owner);
        float damage = (float) this.getBaseDamage();

        if (this.level() instanceof ServerLevel serverLevel) {
            ItemStack weaponItem = this.getWeaponItem();
            damage = EnchantmentHelper.modifyDamage(serverLevel, weaponItem, target, damageSource, damage);
        }

        if (target.hurt(damageSource, damage) && target instanceof LivingEntity livingTarget) {
            this.doKnockback(livingTarget, damageSource);
            this.doPostHurtEffects(livingTarget);
        }

        if (this.level() instanceof ServerLevel serverLevel) {
            ItemStack weaponItem = this.getWeaponItem();
            EnchantmentHelper.doPostAttackEffectsWithItemSource(serverLevel, target, damageSource, weaponItem);
            this.spawnLightningStrike(serverLevel, result.getLocation());
        }

        this.playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);
        this.startReturningToOwner();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        this.startReturningToOwner();
    }

    @Override
    public ItemStack getWeaponItem() {
        return this.getPickupItemStackOrigin();
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.ZEUS_LIGHTNING.get());
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.TRIDENT_HIT_GROUND;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0D;
    }

    @Override
    protected float getWaterInertia() {
        return 1.0F;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.returning = compound.getBoolean("Returning");
        this.returnToOffhand = compound.getBoolean("ReturnToOffhand");
        this.setNoGravity(true);
        if (this.returning) {
            this.setNoPhysics(true);
            this.inGround = false;
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("Returning", this.returning);
        compound.putBoolean("ReturnToOffhand", this.returnToOffhand);
    }

    @Override
    public void remove(RemovalReason reason) {
        this.releaseForcedChunks();
        super.remove(reason);
    }

    private void tickReturnToOwner() {
        Entity owner = this.getOwner();
        if (!this.isAcceptableReturnOwner(owner)) {
            if (!this.level().isClientSide && this.pickup == AbstractArrow.Pickup.ALLOWED) {
                this.spawnAtLocation(this.getPickupItem(), 0.1F);
            }

            this.discard();
            return;
        }

        Vec3 targetPosition = owner.position()
                .add(0.0D, owner.getBbHeight() * 0.5D, 0.0D)
                .subtract(this.position());
        if (owner instanceof Player player && targetPosition.lengthSqr() <= RETURN_COLLECT_DISTANCE_SQ) {
            if (!this.level().isClientSide && this.tryReturnToPlayer(player)) {
                this.discard();
            }

            return;
        }

        this.setNoPhysics(true);
        this.inGround = false;
        if (targetPosition.lengthSqr() > 1.0E-7D) {
            this.setDeltaMovement(targetPosition.normalize().scale(RETURN_SPEED));
        }
    }

    private void startReturningToOwner() {
        this.returning = true;
        this.setNoPhysics(true);
        this.setNoGravity(true);
        this.inGround = false;
        this.inGroundTime = 0;
        this.shakeTime = 0;
    }

    @Override
    protected boolean tryPickup(Player player) {
        if (this.isNoPhysics() && this.ownedBy(player)) {
            return this.tryReturnToPlayer(player) || super.tryPickup(player);
        }

        return super.tryPickup(player);
    }

    @Override
    public void playerTouch(Player entity) {
        if (this.ownedBy(entity) || this.getOwner() == null) {
            super.playerTouch(entity);
        }
    }

    private boolean tryReturnToPlayer(Player player) {
        if (player.hasInfiniteMaterials()) {
            return true;
        }

        ItemStack returnedStack = this.getPickupItem();
        if (returnedStack.isEmpty()) {
            returnedStack = new ItemStack(ModItems.ZEUS_LIGHTNING.get());
        }

        InteractionHand returnHand = this.returnToOffhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack handStack = player.getItemInHand(returnHand);
        if (handStack.isEmpty()) {
            player.setItemInHand(returnHand, returnedStack);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            return true;
        }

        player.getInventory().placeItemBackInInventory(returnedStack);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        return true;
    }

    private boolean isAcceptableReturnOwner(@Nullable Entity owner) {
        if (owner == null || !owner.isAlive() || owner.level() != this.level()) {
            return false;
        }

        return !(owner instanceof ServerPlayer serverPlayer) || !serverPlayer.isSpectator();
    }

    private void updateForcedChunks() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        long currentChunk = ChunkPos.asLong(this.chunkPosition().x, this.chunkPosition().z);
        Vec3 nextPosition = this.position().add(this.getDeltaMovement());
        long nextChunk = ChunkPos.asLong(
                net.minecraft.util.Mth.floor(nextPosition.x) >> 4,
                net.minecraft.util.Mth.floor(nextPosition.z) >> 4);

        if (!Long.valueOf(currentChunk).equals(this.forcedChunk)) {
            this.setForcedChunk(serverLevel, this.forcedChunk, false);
            this.forcedChunk = currentChunk;
            this.setForcedChunk(serverLevel, this.forcedChunk, true);
        }

        if (nextChunk == currentChunk) {
            this.setForcedChunk(serverLevel, this.forcedNextChunk, false);
            this.forcedNextChunk = null;
            return;
        }

        if (!Long.valueOf(nextChunk).equals(this.forcedNextChunk)) {
            this.setForcedChunk(serverLevel, this.forcedNextChunk, false);
            this.forcedNextChunk = nextChunk;
            this.setForcedChunk(serverLevel, this.forcedNextChunk, true);
        }
    }

    private void releaseForcedChunks() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        this.setForcedChunk(serverLevel, this.forcedChunk, false);
        this.setForcedChunk(serverLevel, this.forcedNextChunk, false);
        this.forcedChunk = null;
        this.forcedNextChunk = null;
    }

    private void setForcedChunk(ServerLevel serverLevel, @Nullable Long chunkLong, boolean forced) {
        if (chunkLong == null) {
            return;
        }

        ChunkPos chunkPos = new ChunkPos(chunkLong);
        serverLevel.setChunkForced(chunkPos.x, chunkPos.z, forced);
    }

    private void spawnLightningStrike(ServerLevel serverLevel, Vec3 location) {
        LightningBolt lightningBolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
        if (lightningBolt == null) {
            return;
        }

        lightningBolt.moveTo(location.x(), location.y(), location.z());
        if (this.getOwner() instanceof ServerPlayer serverPlayer) {
            lightningBolt.setCause(serverPlayer);
        }

        serverLevel.addFreshEntity(lightningBolt);
    }
}
