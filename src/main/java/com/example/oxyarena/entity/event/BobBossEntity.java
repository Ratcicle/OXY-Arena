package com.example.oxyarena.entity.event;

import javax.annotation.Nullable;

import com.example.oxyarena.OXYArena;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class BobBossEntity extends Zombie {
    private static final double MAX_HEALTH = 100.0D;
    private static final int SPEED_DURATION_TICKS = Integer.MAX_VALUE;
    private static final int JUMP_ASSIST_COOLDOWN_TICKS = 10;
    private static final double JUMP_ASSIST_MAX_HEIGHT = 3.35D;
    private static final double JUMP_ASSIST_HORIZONTAL_PUSH = 0.22D;
    private static final double JUMP_ASSIST_BASE_Y = 0.56D;
    private static final double JUMP_ASSIST_MAX_Y_BONUS = 0.24D;
    private static final float STEP_HEIGHT = 1.1F;

    private final ServerBossEvent bossEvent = (ServerBossEvent)new ServerBossEvent(
            Component.translatable("entity.oxyarena.bob_boss"),
            BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.PROGRESS).setDarkenScreen(false);
    @Nullable
    private Integer forcedChunkX;
    @Nullable
    private Integer forcedChunkZ;
    private int jumpAssistCooldown;
    private boolean deathHandled;

    public BobBossEntity(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);
        this.setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    public void prepareAsBoss() {
        this.setPersistenceRequired();
        this.setCanBreakDoors(false);
        this.setCanPickUpLoot(false);
        this.setCustomName(Component.translatable("entity.oxyarena.bob_boss")
                .copy()
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        this.setCustomNameVisible(true);
        this.setHealth(this.getMaxHealth());
        this.addTag(OXYArena.MODID + ".miniboss_bob");
        this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SPEED_DURATION_TICKS, 0, false, false));

        this.setItemSlot(EquipmentSlot.HEAD, this.createEnchantedArmor(Items.DIAMOND_HELMET));
        this.setItemSlot(EquipmentSlot.CHEST, this.createEnchantedArmor(Items.DIAMOND_CHESTPLATE));
        this.setItemSlot(EquipmentSlot.LEGS, this.createEnchantedArmor(Items.DIAMOND_LEGGINGS));
        this.setItemSlot(EquipmentSlot.FEET, this.createEnchantedArmor(Items.DIAMOND_BOOTS));
        this.setItemSlot(EquipmentSlot.MAINHAND, this.createBossSword());

        for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
            if (equipmentSlot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || equipmentSlot == EquipmentSlot.MAINHAND) {
                this.setDropChance(equipmentSlot, 0.0F);
            }
        }
    }

    @Override
    public float maxUpStep() {
        return STEP_HEIGHT;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        this.updateForcedChunk();
        if (this.jumpAssistCooldown > 0) {
            this.jumpAssistCooldown--;
        }

        this.tryJumpAssist();
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(this.getDisplayName());
    }

    @Override
    public void die(DamageSource damageSource) {
        if (!this.level().isClientSide() && !this.deathHandled) {
            this.deathHandled = true;
            this.handleDefeat(damageSource);
        }

        super.die(damageSource);
    }

    @Override
    public void remove(RemovalReason reason) {
        this.releaseForcedChunk();
        this.bossEvent.removeAllPlayers();
        super.remove(reason);
    }

    private void tryJumpAssist() {
        if (this.jumpAssistCooldown > 0 || !this.onGround() || this.isInWaterOrBubble()) {
            return;
        }

        LivingEntity target = this.getTarget();
        if (target == null) {
            return;
        }

        double heightDifference = target.getY() - this.getY();
        if (heightDifference < 0.6D || heightDifference > JUMP_ASSIST_MAX_HEIGHT) {
            return;
        }

        if (!this.horizontalCollision && heightDifference < 1.2D) {
            return;
        }

        this.faceTowardTarget(target);
        this.jumpFromGround();
        Vec3 towardTarget = new Vec3(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ());
        if (towardTarget.lengthSqr() > 1.0E-4D) {
            Vec3 currentVelocity = this.getDeltaMovement();
            Vec3 push = towardTarget.normalize().scale(JUMP_ASSIST_HORIZONTAL_PUSH);
            double boostedY = Math.max(
                    currentVelocity.y,
                    JUMP_ASSIST_BASE_Y + Math.min(JUMP_ASSIST_MAX_Y_BONUS, heightDifference * 0.1D));
            this.setDeltaMovement(currentVelocity.x + push.x, boostedY, currentVelocity.z + push.z);
            this.hasImpulse = true;
        }

        this.jumpAssistCooldown = JUMP_ASSIST_COOLDOWN_TICKS;
    }

    private void faceTowardTarget(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        if (dx * dx + dz * dz < 1.0E-6D) {
            return;
        }

        float yaw = (float)(net.minecraft.util.Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.setYBodyRot(yaw);
        this.yBodyRotO = yaw;
        this.setYHeadRot(yaw);
        this.yHeadRotO = yaw;
        this.getLookControl().setLookAt(target, 360.0F, 360.0F);
    }

    private void handleDefeat(DamageSource damageSource) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ServerPlayer killer = damageSource.getEntity() instanceof ServerPlayer player ? player : null;
        if (killer != null) {
            this.giveOrDrop(killer, new ItemStack(Items.DIAMOND_SWORD));
            this.giveOrDrop(killer, this.createRewardBook());
            serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable(
                            "event.oxyarena.miniboss.killed",
                            killer.getGameProfile().getName())
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                    false);
            return;
        }

        serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.miniboss.died_to_world")
                        .withStyle(ChatFormatting.RED),
                false);
    }

    private void giveOrDrop(ServerPlayer player, ItemStack stack) {
        ItemStack rewardCopy = stack.copy();
        if (!player.getInventory().add(rewardCopy)) {
            player.drop(rewardCopy, false);
        }
    }

    private ItemStack createEnchantedArmor(net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        this.enchantItem(stack, Enchantments.PROTECTION, 4);
        return stack;
    }

    private ItemStack createBossSword() {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        this.enchantItem(stack, Enchantments.SHARPNESS, 4);
        this.enchantItem(stack, Enchantments.KNOCKBACK, 2);
        return stack;
    }

    private ItemStack createRewardBook() {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        this.enchantItem(stack, Enchantments.SHARPNESS, 4);
        this.enchantItem(stack, Enchantments.KNOCKBACK, 2);
        return stack;
    }

    private void enchantItem(ItemStack stack, net.minecraft.resources.ResourceKey<Enchantment> enchantmentKey, int level) {
        Holder<Enchantment> enchantment = this.level()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(enchantmentKey);
        EnchantmentHelper.updateEnchantments(stack, mutableEnchantments -> mutableEnchantments.set(enchantment, level));
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
}
