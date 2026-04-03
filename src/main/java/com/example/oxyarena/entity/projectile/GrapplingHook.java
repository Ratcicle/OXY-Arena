package com.example.oxyarena.entity.projectile;

import java.util.List;

import javax.annotation.Nullable;

import com.example.oxyarena.registry.ModEntityTypes;
import com.example.oxyarena.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class GrapplingHook extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Boolean> DATA_ANCHORED = SynchedEntityData.defineId(
            GrapplingHook.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_ANCHOR_Y_ROT = SynchedEntityData.defineId(
            GrapplingHook.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ANCHOR_X_ROT = SynchedEntityData.defineId(
            GrapplingHook.class,
            EntityDataSerializers.FLOAT);
    private static final double MAX_OWNER_DISTANCE = 64.0D;
    private static final double MAX_OWNER_DISTANCE_SQR = MAX_OWNER_DISTANCE * MAX_OWNER_DISTANCE;
    private static final double HOOK_SEARCH_RADIUS = 72.0D;
    private static final double ARRIVAL_DISTANCE = 1.6D;
    private static final double PULL_ACCELERATION = 0.22D;
    private static final double CURRENT_VELOCITY_KEEP = 0.86D;
    private static final double MAX_PULL_SPEED = 1.65D;
    private static final double MAX_PULL_SPEED_SQR = MAX_PULL_SPEED * MAX_PULL_SPEED;
    private static final double ANCHOR_SURFACE_OFFSET = 0.08D;
    private static final int MAX_LIFE_TICKS = 200;

    private int lifeTicks;

    public GrapplingHook(EntityType<? extends GrapplingHook> entityType, Level level) {
        super(entityType, level);
        this.noCulling = true;
    }

    public GrapplingHook(Level level, LivingEntity shooter) {
        super(ModEntityTypes.GRAPPLING_HOOK.get(), shooter, level);
        this.noCulling = true;
        this.setItem(ModItems.GRAPPLING_HOOK.get().getDefaultInstance());
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.GRAPPLING_HOOK.get();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ANCHORED, false);
        builder.define(DATA_ANCHOR_Y_ROT, 0.0F);
        builder.define(DATA_ANCHOR_X_ROT, 0.0F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("Anchored", this.isAnchored());
        compound.putFloat("AnchorYRot", this.getAnchorYRot());
        compound.putFloat("AnchorXRot", this.getAnchorXRot());
        compound.putInt("LifeTicks", this.lifeTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setAnchored(compound.getBoolean("Anchored"));
        this.getEntityData().set(DATA_ANCHOR_Y_ROT, compound.getFloat("AnchorYRot"));
        this.getEntityData().set(DATA_ANCHOR_X_ROT, compound.getFloat("AnchorXRot"));
        this.lifeTicks = compound.getInt("LifeTicks");
    }

    @Override
    public void tick() {
        super.tick();

        this.lifeTicks++;
        if (this.lifeTicks >= MAX_LIFE_TICKS && !this.level().isClientSide) {
            this.discard();
            return;
        }

        if (this.isAnchored()) {
            this.setDeltaMovement(Vec3.ZERO);
        }

        if (this.level().isClientSide) {
            return;
        }

        Player owner = this.getPlayerOwner();
        if (owner == null
                || !owner.isAlive()
                || owner.isRemoved()
                || owner.level() != this.level()
                || !this.isOwnerHoldingGrapplingGun(owner)
                || owner.distanceToSqr(this) > MAX_OWNER_DISTANCE_SQR) {
            this.discard();
            return;
        }

        if (this.isAnchored()) {
            this.pullOwnerToHook(owner);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (this.isAnchored()) {
            return;
        }

        super.onHitBlock(result);

        Vec3 anchorPosition = result.getLocation()
                .add(Vec3.atLowerCornerOf(result.getDirection().getNormal())
                        .scale(ANCHOR_SURFACE_OFFSET));
        this.moveTo(anchorPosition.x, anchorPosition.y, anchorPosition.z, this.getYRot(), this.getXRot());
        this.setDeltaMovement(Vec3.ZERO);
        this.getEntityData().set(DATA_ANCHOR_Y_ROT, this.getYRot());
        this.getEntityData().set(DATA_ANCHOR_X_ROT, this.getXRot());
        this.setAnchored(true);
        this.noPhysics = true;

        this.level().playSound(
                null,
                anchorPosition.x,
                anchorPosition.y,
                anchorPosition.z,
                SoundEvents.CHAIN_HIT,
                SoundSource.PLAYERS,
                0.7F,
                1.0F);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return false;
    }

    @Override
    protected double getDefaultGravity() {
        return this.isAnchored() ? 0.0D : 0.03D;
    }

    public boolean isAnchored() {
        return this.getEntityData().get(DATA_ANCHORED);
    }

    public float getAnchorYRot() {
        return this.getEntityData().get(DATA_ANCHOR_Y_ROT);
    }

    public float getAnchorXRot() {
        return this.getEntityData().get(DATA_ANCHOR_X_ROT);
    }

    @Nullable
    public Player getPlayerOwner() {
        Entity owner = this.getOwner();
        return owner instanceof Player player ? player : null;
    }

    public static boolean discardOwnedHooks(Player owner) {
        AABB searchBox = owner.getBoundingBox().inflate(HOOK_SEARCH_RADIUS);
        List<GrapplingHook> hooks = owner.level().getEntitiesOfClass(
                GrapplingHook.class,
                searchBox,
                grapplingHook -> grapplingHook.ownedBy(owner));

        if (hooks.isEmpty()) {
            return false;
        }

        for (GrapplingHook grapplingHook : hooks) {
            grapplingHook.discard();
        }

        return true;
    }

    private void setAnchored(boolean anchored) {
        this.getEntityData().set(DATA_ANCHORED, anchored);
    }

    private boolean isOwnerHoldingGrapplingGun(Player owner) {
        return owner.getMainHandItem().is(ModItems.GRAPPLING_GUN.get())
                || owner.getOffhandItem().is(ModItems.GRAPPLING_GUN.get());
    }

    private void pullOwnerToHook(Player owner) {
        Vec3 ownerPosition = owner.getEyePosition();
        Vec3 toHook = this.position().subtract(ownerPosition);
        double distance = toHook.length();

        if (distance <= ARRIVAL_DISTANCE) {
            owner.setDeltaMovement(owner.getDeltaMovement().scale(0.25D));
            owner.fallDistance = 0.0F;
            owner.hasImpulse = true;
            owner.hurtMarked = true;
            this.discard();
            return;
        }

        Vec3 pullVelocity = owner.getDeltaMovement()
                .scale(CURRENT_VELOCITY_KEEP)
                .add(toHook.normalize().scale(PULL_ACCELERATION));

        if (pullVelocity.lengthSqr() > MAX_PULL_SPEED_SQR) {
            pullVelocity = pullVelocity.normalize().scale(MAX_PULL_SPEED);
        }

        owner.setDeltaMovement(pullVelocity);
        owner.fallDistance = 0.0F;
        owner.hasImpulse = true;
        owner.hurtMarked = true;
    }
}
