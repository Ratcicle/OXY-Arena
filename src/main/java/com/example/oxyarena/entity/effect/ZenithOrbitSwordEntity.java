package com.example.oxyarena.entity.effect;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.example.oxyarena.registry.ModEntityTypes;
import com.example.oxyarena.registry.ModItems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ZenithOrbitSwordEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER_UUID = SynchedEntityData.defineId(
            ZenithOrbitSwordEntity.class,
            EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<ItemStack> DATA_SWORD_STACK = SynchedEntityData.defineId(
            ZenithOrbitSwordEntity.class,
            EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Float> DATA_BASE_DAMAGE = SynchedEntityData.defineId(
            ZenithOrbitSwordEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(
            ZenithOrbitSwordEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_INCLINATION_DEGREES = SynchedEntityData.defineId(
            ZenithOrbitSwordEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PHASE_OFFSET_DEGREES = SynchedEntityData.defineId(
            ZenithOrbitSwordEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DIRECTION_SIGN = SynchedEntityData.defineId(
            ZenithOrbitSwordEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_VISUAL_PITCH = SynchedEntityData.defineId(
            ZenithOrbitSwordEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_VISUAL_ROLL = SynchedEntityData.defineId(
            ZenithOrbitSwordEntity.class,
            EntityDataSerializers.FLOAT);

    private static final int DURATION_TICKS = 50;
    private static final float ANGULAR_SPEED_DEGREES_PER_TICK = 24.0F;
    private static final float MIN_ORBIT_RADIUS = 1.0F;
    private static final float MAX_ORBIT_RADIUS = 8.0F;
    private static final float INNER_SELF_SPIN_MULTIPLIER = 1.05F;
    private static final float OUTER_SELF_SPIN_MULTIPLIER = 1.4F;
    private static final double ORBIT_CENTER_Y_OFFSET = 0.9D;
    private static final double CONTACT_RADIUS = 1.0D;
    private static final int OWNER_LOOKUP_GRACE_TICKS = 10;
    private static final int TRAIL_HISTORY_SIZE = 8;
    private static final double TRAIL_MIN_SAMPLE_DISTANCE_SQR = 0.0025D;
    private static final double TRAIL_RESET_DISTANCE_SQR = 25.0D;
    private static final float DEFAULT_VISUAL_PITCH = 0.0F;
    private static final float DEFAULT_VISUAL_ROLL = 90.0F;
    private static final Map<UUID, Set<UUID>> ACTIVE_SWORDS_BY_OWNER = new HashMap<>();
    private static final OrbitSwordSpec[] ORBIT_SWORD_SPECS = new OrbitSwordSpec[] {
            new OrbitSwordSpec(() -> new ItemStack(Items.WOODEN_SWORD), 4.0F, 1.0F, -12.0F, 0.0F, 1.0F),
            new OrbitSwordSpec(() -> new ItemStack(Items.STONE_SWORD), 5.0F, 1.875F, -9.0F, 45.0F, -1.0F),
            new OrbitSwordSpec(() -> new ItemStack(Items.IRON_SWORD), 6.0F, 2.75F, -6.0F, 72.0F, 1.0F),
            new OrbitSwordSpec(() -> new ItemStack(Items.GOLDEN_SWORD), 4.0F, 3.625F, -3.0F, 135.0F, -1.0F),
            new OrbitSwordSpec(() -> new ItemStack(Items.DIAMOND_SWORD), 7.0F, 4.5F, 0.0F, 144.0F, 1.0F),
            new OrbitSwordSpec(() -> new ItemStack(Items.NETHERITE_SWORD), 8.0F, 5.375F, 3.0F, 225.0F, -1.0F),
            new OrbitSwordSpec(() -> ModItems.CITRINE_SWORD.get().getDefaultInstance(), 5.5F, 6.25F, 6.0F, 216.0F, 1.0F),
            new OrbitSwordSpec(() -> ModItems.COBALT_SWORD.get().getDefaultInstance(), 6.8F, 7.125F, 9.0F, 315.0F, -1.0F),
            new OrbitSwordSpec(() -> ModItems.AMETRA_SWORD.get().getDefaultInstance(), 7.0F, 8.0F, 12.0F, 288.0F, 1.0F) };

    private final Set<UUID> hitTargetIds = new HashSet<>();
    private final ArrayDeque<Vec3> renderTrailHistory = new ArrayDeque<>(TRAIL_HISTORY_SIZE);

    public ZenithOrbitSwordEntity(EntityType<? extends ZenithOrbitSwordEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public ZenithOrbitSwordEntity(
            Level level,
            Player owner,
            ItemStack swordStack,
            float baseDamage,
            float radius,
            float inclinationDegrees,
            float phaseOffsetDegrees,
            float directionSign) {
        this(ModEntityTypes.ZENITH_ORBIT_SWORD.get(), level);
        this.setOwnerUuid(owner.getUUID());
        this.setSwordStack(swordStack);
        this.setBaseDamage(baseDamage);
        this.setRadius(radius);
        this.setInclinationDegrees(inclinationDegrees);
        this.setPhaseOffsetDegrees(phaseOffsetDegrees);
        this.setDirectionSign(directionSign);
        this.setVisualPitch(DEFAULT_VISUAL_PITCH);
        this.setVisualRoll(DEFAULT_VISUAL_ROLL);
        Vec3 initialPos = this.computeOrbitPosition(owner, 0.0F);
        this.setPos(initialPos);
        this.xo = initialPos.x;
        this.yo = initialPos.y;
        this.zo = initialPos.z;
        this.updateVisualRotation(owner.position(), initialPos);
    }

    public static void spawnAll(Player owner) {
        if (owner.level().isClientSide) {
            return;
        }

        for (OrbitSwordSpec orbitSwordSpec : ORBIT_SWORD_SPECS) {
            ZenithOrbitSwordEntity orbitSword = new ZenithOrbitSwordEntity(
                    owner.level(),
                    owner,
                    orbitSwordSpec.stackSupplier().get(),
                    orbitSwordSpec.baseDamage(),
                    orbitSwordSpec.radius(),
                    orbitSwordSpec.inclinationDegrees(),
                    orbitSwordSpec.phaseOffsetDegrees(),
                    orbitSwordSpec.directionSign());
            owner.level().addFreshEntity(orbitSword);
            registerActiveSword(owner.getUUID(), orbitSword.getUUID());
        }
    }

    public static void discardOwnedOrbitSwords(Player owner) {
        if (!(owner instanceof ServerPlayer serverPlayer) || serverPlayer.getServer() == null) {
            return;
        }

        UUID ownerId = owner.getUUID();
        Set<UUID> activeSwords = ACTIVE_SWORDS_BY_OWNER.get(ownerId);
        if (activeSwords == null || activeSwords.isEmpty()) {
            return;
        }

        Set<UUID> swordIds = Set.copyOf(activeSwords);
        for (ServerLevel level : serverPlayer.getServer().getAllLevels()) {
            for (UUID swordId : swordIds) {
                Entity entity = level.getEntity(swordId);
                if (entity instanceof ZenithOrbitSwordEntity orbitSword) {
                    orbitSword.discard();
                }
            }
        }

        ACTIVE_SWORDS_BY_OWNER.remove(ownerId);
    }

    @Override
    public void tick() {
        Vec3 previousPosition = this.position();
        this.xo = previousPosition.x;
        this.yo = previousPosition.y;
        this.zo = previousPosition.z;

        super.tick();

        Player owner = this.getOwnerPlayer();
        if (owner == null) {
            if (!this.level().isClientSide || this.tickCount > OWNER_LOOKUP_GRACE_TICKS) {
                this.discard();
            }
            return;
        }

        if (!this.isOwnerStillValid(owner)) {
            this.discard();
            return;
        }

        Vec3 orbitPosition = this.computeOrbitPosition(owner, 0.0F);
        this.setPos(orbitPosition);
        this.updateVisualRotation(previousPosition, orbitPosition);
        if (this.level().isClientSide) {
            this.updateClientTrailHistory(orbitPosition);
        }

        if (!this.level().isClientSide) {
            this.tryDamageTarget(owner, previousPosition, orbitPosition);
        }

        if (this.tickCount >= DURATION_TICKS) {
            this.discard();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        this.getOwnerUuid().ifPresent(ownerId -> unregisterActiveSword(ownerId, this.getUUID()));
        super.remove(reason);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER_UUID, Optional.empty());
        builder.define(DATA_SWORD_STACK, ItemStack.EMPTY);
        builder.define(DATA_BASE_DAMAGE, 0.0F);
        builder.define(DATA_RADIUS, 1.0F);
        builder.define(DATA_INCLINATION_DEGREES, 0.0F);
        builder.define(DATA_PHASE_OFFSET_DEGREES, 0.0F);
        builder.define(DATA_DIRECTION_SIGN, 1.0F);
        builder.define(DATA_VISUAL_PITCH, DEFAULT_VISUAL_PITCH);
        builder.define(DATA_VISUAL_ROLL, DEFAULT_VISUAL_ROLL);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        if (compound.hasUUID("OwnerUuid")) {
            this.setOwnerUuid(compound.getUUID("OwnerUuid"));
        }
        this.setBaseDamage(compound.getFloat("BaseDamage"));
        this.setRadius(compound.getFloat("Radius"));
        this.setInclinationDegrees(compound.getFloat("Inclination"));
        this.setPhaseOffsetDegrees(compound.getFloat("Phase"));
        this.setDirectionSign(compound.contains("DirectionSign") ? compound.getFloat("DirectionSign") : 1.0F);
        this.setVisualPitch(compound.contains("VisualPitch") ? compound.getFloat("VisualPitch") : DEFAULT_VISUAL_PITCH);
        this.setVisualRoll(compound.contains("VisualRoll") ? compound.getFloat("VisualRoll") : DEFAULT_VISUAL_ROLL);
        this.hitTargetIds.clear();
        ListTag hitTargetsTag = compound.getList("HitTargets", Tag.TAG_COMPOUND);
        for (int index = 0; index < hitTargetsTag.size(); index++) {
            CompoundTag hitTargetTag = hitTargetsTag.getCompound(index);
            if (hitTargetTag.hasUUID("Uuid")) {
                this.hitTargetIds.add(hitTargetTag.getUUID("Uuid"));
            }
        }
        if (compound.contains("SwordStack")) {
            ItemStack swordStack = ItemStack.parse(this.registryAccess(), compound.getCompound("SwordStack"))
                    .orElse(ItemStack.EMPTY);
            this.setSwordStack(swordStack);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        this.getOwnerUuid().ifPresent(ownerId -> compound.putUUID("OwnerUuid", ownerId));
        compound.putFloat("BaseDamage", this.getBaseDamage());
        compound.putFloat("Radius", this.getRadius());
        compound.putFloat("Inclination", this.getInclinationDegrees());
        compound.putFloat("Phase", this.getPhaseOffsetDegrees());
        compound.putFloat("DirectionSign", this.getDirectionSign());
        compound.putFloat("VisualPitch", this.getVisualPitch());
        compound.putFloat("VisualRoll", this.getVisualRoll());
        ListTag hitTargetsTag = new ListTag();
        for (UUID hitTargetId : this.hitTargetIds) {
            CompoundTag hitTargetTag = new CompoundTag();
            hitTargetTag.putUUID("Uuid", hitTargetId);
            hitTargetsTag.add(hitTargetTag);
        }
        compound.put("HitTargets", hitTargetsTag);
        if (!this.getSwordStack().isEmpty()) {
            compound.put("SwordStack", this.getSwordStack().save(this.registryAccess(), new CompoundTag()));
        }
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    public ItemStack getSwordStack() {
        return this.getEntityData().get(DATA_SWORD_STACK);
    }

    public float getInclinationDegrees() {
        return this.getEntityData().get(DATA_INCLINATION_DEGREES);
    }

    public float getVisualPitch() {
        return this.getEntityData().get(DATA_VISUAL_PITCH);
    }

    public float getVisualRoll() {
        return this.getEntityData().get(DATA_VISUAL_ROLL);
    }

    public float getRenderOrbitAngleDegrees(float partialTick) {
        return this.getPhaseOffsetDegrees()
                + this.getDirectionSign() * (this.tickCount + partialTick) * ANGULAR_SPEED_DEGREES_PER_TICK;
    }

    public float getRenderSelfSpinDegrees(float partialTick) {
        float radiusProgress = Mth.clamp(
                (this.getRadius() - MIN_ORBIT_RADIUS) / (MAX_ORBIT_RADIUS - MIN_ORBIT_RADIUS),
                0.0F,
                1.0F);
        float selfSpinMultiplier = Mth.lerp(
                radiusProgress,
                INNER_SELF_SPIN_MULTIPLIER,
                OUTER_SELF_SPIN_MULTIPLIER);
        return -(this.tickCount + partialTick)
                * ANGULAR_SPEED_DEGREES_PER_TICK
                * selfSpinMultiplier
                * this.getDirectionSign();
    }

    @Nullable
    public Player getRenderOwnerPlayer() {
        return this.getOwnerPlayer();
    }

    public Vec3 getRenderOrbitCenter(float partialTick) {
        Player owner = this.getOwnerPlayer();
        if (owner == null) {
            return this.position();
        }

        double ownerX = Mth.lerp(partialTick, owner.xo, owner.getX());
        double ownerY = Mth.lerp(partialTick, owner.yo, owner.getY());
        double ownerZ = Mth.lerp(partialTick, owner.zo, owner.getZ());
        return new Vec3(ownerX, ownerY + ORBIT_CENTER_Y_OFFSET, ownerZ);
    }

    public List<Vec3> getRenderTrailPoints(float partialTick) {
        if (this.renderTrailHistory.isEmpty()) {
            return List.of();
        }

        Vec3 currentRenderPosition = new Vec3(
                Mth.lerp(partialTick, this.xo, this.getX()),
                Mth.lerp(partialTick, this.yo, this.getY()),
                Mth.lerp(partialTick, this.zo, this.getZ()));
        List<Vec3> trailPoints = new ArrayList<>(this.renderTrailHistory.size() + 1);
        trailPoints.add(currentRenderPosition);
        Vec3 lastPoint = currentRenderPosition;
        for (Vec3 trailPoint : this.renderTrailHistory) {
            if (trailPoint.distanceToSqr(lastPoint) < 1.0E-4D) {
                continue;
            }
            trailPoints.add(trailPoint);
            lastPoint = trailPoint;
        }
        return trailPoints;
    }

    private Optional<UUID> getOwnerUuid() {
        return this.getEntityData().get(DATA_OWNER_UUID);
    }

    @Nullable
    private Player getOwnerPlayer() {
        return this.getOwnerUuid()
                .map(this.level()::getPlayerByUUID)
                .orElse(null);
    }

    private boolean isOwnerStillValid(Player owner) {
        return owner.isAlive()
                && !owner.isRemoved()
                && owner.level() == this.level()
                && owner.getMainHandItem().is(ModItems.ZENITH.get());
    }

    private Vec3 computeOrbitPosition(Player owner, float partialTick) {
        float angleDegrees = this.getPhaseOffsetDegrees()
                + this.getDirectionSign() * (this.tickCount + partialTick) * ANGULAR_SPEED_DEGREES_PER_TICK;
        double angleRadians = Math.toRadians(angleDegrees);
        double xOffset = this.getRadius() * Math.cos(angleRadians);
        double zOffset = this.getRadius() * Math.sin(angleRadians);

        double inclinationRadians = Math.toRadians(this.getInclinationDegrees());
        double tiltedYOffset = zOffset * Math.sin(inclinationRadians);
        double tiltedZOffset = zOffset * Math.cos(inclinationRadians);

        return new Vec3(
                owner.getX() + xOffset,
                owner.getY() + ORBIT_CENTER_Y_OFFSET + tiltedYOffset,
                owner.getZ() + tiltedZOffset);
    }

    private void tryDamageTarget(Player owner, Vec3 previousPosition, Vec3 currentPosition) {
        AABB hitBox = new AABB(previousPosition, currentPosition).inflate(CONTACT_RADIUS);
        List<LivingEntity> candidates = this.level().getEntitiesOfClass(
                LivingEntity.class,
                hitBox,
                this::isValidTarget);

        candidates.sort((left, right) -> Double.compare(
                left.distanceToSqr(currentPosition),
                right.distanceToSqr(currentPosition)));

        for (LivingEntity target : candidates) {
            if (this.hitTargetIds.contains(target.getUUID())) {
                continue;
            }

            if (!this.intersectsSwordPath(target, previousPosition, currentPosition)
                    || !this.hasClearPathToTarget(currentPosition, target)) {
                continue;
            }

            target.invulnerableTime = 0;
            target.hurtTime = 0;
            if (target.hurt(owner.damageSources().playerAttack(owner), this.getBaseDamage())) {
                this.hitTargetIds.add(target.getUUID());
            }
        }
    }

    private boolean isValidTarget(LivingEntity target) {
        if (!target.isAlive()) {
            return false;
        }

        if (target instanceof ArmorStand armorStand && armorStand.isMarker()) {
            return false;
        }

        return this.getOwnerUuid().map(ownerId -> !ownerId.equals(target.getUUID())).orElse(true);
    }

    private boolean intersectsSwordPath(LivingEntity target, Vec3 previousPosition, Vec3 currentPosition) {
        AABB targetHitBox = target.getBoundingBox().inflate(CONTACT_RADIUS);
        return targetHitBox.contains(currentPosition) || targetHitBox.clip(previousPosition, currentPosition).isPresent();
    }

    private boolean hasClearPathToTarget(Vec3 swordPosition, LivingEntity target) {
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        BlockHitResult hitResult = this.level().clip(new ClipContext(
                swordPosition,
                targetCenter,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this));
        return hitResult.getType() == HitResult.Type.MISS;
    }

    private void updateVisualRotation(Vec3 previousPosition, Vec3 currentPosition) {
        Vec3 travel = currentPosition.subtract(previousPosition);
        if (travel.lengthSqr() < 1.0E-6D) {
            return;
        }

        float yaw = (float)(Mth.atan2(travel.x, travel.z) * (180.0D / Math.PI));
        float horizontalDistance = (float)Math.sqrt(travel.x * travel.x + travel.z * travel.z);
        float pitch = (float)(Mth.atan2(travel.y, horizontalDistance) * (180.0D / Math.PI));
        this.setYRot(yaw);
        this.setXRot(-pitch);
    }

    private void updateClientTrailHistory(Vec3 currentPosition) {
        Vec3 latestPoint = this.renderTrailHistory.peekFirst();
        if (latestPoint != null) {
            double distanceSqr = latestPoint.distanceToSqr(currentPosition);
            if (distanceSqr > TRAIL_RESET_DISTANCE_SQR) {
                this.renderTrailHistory.clear();
            } else if (distanceSqr < TRAIL_MIN_SAMPLE_DISTANCE_SQR) {
                return;
            }
        }

        this.renderTrailHistory.addFirst(currentPosition);
        while (this.renderTrailHistory.size() > TRAIL_HISTORY_SIZE) {
            this.renderTrailHistory.removeLast();
        }
    }

    private float getBaseDamage() {
        return this.getEntityData().get(DATA_BASE_DAMAGE);
    }

    private float getRadius() {
        return this.getEntityData().get(DATA_RADIUS);
    }

    private float getPhaseOffsetDegrees() {
        return this.getEntityData().get(DATA_PHASE_OFFSET_DEGREES);
    }

    private float getDirectionSign() {
        return this.getEntityData().get(DATA_DIRECTION_SIGN);
    }

    private void setOwnerUuid(UUID ownerUuid) {
        this.getEntityData().set(DATA_OWNER_UUID, Optional.ofNullable(ownerUuid));
    }

    private void setSwordStack(ItemStack swordStack) {
        this.getEntityData().set(DATA_SWORD_STACK, swordStack.copyWithCount(1));
    }

    private void setBaseDamage(float baseDamage) {
        this.getEntityData().set(DATA_BASE_DAMAGE, baseDamage);
    }

    private void setRadius(float radius) {
        this.getEntityData().set(DATA_RADIUS, radius);
    }

    private void setInclinationDegrees(float inclinationDegrees) {
        this.getEntityData().set(DATA_INCLINATION_DEGREES, inclinationDegrees);
    }

    private void setPhaseOffsetDegrees(float phaseOffsetDegrees) {
        this.getEntityData().set(DATA_PHASE_OFFSET_DEGREES, phaseOffsetDegrees);
    }

    private void setDirectionSign(float directionSign) {
        this.getEntityData().set(DATA_DIRECTION_SIGN, directionSign);
    }

    private void setVisualPitch(float visualPitch) {
        this.getEntityData().set(DATA_VISUAL_PITCH, visualPitch);
    }

    private void setVisualRoll(float visualRoll) {
        this.getEntityData().set(DATA_VISUAL_ROLL, visualRoll);
    }

    private static void registerActiveSword(UUID ownerId, UUID swordId) {
        ACTIVE_SWORDS_BY_OWNER.computeIfAbsent(ownerId, ignored -> new HashSet<>()).add(swordId);
    }

    private static void unregisterActiveSword(UUID ownerId, UUID swordId) {
        Set<UUID> swordIds = ACTIVE_SWORDS_BY_OWNER.get(ownerId);
        if (swordIds == null) {
            return;
        }

        swordIds.remove(swordId);
        if (swordIds.isEmpty()) {
            ACTIVE_SWORDS_BY_OWNER.remove(ownerId);
        }
    }

    private record OrbitSwordSpec(
            Supplier<ItemStack> stackSupplier,
            float baseDamage,
            float radius,
            float inclinationDegrees,
            float phaseOffsetDegrees,
            float directionSign) {
    }
}
