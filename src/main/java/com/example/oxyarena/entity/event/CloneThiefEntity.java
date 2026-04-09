package com.example.oxyarena.entity.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.example.oxyarena.OXYArena;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;

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
    private static final double CHASE_SPEED = 1.05D;
    private static final double FLEE_WALK_SPEED = 1.10D;
    private static final double FLEE_SPRINT_SPEED = 1.35D;

    @Nullable
    private Integer forcedChunkX;
    @Nullable
    private Integer forcedChunkZ;

    public CloneThiefEntity(EntityType<? extends CloneThiefEntity> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 0;
        this.setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(
                2,
                new AvoidEntityGoal<>(
                        this,
                        Player.class,
                        player -> this.hasStolenItem(),
                        10.0F,
                        FLEE_WALK_SPEED,
                        FLEE_SPRINT_SPEED,
                        living -> !(living instanceof Player player) || (!player.isCreative() && !player.isSpectator())));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, CHASE_SPEED, true));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.85D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
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

        if (this.hasStolenItem()) {
            if (this.getTarget() != null) {
                this.setTarget(null);
            }
            return;
        }

        ServerPlayer owner = this.getOwnerPlayer();
        if (owner == null
                || !owner.isAlive()
                || owner.isRemoved()
                || owner.level() != this.level()
                || owner.isCreative()
                || owner.isSpectator()) {
            if (this.getTarget() != null) {
                this.setTarget(null);
            }
            return;
        }

        if (this.getTarget() != owner) {
            this.setTarget(owner);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean damaged = super.doHurtTarget(target);
        if (!damaged || this.hasStolenItem() || !(target instanceof ServerPlayer player) || !this.isOwner(player)) {
            return damaged;
        }

        ItemStack stolenStack = this.stealRandomInventoryStack(player);
        if (stolenStack.isEmpty()) {
            return damaged;
        }

        this.setStolenStack(stolenStack);
        this.setTarget(null);
        this.getNavigation().stop();
        player.sendSystemMessage(
                Component.translatable("event.oxyarena.clones.stolen", stolenStack.getHoverName())
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

    private void refreshCloneName() {
        String ownerName = this.getOwnerName();
        if (ownerName.isBlank()) {
            this.setCustomName(Component.translatable("entity.oxyarena.clone_thief"));
        } else {
            this.setCustomName(Component.translatable("entity.oxyarena.clone_thief.named", ownerName));
        }
        this.setCustomNameVisible(true);
    }

    private ItemStack stealRandomInventoryStack(ServerPlayer player) {
        List<Integer> eligibleSlots = new ArrayList<>();
        List<ItemStack> items = player.getInventory().items;
        for (int slot = 0; slot < items.size(); slot++) {
            if (!items.get(slot).isEmpty()) {
                eligibleSlots.add(slot);
            }
        }

        if (eligibleSlots.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int chosenSlot = eligibleSlots.get(player.getRandom().nextInt(eligibleSlots.size()));
        ItemStack stolenStack = player.getInventory().removeItemNoUpdate(chosenSlot);
        if (!stolenStack.isEmpty()) {
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) {
                player.containerMenu.broadcastChanges();
            }
        }
        return stolenStack;
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
}
