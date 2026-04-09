package com.example.oxyarena.entity.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.entity.projectile.SmokeBomb;
import com.example.oxyarena.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public final class CloneThiefEntity extends Monster {
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER_UUID = SynchedEntityData.defineId(
            CloneThiefEntity.class,
            EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> DATA_OWNER_NAME = SynchedEntityData.defineId(
            CloneThiefEntity.class,
            EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> DATA_SKIN_PROFILE_UUID = SynchedEntityData.defineId(
            CloneThiefEntity.class,
            EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<ItemStack> DATA_STOLEN_STACK = SynchedEntityData.defineId(
            CloneThiefEntity.class,
            EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> DATA_HAS_STOLEN_ITEM = SynchedEntityData.defineId(
            CloneThiefEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final String CLONE_TAG = OXYArena.MODID + ".clone_thief";
    private static final String SNAPSHOT_TAG = "CloneSnapshot";
    private static final double PLAYER_EQUIVALENT_MOB_SPEED = 0.30D;
    private static final double CHASE_SPEED = 1.0D;
    private static final double FLEE_SPEED = 1.0D;
    private static final float STEP_HEIGHT = 1.1F;
    private static final int QUICK_ATTACK_INTERVAL_TICKS = 5;
    private static final double QUICK_ATTACK_REACH_PADDING = 0.35D;
    private static final double RANGED_MIN_DISTANCE_SQR = 49.0D;
    private static final int RANGED_ATTACK_INTERVAL_TICKS = 30;
    private static final int UTILITY_ACTION_COOLDOWN_TICKS = 8;
    private static final int STEP_CLIMB_UTILITY_COOLDOWN_TICKS = 4;
    private static final int FLEE_TARGET_REFRESH_TICKS = 15;
    private static final double FLEE_TARGET_DISTANCE = 24.0D;
    private static final int POST_BUILD_LOCK_TICKS = 4;
    private static final int STEP_CLIMB_LOCK_TICKS = 2;
    private static final int FLEE_NO_PROGRESS_REPATH_TICKS = 10;
    private static final double MIN_FLEE_PROGRESS_SQR = 0.04D;
    private static final int INITIAL_SMOKE_BOMB_CHARGES = 3;
    private static final int SMOKE_BOMB_MIN_COOLDOWN_TICKS = 300;
    private static final int SMOKE_BOMB_MAX_COOLDOWN_TICKS = 600;
    private static final double SMOKE_BOMB_MAX_USE_DISTANCE_SQR = 100.0D;
    private static final double FLEE_SHOVE_DISTANCE_SQR = 6.25D;
    private static final int FLEE_SHOVE_COOLDOWN_TICKS = 12;

    private final CloneInventorySnapshot inventorySnapshot = new CloneInventorySnapshot();
    private final CloneThiefTerrainTracker terrainTracker = new CloneThiefTerrainTracker();

    @Nullable
    private Integer forcedChunkX;
    @Nullable
    private Integer forcedChunkZ;
    @Nullable
    private BlockPos fleeTarget;
    private Vec3 lastKnownOwnerPos = Vec3.ZERO;
    private Vec3 lastFleeProgressPos = Vec3.ZERO;
    private int utilityActionCooldown;
    private int rangedAttackCooldown;
    private int fleeTargetRefreshCooldown;
    private int postBuildLockTicks;
    private int fleeNoProgressTicks;
    private int smokeBombCooldown;
    private int fleeShoveCooldown;
    private int smokeBombCharges;
    private boolean terrainReverted;
    private CloneState cloneState = CloneState.HUNT_MELEE;

    public CloneThiefEntity(EntityType<? extends CloneThiefEntity> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 0;
        this.setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, PLAYER_EQUIVALENT_MOB_SPEED)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    public float maxUpStep() {
        return STEP_HEIGHT;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new QuickStealAttackGoal(this));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER_UUID, Optional.empty());
        builder.define(DATA_OWNER_NAME, "");
        builder.define(DATA_SKIN_PROFILE_UUID, Optional.empty());
        builder.define(DATA_STOLEN_STACK, ItemStack.EMPTY);
        builder.define(DATA_HAS_STOLEN_ITEM, false);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        this.updateForcedChunk();

        if (this.utilityActionCooldown > 0) {
            this.utilityActionCooldown--;
        }
        if (this.rangedAttackCooldown > 0) {
            this.rangedAttackCooldown--;
        }
        if (this.fleeTargetRefreshCooldown > 0) {
            this.fleeTargetRefreshCooldown--;
        }
        if (this.smokeBombCooldown > 0) {
            this.smokeBombCooldown--;
        }
        if (this.fleeShoveCooldown > 0) {
            this.fleeShoveCooldown--;
        }
        if (this.postBuildLockTicks > 0) {
            this.postBuildLockTicks--;
            this.getNavigation().stop();
            this.setCloneMovementState(false);
            Vec3 movement = this.getDeltaMovement();
            this.setDeltaMovement(0.0D, Math.min(movement.y, 0.0D), 0.0D);
            return;
        }

        ServerPlayer owner = this.getOwnerPlayer();
        if (owner != null && owner.isAlive() && !owner.isRemoved() && owner.level() == this.level()) {
            this.lastKnownOwnerPos = owner.position();
        }

        if (this.hasStolenItem()) {
            this.handleFlee(owner);
            return;
        }

        if (!this.isValidOwner(owner)) {
            this.cloneState = CloneState.HUNT_MELEE;
            this.clearCombatTarget();
            this.setCloneMovementState(false);
            this.updateVisibleMainHand(this.getBestMeleeWeaponCopy());
            return;
        }

        if (this.tryUseUtilityToward(owner.blockPosition())) {
            return;
        }

        if (this.shouldUseRanged(owner)) {
            this.handleRangedHunt(owner);
            return;
        }

        this.handleMeleeHunt(owner);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean damaged = super.doHurtTarget(target);
        if (!damaged || this.hasStolenItem() || !(target instanceof ServerPlayer player) || !this.isOwner(player)) {
            return damaged;
        }

        StolenSelection selection = this.stealRandomStealableStack(player);
        if (selection == StolenSelection.EMPTY || selection.stack.isEmpty()) {
            return damaged;
        }

        this.setStolenStack(selection.stack);
        this.integrateStolenArmorCopy(selection);
        this.clearCombatTarget();
        this.fleeTarget = null;
        this.fleeTargetRefreshCooldown = 0;
        this.fleeNoProgressTicks = 0;
        this.lastFleeProgressPos = this.position();
        this.cloneState = CloneState.FLEE;
        player.sendSystemMessage(
                Component.translatable("event.oxyarena.clones.stolen", selection.stack.getHoverName())
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        return damaged;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        ItemStack stolenStack = this.getStolenStack();
        if (stolenStack.isEmpty()) {
            return;
        }

        this.spawnAtLocation(stolenStack.copy(), 0.0F);
        this.setStolenStack(ItemStack.EMPTY);
        ServerPlayer owner = this.getOwnerPlayer();
        if (owner != null) {
            owner.sendSystemMessage(
                    Component.translatable("event.oxyarena.clones.recovered", stolenStack.getHoverName())
                            .withStyle(ChatFormatting.GREEN));
        }
    }

    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource damageSource) {
        this.dropCustomDeathLoot(level, damageSource, true);
    }

    @Override
    public void remove(RemovalReason reason) {
        this.revertTerrainIfNeeded();
        this.releaseForcedChunk();
        super.remove(reason);
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        this.getOwnerUuid().ifPresent(uuid -> compound.putUUID("OwnerUuid", uuid));
        compound.putString("OwnerName", this.getOwnerName());
        this.getSkinProfileUuid().ifPresent(uuid -> compound.putUUID("SkinProfileUuid", uuid));
        if (!this.getStolenStack().isEmpty()) {
            compound.put("StolenStack", this.getStolenStack().save(this.registryAccess(), new CompoundTag()));
        }
        compound.putBoolean("HasStolenItem", this.hasStolenItem());
        compound.putInt("SmokeBombCooldown", this.smokeBombCooldown);
        compound.putInt("FleeShoveCooldown", this.fleeShoveCooldown);
        compound.putInt("SmokeBombCharges", this.smokeBombCharges);
        CompoundTag snapshotTag = new CompoundTag();
        this.inventorySnapshot.saveToTag(snapshotTag, this.registryAccess());
        compound.put(SNAPSHOT_TAG, snapshotTag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setOwnerUuid(compound.hasUUID("OwnerUuid") ? compound.getUUID("OwnerUuid") : null);
        this.setOwnerName(compound.getString("OwnerName"));
        this.setSkinProfileUuid(compound.hasUUID("SkinProfileUuid") ? compound.getUUID("SkinProfileUuid") : null);
        ItemStack stolenStack = compound.contains("StolenStack")
                ? ItemStack.parse(this.registryAccess(), compound.getCompound("StolenStack")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        this.setStolenStack(stolenStack);
        if (compound.contains("HasStolenItem") && stolenStack.isEmpty()) {
            this.getEntityData().set(DATA_HAS_STOLEN_ITEM, compound.getBoolean("HasStolenItem"));
        }
        this.smokeBombCooldown = Math.max(0, compound.getInt("SmokeBombCooldown"));
        this.fleeShoveCooldown = Math.max(0, compound.getInt("FleeShoveCooldown"));
        this.smokeBombCharges = Math.max(0, compound.getInt("SmokeBombCharges"));
        if (compound.contains(SNAPSHOT_TAG, Tag.TAG_COMPOUND)) {
            this.inventorySnapshot.loadFromTag(compound.getCompound(SNAPSHOT_TAG), this.registryAccess());
        }
        this.refreshUtilityOffhand();
        this.refreshCloneName();
    }

    public void prepareClone(ServerPlayer owner) {
        this.setPersistenceRequired();
        this.addTag(CLONE_TAG);
        this.setCanPickUpLoot(false);
        this.setOwnerUuid(owner.getUUID());
        this.setOwnerName(owner.getGameProfile().getName());
        this.setSkinProfileUuid(owner.getUUID());
        this.setStolenStack(ItemStack.EMPTY);
        this.setCloneMovementState(false);
        this.smokeBombCharges = INITIAL_SMOKE_BOMB_CHARGES;
        this.smokeBombCooldown = this.nextSmokeBombCooldown();
        this.fleeShoveCooldown = 0;
        this.inventorySnapshot.copyFromPlayer(owner);
        this.copyArmorFromPlayer(owner);
        this.updateVisibleMainHand(this.getBestMeleeWeaponCopy());
        this.refreshUtilityOffhand();
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        this.setDropChance(EquipmentSlot.HEAD, 0.0F);
        this.setDropChance(EquipmentSlot.CHEST, 0.0F);
        this.setDropChance(EquipmentSlot.LEGS, 0.0F);
        this.setDropChance(EquipmentSlot.FEET, 0.0F);
        this.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
        this.refreshCloneName();
    }

    public Optional<UUID> getOwnerUuid() {
        return this.getEntityData().get(DATA_OWNER_UUID);
    }

    public String getOwnerName() {
        return this.getEntityData().get(DATA_OWNER_NAME);
    }

    public Optional<UUID> getSkinProfileUuid() {
        return this.getEntityData().get(DATA_SKIN_PROFILE_UUID);
    }

    public ItemStack getStolenStack() {
        return this.getEntityData().get(DATA_STOLEN_STACK);
    }

    public boolean hasStolenItem() {
        return this.getEntityData().get(DATA_HAS_STOLEN_ITEM) && !this.getStolenStack().isEmpty();
    }

    @Nullable
    public ServerPlayer getOwnerPlayer() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }

        return this.getOwnerUuid()
                .map(serverLevel.getServer().getPlayerList()::getPlayer)
                .orElse(null);
    }

    public boolean isOwner(Player player) {
        return this.getOwnerUuid().map(player.getUUID()::equals).orElse(false);
    }

    public void dropStolenItemManually() {
        if (!(this.level() instanceof ServerLevel) || this.getStolenStack().isEmpty()) {
            return;
        }

        this.spawnAtLocation(this.getStolenStack().copy(), 0.0F);
        this.setStolenStack(ItemStack.EMPTY);
    }

    public void destroyStolenItem() {
        this.setStolenStack(ItemStack.EMPTY);
    }

    public static boolean isValidSpawnPos(Level level, BlockPos spawnPos, EntityType<?> entityType) {
        if (!level.getBlockState(spawnPos).canBeReplaced() || !level.getBlockState(spawnPos.above()).canBeReplaced()) {
            return false;
        }

        if (!level.noCollision(entityType.getDimensions().makeBoundingBox(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D))) {
            return false;
        }

        FluidState supportFluid = level.getBlockState(spawnPos.below()).getFluidState();
        return !level.getBlockState(spawnPos.below()).isAir() && supportFluid.isEmpty();
    }

    private void handleMeleeHunt(ServerPlayer owner) {
        this.cloneState = CloneState.HUNT_MELEE;
        this.setTarget(owner);
        this.setAggressive(true);
        this.setCloneMovementState(true);
        this.updateVisibleMainHand(this.getBestMeleeWeaponCopy());
        this.getNavigation().moveTo(owner, CHASE_SPEED);
    }

    private void handleRangedHunt(ServerPlayer owner) {
        int bowSlot = this.inventorySnapshot.findBestBowSlot();
        int arrowSlot = this.inventorySnapshot.findFirstArrowSlot();
        if (bowSlot < 0 || arrowSlot < 0) {
            this.handleMeleeHunt(owner);
            return;
        }

        this.cloneState = CloneState.HUNT_RANGED;
        this.setTarget(owner);
        this.setAggressive(true);
        this.setCloneMovementState(false);
        this.getNavigation().stop();
        this.getLookControl().setLookAt(owner, 30.0F, 30.0F);
        this.updateVisibleMainHand(this.inventorySnapshot.getItem(bowSlot).copy());

        if (this.rangedAttackCooldown <= 0) {
            this.fireArrowAt(owner, bowSlot, arrowSlot);
            this.rangedAttackCooldown = RANGED_ATTACK_INTERVAL_TICKS;
        }
    }

    private void handleFlee(@Nullable ServerPlayer owner) {
        this.cloneState = CloneState.FLEE;
        if (this.getTarget() != null) {
            this.setTarget(null);
        }
        this.setAggressive(false);
        this.setCloneMovementState(true);
        this.updateVisibleMainHand(this.getBestMeleeWeaponCopy());

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (owner != null && this.tryFleeShove(owner)) {
            return;
        }

        if (owner != null) {
            this.tryUseSmokeBomb(serverLevel, owner);
        }

        Vec3 currentPos = this.position();
        if (currentPos.distanceToSqr(this.lastFleeProgressPos) < MIN_FLEE_PROGRESS_SQR) {
            this.fleeNoProgressTicks++;
        } else {
            this.fleeNoProgressTicks = 0;
            this.lastFleeProgressPos = currentPos;
        }

        if (this.shouldRefreshFleeTarget(owner)) {
            this.fleeTarget = this.findFleeTarget(owner);
            this.fleeTargetRefreshCooldown = FLEE_TARGET_REFRESH_TICKS;
        }

        if (this.fleeTarget == null) {
            this.setCloneMovementState(false);
            return;
        }

        this.lookAtFleeTarget(this.fleeTarget);
        net.minecraft.core.Direction direction = getHorizontalDirectionToward(this.blockPosition(), this.fleeTarget);
        if (direction == null) {
            this.fleeTarget = null;
            this.fleeTargetRefreshCooldown = 0;
            return;
        }

        if (this.shouldAttemptFleeUtility(this.fleeTarget)) {
            if (this.tryBuildPillarToEscape(serverLevel, this.fleeTarget)) {
                return;
            }
            if (this.tryBuildPillarToClimbStep(serverLevel, this.fleeTarget)) {
                return;
            }
            if (this.tryBreakToward(serverLevel, direction)) {
                return;
            }
            if (this.tryPlaceBlockToward(serverLevel, direction, this.fleeTarget)) {
                return;
            }
        }

        boolean hasPath = this.getNavigation().moveTo(
                this.fleeTarget.getX() + 0.5D,
                this.fleeTarget.getY(),
                this.fleeTarget.getZ() + 0.5D,
                FLEE_SPEED);
        if (!hasPath) {
            this.fleeTarget = null;
            this.fleeTargetRefreshCooldown = 0;
        }
    }

    private boolean shouldUseRanged(ServerPlayer owner) {
        return this.inventorySnapshot.findBestBowSlot() >= 0
                && this.inventorySnapshot.findFirstArrowSlot() >= 0
                && this.distanceToSqr(owner) >= RANGED_MIN_DISTANCE_SQR
                && this.hasLineOfSight(owner);
    }

    private boolean tryUseUtilityToward(BlockPos targetPos) {
        if (this.utilityActionCooldown > 0 || !(this.level() instanceof ServerLevel serverLevel) || !this.onGround()) {
            return false;
        }

        if (this.tryBuildPillarToClimbStep(serverLevel, targetPos)) {
            return true;
        }

        if (this.tryBuildPillarToEscape(serverLevel, targetPos)) {
            return true;
        }

        net.minecraft.core.Direction direction = getHorizontalDirectionToward(this.blockPosition(), targetPos);
        if (direction == null) {
            return false;
        }

        return this.tryBreakToward(serverLevel, direction) || this.tryPlaceBlockToward(serverLevel, direction, targetPos);
    }

    private boolean tryBreakToward(ServerLevel level, net.minecraft.core.Direction direction) {
        if (!this.horizontalCollision && !this.getNavigation().isDone()) {
            return false;
        }

        BlockPos front = this.blockPosition().relative(direction);
        for (BlockPos candidate : new BlockPos[] { front, front.above() }) {
            BlockState state = level.getBlockState(candidate);
            if (state.isAir() || state.canBeReplaced()) {
                continue;
            }

            int toolSlot = this.inventorySnapshot.findBestToolSlot(state);
            if (toolSlot < 0) {
                continue;
            }

            this.cloneState = CloneState.BREAK_TO_REACH;
            this.updateVisibleMainHand(this.inventorySnapshot.getItem(toolSlot).copy());
            if (!this.terrainTracker.breakBlock(level, candidate)) {
                continue;
            }

            this.swing(InteractionHand.MAIN_HAND);
            this.inventorySnapshot.damageItemInSlot(toolSlot, this, 1);
            this.utilityActionCooldown = UTILITY_ACTION_COOLDOWN_TICKS;
            this.terrainReverted = false;
            return true;
        }

        return false;
    }

    private boolean tryPlaceBlockToward(ServerLevel level, net.minecraft.core.Direction direction, BlockPos targetPos) {
        BlockPos front = this.blockPosition().relative(direction);
        BlockPos bridgePos = front.below();
        BlockState frontState = level.getBlockState(front);
        BlockState bridgeState = level.getBlockState(bridgePos);
        boolean gapAhead = !level.getBlockState(bridgePos.below()).isFaceSturdy(level, bridgePos.below(), net.minecraft.core.Direction.UP);

        if (!frontState.canBeReplaced() || !bridgeState.canBeReplaced() || !gapAhead) {
            return false;
        }

        if (this.distanceToSqr(targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D) < 9.0D) {
            return false;
        }

        int blockSlot = this.inventorySnapshot.findBestBuildingBlockSlot();
        if (blockSlot < 0) {
            return false;
        }

        ItemStack displayStack = this.inventorySnapshot.getItem(blockSlot).copy();
        BlockState placedState = this.inventorySnapshot.consumeBuildingBlockState(blockSlot);
        if (placedState == null) {
            return false;
        }

        this.cloneState = CloneState.BUILD_BRIDGE_TO_REACH;
        this.updateVisibleMainHand(displayStack);
        if (!this.terrainTracker.placeBlock(level, bridgePos, placedState)) {
            this.inventorySnapshot.addToInventory(placedState.getBlock().asItem().getDefaultInstance());
            return false;
        }

        this.swing(InteractionHand.MAIN_HAND);
        this.utilityActionCooldown = UTILITY_ACTION_COOLDOWN_TICKS;
        this.postBuildLockTicks = POST_BUILD_LOCK_TICKS;
        this.getNavigation().stop();
        this.setCloneMovementState(false);
        this.setDeltaMovement(0.0D, Math.min(this.getDeltaMovement().y, 0.0D), 0.0D);
        this.lastFleeProgressPos = this.position();
        this.fleeNoProgressTicks = 0;
        this.terrainReverted = false;
        return true;
    }

    private void fireArrowAt(ServerPlayer owner, int bowSlot, int arrowSlot) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ItemStack bow = this.inventorySnapshot.getItem(bowSlot).copy();
        ItemStack ammo = this.inventorySnapshot.consumeArrow(arrowSlot);
        if (bow.isEmpty() || ammo.isEmpty() || !(ammo.getItem() instanceof ArrowItem arrowItem)) {
            return;
        }

        AbstractArrow arrow = arrowItem.createArrow(serverLevel, ammo, this, bow);
        if (bow.getItem() instanceof ProjectileWeaponItem projectileWeaponItem) {
            arrow = projectileWeaponItem.customArrow(arrow, ammo, bow);
        }

        Vec3 delta = owner.getEyePosition().subtract(this.getEyePosition());
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        arrow.setPos(this.getX(), this.getEyeY() - 0.1D, this.getZ());
        arrow.shoot(delta.x, delta.y + horizontalDistance * 0.2D, delta.z, 1.6F, 10.0F);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        serverLevel.addFreshEntity(arrow);
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 0.9F + this.getRandom().nextFloat() * 0.2F);
        this.swing(InteractionHand.MAIN_HAND);
        this.inventorySnapshot.damageItemInSlot(bowSlot, this, 1);
    }

    @Nullable
    private BlockPos findFleeTarget(@Nullable ServerPlayer owner) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }

        Vec3 source = owner != null ? owner.position() : this.lastKnownOwnerPos;
        Vec3 away = this.position().subtract(source).multiply(1.0D, 0.0D, 1.0D);
        if (away.lengthSqr() < 1.0E-4D) {
            away = this.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        }
        if (away.lengthSqr() < 1.0E-4D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }

        Vec3 desired = this.position().add(away.normalize().scale(FLEE_TARGET_DISTANCE));
        double currentDistanceToOwner = owner != null ? this.distanceToSqr(owner) : 0.0D;

        for (int radius = 0; radius <= 10; radius++) {
            for (int yOffset : new int[] { 0, 1, -1, 2, -2, 3, -3, 4, -4 }) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                            continue;
                        }

                        BlockPos candidate = BlockPos.containing(
                                desired.x + dx,
                                Mth.floor(this.getY()) + yOffset,
                                desired.z + dz);
                        if (!isValidSpawnPos(serverLevel, candidate, this.getType())) {
                            continue;
                        }
                        if (owner != null && candidate.distSqr(owner.blockPosition()) <= currentDistanceToOwner + 16.0D) {
                            continue;
                        }
                        return candidate;
                    }
                }
            }
        }

        return null;
    }

    private boolean shouldRefreshFleeTarget(@Nullable ServerPlayer owner) {
        if (this.fleeTarget == null
                || this.fleeTargetRefreshCooldown <= 0
                || this.distanceToSqr(Vec3.atBottomCenterOf(this.fleeTarget)) < 4.0D
                || this.fleeNoProgressTicks >= FLEE_NO_PROGRESS_REPATH_TICKS
                || this.getNavigation().isDone()) {
            return true;
        }

        if (owner != null && this.fleeTarget.distSqr(owner.blockPosition()) <= owner.blockPosition().distSqr(this.blockPosition()) + 16.0D) {
            return true;
        }

        return false;
    }

    private boolean shouldAttemptFleeUtility(@Nullable BlockPos targetPos) {
        if (targetPos == null) {
            return false;
        }

        return this.horizontalCollision || this.fleeNoProgressTicks >= FLEE_NO_PROGRESS_REPATH_TICKS;
    }

    private void lookAtFleeTarget(BlockPos fleePos) {
        double targetX = fleePos.getX() + 0.5D;
        double targetY = fleePos.getY() + 0.5D;
        double targetZ = fleePos.getZ() + 0.5D;
        this.getLookControl().setLookAt(targetX, targetY, targetZ, 360.0F, 360.0F);
    }

    private boolean tryBuildPillarToEscape(ServerLevel level, @Nullable BlockPos targetPos) {
        if (this.utilityActionCooldown > 0 || !this.onGround()) {
            return false;
        }

        BlockPos feetPos = this.blockPosition();
        if (!this.shouldPillarEscape(level, feetPos, targetPos)) {
            return false;
        }

        int blockSlot = this.inventorySnapshot.findBestBuildingBlockSlot();
        if (blockSlot < 0) {
            return false;
        }

        ItemStack displayStack = this.inventorySnapshot.getItem(blockSlot).copy();
        BlockState placedState = this.inventorySnapshot.consumeBuildingBlockState(blockSlot);
        if (placedState == null) {
            return false;
        }

        this.cloneState = CloneState.BUILD_PILLAR_TO_ESCAPE;
        this.updateVisibleMainHand(displayStack);
        if (!this.terrainTracker.placeBlock(level, feetPos, placedState)) {
            this.inventorySnapshot.addToInventory(placedState.getBlock().asItem().getDefaultInstance());
            return false;
        }

        this.swing(InteractionHand.MAIN_HAND);
        this.getNavigation().stop();
        this.moveTo(this.getX(), feetPos.getY() + 1.0D, this.getZ(), this.getYRot(), this.getXRot());
        this.setDeltaMovement(0.0D, 0.0D, 0.0D);
        this.fallDistance = 0.0F;
        this.utilityActionCooldown = UTILITY_ACTION_COOLDOWN_TICKS;
        this.postBuildLockTicks = POST_BUILD_LOCK_TICKS;
        this.lastFleeProgressPos = this.position();
        this.fleeNoProgressTicks = 0;
        this.terrainReverted = false;
        return true;
    }

    private boolean tryBuildPillarToClimbStep(ServerLevel level, @Nullable BlockPos targetPos) {
        if (this.utilityActionCooldown > 0 || !this.onGround()) {
            return false;
        }

        BlockPos feetPos = this.blockPosition();
        net.minecraft.core.Direction direction = getHorizontalDirectionToward(feetPos, targetPos);
        if (direction == null || !this.shouldPillarForShortStep(level, feetPos, direction)) {
            return false;
        }

        int blockSlot = this.inventorySnapshot.findBestBuildingBlockSlot();
        if (blockSlot < 0) {
            return false;
        }

        ItemStack displayStack = this.inventorySnapshot.getItem(blockSlot).copy();
        BlockState placedState = this.inventorySnapshot.consumeBuildingBlockState(blockSlot);
        if (placedState == null) {
            return false;
        }

        this.cloneState = CloneState.BUILD_PILLAR_TO_CLIMB;
        this.updateVisibleMainHand(displayStack);
        if (!this.terrainTracker.placeBlock(level, feetPos, placedState)) {
            this.inventorySnapshot.addToInventory(placedState.getBlock().asItem().getDefaultInstance());
            return false;
        }

        this.swing(InteractionHand.MAIN_HAND);
        this.getNavigation().stop();
        this.moveTo(this.getX(), feetPos.getY() + 1.0D, this.getZ(), this.getYRot(), this.getXRot());
        this.setDeltaMovement(0.0D, 0.0D, 0.0D);
        this.fallDistance = 0.0F;
        this.utilityActionCooldown = STEP_CLIMB_UTILITY_COOLDOWN_TICKS;
        this.postBuildLockTicks = STEP_CLIMB_LOCK_TICKS;
        this.lastFleeProgressPos = this.position();
        this.fleeNoProgressTicks = 0;
        this.terrainReverted = false;
        return true;
    }

    private boolean shouldPillarEscape(ServerLevel level, BlockPos feetPos, @Nullable BlockPos targetPos) {
        if (!level.getBlockState(feetPos).canBeReplaced()
                || !level.getBlockState(feetPos.above()).canBeReplaced()
                || !level.getBlockState(feetPos.above(2)).canBeReplaced()) {
            return false;
        }

        boolean stuck = this.horizontalCollision || this.fleeNoProgressTicks >= FLEE_NO_PROGRESS_REPATH_TICKS;
        return stuck && this.isPitLikeTrap(level, feetPos);
    }

    private boolean shouldPillarForShortStep(ServerLevel level, BlockPos feetPos, net.minecraft.core.Direction direction) {
        if (!level.getBlockState(feetPos).canBeReplaced()
                || !level.getBlockState(feetPos.above()).canBeReplaced()
                || !level.getBlockState(feetPos.above(2)).canBeReplaced()) {
            return false;
        }

        boolean stuck = this.horizontalCollision || this.getNavigation().isDone() || this.fleeNoProgressTicks >= 4;
        if (!stuck) {
            return false;
        }

        BlockPos front = feetPos.relative(direction);
        BlockState frontFeet = level.getBlockState(front);
        BlockState frontHead = level.getBlockState(front.above());
        BlockPos landingFeetPos = front.above(2);
        BlockPos landingHeadPos = front.above(3);
        BlockState landingSupport = level.getBlockState(landingFeetPos.below());

        return !frontFeet.canBeReplaced()
                && !frontHead.canBeReplaced()
                && level.getBlockState(landingFeetPos).canBeReplaced()
                && level.getBlockState(landingHeadPos).canBeReplaced()
                && landingSupport.isFaceSturdy(level, landingFeetPos.below(), net.minecraft.core.Direction.UP);
    }

    private boolean isInTightSpace(ServerLevel level, BlockPos feetPos) {
        int blockedSides = 0;
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockState sideFeet = level.getBlockState(feetPos.relative(direction));
            BlockState sideHead = level.getBlockState(feetPos.above().relative(direction));
            if (!sideFeet.canBeReplaced() || !sideHead.canBeReplaced()) {
                blockedSides++;
            }
        }

        return blockedSides >= 3;
    }

    private boolean isPitLikeTrap(ServerLevel level, BlockPos feetPos) {
        if (!this.isInTightSpace(level, feetPos)) {
            return false;
        }

        return level.getBlockState(feetPos.above(3)).canBeReplaced()
                && level.getBlockState(feetPos.above(4)).canBeReplaced();
    }

    private void clearCombatTarget() {
        this.setTarget(null);
        this.getNavigation().stop();
        this.setAggressive(false);
    }

    private void setCloneMovementState(boolean sprinting) {
        if (this.getAttribute(Attributes.MOVEMENT_SPEED) != null
                && Math.abs(this.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() - PLAYER_EQUIVALENT_MOB_SPEED) > 1.0E-6D) {
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(PLAYER_EQUIVALENT_MOB_SPEED);
        }
        this.setSprinting(sprinting);
    }

    private void refreshUtilityOffhand() {
        ItemStack offhandStack = this.smokeBombCharges > 0
                ? new ItemStack(ModItems.SMOKE_BOMB.get(), this.smokeBombCharges)
                : ItemStack.EMPTY;
        ItemStack current = this.getOffhandItem();
        if (ItemStack.isSameItemSameComponents(current, offhandStack) && current.getCount() == offhandStack.getCount()) {
            return;
        }

        this.setItemSlot(EquipmentSlot.OFFHAND, offhandStack);
        this.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
    }

    private int nextSmokeBombCooldown() {
        return Mth.nextInt(this.random, SMOKE_BOMB_MIN_COOLDOWN_TICKS, SMOKE_BOMB_MAX_COOLDOWN_TICKS);
    }

    private boolean tryUseSmokeBomb(ServerLevel level, ServerPlayer owner) {
        if (this.smokeBombCharges <= 0
                || this.smokeBombCooldown > 0
                || this.distanceToSqr(owner) > SMOKE_BOMB_MAX_USE_DISTANCE_SQR
                || !this.hasLineOfSight(owner)) {
            return false;
        }

        SmokeBomb smokeBomb = new SmokeBomb(level, this);
        ItemStack smokeBombStack = new ItemStack(ModItems.SMOKE_BOMB.get());
        smokeBomb.setItem(smokeBombStack);

        Vec3 delta = owner.getEyePosition().subtract(this.getEyePosition());
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        smokeBomb.setPos(this.getX(), this.getEyeY() - 0.1D, this.getZ());
        smokeBomb.shoot(delta.x, delta.y + horizontalDistance * 0.05D, delta.z, 1.35F, 0.8F);
        level.addFreshEntity(smokeBomb);

        this.playSound(SoundEvents.SNOWBALL_THROW, 0.6F, 0.75F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        this.swing(InteractionHand.OFF_HAND);
        this.smokeBombCharges--;
        this.smokeBombCooldown = this.nextSmokeBombCooldown();
        this.refreshUtilityOffhand();
        return true;
    }

    private boolean tryFleeShove(ServerPlayer owner) {
        if (this.fleeShoveCooldown > 0
                || this.distanceToSqr(owner) > FLEE_SHOVE_DISTANCE_SQR
                || !this.hasLineOfSight(owner)
                || !this.canLandQuickAttack(owner)) {
            return false;
        }

        this.getLookControl().setLookAt(owner, 30.0F, 30.0F);
        this.updateVisibleMainHand(this.getBestMeleeWeaponCopy());
        this.swing(InteractionHand.MAIN_HAND);
        boolean damaged = super.doHurtTarget(owner);
        if (damaged) {
            owner.knockback(0.9D, this.getX() - owner.getX(), this.getZ() - owner.getZ());
        }
        this.fleeShoveCooldown = FLEE_SHOVE_COOLDOWN_TICKS;
        return damaged;
    }

    private void copyArmorFromPlayer(ServerPlayer owner) {
        this.setItemSlot(EquipmentSlot.HEAD, owner.getItemBySlot(EquipmentSlot.HEAD).copy());
        this.setItemSlot(EquipmentSlot.CHEST, owner.getItemBySlot(EquipmentSlot.CHEST).copy());
        this.setItemSlot(EquipmentSlot.LEGS, owner.getItemBySlot(EquipmentSlot.LEGS).copy());
        this.setItemSlot(EquipmentSlot.FEET, owner.getItemBySlot(EquipmentSlot.FEET).copy());
    }

    private void integrateStolenArmorCopy(StolenSelection selection) {
        if (selection.armorSlot == null || selection.stack.isEmpty()) {
            return;
        }

        if (this.getItemBySlot(selection.armorSlot).isEmpty()) {
            this.setItemSlot(selection.armorSlot, selection.stack.copy());
            this.setDropChance(selection.armorSlot, 0.0F);
        } else {
            this.inventorySnapshot.addToInventory(selection.stack.copy());
        }
    }

    private ItemStack getBestMeleeWeaponCopy() {
        int slot = this.inventorySnapshot.findBestMeleeSlot();
        return slot >= 0 ? this.inventorySnapshot.getItem(slot).copy() : ItemStack.EMPTY;
    }

    private void updateVisibleMainHand(ItemStack stack) {
        ItemStack visibleStack = stack == null ? ItemStack.EMPTY : stack.copy();
        ItemStack currentStack = this.getMainHandItem();
        if (ItemStack.isSameItemSameComponents(currentStack, visibleStack) && currentStack.getCount() == visibleStack.getCount()) {
            return;
        }

        this.setItemSlot(EquipmentSlot.MAINHAND, visibleStack);
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    private boolean isValidOwner(@Nullable ServerPlayer owner) {
        return owner != null
                && owner.isAlive()
                && !owner.isRemoved()
                && owner.level() == this.level()
                && !owner.isCreative()
                && !owner.isSpectator();
    }

    private void refreshCloneName() {
        String ownerName = this.getOwnerName();
        if (ownerName.isBlank()) {
            this.setCustomName(Component.translatable("entity.oxyarena.clone_thief"));
        } else {
            this.setCustomName(Component.translatable("entity.oxyarena.clone_thief.named", ownerName));
        }
        this.setCustomNameVisible(true);
    }

    private void setOwnerUuid(@Nullable UUID ownerUuid) {
        this.getEntityData().set(DATA_OWNER_UUID, Optional.ofNullable(ownerUuid));
    }

    private void setOwnerName(String ownerName) {
        this.getEntityData().set(DATA_OWNER_NAME, ownerName == null ? "" : ownerName);
    }

    private void setSkinProfileUuid(@Nullable UUID skinProfileUuid) {
        this.getEntityData().set(DATA_SKIN_PROFILE_UUID, Optional.ofNullable(skinProfileUuid));
    }

    private void setStolenStack(ItemStack stack) {
        ItemStack storedStack = stack == null ? ItemStack.EMPTY : stack.copy();
        this.getEntityData().set(DATA_STOLEN_STACK, storedStack);
        this.getEntityData().set(DATA_HAS_STOLEN_ITEM, !storedStack.isEmpty());
    }

    private void updateForcedChunk() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        int currentChunkX = this.chunkPosition().x;
        int currentChunkZ = this.chunkPosition().z;
        if (this.forcedChunkX != null && this.forcedChunkZ != null
                && this.forcedChunkX == currentChunkX
                && this.forcedChunkZ == currentChunkZ) {
            return;
        }

        this.releaseForcedChunk();
        serverLevel.setChunkForced(currentChunkX, currentChunkZ, true);
        this.forcedChunkX = currentChunkX;
        this.forcedChunkZ = currentChunkZ;
    }

    private void releaseForcedChunk() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (this.forcedChunkX != null && this.forcedChunkZ != null) {
            serverLevel.setChunkForced(this.forcedChunkX, this.forcedChunkZ, false);
        }

        this.forcedChunkX = null;
        this.forcedChunkZ = null;
    }

    private void revertTerrainIfNeeded() {
        if (this.terrainReverted || !(this.level() instanceof ServerLevel serverLevel) || !this.terrainTracker.hasTrackedChanges()) {
            return;
        }

        this.terrainTracker.revert(serverLevel);
        this.terrainReverted = true;
    }

    private static int toInventoryArmorIndex(EquipmentSlot armorSlot) {
        return switch (armorSlot) {
            case FEET -> 0;
            case LEGS -> 1;
            case CHEST -> 2;
            case HEAD -> 3;
            default -> -1;
        };
    }

    @Nullable
    private static net.minecraft.core.Direction getHorizontalDirectionToward(BlockPos from, BlockPos target) {
        int dx = target.getX() - from.getX();
        int dz = target.getZ() - from.getZ();
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx >= 0 ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST;
        }
        if (dz != 0) {
            return dz >= 0 ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH;
        }
        return null;
    }

    private boolean canLandQuickAttack(LivingEntity target) {
        return this.distanceToSqr(target) <= this.getMeleeAttackRangeSqr(target) + QUICK_ATTACK_REACH_PADDING;
    }

    private double getMeleeAttackRangeSqr(LivingEntity target) {
        return this.getBbWidth() * 2.0F * this.getBbWidth() * 2.0F + target.getBbWidth();
    }

    private StolenSelection stealRandomStealableStack(ServerPlayer player) {
        List<StolenSelection> candidates = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            ItemStack stack = player.getInventory().items.get(slot);
            if (!stack.isEmpty()) {
                candidates.add(new StolenSelection(slot, null, stack.copy()));
            }
        }

        for (EquipmentSlot armorSlot : new EquipmentSlot[] {
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET }) {
            ItemStack stack = player.getItemBySlot(armorSlot);
            if (!stack.isEmpty()) {
                candidates.add(new StolenSelection(-1, armorSlot, stack.copy()));
            }
        }

        if (candidates.isEmpty()) {
            return StolenSelection.EMPTY;
        }

        StolenSelection selection = candidates.get(player.getRandom().nextInt(candidates.size()));
        ItemStack removedStack;
        if (selection.armorSlot != null) {
            int armorIndex = toInventoryArmorIndex(selection.armorSlot);
            if (armorIndex < 0) {
                return StolenSelection.EMPTY;
            }

            removedStack = player.getInventory().armor.get(armorIndex).copy();
            player.getInventory().armor.set(armorIndex, ItemStack.EMPTY);
            player.setItemSlot(selection.armorSlot, ItemStack.EMPTY);
        } else {
            removedStack = player.getInventory().removeItemNoUpdate(selection.inventorySlot);
        }

        if (removedStack.isEmpty()) {
            return StolenSelection.EMPTY;
        }

        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }

        return new StolenSelection(selection.inventorySlot, selection.armorSlot, removedStack);
    }

    private final class QuickStealAttackGoal extends Goal {
        private int attackCooldown;

        private QuickStealAttackGoal(PathfinderMob mob) {
        }

        @Override
        public boolean canUse() {
            return CloneThiefEntity.this.cloneState == CloneState.HUNT_MELEE
                    && !CloneThiefEntity.this.hasStolenItem()
                    && CloneThiefEntity.this.getTarget() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void start() {
            this.attackCooldown = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = CloneThiefEntity.this.getTarget();
            if (target == null) {
                return;
            }

            CloneThiefEntity.this.getLookControl().setLookAt(target, 30.0F, 30.0F);
            CloneThiefEntity.this.getNavigation().moveTo(target, CHASE_SPEED);
            if (this.attackCooldown > 0) {
                this.attackCooldown--;
            }

            if (this.attackCooldown <= 0
                    && CloneThiefEntity.this.canLandQuickAttack(target)
                    && CloneThiefEntity.this.hasLineOfSight(target)) {
                this.attackCooldown = QUICK_ATTACK_INTERVAL_TICKS;
                CloneThiefEntity.this.swing(InteractionHand.MAIN_HAND);
                CloneThiefEntity.this.doHurtTarget(target);
            }
        }
    }

    private enum CloneState {
        HUNT_MELEE,
        HUNT_RANGED,
        BUILD_BRIDGE_TO_REACH,
        BUILD_PILLAR_TO_CLIMB,
        BUILD_PILLAR_TO_ESCAPE,
        BREAK_TO_REACH,
        FLEE
    }

    private static final class StolenSelection {
        private static final StolenSelection EMPTY = new StolenSelection(-1, null, ItemStack.EMPTY);

        private final int inventorySlot;
        @Nullable
        private final EquipmentSlot armorSlot;
        private final ItemStack stack;

        private StolenSelection(int inventorySlot, @Nullable EquipmentSlot armorSlot, ItemStack stack) {
            this.inventorySlot = inventorySlot;
            this.armorSlot = armorSlot;
            this.stack = stack;
        }
    }
}
