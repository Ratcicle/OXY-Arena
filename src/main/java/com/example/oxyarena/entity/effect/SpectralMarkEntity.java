package com.example.oxyarena.entity.effect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.example.oxyarena.registry.ModEntityTypes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SpectralMarkEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER_UUID = SynchedEntityData.defineId(
            SpectralMarkEntity.class,
            EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> DATA_TARGET_UUID = SynchedEntityData.defineId(
            SpectralMarkEntity.class,
            EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Float> DATA_LOCAL_X = SynchedEntityData.defineId(
            SpectralMarkEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LOCAL_Y = SynchedEntityData.defineId(
            SpectralMarkEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LOCAL_Z = SynchedEntityData.defineId(
            SpectralMarkEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LOCAL_YAW = SynchedEntityData.defineId(
            SpectralMarkEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LOCAL_PITCH = SynchedEntityData.defineId(
            SpectralMarkEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LOCAL_ROLL = SynchedEntityData.defineId(
            SpectralMarkEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_COLOR_MIX = SynchedEntityData.defineId(
            SpectralMarkEntity.class,
            EntityDataSerializers.FLOAT);

    private static final Map<UUID, Map<UUID, Set<SpectralMarkEntity>>> ACTIVE_MARKS_BY_OWNER = new HashMap<>();
    private static final int MAX_AGE_TICKS = 400;
    private static final int TARGET_LOOKUP_GRACE_TICKS = 10;
    private static final float SURFACE_OFFSET = 0.018F;

    private int markAgeTicks;
    private int missingTargetTicks;

    public SpectralMarkEntity(EntityType<? extends SpectralMarkEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public SpectralMarkEntity(Level level, Player owner, LivingEntity target) {
        this(ModEntityTypes.SPECTRAL_MARK.get(), level);
        this.setOwnerUuid(owner.getUUID());
        this.setTargetUuid(target.getUUID());
        this.configureAttachment(target, level.getRandom());
        this.updateAttachmentPose(target);
    }

    public static void spawn(ServerLevel level, Player owner, LivingEntity target) {
        level.addFreshEntity(new SpectralMarkEntity(level, owner, target));
    }

    public static int consumeMarks(ServerLevel level, UUID ownerId, UUID targetId) {
        Map<UUID, Set<SpectralMarkEntity>> marksByTarget = ACTIVE_MARKS_BY_OWNER.get(ownerId);
        if (marksByTarget == null) {
            return 0;
        }

        Set<SpectralMarkEntity> marks = marksByTarget.get(targetId);
        if (marks == null || marks.isEmpty()) {
            return 0;
        }

        int consumed = 0;
        List<SpectralMarkEntity> toDiscard = new ArrayList<>(marks);
        for (SpectralMarkEntity mark : toDiscard) {
            if (mark.level() != level
                    || mark.isRemoved()
                    || !ownerId.equals(mark.getOwnerUuid().orElse(null))
                    || !targetId.equals(mark.getTargetUuid().orElse(null))) {
                continue;
            }

            consumed++;
            mark.discard();
        }

        return consumed;
    }

    public static void clearServerState() {
        ACTIVE_MARKS_BY_OWNER.clear();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER_UUID, Optional.empty());
        builder.define(DATA_TARGET_UUID, Optional.empty());
        builder.define(DATA_LOCAL_X, 0.0F);
        builder.define(DATA_LOCAL_Y, 0.0F);
        builder.define(DATA_LOCAL_Z, 0.0F);
        builder.define(DATA_LOCAL_YAW, 0.0F);
        builder.define(DATA_LOCAL_PITCH, 0.0F);
        builder.define(DATA_LOCAL_ROLL, 0.0F);
        builder.define(DATA_COLOR_MIX, 0.5F);
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

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        this.markAgeTicks++;
        if (this.markAgeTicks >= MAX_AGE_TICKS) {
            this.discard();
            return;
        }

        LivingEntity target = this.findTarget(serverLevel);
        if (target == null || !target.isAlive()) {
            this.missingTargetTicks++;
            if (this.missingTargetTicks > TARGET_LOOKUP_GRACE_TICKS) {
                this.discard();
            }
            return;
        }

        this.missingTargetTicks = 0;
        this.updateAttachmentPose(target);
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!this.level().isClientSide) {
            this.registerActiveMark();
        }
    }

    @Override
    public void onRemovedFromLevel() {
        if (!this.level().isClientSide) {
            this.unregisterActiveMark();
        }
        super.onRemovedFromLevel();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        if (compound.hasUUID("OwnerUuid")) {
            this.setOwnerUuid(compound.getUUID("OwnerUuid"));
        } else {
            this.setOwnerUuid(null);
        }

        if (compound.hasUUID("TargetUuid")) {
            this.setTargetUuid(compound.getUUID("TargetUuid"));
        } else {
            this.setTargetUuid(null);
        }

        this.setLocalX(compound.getFloat("LocalX"));
        this.setLocalY(compound.getFloat("LocalY"));
        this.setLocalZ(compound.getFloat("LocalZ"));
        this.setLocalYaw(compound.getFloat("LocalYaw"));
        this.setLocalPitch(compound.getFloat("LocalPitch"));
        this.setLocalRoll(compound.getFloat("LocalRoll"));
        this.setColorMix(compound.getFloat("ColorMix"));
        this.markAgeTicks = compound.contains("MarkAgeTicks") ? compound.getInt("MarkAgeTicks") : 0;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        this.getOwnerUuid().ifPresent(ownerUuid -> compound.putUUID("OwnerUuid", ownerUuid));
        this.getTargetUuid().ifPresent(targetUuid -> compound.putUUID("TargetUuid", targetUuid));
        compound.putFloat("LocalX", this.getLocalX());
        compound.putFloat("LocalY", this.getLocalY());
        compound.putFloat("LocalZ", this.getLocalZ());
        compound.putFloat("LocalYaw", this.getLocalYaw());
        compound.putFloat("LocalPitch", this.getLocalPitch());
        compound.putFloat("LocalRoll", this.getLocalRoll());
        compound.putFloat("ColorMix", this.getColorMix());
        compound.putInt("MarkAgeTicks", this.markAgeTicks);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    public float getLocalRoll() {
        return this.getEntityData().get(DATA_LOCAL_ROLL);
    }

    public float getColorMix() {
        return this.getEntityData().get(DATA_COLOR_MIX);
    }

    public Optional<UUID> getOwnerUuid() {
        return this.getEntityData().get(DATA_OWNER_UUID);
    }

    public Optional<UUID> getTargetUuid() {
        return this.getEntityData().get(DATA_TARGET_UUID);
    }

    private void configureAttachment(LivingEntity target, RandomSource random) {
        float width = Math.max(0.35F, target.getBbWidth());
        float halfSurface = width * 0.34F + 0.04F;
        float height = Math.max(0.7F, target.getBbHeight());
        float y = Mth.lerp(random.nextFloat(), height * 0.3F, height * 0.8F);

        int face = random.nextInt(4);
        float lateralJitter = (random.nextFloat() - 0.5F) * width * 0.16F;
        float depthJitter = (random.nextFloat() - 0.5F) * width * 0.08F;
        float localX;
        float localZ;
        float outwardYaw;

        switch (face) {
            case 1 -> {
                localX = lateralJitter;
                localZ = -(halfSurface + SURFACE_OFFSET);
                outwardYaw = 180.0F;
            }
            case 2 -> {
                localX = -(halfSurface + SURFACE_OFFSET);
                localZ = depthJitter;
                outwardYaw = -90.0F;
            }
            case 3 -> {
                localX = halfSurface + SURFACE_OFFSET;
                localZ = depthJitter;
                outwardYaw = 90.0F;
            }
            default -> {
                localX = lateralJitter;
                localZ = halfSurface + SURFACE_OFFSET;
                outwardYaw = 0.0F;
            }
        }

        float verticalToCenter = height * 0.55F - y;
        float inwardPitch = (float)Math.toDegrees(Math.atan2(verticalToCenter, halfSurface));

        this.setLocalX(localX);
        this.setLocalY(y);
        this.setLocalZ(localZ);
        this.setLocalYaw(outwardYaw + Mth.lerp(random.nextFloat(), -8.0F, 8.0F));
        this.setLocalPitch(Mth.clamp(inwardPitch, -16.0F, 16.0F) + Mth.lerp(random.nextFloat(), -4.0F, 4.0F));
        this.setLocalRoll(Mth.lerp(random.nextFloat(), -9.0F, 9.0F));
        this.setColorMix(random.nextFloat());
    }

    private void updateAttachmentPose(LivingEntity target) {
        float bodyYaw = target.yBodyRot;
        Vec3 rotatedOffset = rotateYOffset(this.getLocalX(), this.getLocalY(), this.getLocalZ(), bodyYaw);
        Vec3 worldPosition = target.position().add(rotatedOffset);
        this.setPos(worldPosition);
        this.setYRot(bodyYaw + this.getLocalYaw());
        this.setXRot(this.getLocalPitch());
    }

    @Nullable
    private LivingEntity findTarget(ServerLevel level) {
        UUID targetUuid = this.getTargetUuid().orElse(null);
        if (targetUuid == null) {
            return null;
        }

        Entity entity = level.getEntity(targetUuid);
        return entity instanceof LivingEntity livingEntity ? livingEntity : null;
    }

    private void registerActiveMark() {
        UUID ownerUuid = this.getOwnerUuid().orElse(null);
        UUID targetUuid = this.getTargetUuid().orElse(null);
        if (ownerUuid == null || targetUuid == null) {
            return;
        }

        ACTIVE_MARKS_BY_OWNER
                .computeIfAbsent(ownerUuid, ignored -> new HashMap<>())
                .computeIfAbsent(targetUuid, ignored -> new HashSet<>())
                .add(this);
    }

    private void unregisterActiveMark() {
        UUID ownerUuid = this.getOwnerUuid().orElse(null);
        UUID targetUuid = this.getTargetUuid().orElse(null);
        if (ownerUuid == null || targetUuid == null) {
            return;
        }

        Map<UUID, Set<SpectralMarkEntity>> marksByTarget = ACTIVE_MARKS_BY_OWNER.get(ownerUuid);
        if (marksByTarget == null) {
            return;
        }

        Set<SpectralMarkEntity> marks = marksByTarget.get(targetUuid);
        if (marks == null) {
            return;
        }

        marks.remove(this);
        if (marks.isEmpty()) {
            marksByTarget.remove(targetUuid);
        }
        if (marksByTarget.isEmpty()) {
            ACTIVE_MARKS_BY_OWNER.remove(ownerUuid);
        }
    }

    private static Vec3 rotateYOffset(float x, float y, float z, float yawDegrees) {
        double radians = Math.toRadians(yawDegrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double rotatedX = x * cos - z * sin;
        double rotatedZ = x * sin + z * cos;
        return new Vec3(rotatedX, y, rotatedZ);
    }

    private float getLocalX() {
        return this.getEntityData().get(DATA_LOCAL_X);
    }

    private void setLocalX(float localX) {
        this.getEntityData().set(DATA_LOCAL_X, localX);
    }

    private float getLocalY() {
        return this.getEntityData().get(DATA_LOCAL_Y);
    }

    private void setLocalY(float localY) {
        this.getEntityData().set(DATA_LOCAL_Y, localY);
    }

    private float getLocalZ() {
        return this.getEntityData().get(DATA_LOCAL_Z);
    }

    private void setLocalZ(float localZ) {
        this.getEntityData().set(DATA_LOCAL_Z, localZ);
    }

    private float getLocalYaw() {
        return this.getEntityData().get(DATA_LOCAL_YAW);
    }

    private void setLocalYaw(float localYaw) {
        this.getEntityData().set(DATA_LOCAL_YAW, localYaw);
    }

    private float getLocalPitch() {
        return this.getEntityData().get(DATA_LOCAL_PITCH);
    }

    private void setLocalPitch(float localPitch) {
        this.getEntityData().set(DATA_LOCAL_PITCH, localPitch);
    }

    private void setLocalRoll(float localRoll) {
        this.getEntityData().set(DATA_LOCAL_ROLL, localRoll);
    }

    private void setColorMix(float colorMix) {
        this.getEntityData().set(DATA_COLOR_MIX, colorMix);
    }

    private void setOwnerUuid(@Nullable UUID ownerUuid) {
        this.getEntityData().set(DATA_OWNER_UUID, Optional.ofNullable(ownerUuid));
    }

    private void setTargetUuid(@Nullable UUID targetUuid) {
        this.getEntityData().set(DATA_TARGET_UUID, Optional.ofNullable(targetUuid));
    }
}
