package com.example.oxyarena.entity.event;

import com.example.oxyarena.registry.ModEntityTypes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class AirdropCrateEntity extends Entity {
    public AirdropCrateEntity(EntityType<? extends AirdropCrateEntity> entityType, Level level) {
        super(entityType, level);
        this.noCulling = true;
        this.noPhysics = true;
    }

    public AirdropCrateEntity(Level level, double x, double y, double z) {
        this(ModEntityTypes.AIRDROP_CRATE.get(), level);
        this.setPos(x, y, z);
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
