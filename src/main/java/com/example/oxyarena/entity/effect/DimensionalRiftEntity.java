package com.example.oxyarena.entity.effect;

import java.util.Optional;
import java.util.UUID;

import com.example.oxyarena.event.gameplay.AmetraWarpedGlaiveEvents;
import com.example.oxyarena.registry.ModEntityTypes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class DimensionalRiftEntity extends Entity {
    public static final int DURATION_TICKS = 140;

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER_UUID = SynchedEntityData.defineId(
            DimensionalRiftEntity.class,
            EntityDataSerializers.OPTIONAL_UUID);

    public DimensionalRiftEntity(EntityType<? extends DimensionalRiftEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public DimensionalRiftEntity(Level level, Player owner, Vec3 position, float yaw) {
        this(ModEntityTypes.DIMENSIONAL_RIFT.get(), level);
        this.setOwnerUuid(owner.getUUID());
        this.setPos(position);
        this.xo = position.x;
        this.yo = position.y;
        this.zo = position.z;
        this.setYRot(yaw);
        this.yRotO = yaw;
    }

    @Override
    public void tick() {
        Vec3 previousPosition = this.position();
        this.xo = previousPosition.x;
        this.yo = previousPosition.y;
        this.zo = previousPosition.z;
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();

        super.tick();

        if (!this.level().isClientSide && this.tickCount >= DURATION_TICKS) {
            this.discard();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        this.getOwnerUuid().ifPresent(ownerId -> AmetraWarpedGlaiveEvents.unregisterRift(ownerId, this.getUUID()));
        super.remove(reason);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER_UUID, Optional.empty());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        if (compound.hasUUID("OwnerUuid")) {
            this.setOwnerUuid(compound.getUUID("OwnerUuid"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        this.getOwnerUuid().ifPresent(ownerId -> compound.putUUID("OwnerUuid", ownerId));
    }

    public Optional<UUID> getOwnerUuid() {
        return this.getEntityData().get(DATA_OWNER_UUID);
    }

    private void setOwnerUuid(UUID ownerUuid) {
        this.getEntityData().set(DATA_OWNER_UUID, Optional.of(ownerUuid));
    }
}
