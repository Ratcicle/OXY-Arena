package com.example.oxyarena.entity.effect;

import java.util.Optional;
import java.util.UUID;

import com.example.oxyarena.registry.ModEntityTypes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class GhostSaberEchoEntity extends Entity {
    public static final int ECHO_DELAY_TICKS = 30;
    public static final int ECHO_DURATION_TICKS = 6;
    private static final int FAILSAFE_LIFETIME_TICKS = 80;
    private static final EntityDataAccessor<Vector3f> DATA_RENDER_ORIGIN = SynchedEntityData.defineId(
            GhostSaberEchoEntity.class,
            EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> DATA_RENDER_TARGET = SynchedEntityData.defineId(
            GhostSaberEchoEntity.class,
            EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER_UUID = SynchedEntityData.defineId(
            GhostSaberEchoEntity.class,
            EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DATA_MOVING = SynchedEntityData.defineId(
            GhostSaberEchoEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_MOVE_PROGRESS = SynchedEntityData.defineId(
            GhostSaberEchoEntity.class,
            EntityDataSerializers.FLOAT);
    private float previousMoveProgress;
    private float clientPreviousMoveProgress;
    private float clientMoveProgress;
    private boolean wasMovingClientSide;

    public GhostSaberEchoEntity(EntityType<? extends GhostSaberEchoEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public GhostSaberEchoEntity(Level level, UUID ownerId, Vec3 position, Vec3 target, float yaw, float pitch) {
        this(ModEntityTypes.GHOST_SABER_ECHO.get(), level);
        this.setPos(position.x, position.y, position.z);
        this.setOwnerUuid(ownerId);
        this.setRenderPath(position, target);
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.yRotO = yaw;
        this.xRotO = pitch;
    }

    public static GhostSaberEchoEntity spawn(ServerLevel level, UUID ownerId, Vec3 position, Vec3 target, float yaw, float pitch) {
        GhostSaberEchoEntity echo = new GhostSaberEchoEntity(level, ownerId, position, target, yaw, pitch);
        level.addFreshEntity(echo);
        return echo;
    }

    @Override
    public void tick() {
        this.previousMoveProgress = this.getMoveProgress();
        Vec3 previousPosition = this.position();
        this.xo = previousPosition.x;
        this.yo = previousPosition.y;
        this.zo = previousPosition.z;
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        super.tick();
        if (this.level().isClientSide) {
            this.tickClientRenderProgress();
        }

        if (!this.level().isClientSide && this.tickCount > FAILSAFE_LIFETIME_TICKS) {
            this.discard();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RENDER_ORIGIN, new Vector3f());
        builder.define(DATA_RENDER_TARGET, new Vector3f());
        builder.define(DATA_OWNER_UUID, Optional.empty());
        builder.define(DATA_MOVING, false);
        builder.define(DATA_MOVE_PROGRESS, 0.0F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0D;
    }

    public Vec3 getSmoothRenderPosition(float partialTick) {
        Vec3 origin = vectorToVec3(this.getEntityData().get(DATA_RENDER_ORIGIN));
        Vec3 target = vectorToVec3(this.getEntityData().get(DATA_RENDER_TARGET));
        if (!this.isMoving()) {
            return origin;
        }

        double progress = this.level().isClientSide
                ? Mth.clamp(Mth.lerp(partialTick, this.clientPreviousMoveProgress, this.clientMoveProgress), 0.0F, 1.0F)
                : Mth.clamp(Mth.lerp(partialTick, this.previousMoveProgress, this.getMoveProgress()), 0.0F, 1.0F);
        return origin.lerp(target, smoothstep(progress));
    }

    public Optional<UUID> getOwnerUuid() {
        return this.getEntityData().get(DATA_OWNER_UUID);
    }

    public boolean isMoving() {
        return this.getEntityData().get(DATA_MOVING);
    }

    public float getMoveProgress() {
        return this.getEntityData().get(DATA_MOVE_PROGRESS);
    }

    public void setMoving(boolean moving) {
        this.getEntityData().set(DATA_MOVING, moving);
    }

    public void setMoveProgress(float moveProgress) {
        this.getEntityData().set(DATA_MOVE_PROGRESS, Mth.clamp(moveProgress, 0.0F, 1.0F));
    }

    public void retarget(Vec3 target) {
        Vec3 origin = vectorToVec3(this.getEntityData().get(DATA_RENDER_ORIGIN));
        this.setRenderPath(origin, target);
    }

    private void setRenderPath(Vec3 origin, Vec3 target) {
        this.getEntityData().set(DATA_RENDER_ORIGIN, new Vector3f((float)origin.x, (float)origin.y, (float)origin.z));
        this.getEntityData().set(DATA_RENDER_TARGET, new Vector3f((float)target.x, (float)target.y, (float)target.z));
    }

    private void setOwnerUuid(UUID ownerId) {
        this.getEntityData().set(DATA_OWNER_UUID, Optional.of(ownerId));
    }

    private void tickClientRenderProgress() {
        this.clientPreviousMoveProgress = this.clientMoveProgress;
        if (!this.isMoving()) {
            this.wasMovingClientSide = false;
            this.clientMoveProgress = 0.0F;
            this.clientPreviousMoveProgress = 0.0F;
            return;
        }

        if (!this.wasMovingClientSide) {
            this.wasMovingClientSide = true;
            this.clientMoveProgress = 0.0F;
            this.clientPreviousMoveProgress = 0.0F;
        }

        this.clientMoveProgress = Mth.clamp(
                this.clientMoveProgress + 1.0F / ECHO_DURATION_TICKS,
                this.getMoveProgress(),
                1.0F);
    }

    private static Vec3 vectorToVec3(Vector3f vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    private static double smoothstep(double progress) {
        double clampedProgress = Math.max(0.0D, Math.min(1.0D, progress));
        return clampedProgress * clampedProgress * (3.0D - 2.0D * clampedProgress);
    }
}
