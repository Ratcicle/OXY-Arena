package com.example.oxyarena.entity.event;

import com.example.oxyarena.serverevent.AirdropLootPool;
import com.example.oxyarena.registry.ModEntityTypes;
import com.example.oxyarena.registry.ModBlocks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class AirdropCrateEntity extends Entity {
    public static final double DESCENT_SPEED_BLOCKS_PER_TICK = 0.05D;

    private static final EntityDataAccessor<Float> DATA_LANDING_Y = SynchedEntityData.defineId(
            AirdropCrateEntity.class,
            EntityDataSerializers.FLOAT);
    private static final int DESCENT_PARTICLE_INTERVAL_TICKS = 2;
    private static final double DESCENT_PARTICLE_RADIUS = 0.2D;

    public AirdropCrateEntity(EntityType<? extends AirdropCrateEntity> entityType, Level level) {
        super(entityType, level);
        this.noCulling = true;
        this.noPhysics = true;
    }

    public AirdropCrateEntity(Level level, double x, double y, double z) {
        this(level, x, y, z, y);
    }

    public AirdropCrateEntity(Level level, double x, double y, double z, double landingY) {
        this(ModEntityTypes.AIRDROP_CRATE.get(), level);
        this.setLandingY(landingY);
        this.setPos(x, y, z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_LANDING_Y, 0.0F);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            if (!this.hasLanded() && this.tickCount % DESCENT_PARTICLE_INTERVAL_TICKS == 0) {
                this.spawnDescentParticles();
            }
            return;
        }

        if (!this.hasLanded()) {
            double currentY = this.getY();
            double nextY = Math.max(this.getLandingY(), currentY - DESCENT_SPEED_BLOCKS_PER_TICK);
            this.setDeltaMovement(0.0D, nextY - currentY, 0.0D);
            this.setPos(this.getX(), nextY, this.getZ());
            this.hasImpulse = true;
            return;
        }

        this.completeLanding((ServerLevel)this.level());
    }

    public boolean hasLanded() {
        return this.getY() <= this.getLandingY() + 1.0E-4D;
    }

    public double getLandingY() {
        return this.getEntityData().get(DATA_LANDING_Y);
    }

    private void setLandingY(double landingY) {
        this.getEntityData().set(DATA_LANDING_Y, (float)landingY);
    }

    private void spawnDescentParticles() {
        double offsetX = (this.random.nextDouble() - 0.5D) * DESCENT_PARTICLE_RADIUS * 2.0D;
        double offsetZ = (this.random.nextDouble() - 0.5D) * DESCENT_PARTICLE_RADIUS * 2.0D;
        double particleY = this.getY() + 0.08D + this.random.nextDouble() * 0.15D;

        this.level().addParticle(
                ParticleTypes.SMOKE,
                this.getX() + offsetX,
                particleY,
                this.getZ() + offsetZ,
                offsetX * 0.02D,
                0.02D,
                offsetZ * 0.02D);
    }

    private void completeLanding(ServerLevel level) {
        BlockPos chestPos = this.blockPosition();
        level.setBlockAndUpdate(chestPos, ModBlocks.OXYDROP_CRATE.get().defaultBlockState());

        BlockEntity blockEntity = level.getBlockEntity(chestPos);
        if (blockEntity instanceof Container container) {
            AirdropLootPool.fillChest(level.random, container);
            blockEntity.setChanged();
        }

        level.playSound(
                null,
                chestPos,
                SoundEvents.ANVIL_LAND,
                SoundSource.BLOCKS,
                1.8F,
                0.7F);
        level.playSound(
                null,
                chestPos,
                SoundEvents.BARREL_CLOSE,
                SoundSource.BLOCKS,
                1.0F,
                0.85F);
        level.sendParticles(
                ParticleTypes.CLOUD,
                chestPos.getX() + 0.5D,
                chestPos.getY() + 0.15D,
                chestPos.getZ() + 0.5D,
                24,
                0.35D,
                0.08D,
                0.35D,
                0.02D);
        level.sendParticles(
                ParticleTypes.POOF,
                chestPos.getX() + 0.5D,
                chestPos.getY() + 0.2D,
                chestPos.getZ() + 0.5D,
                12,
                0.2D,
                0.05D,
                0.2D,
                0.01D);

        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable(
                        "event.oxyarena.airdrop.landed",
                        chestPos.getX(),
                        chestPos.getY(),
                        chestPos.getZ()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                false);
        this.discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        this.setLandingY(compound.getDouble("LandingY"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.putDouble("LandingY", this.getLandingY());
    }
}
