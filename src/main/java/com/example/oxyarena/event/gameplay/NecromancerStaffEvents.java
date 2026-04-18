package com.example.oxyarena.event.gameplay;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.example.oxyarena.item.NecromancerStaffItem;
import com.example.oxyarena.registry.ModEntityTypes;
import com.example.oxyarena.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class NecromancerStaffEvents {
    private static final String SOUL_ENTITY_TYPE_TAG = "EntityType";
    private static final String SOUL_DISPLAY_NAME_TAG = "DisplayName";
    private static final String SOUL_ENTITY_DATA_TAG = "EntityData";
    private static final String SUMMON_TAG = "OxyNecromancerSummon";
    private static final String OWNER_UUID_TAG = "OxyNecromancerOwner";

    private static final double FOLLOW_DISTANCE_SQR = 12.0D * 12.0D;
    private static final double TELEPORT_DISTANCE_SQR = 32.0D * 32.0D;
    private static final double FORGET_OWNER_DISTANCE_SQR = 96.0D * 96.0D;
    private static final int COMMAND_RADIUS = 32;
    private static final int COMMAND_MEMORY_TICKS = 120;
    private static final int OWNER_MISSING_GRACE_TICKS = 200;

    private static final Set<UUID> SUMMON_IDS = new HashSet<>();
    private static final Map<UUID, Set<UUID>> SUMMONS_BY_OWNER = new HashMap<>();
    private static final Map<UUID, Integer> MISSING_OWNER_TICKS = new HashMap<>();
    private static final Map<UUID, TargetMemory> OWNER_COMMAND_TARGETS = new HashMap<>();

    private NecromancerStaffEvents() {
    }

    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        Entity attacker = event.getSource().getEntity();
        if (attacker != null && shouldCancelFriendlyDamage(attacker, target)) {
            if (attacker instanceof Mob mob) {
                clearTargetIfFriendly(mob, target);
            }
            event.setCanceled(true);
            return;
        }

        if (target instanceof ServerPlayer owner && attacker instanceof LivingEntity livingAttacker && attacker != owner) {
            commandSummons(owner, livingAttacker);
        }
    }

    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !isNecromancerSummon(mob)) {
            return;
        }

        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (newTarget == null) {
            return;
        }

        UUID ownerId = getOwnerId(mob).orElse(null);
        if (ownerId != null && isFriendlyToOwner(newTarget, ownerId)) {
            event.setNewAboutToBeSetTarget(null);
            mob.setAggressive(false);
        }
    }

    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target) || event.getNewDamage() <= 0.0F) {
            return;
        }

        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof ServerPlayer player && attacker != target) {
            commandSummons(player, target);
        } else if (target instanceof ServerPlayer player && attacker instanceof LivingEntity livingAttacker) {
            commandSummons(player, livingAttacker);
        }
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity killed = event.getEntity();
        if (isNecromancerSummon(killed)) {
            unregisterSummon(killed);
            return;
        }

        if (!(killed instanceof Mob mob)
                || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || !NecromancerStaffItem.isStaff(player.getMainHandItem())) {
            return;
        }

        captureSoul(player, mob, player.getMainHandItem());
    }

    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getTarget() instanceof LivingEntity target
                && target.isAlive()
                && target != player) {
            commandSummons(player, target);
        }
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND
                || !player.isShiftKeyDown()
                || !NecromancerStaffItem.isStaff(player.getMainHandItem())
                || !(event.getTarget() instanceof Mob mob)
                || !isOwnedBy(mob, player.getUUID())) {
            return;
        }

        if (!captureSummonBack(player, mob, player.getMainHandItem())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    public static boolean summonSelectedSoul(Player player, ItemStack staffStack) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(player.level() instanceof ServerLevel serverLevel)
                || activeSummonCount(serverPlayer.getUUID()) >= NecromancerStaffItem.MAX_ACTIVE_SUMMONS) {
            return false;
        }

        Optional<CompoundTag> selectedSoul = NecromancerStaffItem.getSelectedSoul(staffStack);
        if (selectedSoul.isEmpty()) {
            return false;
        }

        Mob summonedMob = createSummon(serverLevel, serverPlayer, selectedSoul.get());
        if (summonedMob == null) {
            return false;
        }

        NecromancerStaffItem.removeSelectedSoul(staffStack);
        registerSummon(serverPlayer, summonedMob);
        serverLevel.addFreshEntity(summonedMob);
        serverLevel.sendParticles(ParticleTypes.SOUL, summonedMob.getX(), summonedMob.getY() + 0.8D, summonedMob.getZ(), 18, 0.35D, 0.45D, 0.35D, 0.02D);
        serverLevel.playSound(null, summonedMob.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.7F, 0.85F);
        return true;
    }

    public static void cycleSelectedSoul(ServerPlayer player, int direction) {
        ItemStack stack = player.getMainHandItem();
        if (!NecromancerStaffItem.isStaff(stack)) {
            return;
        }

        NecromancerStaffItem.cycleSelectedSoul(stack, direction);
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        tickSummons(event.getServer());
    }

    public static void clearPlayer(ServerPlayer player) {
        removeSummonsOwnedBy(player.getUUID(), player.server);
        SUMMONS_BY_OWNER.remove(player.getUUID());
        OWNER_COMMAND_TARGETS.remove(player.getUUID());
    }

    public static void clearAll(MinecraftServer server) {
        for (UUID ownerId : Set.copyOf(SUMMONS_BY_OWNER.keySet())) {
            removeSummonsOwnedBy(ownerId, server);
        }
        SUMMONS_BY_OWNER.clear();
        SUMMON_IDS.clear();
        MISSING_OWNER_TICKS.clear();
        OWNER_COMMAND_TARGETS.clear();
    }

    private static boolean captureSoul(ServerPlayer player, Mob mob, ItemStack staffStack) {
        if (!isEligibleCaptureTarget(mob)) {
            return false;
        }

        CompoundTag soulTag = createSoulTag(mob);
        if (!NecromancerStaffItem.addSoul(staffStack, soulTag)) {
            return false;
        }

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5D, mob.getZ(), 16, 0.3D, 0.4D, 0.3D, 0.04D);
            serverLevel.playSound(null, mob.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
        }
        return true;
    }

    private static boolean captureSummonBack(ServerPlayer player, Mob summon, ItemStack staffStack) {
        if (!isOwnedBy(summon, player.getUUID()) || NecromancerStaffItem.getSoulCount(staffStack) >= NecromancerStaffItem.MAX_SOULS) {
            return false;
        }

        CompoundTag soulTag = createSoulTag(summon);
        if (!NecromancerStaffItem.addSoul(staffStack, soulTag)) {
            return false;
        }

        unregisterSummon(summon);
        summon.discard();
        player.serverLevel().sendParticles(ParticleTypes.SOUL, summon.getX(), summon.getY() + summon.getBbHeight() * 0.5D, summon.getZ(), 14, 0.3D, 0.4D, 0.3D, 0.02D);
        player.serverLevel().playSound(null, summon.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.7F, 1.45F);
        return true;
    }

    private static boolean isEligibleCaptureTarget(Mob mob) {
        EntityType<?> type = mob.getType();
        if (isNecromancerSummon(mob)
                || mob.isPassenger()
                || mob.isVehicle()
                || mob instanceof WitherBoss
                || mob instanceof Warden
                || type == EntityType.ENDER_DRAGON
                || type == EntityType.WITHER
                || type == EntityType.WARDEN
                || type == ModEntityTypes.BOB_BOSS.get()
                || type == ModEntityTypes.CLONE_THIEF.get()) {
            return false;
        }

        return isAllowedEliteSoulType(type)
                || mob instanceof Enemy && type.getCategory() == MobCategory.MONSTER;
    }

    private static boolean isAllowedEliteSoulType(EntityType<?> type) {
        return type == EntityType.IRON_GOLEM
                || type == EntityType.RAVAGER
                || type == EntityType.EVOKER;
    }

    private static CompoundTag createSoulTag(Mob mob) {
        CompoundTag entityData = mob.saveWithoutId(new CompoundTag());
        sanitizeEntityData(entityData);

        CompoundTag soulTag = new CompoundTag();
        soulTag.putString(SOUL_ENTITY_TYPE_TAG, BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
        soulTag.putString(SOUL_DISPLAY_NAME_TAG, mob.getType().getDescription().getString());
        soulTag.put(SOUL_ENTITY_DATA_TAG, entityData);
        return soulTag;
    }

    private static void sanitizeEntityData(CompoundTag entityData) {
        entityData.remove("UUID");
        entityData.remove("Pos");
        entityData.remove("Motion");
        entityData.remove("Rotation");
        entityData.remove("Passengers");
        entityData.remove("Leash");
        entityData.remove("Brain");
        entityData.remove("DeathTime");
        entityData.remove("HurtTime");
        entityData.remove("Tags");
        entityData.remove("ActiveEffects");
        entityData.remove("Fire");
        entityData.remove("AngerTime");
        entityData.remove("AngryAt");
        entityData.remove("HurtByTimestamp");
        entityData.remove(SUMMON_TAG);
        entityData.remove(OWNER_UUID_TAG);
        entityData.putBoolean("PersistenceRequired", true);

        if (entityData.contains("ArmorDropChances")) {
            entityData.put("ArmorDropChances", zeroDropChances(4));
        }
        if (entityData.contains("HandDropChances")) {
            entityData.put("HandDropChances", zeroDropChances(2));
        }
    }

    private static ListTag zeroDropChances(int count) {
        ListTag list = new ListTag();
        for (int index = 0; index < count; index++) {
            list.add(net.minecraft.nbt.FloatTag.valueOf(0.0F));
        }
        return list;
    }

    private static Mob createSummon(ServerLevel level, ServerPlayer owner, CompoundTag soulTag) {
        ResourceLocation entityTypeId = ResourceLocation.tryParse(soulTag.getString(SOUL_ENTITY_TYPE_TAG));
        if (entityTypeId == null) {
            return null;
        }

        Optional<EntityType<?>> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityTypeId);
        if (entityType.isEmpty()) {
            return null;
        }

        Entity entity = entityType.get().create(level);
        if (!(entity instanceof Mob mob)) {
            return null;
        }

        mob.load(soulTag.getCompound(SOUL_ENTITY_DATA_TAG).copy());
        Vec3 spawnPos = owner.position().add(owner.getLookAngle().normalize().scale(2.0D));
        mob.moveTo(spawnPos.x, owner.getY(), spawnPos.z, owner.getYRot(), 0.0F);
        mob.setTarget(null);
        mob.setLastHurtByMob(null);
        mob.setLastHurtMob(null);
        mob.setNoAi(false);
        mob.setPersistenceRequired();
        mob.getPersistentData().putBoolean(SUMMON_TAG, true);
        mob.getPersistentData().putUUID(OWNER_UUID_TAG, owner.getUUID());
        mob.setHealth(mob.getMaxHealth());
        return mob;
    }

    private static void registerSummon(ServerPlayer owner, Mob summon) {
        SUMMON_IDS.add(summon.getUUID());
        SUMMONS_BY_OWNER.computeIfAbsent(owner.getUUID(), ignored -> new HashSet<>()).add(summon.getUUID());
        MISSING_OWNER_TICKS.remove(summon.getUUID());
    }

    private static void unregisterSummon(LivingEntity summon) {
        UUID summonId = summon.getUUID();
        SUMMON_IDS.remove(summonId);
        MISSING_OWNER_TICKS.remove(summonId);
        UUID ownerId = getOwnerId(summon).orElse(null);
        if (ownerId == null) {
            return;
        }

        Set<UUID> summons = SUMMONS_BY_OWNER.get(ownerId);
        if (summons != null) {
            summons.remove(summonId);
            if (summons.isEmpty()) {
                SUMMONS_BY_OWNER.remove(ownerId);
            }
        }
    }

    private static int activeSummonCount(UUID ownerId) {
        Set<UUID> summons = SUMMONS_BY_OWNER.get(ownerId);
        return summons == null ? 0 : summons.size();
    }

    private static boolean shouldCancelFriendlyDamage(Entity attacker, LivingEntity target) {
        Optional<UUID> attackerOwner = getOwnerId(attacker);
        Optional<UUID> targetOwner = getOwnerId(target);
        if (attackerOwner.isPresent()) {
            return target.getUUID().equals(attackerOwner.get()) || targetOwner.filter(attackerOwner.get()::equals).isPresent();
        }

        return targetOwner.filter(ownerId -> attacker.getUUID().equals(ownerId)).isPresent();
    }

    private static void commandSummons(ServerPlayer owner, LivingEntity target) {
        if (target == owner || isOwnedBy(target, owner.getUUID())) {
            return;
        }

        Set<UUID> summons = SUMMONS_BY_OWNER.get(owner.getUUID());
        if (summons == null || summons.isEmpty()) {
            return;
        }

        rememberCommandTarget(owner, target);
        for (Mob summon : findOwnedSummons(owner.server, owner.getUUID())) {
            if (summon.level() == target.level() && summon.distanceToSqr(target) <= Mth.square(COMMAND_RADIUS)) {
                makeSummonAttack(summon, owner.getUUID(), target);
            }
        }
    }

    private static void tickSummons(MinecraftServer server) {
        for (Mob summon : findAllTrackedSummons(server)) {
            UUID ownerId = getOwnerId(summon).orElse(null);
            if (ownerId == null) {
                unregisterSummon(summon);
                summon.discard();
                continue;
            }

            ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
            if (owner == null || owner.level() != summon.level()) {
                int missingTicks = MISSING_OWNER_TICKS.getOrDefault(summon.getUUID(), 0) + 1;
                if (missingTicks >= OWNER_MISSING_GRACE_TICKS) {
                    unregisterSummon(summon);
                    summon.discard();
                } else {
                    MISSING_OWNER_TICKS.put(summon.getUUID(), missingTicks);
                }
                continue;
            }

            MISSING_OWNER_TICKS.remove(summon.getUUID());
            LivingEntity target = summon.getTarget();
            if (target != null && !isValidSummonTarget(summon, ownerId, target)) {
                summon.setTarget(null);
                summon.setAggressive(false);
                target = null;
            }

            if (target == null) {
                target = findRememberedTarget(server, owner, ownerId).orElse(null);
                if (target != null && summon.distanceToSqr(target) <= Mth.square(COMMAND_RADIUS)) {
                    makeSummonAttack(summon, ownerId, target);
                }
            } else {
                driveSummonTowardTarget(summon, target);
            }

            double distanceToOwner = summon.distanceToSqr(owner);
            if (distanceToOwner > FORGET_OWNER_DISTANCE_SQR) {
                unregisterSummon(summon);
                summon.discard();
            } else if (distanceToOwner > TELEPORT_DISTANCE_SQR) {
                teleportNearOwner(summon, owner);
            } else if (summon.getTarget() == null && distanceToOwner > FOLLOW_DISTANCE_SQR) {
                summon.getNavigation().moveTo(owner, 1.15D);
            }
        }
    }

    private static void teleportNearOwner(Mob summon, ServerPlayer owner) {
        BlockPos pos = owner.blockPosition().offset(1 - owner.getRandom().nextInt(3), 0, 1 - owner.getRandom().nextInt(3));
        summon.moveTo(pos.getX() + 0.5D, owner.getY(), pos.getZ() + 0.5D, summon.getYRot(), summon.getXRot());
        summon.getNavigation().stop();
    }

    private static Set<Mob> findOwnedSummons(MinecraftServer server, UUID ownerId) {
        Set<Mob> result = new HashSet<>();
        Set<UUID> summons = SUMMONS_BY_OWNER.get(ownerId);
        if (summons == null || summons.isEmpty()) {
            return result;
        }

        for (Mob summon : findAllTrackedSummons(server)) {
            if (summons.contains(summon.getUUID())) {
                result.add(summon);
            }
        }
        return result;
    }

    private static Set<Mob> findAllTrackedSummons(MinecraftServer server) {
        Set<Mob> result = new HashSet<>();
        Set<UUID> missing = new HashSet<>(SUMMON_IDS);
        for (UUID summonId : Set.copyOf(SUMMON_IDS)) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(summonId);
                if (!(entity instanceof Mob mob) || !isNecromancerSummon(mob)) {
                    continue;
                }

                result.add(mob);
                missing.remove(mob.getUUID());
                break;
            }
        }

        for (UUID missingId : missing) {
            SUMMON_IDS.remove(missingId);
            MISSING_OWNER_TICKS.remove(missingId);
            for (Iterator<Set<UUID>> iterator = SUMMONS_BY_OWNER.values().iterator(); iterator.hasNext();) {
                Set<UUID> summons = iterator.next();
                summons.remove(missingId);
                if (summons.isEmpty()) {
                    iterator.remove();
                }
            }
        }
        return result;
    }

    private static void removeSummonsOwnedBy(UUID ownerId, MinecraftServer server) {
        for (Mob summon : findOwnedSummons(server, ownerId)) {
            unregisterSummon(summon);
            summon.discard();
        }
        OWNER_COMMAND_TARGETS.remove(ownerId);
    }

    private static void rememberCommandTarget(ServerPlayer owner, LivingEntity target) {
        OWNER_COMMAND_TARGETS.put(owner.getUUID(), new TargetMemory(
                target.getUUID(),
                target.level().dimension(),
                owner.serverLevel().getGameTime() + COMMAND_MEMORY_TICKS));
    }

    private static Optional<LivingEntity> findRememberedTarget(MinecraftServer server, ServerPlayer owner, UUID ownerId) {
        TargetMemory memory = OWNER_COMMAND_TARGETS.get(ownerId);
        if (memory == null || owner.serverLevel().getGameTime() > memory.expiresAt()) {
            OWNER_COMMAND_TARGETS.remove(ownerId);
            return Optional.empty();
        }

        ServerLevel level = server.getLevel(memory.dimension());
        if (level == null || level != owner.level()) {
            return Optional.empty();
        }

        Entity entity = level.getEntity(memory.targetId());
        if (entity instanceof LivingEntity target && isValidTargetForOwner(ownerId, target)) {
            return Optional.of(target);
        }

        OWNER_COMMAND_TARGETS.remove(ownerId);
        return Optional.empty();
    }

    private static void makeSummonAttack(Mob summon, UUID ownerId, LivingEntity target) {
        if (!isValidSummonTarget(summon, ownerId, target)) {
            summon.setTarget(null);
            summon.setAggressive(false);
            return;
        }

        summon.setTarget(target);
        summon.setLastHurtByMob(target);
        summon.setAggressive(true);
        if (summon instanceof NeutralMob neutralMob) {
            neutralMob.setPersistentAngerTarget(target.getUUID());
            neutralMob.startPersistentAngerTimer();
        }
        driveSummonTowardTarget(summon, target);
    }

    private static void driveSummonTowardTarget(Mob summon, LivingEntity target) {
        summon.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double speed = Math.max(1.15D, summon.getAttributeValue(Attributes.MOVEMENT_SPEED) * 4.0D);
        if (summon.distanceToSqr(target) > 2.25D || summon.getNavigation().isDone()) {
            summon.getNavigation().moveTo(target, speed);
        }
    }

    private static boolean isValidSummonTarget(Mob summon, UUID ownerId, LivingEntity target) {
        return target.isAlive()
                && summon.level() == target.level()
                && target != summon
                && isValidTargetForOwner(ownerId, target);
    }

    private static boolean isValidTargetForOwner(UUID ownerId, LivingEntity target) {
        return target.isAlive() && !isFriendlyToOwner(target, ownerId);
    }

    private static boolean isFriendlyToOwner(Entity entity, UUID ownerId) {
        return entity.getUUID().equals(ownerId) || isOwnedBy(entity, ownerId);
    }

    private static void clearTargetIfFriendly(Mob mob, LivingEntity target) {
        UUID ownerId = getOwnerId(mob).orElse(null);
        if (ownerId != null && isFriendlyToOwner(target, ownerId)) {
            mob.setTarget(null);
            mob.setAggressive(false);
            mob.getNavigation().stop();
        }
    }

    private static boolean isNecromancerSummon(Entity entity) {
        return entity.getPersistentData().getBoolean(SUMMON_TAG);
    }

    private static boolean isOwnedBy(Entity entity, UUID ownerId) {
        return getOwnerId(entity).filter(ownerId::equals).isPresent();
    }

    private static Optional<UUID> getOwnerId(Entity entity) {
        CompoundTag persistentData = entity.getPersistentData();
        return persistentData.hasUUID(OWNER_UUID_TAG) ? Optional.of(persistentData.getUUID(OWNER_UUID_TAG)) : Optional.empty();
    }

    private record TargetMemory(UUID targetId, ResourceKey<Level> dimension, long expiresAt) {
    }
}
