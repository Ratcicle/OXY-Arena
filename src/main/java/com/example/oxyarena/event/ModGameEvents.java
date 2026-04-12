package com.example.oxyarena.event;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.entity.effect.SpectralMarkEntity;
import com.example.oxyarena.item.CobaltBowItem;
import com.example.oxyarena.item.SoulReaperItem;
import com.example.oxyarena.network.OccultCamouflageSyncPayload;
import com.example.oxyarena.registry.ModItems;
import com.example.oxyarena.registry.ModMobEffects;
import com.example.oxyarena.registry.ModSoundEvents;
import com.example.oxyarena.util.OccultCamouflageTuning;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class ModGameEvents {
    private static final boolean SOUL_REAPER_LEGACY_ABILITY_ENABLED = false;
    private static final String COBALT_RAIN_ARROW_TAG = "OxyArenaCobaltRainArrow";
    private static final String COBALT_RAIN_TARGET_TAG = "OxyArenaCobaltRainTarget";
    private static final String COBALT_RAIN_TRIGGERED_TAG = "OxyArenaCobaltRainTriggered";
    private static final int COBALT_ARROW_RAIN_ARROWS_PER_TICK = 3;
    private static final int COBALT_ARROW_RAIN_WAVES = 10; //10
    private static final double COBALT_ARROW_RAIN_RADIUS = 4.5D; //#4.5
    private static final double COBALT_ARROW_RAIN_HEIGHT = 16.0D;
    private static final double COBALT_ARROW_RAIN_DAMAGE = 0.5D;
    private static final float COBALT_ARROW_RAIN_VELOCITY = 2.6F;
    private static final float AMETRA_SWEEPING_DAMAGE_RATIO = 0.75F;
    private static final float MURASAMA_CRIT_DAMAGE_MULTIPLIER = 1.5F;
    private static final float COBALT_SWORD_ARMOR_IGNORE_RATIO = 0.25F;
    private static final int BLACK_DIAMOND_EXTRA_ARMOR_DURABILITY_DAMAGE = 9;
    private static final int BLACK_DIAMOND_WEAPON_DURABILITY_DAMAGE = 10;
    private static final double COBALT_SHIELD_SHOCKWAVE_RADIUS = 4.5D;
    private static final float COBALT_SHIELD_SHOCKWAVE_KNOCKBACK = 1.1F;
    private static final float FLAMING_SCYTHE_HIT_BURN_SECONDS = 4.0F;
    private static final int INCANDESCENT_MAINHAND_SELF_DAMAGE_INTERVAL_TICKS = 20;
    private static final float INCANDESCENT_MAINHAND_SELF_DAMAGE = 1.0F;
    private static final float INCANDESCENT_HIT_BURN_SECONDS = 4.0F;
    private static final int STORM_CHARGE_FALL_IMMUNITY_TICKS = 80;
    private static final double STORM_CHARGE_SELF_BOOST_MAX_DISTANCE_SQR = 36.0D;
    private static final int KUSABIMARU_DEFLECT_WINDOW_TICKS = 4;
    private static final int KUSABIMARU_STUN_TICKS = 15;
    private static final int KUSABIMARU_DEFLECT_SOUND_CHAIN_WINDOW_TICKS = 30;
    private static final int KUSABIMARU_DEFLECT_SOUND_COUNT = 6;
    private static final double SPECTRAL_BLADE_CONSUME_RANGE_SQR = 64.0D;
    private static final double ASSASSIN_DAGGER_REPLAY_RANGE_SQR = 64.0D;
    private static final int ASSASSIN_DAGGER_HISTORY_TICKS = 40;
    private static final float ASSASSIN_DAGGER_REPLAY_DAMAGE_MULTIPLIER = 0.5F;
    private static final double ASSASSIN_DAGGER_BACKSTAB_DOT_THRESHOLD = -0.35D;
    private static final int SOUL_REAPER_WEAKNESS_TICKS = 100;
    private static final int SOUL_REAPER_SELF_DAMAGE_INTERVAL_TICKS = 100;
    private static final float SOUL_REAPER_SELF_DAMAGE = 2.0F;
    private static final int SET_PASSIVE_EFFECT_DURATION_TICKS = 10;
    private static final int DIAMOND_SET_FORTUNE_LEVEL = 3;
    private static final double COBALT_SET_MAX_HEALTH_BONUS = 4.0D;
    private static final double NETHERITE_SET_KNOCKBACK_RESISTANCE_BONUS = 0.6D;
    private static final double NETHERITE_SET_EXPLOSION_KNOCKBACK_RESISTANCE_BONUS = 1.0D;
    private static final float NETHERITE_SET_EXPLOSION_DAMAGE_MULTIPLIER = 0.65F;
    private static final ResourceLocation COBALT_SET_MAX_HEALTH_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            OXYArena.MODID,
            "set_bonus.cobalt_max_health");
    private static final ResourceLocation NETHERITE_SET_KNOCKBACK_RESISTANCE_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            OXYArena.MODID,
            "set_bonus.netherite_knockback_resistance");
    private static final ResourceLocation NETHERITE_SET_EXPLOSION_KNOCKBACK_RESISTANCE_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            OXYArena.MODID,
            "set_bonus.netherite_explosion_knockback_resistance");
    private static final List<CobaltArrowRainWave> COBALT_ARROW_RAIN_WAVES_QUEUE = new ArrayList<>();
    private static final Set<UUID> AMETRA_SWEEP_ATTACKERS = new HashSet<>();
    private static final Map<UUID, Integer> MURASAMA_COMBO_COUNTS = new HashMap<>();
    private static final Set<UUID> MURASAMA_CRIT_ATTACKERS = new HashSet<>();
    private static final Map<UUID, Integer> KUSABIMARU_DEFLECT_ACTIVE_UNTIL = new HashMap<>();
    private static final Map<UUID, Integer> KUSABIMARU_DEFLECT_SOUND_INDEX = new HashMap<>();
    private static final Map<UUID, Integer> KUSABIMARU_DEFLECT_LAST_SOUND_TICK = new HashMap<>();
    private static final Set<UUID> SPECTRAL_BLADE_BURST_ATTACKERS = new HashSet<>();
    private static final Map<UUID, UUID> SPECTRAL_BLADE_LAST_TARGETS = new HashMap<>();
    private static final Set<UUID> ASSASSIN_DAGGER_REPLAY_ATTACKERS = new HashSet<>();
    private static final Map<UUID, UUID> ASSASSIN_DAGGER_LAST_TARGETS = new HashMap<>();
    private static final Map<UUID, Map<UUID, Deque<AssassinDamageSnapshot>>> ASSASSIN_DAGGER_DAMAGE_HISTORY = new HashMap<>();
    private static final List<AssassinReplayPulse> ASSASSIN_DAGGER_PENDING_REPLAY_PULSES = new ArrayList<>();
    private static final Map<UUID, Integer> STORM_CHARGE_FALL_IMMUNE_UNTIL = new HashMap<>();
    private static final Map<UUID, Map<UUID, Integer>> SOUL_REAPER_MARKED_TARGETS = new HashMap<>();
    private static final Map<UUID, UUID> SOUL_REAPER_TARGET_OWNERS = new HashMap<>();
    private static final Map<UUID, Integer> SOUL_REAPER_SELF_DAMAGE_AT = new HashMap<>();
    private static final Map<UUID, OccultCamouflageState> OCCULT_CAMOUFLAGE_STATES = new HashMap<>();

    private ModGameEvents() {
    }

    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        handleMurasamaDamagePre(event);
        handleAssassinDaggerDamagePre(event);
        if (event.getEntity() instanceof LivingEntity livingEntity
                && event.getSource().is(DamageTypeTags.IS_FIRE)
                && livingEntity.level() instanceof ServerLevel serverLevel) {
            SoulReaperFireHelper.adjustFireTickDamage(
                    livingEntity,
                    serverLevel.getServer().getTickCount(),
                    event.getNewDamage(),
                    event::setNewDamage);
        }

        if (!(event.getEntity() instanceof Player player)
                || !event.getSource().is(DamageTypeTags.IS_FIRE)
                || !isHoldingFlamingScythe(player)) {
            return;
        }

        float fireDamage = event.getNewDamage();
        if (fireDamage <= 0.0F) {
            event.setNewDamage(0.0F);
            return;
        }

        event.setNewDamage(0.0F);
        if (!player.level().isClientSide) {
            player.heal(fireDamage);
        }
    }

    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        handleStormChargeFallImmunity(event);
        handleCobaltSwordArmorPenetration(event);
        handleCobaltShieldShockwave(event);
        handleNetheriteSetExplosionResistance(event);

        if (!(event.getEntity() instanceof Player defender) || !isKusabimaruDeflectActive(defender)) {
            return;
        }

        LivingEntity attacker = getKusabimaruAttacker(event.getSource().getDirectEntity());
        if (attacker == null) {
            return;
        }

        event.setCanceled(true);
        if (event.getSource().getDirectEntity() instanceof Projectile projectile) {
            handleSuccessfulKusabimaruDeflect(defender, attacker, projectile);
            return;
        }

        handleSuccessfulKusabimaruDeflect(defender, attacker, null);
    }

    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        handleOccultCamouflageDamagePost(event);
        handleMurasamaDamagePost(event);
        handleSpectralBladeDamagePost(event);
        handleAssassinDaggerDamagePost(event);
        handleSoulReaperDamagePost(event);
        handleBlackDiamondSwordDamagePost(event);
        handleFlamingScytheDamagePost(event);
        handleIncandescentDamagePost(event);

        if (!(event.getEntity() instanceof LivingEntity target)
                || !(target.level() instanceof ServerLevel serverLevel)
                || event.getNewDamage() <= 0.0F
                || !(event.getSource().getEntity() instanceof Player player)
                || event.getSource().getDirectEntity() != player
                || AMETRA_SWEEP_ATTACKERS.contains(player.getUUID())
                || !isAmetraSwordAwakened(player)) {
            return;
        }

        ItemStack weapon = player.getMainHandItem();
        float sweepDamage = 1.0F + AMETRA_SWEEPING_DAMAGE_RATIO * event.getNewDamage();
        boolean hitAnySecondaryTarget = false;

        AMETRA_SWEEP_ATTACKERS.add(player.getUUID());
        try {
            double entityReachSq = Mth.square(player.entityInteractionRange());
            for (LivingEntity secondaryTarget : player.level().getEntitiesOfClass(
                    LivingEntity.class,
                    weapon.getSweepHitBox(player, target))) {
                if (secondaryTarget == player
                        || secondaryTarget == target
                        || player.isAlliedTo(secondaryTarget)
                        || secondaryTarget instanceof ArmorStand armorStand && armorStand.isMarker()
                        || player.distanceToSqr(secondaryTarget) >= entityReachSq) {
                    continue;
                }

                secondaryTarget.knockback(
                        0.4F,
                        Mth.sin(player.getYRot() * (float)(Math.PI / 180.0)),
                        -Mth.cos(player.getYRot() * (float)(Math.PI / 180.0)));
                hitAnySecondaryTarget |= secondaryTarget.hurt(event.getSource(), sweepDamage);
            }
        } finally {
            AMETRA_SWEEP_ATTACKERS.remove(player.getUUID());
        }

        if (!hitAnySecondaryTarget) {
            return;
        }

        serverLevel.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP,
                player.getSoundSource(),
                1.0F,
                1.0F);
        player.sweepAttack();
    }

    public static void onSweepAttack(SweepAttackEvent event) {
        if (isAmetraSwordAwakened(event.getEntity())) {
            event.setSweeping(false);
        }
    }

    public static void onBlockDrops(BlockDropsEvent event) {
        handleDiamondSetFortune(event);
    }

    private static boolean isHoldingFlamingScythe(Player player) {
        return player.getMainHandItem().is(ModItems.FLAMING_SCYTHE.get())
                || player.getOffhandItem().is(ModItems.FLAMING_SCYTHE.get());
    }

    private static void handleMurasamaDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity)
                || event.getNewDamage() <= 0.0F
                || !(event.getSource().getEntity() instanceof Player player)
                || event.getSource().getDirectEntity() != player) {
            return;
        }

        UUID playerId = player.getUUID();
        if (!player.getMainHandItem().is(ModItems.MURASAMA.get())) {
            MURASAMA_COMBO_COUNTS.remove(playerId);
            MURASAMA_CRIT_ATTACKERS.remove(playerId);
            return;
        }

        int comboCount = MURASAMA_COMBO_COUNTS.getOrDefault(playerId, 0) + 1;
        if (comboCount >= 3) {
            event.setNewDamage(event.getNewDamage() * MURASAMA_CRIT_DAMAGE_MULTIPLIER);
            MURASAMA_COMBO_COUNTS.put(playerId, 0);
            MURASAMA_CRIT_ATTACKERS.add(playerId);
        } else {
            MURASAMA_COMBO_COUNTS.put(playerId, comboCount);
        }
    }

    private static void handleMurasamaDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target)
                || event.getNewDamage() <= 0.0F
                || !(event.getSource().getEntity() instanceof Player player)
                || event.getSource().getDirectEntity() != player
                || !MURASAMA_CRIT_ATTACKERS.remove(player.getUUID())) {
            return;
        }

        player.crit(target);
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.PLAYER_ATTACK_CRIT,
                    player.getSoundSource(),
                    1.0F,
                    1.0F);
        }
    }

    private static boolean isAmetraSwordAwakened(Player player) {
        return player.hasEffect(ModMobEffects.AMETRA_AWAKENING)
                && player.getMainHandItem().is(ModItems.AMETRA_SWORD.get());
    }

    public static void clearMurasamaState(Player player) {
        UUID playerId = player.getUUID();
        MURASAMA_COMBO_COUNTS.remove(playerId);
        MURASAMA_CRIT_ATTACKERS.remove(playerId);
    }

    public static int consumeSpectralBladeMarks(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return 0;
        }

        UUID playerId = player.getUUID();
        UUID targetId = SPECTRAL_BLADE_LAST_TARGETS.get(playerId);
        if (targetId == null) {
            return 0;
        }

        Entity targetEntity = serverLevel.getEntity(targetId);
        if (!(targetEntity instanceof LivingEntity target)
                || !target.isAlive()
                || player.distanceToSqr(target) > SPECTRAL_BLADE_CONSUME_RANGE_SQR) {
            if (targetEntity == null || !(targetEntity instanceof LivingEntity livingEntity) || !livingEntity.isAlive()) {
                SPECTRAL_BLADE_LAST_TARGETS.remove(playerId, targetId);
            }
            return 0;
        }

        int consumedMarks = SpectralMarkEntity.consumeMarks(serverLevel, playerId, targetId);
        if (consumedMarks <= 0) {
            return 0;
        }

        SPECTRAL_BLADE_BURST_ATTACKERS.add(playerId);
        try {
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            target.hurt(player.damageSources().playerAttack(player), (float)consumedMarks);
        } finally {
            SPECTRAL_BLADE_BURST_ATTACKERS.remove(playerId);
        }

        return consumedMarks;
    }

    public static void clearSpectralBladeState(Player player) {
        SPECTRAL_BLADE_LAST_TARGETS.remove(player.getUUID());
        SPECTRAL_BLADE_BURST_ATTACKERS.remove(player.getUUID());
    }

    public static void clearSpectralBladeTarget(LivingEntity target) {
        UUID targetId = target.getUUID();
        SPECTRAL_BLADE_LAST_TARGETS.entrySet().removeIf(entry -> targetId.equals(entry.getValue()));
    }

    public static void clearSpectralBladeTracking() {
        SPECTRAL_BLADE_BURST_ATTACKERS.clear();
        SPECTRAL_BLADE_LAST_TARGETS.clear();
        SpectralMarkEntity.clearServerState();
    }

    public static int activateAssassinDagger(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel) || player.getServer() == null) {
            return 0;
        }

        UUID playerId = player.getUUID();
        UUID targetId = ASSASSIN_DAGGER_LAST_TARGETS.get(playerId);
        if (targetId == null) {
            return 0;
        }

        Entity targetEntity = serverLevel.getEntity(targetId);
        if (!(targetEntity instanceof LivingEntity target) || !target.isAlive()) {
            ASSASSIN_DAGGER_LAST_TARGETS.remove(playerId, targetId);
            return 0;
        }

        if (player.distanceToSqr(target) > ASSASSIN_DAGGER_REPLAY_RANGE_SQR) {
            return 0;
        }

        int currentTick = player.getServer().getTickCount();
        Deque<AssassinDamageSnapshot> history = getAssassinDaggerHistory(playerId, targetId, false);
        if (history == null || history.isEmpty()) {
            return 0;
        }

        trimAssassinDaggerHistory(history, currentTick);
        if (history.isEmpty()) {
            return 0;
        }

        List<AssassinDamageSnapshot> snapshot = new ArrayList<>(history);
        int firstTick = snapshot.get(0).serverTick();
        int queuedPulses = 0;

        for (AssassinDamageSnapshot damageSnapshot : snapshot) {
            float replayDamage = damageSnapshot.damage() * ASSASSIN_DAGGER_REPLAY_DAMAGE_MULTIPLIER;
            if (replayDamage <= 0.0F) {
                continue;
            }

            int delay = Math.max(0, damageSnapshot.serverTick() - firstTick);
            if (delay == 0) {
                applyAssassinDaggerReplayDamage(player, target, replayDamage);
            } else {
                ASSASSIN_DAGGER_PENDING_REPLAY_PULSES.add(new AssassinReplayPulse(
                        playerId,
                        targetId,
                        serverLevel.dimension(),
                        currentTick + delay,
                        replayDamage));
            }

            queuedPulses++;
        }

        return queuedPulses;
    }

    public static void clearAssassinDaggerState(Player player) {
        UUID playerId = player.getUUID();
        ASSASSIN_DAGGER_LAST_TARGETS.remove(playerId);
        ASSASSIN_DAGGER_REPLAY_ATTACKERS.remove(playerId);
        ASSASSIN_DAGGER_DAMAGE_HISTORY.remove(playerId);
        ASSASSIN_DAGGER_PENDING_REPLAY_PULSES.removeIf(pulse -> playerId.equals(pulse.ownerId()));
    }

    public static void clearAssassinDaggerTarget(LivingEntity target) {
        UUID targetId = target.getUUID();
        ASSASSIN_DAGGER_LAST_TARGETS.entrySet().removeIf(entry -> targetId.equals(entry.getValue()));
        ASSASSIN_DAGGER_PENDING_REPLAY_PULSES.removeIf(pulse -> targetId.equals(pulse.targetId()));

        for (Iterator<Entry<UUID, Map<UUID, Deque<AssassinDamageSnapshot>>>> playerIterator = ASSASSIN_DAGGER_DAMAGE_HISTORY.entrySet()
                .iterator(); playerIterator.hasNext();) {
            Entry<UUID, Map<UUID, Deque<AssassinDamageSnapshot>>> playerEntry = playerIterator.next();
            playerEntry.getValue().remove(targetId);
            if (playerEntry.getValue().isEmpty()) {
                playerIterator.remove();
            }
        }
    }

    public static void clearAssassinDaggerTracking() {
        ASSASSIN_DAGGER_REPLAY_ATTACKERS.clear();
        ASSASSIN_DAGGER_LAST_TARGETS.clear();
        ASSASSIN_DAGGER_DAMAGE_HISTORY.clear();
        ASSASSIN_DAGGER_PENDING_REPLAY_PULSES.clear();
    }

    public static void onSoulReaperActivated(Player player) {
        if (player.getServer() == null) {
            return;
        }

        consumeSoulReaperWeakness(player);
        SOUL_REAPER_SELF_DAMAGE_AT.put(
                player.getUUID(),
                player.getServer().getTickCount() + SOUL_REAPER_SELF_DAMAGE_INTERVAL_TICKS);
    }

    public static void onSoulReaperDeactivated(Player player) {
        SOUL_REAPER_SELF_DAMAGE_AT.remove(player.getUUID());
    }

    public static void clearSoulReaperState(Player player) {
        UUID playerId = player.getUUID();
        SOUL_REAPER_SELF_DAMAGE_AT.remove(playerId);

        Map<UUID, Integer> trackedTargets = SOUL_REAPER_MARKED_TARGETS.remove(playerId);
        if (trackedTargets == null) {
            return;
        }

        for (UUID targetId : trackedTargets.keySet()) {
            SOUL_REAPER_TARGET_OWNERS.remove(targetId, playerId);
        }
    }

    public static void grantStormChargeFallImmunity(Player player, Vec3 explosionPos) {
        if (player.getServer() == null || player.distanceToSqr(explosionPos) > STORM_CHARGE_SELF_BOOST_MAX_DISTANCE_SQR) {
            return;
        }

        STORM_CHARGE_FALL_IMMUNE_UNTIL.put(
                player.getUUID(),
                player.getServer().getTickCount() + STORM_CHARGE_FALL_IMMUNITY_TICKS);
    }

    public static void clearStormChargeState(Player player) {
        STORM_CHARGE_FALL_IMMUNE_UNTIL.remove(player.getUUID());
    }

    public static void clearSoulReaperTarget(LivingEntity target) {
        UUID targetId = target.getUUID();
        UUID ownerId = SOUL_REAPER_TARGET_OWNERS.remove(targetId);
        if (ownerId != null) {
            Map<UUID, Integer> ownerTargets = SOUL_REAPER_MARKED_TARGETS.get(ownerId);
            if (ownerTargets != null) {
                ownerTargets.remove(targetId);
                if (ownerTargets.isEmpty()) {
                    SOUL_REAPER_MARKED_TARGETS.remove(ownerId);
                }
            }
        }

        for (Iterator<Entry<UUID, Map<UUID, Integer>>> iterator = SOUL_REAPER_MARKED_TARGETS.entrySet().iterator();
                iterator.hasNext();) {
            Entry<UUID, Map<UUID, Integer>> entry = iterator.next();
            entry.getValue().remove(targetId);
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    public static void activateKusabimaruDeflect(Player player) {
        if (player.getServer() == null) {
            return;
        }

        KUSABIMARU_DEFLECT_ACTIVE_UNTIL.put(
                player.getUUID(),
                player.getServer().getTickCount() + KUSABIMARU_DEFLECT_WINDOW_TICKS);
    }

    public static void clearKusabimaruState(Player player) {
        KUSABIMARU_DEFLECT_ACTIVE_UNTIL.remove(player.getUUID());
        KUSABIMARU_DEFLECT_SOUND_INDEX.remove(player.getUUID());
        KUSABIMARU_DEFLECT_LAST_SOUND_TICK.remove(player.getUUID());
    }

    public static boolean isKusabimaruStunned(Player player) {
        return player.hasEffect(ModMobEffects.KUSABIMARU_STUN);
    }

    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (handleKusabimaruProjectileImpact(event)) {
            return;
        }

        if (!(event.getProjectile() instanceof AbstractArrow arrow)
                || arrow.level().isClientSide()
                || !(event.getRayTraceResult() instanceof EntityHitResult entityHitResult)
                || !(entityHitResult.getEntity() instanceof LivingEntity target)) {
            return;
        }

        if (isCobaltRainArrow(arrow)) {
            resetInitialTargetIframesIfNeeded(arrow, target);
            return;
        }

        if (!CobaltBowItem.hasArrowRain(arrow) || hasTriggeredCobaltRain(arrow)) {
            return;
        }

        markCobaltRainTriggered(arrow);
        spawnCobaltArrowRain((ServerLevel)arrow.level(), arrow.getOwner(), target);
    }

    private static void spawnCobaltArrowRain(ServerLevel level, Entity owner, LivingEntity target) {
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        COBALT_ARROW_RAIN_WAVES_QUEUE.add(new CobaltArrowRainWave(
                level,
                owner,
                target.getUUID(),
                targetCenter.x,
                target.getY() + target.getBbHeight() + COBALT_ARROW_RAIN_HEIGHT,
                targetCenter.z,
                COBALT_ARROW_RAIN_WAVES));
    }

    private static boolean isCobaltRainArrow(AbstractArrow arrow) {
        return arrow.getPersistentData().getBoolean(COBALT_RAIN_ARROW_TAG);
    }

    private static boolean hasTriggeredCobaltRain(AbstractArrow arrow) {
        return arrow.getPersistentData().getBoolean(COBALT_RAIN_TRIGGERED_TAG);
    }

    private static void markAsCobaltRainArrow(AbstractArrow arrow, UUID targetUuid) {
        CompoundTag persistentData = arrow.getPersistentData();
        persistentData.putBoolean(COBALT_RAIN_ARROW_TAG, true);
        persistentData.putUUID(COBALT_RAIN_TARGET_TAG, targetUuid);
    }

    private static void markCobaltRainTriggered(AbstractArrow arrow) {
        arrow.getPersistentData().putBoolean(COBALT_RAIN_TRIGGERED_TAG, true);
    }

    private static void resetInitialTargetIframesIfNeeded(AbstractArrow arrow, LivingEntity target) {
        CompoundTag persistentData = arrow.getPersistentData();
        if (!persistentData.hasUUID(COBALT_RAIN_TARGET_TAG)) {
            return;
        }

        if (!target.getUUID().equals(persistentData.getUUID(COBALT_RAIN_TARGET_TAG))) {
            return;
        }

        target.invulnerableTime = 0;
        target.hurtTime = 0;
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        int currentTick = event.getServer().getTickCount();
        cleanupStormChargeFallImmunity(currentTick);
        cleanupAssassinDaggerHistory(currentTick);
        tickKusabimaruStunnedPlayers(event);
        tickSoulReaperAlteredPlayers(event);
        tickIncandescentMainHandDamage(event);
        tickArmorSetPassives(event);
        tickAssassinDaggerReplay(event);
        SoulReaperFireHelper.onServerTickPost(event);

        if (COBALT_ARROW_RAIN_WAVES_QUEUE.isEmpty()) {
            return;
        }

        Iterator<CobaltArrowRainWave> iterator = COBALT_ARROW_RAIN_WAVES_QUEUE.iterator();
        while (iterator.hasNext()) {
            CobaltArrowRainWave arrowRainWave = iterator.next();
            if (arrowRainWave.level().getServer() != event.getServer()) {
                iterator.remove();
                continue;
            }

            arrowRainWave.spawnWave();
            if (arrowRainWave.isFinished()) {
                iterator.remove();
            }
        }
    }

    private static boolean handleKusabimaruProjectileImpact(ProjectileImpactEvent event) {
        if (event.getProjectile().level().isClientSide()
                || !(event.getRayTraceResult() instanceof EntityHitResult entityHitResult)
                || !(entityHitResult.getEntity() instanceof Player defender)
                || !isKusabimaruDeflectActive(defender)) {
            return false;
        }

        LivingEntity attacker = getKusabimaruAttacker(event.getProjectile());
        if (attacker == null) {
            return false;
        }

        event.setCanceled(true);
        handleSuccessfulKusabimaruDeflect(defender, attacker, event.getProjectile());
        return true;
    }

    private static boolean isKusabimaruDeflectActive(Player player) {
        if (player.getServer() == null || !player.getMainHandItem().is(ModItems.KUSABIMARU.get())) {
            KUSABIMARU_DEFLECT_ACTIVE_UNTIL.remove(player.getUUID());
            return false;
        }

        Integer activeUntilTick = KUSABIMARU_DEFLECT_ACTIVE_UNTIL.get(player.getUUID());
        if (activeUntilTick == null) {
            return false;
        }

        if (player.getServer().getTickCount() >= activeUntilTick.intValue()) {
            KUSABIMARU_DEFLECT_ACTIVE_UNTIL.remove(player.getUUID());
            return false;
        }

        return true;
    }

    private static LivingEntity getKusabimaruAttacker(Entity directEntity) {
        if (directEntity instanceof Projectile projectile) {
            return projectile.getOwner() instanceof LivingEntity livingOwner ? livingOwner : null;
        }

        return directEntity instanceof LivingEntity livingAttacker ? livingAttacker : null;
    }

    private static void handleSuccessfulKusabimaruDeflect(Player defender, LivingEntity attacker, Projectile projectile) {
        if (projectile != null && !projectile.isRemoved()) {
            projectile.deflect(ProjectileDeflection.AIM_DEFLECT, defender, defender, true);
            Vec3 look = defender.getLookAngle().normalize();
            Vec3 origin = defender.getEyePosition().add(look.scale(0.75D));
            projectile.setPos(origin.x, origin.y, origin.z);
        } else if (!(attacker instanceof Player)) {
            double knockbackX = defender.getX() - attacker.getX();
            double knockbackZ = defender.getZ() - attacker.getZ();
            if (Mth.equal((float)knockbackX, 0.0F) && Mth.equal((float)knockbackZ, 0.0F)) {
                Vec3 look = defender.getLookAngle();
                knockbackX = look.x;
                knockbackZ = look.z;
            }

            attacker.knockback(0.9F, knockbackX, knockbackZ);
        }

        ItemStack weapon = defender.getMainHandItem();
        if (!weapon.isEmpty()) {
            weapon.hurtAndBreak(1, defender, EquipmentSlot.MAINHAND);
        }
        defender.getCooldowns().removeCooldown(ModItems.KUSABIMARU.get());

        if (defender.level() instanceof ServerLevel serverLevel) {
            Holder<SoundEvent> deflectSound = getNextKusabimaruDeflectSound(defender, serverLevel.getServer().getTickCount());
            serverLevel.playSound(
                    null,
                    defender.getX(),
                    defender.getY(),
                    defender.getZ(),
                    deflectSound.value(),
                    defender.getSoundSource(),
                    1.0F,
                    1.0F);
        }

        if (attacker instanceof Player attackingPlayer && attackingPlayer != defender) {
            attackingPlayer.addEffect(new MobEffectInstance(
                    ModMobEffects.KUSABIMARU_STUN,
                    KUSABIMARU_STUN_TICKS,
                    0,
                    false,
                    false,
                    true));
        }
    }

    private static void tickKusabimaruStunnedPlayers(ServerTickEvent.Post event) {
        for (Player player : event.getServer().getPlayerList().getPlayers()) {
            if (!isKusabimaruStunned(player)) {
                continue;
            }

            Vec3 movement = player.getDeltaMovement();
            player.stopUsingItem();
            player.setSprinting(false);
            player.setDeltaMovement(0.0D, Math.min(movement.y, 0.0D), 0.0D);
            player.hurtMarked = true;
        }
    }

    private static Holder<SoundEvent> getNextKusabimaruDeflectSound(Player defender, int currentTick) {
        UUID playerId = defender.getUUID();
        int nextIndex = 0;
        Integer lastTick = KUSABIMARU_DEFLECT_LAST_SOUND_TICK.get(playerId);
        if (lastTick != null && currentTick - lastTick.intValue() <= KUSABIMARU_DEFLECT_SOUND_CHAIN_WINDOW_TICKS) {
            nextIndex = (KUSABIMARU_DEFLECT_SOUND_INDEX.getOrDefault(playerId, -1) + 1) % KUSABIMARU_DEFLECT_SOUND_COUNT;
        }

        KUSABIMARU_DEFLECT_SOUND_INDEX.put(playerId, nextIndex);
        KUSABIMARU_DEFLECT_LAST_SOUND_TICK.put(playerId, currentTick);

        return switch (nextIndex) {
            case 1 -> ModSoundEvents.DEFLECT2;
            case 2 -> ModSoundEvents.DEFLECT3;
            case 3 -> ModSoundEvents.DEFLECT4;
            case 4 -> ModSoundEvents.DEFLECT5;
            case 5 -> ModSoundEvents.DEFLECT6;
            default -> ModSoundEvents.DEFLECT;
        };
    }

    private static void handleSoulReaperDamagePost(LivingDamageEvent.Post event) {
        if (!SOUL_REAPER_LEGACY_ABILITY_ENABLED) {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity target)
                || !(target.level() instanceof ServerLevel serverLevel)
                || event.getNewDamage() <= 0.0F
                || !(event.getSource().getEntity() instanceof Player player)
                || event.getSource().getDirectEntity() != player
                || !player.getMainHandItem().is(ModItems.SOUL_REAPER.get())) {
            return;
        }

        target.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS,
                SOUL_REAPER_WEAKNESS_TICKS,
                0,
                false,
                true,
                true));
        trackSoulReaperWeakness(
                player.getUUID(),
                target.getUUID(),
                serverLevel.getServer().getTickCount() + SOUL_REAPER_WEAKNESS_TICKS);
    }

    private static void handleAssassinDaggerDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity target)
                || event.getNewDamage() <= 0.0F
                || !(event.getSource().getEntity() instanceof Player attacker)
                || event.getSource().getDirectEntity() != attacker
                || attacker == target
                || ASSASSIN_DAGGER_REPLAY_ATTACKERS.contains(attacker.getUUID())
                || !attacker.getMainHandItem().is(ModItems.ASSASSIN_DAGGER.get())
                || !isAssassinDaggerBackstab(attacker, target)) {
            return;
        }

        event.setNewDamage(event.getNewDamage() * 2.0F);
    }

    private static void handleSpectralBladeDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target)
                || !(target.level() instanceof ServerLevel serverLevel)
                || event.getNewDamage() <= 0.0F
                || !(event.getSource().getEntity() instanceof Player attacker)
                || event.getSource().getDirectEntity() != attacker
                || attacker == target
                || SPECTRAL_BLADE_BURST_ATTACKERS.contains(attacker.getUUID())
                || !attacker.getMainHandItem().is(ModItems.SPECTRAL_BLADE.get())) {
            return;
        }

        SPECTRAL_BLADE_LAST_TARGETS.put(attacker.getUUID(), target.getUUID());
        SpectralMarkEntity.spawn(serverLevel, attacker, target);
    }

    private static void handleAssassinDaggerDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target)
                || !(target.level() instanceof ServerLevel serverLevel)
                || event.getNewDamage() <= 0.0F
                || !(event.getSource().getEntity() instanceof Player attacker)
                || event.getSource().getDirectEntity() != attacker
                || attacker == target
                || ASSASSIN_DAGGER_REPLAY_ATTACKERS.contains(attacker.getUUID())
                || !attacker.getMainHandItem().is(ModItems.ASSASSIN_DAGGER.get())) {
            return;
        }

        UUID attackerId = attacker.getUUID();
        UUID targetId = target.getUUID();
        int currentTick = serverLevel.getServer().getTickCount();
        Deque<AssassinDamageSnapshot> history = getAssassinDaggerHistory(attackerId, targetId, true);
        history.addLast(new AssassinDamageSnapshot(currentTick, event.getNewDamage()));
        trimAssassinDaggerHistory(history, currentTick);
        ASSASSIN_DAGGER_LAST_TARGETS.put(attackerId, targetId);
    }

    private static void handleBlackDiamondSwordDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target)
                || event.getNewDamage() <= 0.0F
                || !(event.getSource().getEntity() instanceof Player attacker)
                || event.getSource().getDirectEntity() != attacker
                || !attacker.getMainHandItem().is(ModItems.BLACK_DIAMOND_SWORD.get())
                || attacker == target) {
            return;
        }

        applyBlackDiamondArmorDurabilityDamage(target);
        if (target instanceof Player defendingPlayer) {
            applyBlackDiamondWeaponDurabilityDamage(defendingPlayer);
        }
    }

    private static void handleFlamingScytheDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target)
                || event.getNewDamage() <= 0.0F
                || !(event.getSource().getEntity() instanceof Player attacker)
                || event.getSource().getDirectEntity() != attacker
                || !attacker.getMainHandItem().is(ModItems.FLAMING_SCYTHE.get())
                || !attacker.isOnFire()
                || attacker == target) {
            return;
        }

        target.igniteForSeconds(FLAMING_SCYTHE_HIT_BURN_SECONDS);
    }

    private static void handleIncandescentDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target)
                || event.getNewDamage() <= 0.0F
                || !(event.getSource().getEntity() instanceof Player attacker)
                || event.getSource().getDirectEntity() != attacker
                || attacker == target
                || !isIncandescentMeleeItem(attacker.getMainHandItem())) {
            return;
        }

        target.igniteForSeconds(INCANDESCENT_HIT_BURN_SECONDS);
    }

    private static void handleStormChargeFallImmunity(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || player.getServer() == null
                || !event.getSource().is(DamageTypeTags.IS_FALL)) {
            return;
        }

        Integer immuneUntil = STORM_CHARGE_FALL_IMMUNE_UNTIL.get(player.getUUID());
        if (immuneUntil == null) {
            return;
        }

        if (player.getServer().getTickCount() > immuneUntil.intValue()) {
            STORM_CHARGE_FALL_IMMUNE_UNTIL.remove(player.getUUID());
            return;
        }

        event.setCanceled(true);
        STORM_CHARGE_FALL_IMMUNE_UNTIL.remove(player.getUUID());
        player.resetFallDistance();
    }

    private static void handleCobaltSwordArmorPenetration(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)
                || event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)
                || !(event.getSource().getEntity() instanceof Player attacker)
                || event.getSource().getDirectEntity() != attacker
                || !attacker.getMainHandItem().is(ModItems.COBALT_SWORD.get())) {
            return;
        }

        event.getContainer().addModifier(DamageContainer.Reduction.ARMOR, (container, currentReduction) -> {
            float incomingDamage = container.getNewDamage();
            float armor = (float)target.getAttributeValue(Attributes.ARMOR);
            float armorToughness = (float)target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            if (incomingDamage <= 0.0F || armor <= 0.0F) {
                return currentReduction;
            }

            float reducedArmor = armor * (1.0F - COBALT_SWORD_ARMOR_IGNORE_RATIO);
            float reducedArmorDamage = CombatRules.getDamageAfterAbsorb(
                    target,
                    incomingDamage,
                    container.getSource(),
                    reducedArmor,
                    armorToughness);
            return Mth.clamp(incomingDamage - reducedArmorDamage, 0.0F, incomingDamage);
        });
    }

    private static void handleCobaltShieldShockwave(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player defender)
                || defender.level().isClientSide()
                || !defender.isBlocking()
                || !defender.getUseItem().is(ModItems.COBALT_SHIELD.get())
                || !defender.isDamageSourceBlocked(event.getSource())) {
            return;
        }

        Vec3 defenderCenter = defender.position();
        for (LivingEntity nearbyEntity : defender.level().getEntitiesOfClass(
                LivingEntity.class,
                defender.getBoundingBox().inflate(COBALT_SHIELD_SHOCKWAVE_RADIUS),
                nearby -> nearby != defender && nearby.isAlive())) {
            double knockbackX = defenderCenter.x - nearbyEntity.getX();
            double knockbackZ = defenderCenter.z - nearbyEntity.getZ();
            if (Mth.equal((float)knockbackX, 0.0F) && Mth.equal((float)knockbackZ, 0.0F)) {
                Vec3 look = defender.getLookAngle();
                knockbackX = -look.x;
                knockbackZ = -look.z;
            }

            nearbyEntity.knockback(COBALT_SHIELD_SHOCKWAVE_KNOCKBACK, knockbackX, knockbackZ);
        }
    }

    private static void applyBlackDiamondArmorDurabilityDamage(LivingEntity target) {
        for (EquipmentSlot armorSlot : new EquipmentSlot[] {
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET }) {
            ItemStack armorPiece = target.getItemBySlot(armorSlot);
            if (armorPiece.isEmpty() || !armorPiece.isDamageableItem()) {
                continue;
            }

            armorPiece.hurtAndBreak(BLACK_DIAMOND_EXTRA_ARMOR_DURABILITY_DAMAGE, target, armorSlot);
        }
    }

    private static void applyBlackDiamondWeaponDurabilityDamage(Player target) {
        ItemStack targetWeapon = target.getMainHandItem();
        if (targetWeapon.isEmpty() || !targetWeapon.isDamageableItem()) {
            applyBlackDiamondOffhandDurabilityDamage(target);
            return;
        }

        targetWeapon.hurtAndBreak(BLACK_DIAMOND_WEAPON_DURABILITY_DAMAGE, target, EquipmentSlot.MAINHAND);
        applyBlackDiamondOffhandDurabilityDamage(target);
    }

    private static void applyBlackDiamondOffhandDurabilityDamage(Player target) {
        ItemStack offhandItem = target.getOffhandItem();
        if (offhandItem.isEmpty() || !offhandItem.isDamageableItem()) {
            return;
        }

        offhandItem.hurtAndBreak(BLACK_DIAMOND_WEAPON_DURABILITY_DAMAGE, target, EquipmentSlot.OFFHAND);
    }

    private static void trackSoulReaperWeakness(UUID playerId, UUID targetId, int expiryTick) {
        UUID previousOwnerId = SOUL_REAPER_TARGET_OWNERS.put(targetId, playerId);
        if (previousOwnerId != null && !previousOwnerId.equals(playerId)) {
            Map<UUID, Integer> previousOwnerTargets = SOUL_REAPER_MARKED_TARGETS.get(previousOwnerId);
            if (previousOwnerTargets != null) {
                previousOwnerTargets.remove(targetId);
                if (previousOwnerTargets.isEmpty()) {
                    SOUL_REAPER_MARKED_TARGETS.remove(previousOwnerId);
                }
            }
        }

        SOUL_REAPER_MARKED_TARGETS
                .computeIfAbsent(playerId, ignored -> new HashMap<>())
                .put(targetId, expiryTick);
    }

    private static void consumeSoulReaperWeakness(Player player) {
        if (player.getServer() == null) {
            return;
        }

        UUID playerId = player.getUUID();
        Map<UUID, Integer> trackedTargets = SOUL_REAPER_MARKED_TARGETS.get(playerId);
        if (trackedTargets == null || trackedTargets.isEmpty()) {
            return;
        }

        int currentTick = player.getServer().getTickCount();
        int totalStrengthTicks = 0;
        Iterator<Entry<UUID, Integer>> iterator = trackedTargets.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<UUID, Integer> entry = iterator.next();
            UUID targetId = entry.getKey();
            Integer trackedExpiryTick = entry.getValue();
            if (!playerId.equals(SOUL_REAPER_TARGET_OWNERS.get(targetId))) {
                iterator.remove();
                continue;
            }

            int trackedRemainingTicks = trackedExpiryTick.intValue() - currentTick;
            if (trackedRemainingTicks <= 0) {
                SOUL_REAPER_TARGET_OWNERS.remove(targetId, playerId);
                iterator.remove();
                continue;
            }

            LivingEntity target = findLoadedLivingEntity(player.getServer(), targetId);
            if (target == null) {
                continue;
            }

            MobEffectInstance weakness = target.getEffect(MobEffects.WEAKNESS);
            if (weakness == null) {
                SOUL_REAPER_TARGET_OWNERS.remove(targetId, playerId);
                iterator.remove();
                continue;
            }

            int grantedTicks = Math.min(weakness.getDuration(), trackedRemainingTicks);
            if (grantedTicks <= 0) {
                SOUL_REAPER_TARGET_OWNERS.remove(targetId, playerId);
                iterator.remove();
                continue;
            }

            totalStrengthTicks += grantedTicks;
            target.removeEffect(MobEffects.WEAKNESS);
            SOUL_REAPER_TARGET_OWNERS.remove(targetId, playerId);
            iterator.remove();
        }

        if (trackedTargets.isEmpty()) {
            SOUL_REAPER_MARKED_TARGETS.remove(playerId);
        }

        if (totalStrengthTicks <= 0) {
            return;
        }

        MobEffectInstance currentStrength = player.getEffect(MobEffects.DAMAGE_BOOST);
        int totalDuration = totalStrengthTicks;
        int amplifier = 0;
        if (currentStrength != null) {
            totalDuration += currentStrength.getDuration();
            amplifier = Math.max(amplifier, currentStrength.getAmplifier());
        }

        player.addEffect(new MobEffectInstance(
                MobEffects.DAMAGE_BOOST,
                totalDuration,
                amplifier,
                false,
                true,
                true));
    }

    private static LivingEntity findLoadedLivingEntity(MinecraftServer server, UUID entityId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity instanceof LivingEntity livingEntity && livingEntity.isAlive()) {
                return livingEntity;
            }
        }

        return null;
    }

    private static void tickSoulReaperAlteredPlayers(ServerTickEvent.Post event) {
        if (!SOUL_REAPER_LEGACY_ABILITY_ENABLED) {
            return;
        }

        int currentTick = event.getServer().getTickCount();
        if (currentTick % 20 == 0) {
            cleanupSoulReaperWeaknessTracking(currentTick);
        }

        for (Player player : event.getServer().getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            ItemStack mainHandItem = player.getMainHandItem();
            if (!mainHandItem.is(ModItems.SOUL_REAPER.get()) || !SoulReaperItem.isAltered(mainHandItem)) {
                SOUL_REAPER_SELF_DAMAGE_AT.remove(playerId);
                continue;
            }

            Integer nextDamageTick = SOUL_REAPER_SELF_DAMAGE_AT.get(playerId);
            if (nextDamageTick == null) {
                SOUL_REAPER_SELF_DAMAGE_AT.put(playerId, currentTick + SOUL_REAPER_SELF_DAMAGE_INTERVAL_TICKS);
                continue;
            }

            if (currentTick < nextDamageTick.intValue()) {
                continue;
            }

            player.hurt(player.damageSources().magic(), SOUL_REAPER_SELF_DAMAGE);
            SOUL_REAPER_SELF_DAMAGE_AT.put(playerId, currentTick + SOUL_REAPER_SELF_DAMAGE_INTERVAL_TICKS);
        }
    }

    private static void cleanupSoulReaperWeaknessTracking(int currentTick) {
        Iterator<Entry<UUID, Map<UUID, Integer>>> playerIterator = SOUL_REAPER_MARKED_TARGETS.entrySet().iterator();
        while (playerIterator.hasNext()) {
            Entry<UUID, Map<UUID, Integer>> playerEntry = playerIterator.next();
            UUID playerId = playerEntry.getKey();
            Iterator<Entry<UUID, Integer>> targetIterator = playerEntry.getValue().entrySet().iterator();
            while (targetIterator.hasNext()) {
                Entry<UUID, Integer> targetEntry = targetIterator.next();
                UUID targetId = targetEntry.getKey();
                boolean expired = targetEntry.getValue().intValue() <= currentTick;
                boolean ownerChanged = !playerId.equals(SOUL_REAPER_TARGET_OWNERS.get(targetId));
                if (!expired && !ownerChanged) {
                    continue;
                }

                if (expired) {
                    SOUL_REAPER_TARGET_OWNERS.remove(targetId, playerId);
                }

                targetIterator.remove();
            }

            if (playerEntry.getValue().isEmpty()) {
                playerIterator.remove();
            }
        }
    }

    private static void cleanupStormChargeFallImmunity(int currentTick) {
        STORM_CHARGE_FALL_IMMUNE_UNTIL.entrySet().removeIf(entry -> entry.getValue().intValue() < currentTick);
    }

    private static boolean isAssassinDaggerBackstab(Player attacker, LivingEntity target) {
        float bodyYawRadians = target.yBodyRot * ((float)Math.PI / 180.0F);
        Vec3 targetForward = new Vec3(-Mth.sin(bodyYawRadians), 0.0D, Mth.cos(bodyYawRadians));
        Vec3 toAttacker = attacker.position().subtract(target.position());
        Vec3 horizontalDirection = new Vec3(toAttacker.x, 0.0D, toAttacker.z);
        if (horizontalDirection.lengthSqr() < 1.0E-6D) {
            return false;
        }

        return targetForward.dot(horizontalDirection.normalize()) <= ASSASSIN_DAGGER_BACKSTAB_DOT_THRESHOLD;
    }

    private static Deque<AssassinDamageSnapshot> getAssassinDaggerHistory(UUID attackerId, UUID targetId, boolean create) {
        Map<UUID, Deque<AssassinDamageSnapshot>> playerHistory = create
                ? ASSASSIN_DAGGER_DAMAGE_HISTORY.computeIfAbsent(attackerId, ignored -> new HashMap<>())
                : ASSASSIN_DAGGER_DAMAGE_HISTORY.get(attackerId);
        if (playerHistory == null) {
            return null;
        }

        return create ? playerHistory.computeIfAbsent(targetId, ignored -> new ArrayDeque<>()) : playerHistory.get(targetId);
    }

    private static void trimAssassinDaggerHistory(Deque<AssassinDamageSnapshot> history, int currentTick) {
        int oldestAllowedTick = currentTick - ASSASSIN_DAGGER_HISTORY_TICKS;
        while (!history.isEmpty() && history.peekFirst().serverTick() < oldestAllowedTick) {
            history.removeFirst();
        }
    }

    private static void cleanupAssassinDaggerHistory(int currentTick) {
        for (Iterator<Entry<UUID, Map<UUID, Deque<AssassinDamageSnapshot>>>> playerIterator = ASSASSIN_DAGGER_DAMAGE_HISTORY.entrySet()
                .iterator(); playerIterator.hasNext();) {
            Entry<UUID, Map<UUID, Deque<AssassinDamageSnapshot>>> playerEntry = playerIterator.next();
            for (Iterator<Entry<UUID, Deque<AssassinDamageSnapshot>>> targetIterator = playerEntry.getValue().entrySet()
                    .iterator(); targetIterator.hasNext();) {
                Entry<UUID, Deque<AssassinDamageSnapshot>> targetEntry = targetIterator.next();
                trimAssassinDaggerHistory(targetEntry.getValue(), currentTick);
                if (targetEntry.getValue().isEmpty()) {
                    targetIterator.remove();
                }
            }

            if (playerEntry.getValue().isEmpty()) {
                playerIterator.remove();
            }
        }
    }

    private static void tickAssassinDaggerReplay(ServerTickEvent.Post event) {
        if (ASSASSIN_DAGGER_PENDING_REPLAY_PULSES.isEmpty()) {
            return;
        }

        int currentTick = event.getServer().getTickCount();
        List<AssassinReplayPulse> duePulses = new ArrayList<>();
        ASSASSIN_DAGGER_PENDING_REPLAY_PULSES.removeIf(replayPulse -> {
            if (replayPulse.executeAtTick() > currentTick) {
                return false;
            }

            duePulses.add(replayPulse);
            return true;
        });

        for (AssassinReplayPulse replayPulse : duePulses) {
            Player attacker = event.getServer().getPlayerList().getPlayer(replayPulse.ownerId());
            LivingEntity target = findLoadedLivingEntity(event.getServer(), replayPulse.targetId());
            if (attacker == null
                    || !attacker.isAlive()
                    || target == null
                    || !target.isAlive()
                    || target.level() != attacker.level()
                    || !target.level().dimension().equals(replayPulse.dimension())) {
                continue;
            }

            applyAssassinDaggerReplayDamage(attacker, target, replayPulse.damage());
        }
    }

    private static void applyAssassinDaggerReplayDamage(Player attacker, LivingEntity target, float damage) {
        if (damage <= 0.0F) {
            return;
        }

        UUID attackerId = attacker.getUUID();
        ASSASSIN_DAGGER_REPLAY_ATTACKERS.add(attackerId);
        try {
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            target.hurt(attacker.damageSources().playerAttack(attacker), damage);
        } finally {
            ASSASSIN_DAGGER_REPLAY_ATTACKERS.remove(attackerId);
        }
    }

    private static void tickIncandescentMainHandDamage(ServerTickEvent.Post event) {
        int currentTick = event.getServer().getTickCount();
        if (currentTick % INCANDESCENT_MAINHAND_SELF_DAMAGE_INTERVAL_TICKS != 0) {
            return;
        }

        for (Player player : event.getServer().getPlayerList().getPlayers()) {
            if (player.isCreative() || player.isSpectator() || !isIncandescentHotItem(player.getMainHandItem())) {
                continue;
            }

            player.hurt(player.damageSources().magic(), INCANDESCENT_MAINHAND_SELF_DAMAGE);
        }
    }

    private static void tickArmorSetPassives(ServerTickEvent.Post event) {
        for (Player player : event.getServer().getPlayerList().getPlayers()) {
            if (player.isSpectator()) {
                clearArmorSetAttributeModifiers(player);
                clearOccultCamouflageState(player);
                continue;
            }

            if (hasFullCitrineSet(player)) {
                applyRefreshingEffect(player, MobEffects.DIG_SPEED, 1);
            }

            if (hasFullIronSet(player)) {
                applyRefreshingEffect(player, MobEffects.DAMAGE_RESISTANCE, 0);
            }

            updateTransientAttributeModifier(
                    player,
                    Attributes.MAX_HEALTH,
                    COBALT_SET_MAX_HEALTH_MODIFIER_ID,
                    COBALT_SET_MAX_HEALTH_BONUS,
                    AttributeModifier.Operation.ADD_VALUE,
                    hasFullCobaltSet(player));
            updateTransientAttributeModifier(
                    player,
                    Attributes.KNOCKBACK_RESISTANCE,
                    NETHERITE_SET_KNOCKBACK_RESISTANCE_MODIFIER_ID,
                    NETHERITE_SET_KNOCKBACK_RESISTANCE_BONUS,
                    AttributeModifier.Operation.ADD_VALUE,
                    hasFullNetheriteSet(player));
            updateTransientAttributeModifier(
                    player,
                    Attributes.EXPLOSION_KNOCKBACK_RESISTANCE,
                    NETHERITE_SET_EXPLOSION_KNOCKBACK_RESISTANCE_MODIFIER_ID,
                    NETHERITE_SET_EXPLOSION_KNOCKBACK_RESISTANCE_BONUS,
                    AttributeModifier.Operation.ADD_VALUE,
                    hasFullNetheriteSet(player));
            tickOccultCamouflage(player);
        }
    }

    private static void clearArmorSetAttributeModifiers(Player player) {
        removeTransientAttributeModifier(player, Attributes.MAX_HEALTH, COBALT_SET_MAX_HEALTH_MODIFIER_ID);
        removeTransientAttributeModifier(player, Attributes.KNOCKBACK_RESISTANCE, NETHERITE_SET_KNOCKBACK_RESISTANCE_MODIFIER_ID);
        removeTransientAttributeModifier(
                player,
                Attributes.EXPLOSION_KNOCKBACK_RESISTANCE,
                NETHERITE_SET_EXPLOSION_KNOCKBACK_RESISTANCE_MODIFIER_ID);
    }

    private static void applyRefreshingEffect(Player player, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance currentEffect = player.getEffect(effect);
        if (currentEffect != null
                && currentEffect.getAmplifier() > amplifier
                && currentEffect.getDuration() > SET_PASSIVE_EFFECT_DURATION_TICKS / 2) {
            return;
        }

        if (currentEffect != null
                && currentEffect.getAmplifier() == amplifier
                && currentEffect.getDuration() > SET_PASSIVE_EFFECT_DURATION_TICKS / 2) {
            return;
        }

        player.addEffect(new MobEffectInstance(effect, SET_PASSIVE_EFFECT_DURATION_TICKS, amplifier, false, false, true));
    }

    private static void updateTransientAttributeModifier(
            Player player,
            Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            ResourceLocation modifierId,
            double amount,
            AttributeModifier.Operation operation,
            boolean active) {
        AttributeInstance attributeInstance = player.getAttribute(attribute);
        if (attributeInstance == null) {
            return;
        }

        if (active) {
            attributeInstance.addOrUpdateTransientModifier(new AttributeModifier(modifierId, amount, operation));
            return;
        }

        boolean removed = attributeInstance.removeModifier(modifierId);
        if (removed && attribute.is(Attributes.MAX_HEALTH) && player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void removeTransientAttributeModifier(
            Player player,
            Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            ResourceLocation modifierId) {
        AttributeInstance attributeInstance = player.getAttribute(attribute);
        if (attributeInstance == null) {
            return;
        }

        boolean removed = attributeInstance.removeModifier(modifierId);
        if (removed && attribute.is(Attributes.MAX_HEALTH) && player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static boolean hasFullCitrineSet(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.CITRINE_HELMET.get())
                && player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.CITRINE_CHESTPLATE.get())
                && player.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.CITRINE_LEGGINGS.get())
                && player.getItemBySlot(EquipmentSlot.FEET).is(ModItems.CITRINE_BOOTS.get());
    }

    private static boolean hasFullCobaltSet(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.COBALT_HELMET.get())
                && player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.COBALT_CHESTPLATE.get())
                && player.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.COBALT_LEGGINGS.get())
                && player.getItemBySlot(EquipmentSlot.FEET).is(ModItems.COBALT_BOOTS.get());
    }

    private static boolean hasFullOccultSet(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.OCCULT_HELMET.get())
                && player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.OCCULT_CHESTPLATE.get())
                && player.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.OCCULT_LEGGINGS.get())
                && player.getItemBySlot(EquipmentSlot.FEET).is(ModItems.OCCULT_BOOTS.get());
    }

    private static boolean hasFullIronSet(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)
                && player.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)
                && player.getItemBySlot(EquipmentSlot.LEGS).is(Items.IRON_LEGGINGS)
                && player.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS);
    }

    private static boolean hasFullDiamondSet(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET)
                && player.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE)
                && player.getItemBySlot(EquipmentSlot.LEGS).is(Items.DIAMOND_LEGGINGS)
                && player.getItemBySlot(EquipmentSlot.FEET).is(Items.DIAMOND_BOOTS);
    }

    private static boolean hasFullNetheriteSet(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(Items.NETHERITE_HELMET)
                && player.getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE)
                && player.getItemBySlot(EquipmentSlot.LEGS).is(Items.NETHERITE_LEGGINGS)
                && player.getItemBySlot(EquipmentSlot.FEET).is(Items.NETHERITE_BOOTS);
    }

    private static void handleNetheriteSetExplosionResistance(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !hasFullNetheriteSet(player)
                || !event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
            return;
        }

        event.setAmount(event.getAmount() * NETHERITE_SET_EXPLOSION_DAMAGE_MULTIPLIER);
    }

    private static void handleDiamondSetFortune(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player player)
                || !hasFullDiamondSet(player)
                || event.isCanceled()) {
            return;
        }

        ItemStack tool = event.getTool();
        if (tool.isEmpty()) {
            return;
        }

        Holder<Enchantment> silkTouch = event.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH);
        if (tool.getEnchantmentLevel(silkTouch) > 0) {
            return;
        }

        Holder<Enchantment> fortune = event.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.FORTUNE);
        if (tool.getEnchantmentLevel(fortune) >= DIAMOND_SET_FORTUNE_LEVEL) {
            return;
        }

        ItemStack simulatedTool = tool.copy();
        simulatedTool.enchant(fortune, DIAMOND_SET_FORTUNE_LEVEL);

        BlockState state = event.getState();
        BlockPos pos = event.getPos();
        BlockEntity blockEntity = event.getBlockEntity();
        List<ItemStack> recalculatedDrops = Block.getDrops(state, event.getLevel(), pos, blockEntity, player, simulatedTool);

        event.getDrops().clear();
        event.getDrops().addAll(createDropEntities(event.getLevel(), pos, recalculatedDrops));
        event.setDroppedExperience(EnchantmentHelper.processBlockExperience(
                event.getLevel(),
                simulatedTool,
                state.getExpDrop(event.getLevel(), pos, blockEntity, player, simulatedTool)));
    }

    private static List<ItemEntity> createDropEntities(ServerLevel level, BlockPos pos, List<ItemStack> drops) {
        List<ItemEntity> entities = new ArrayList<>();
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            return entities;
        }

        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }

            double x = (double) pos.getX() + 0.5D + Mth.nextDouble(level.random, -0.25D, 0.25D);
            double y = (double) pos.getY() + 0.5D + Mth.nextDouble(level.random, -0.25D, 0.25D);
            double z = (double) pos.getZ() + 0.5D + Mth.nextDouble(level.random, -0.25D, 0.25D);
            ItemEntity entity = new ItemEntity(level, x, y, z, drop);
            entity.setDefaultPickUpDelay();
            entities.add(entity);
        }

        return entities;
    }

    public static void cancelOccultCamouflage(Player player) {
        OccultCamouflageState state = OCCULT_CAMOUFLAGE_STATES.computeIfAbsent(
                player.getUUID(),
                ignored -> new OccultCamouflageState(player.position()));
        state.lastPosition = player.position();
        state.stationaryTicks = 0;
        state.progress = 0.0F;
        updateOccultMarkerEffect(player, state.progress);
        syncOccultCamouflageProgress(player, state, false);
    }

    public static void clearOccultCamouflageState(Player player) {
        UUID playerId = player.getUUID();
        OccultCamouflageState state = OCCULT_CAMOUFLAGE_STATES.get(playerId);
        if (state != null) {
            state.progress = 0.0F;
            syncOccultCamouflageProgress(player, state, false);
            OCCULT_CAMOUFLAGE_STATES.remove(playerId);
        }
        player.removeEffect(ModMobEffects.OCCULT_CAMOUFLAGE);
    }

    public static void clearOccultCamouflageTracking() {
        OCCULT_CAMOUFLAGE_STATES.clear();
    }

    public static void syncOccultCamouflageStateTo(ServerPlayer recipient, Player target) {
        if (!(target.level() instanceof ServerLevel) || recipient.server != target.getServer()) {
            return;
        }

        int quantizedProgress = OccultCamouflageTuning.progressToQuantized(getOccultCamouflageProgress(target));
        PacketDistributor.sendToPlayer(recipient, new OccultCamouflageSyncPayload(target.getUUID(), quantizedProgress));
    }

    public static float getOccultCamouflageProgress(Player player) {
        OccultCamouflageState state = OCCULT_CAMOUFLAGE_STATES.get(player.getUUID());
        return state == null ? 0.0F : state.progress;
    }

    private static void handleOccultCamouflageDamagePost(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0.0F) {
            return;
        }

        if (event.getEntity() instanceof Player damagedPlayer) {
            cancelOccultCamouflage(damagedPlayer);
        }

        if (event.getSource().getEntity() instanceof Player attackingPlayer) {
            cancelOccultCamouflage(attackingPlayer);
        }
    }

    private static void tickOccultCamouflage(Player player) {
        if (!(player.level() instanceof ServerLevel)) {
            return;
        }

        if (!hasFullOccultSet(player)) {
            clearOccultCamouflageState(player);
            return;
        }

        OccultCamouflageState state = OCCULT_CAMOUFLAGE_STATES.computeIfAbsent(
                player.getUUID(),
                ignored -> new OccultCamouflageState(player.position()));
        Vec3 currentPosition = player.position();
        double deltaMovementSqr = currentPosition.distanceToSqr(state.lastPosition);
        boolean stationary = deltaMovementSqr <= OccultCamouflageTuning.MOVEMENT_EPSILON_SQR;
        boolean microMovement = OccultCamouflageTuning.isMicroMovement(deltaMovementSqr, player.isCrouching());
        boolean hardBreakMovement = OccultCamouflageTuning.isNormalMovement(deltaMovementSqr, player.isCrouching())
                || player.isSprinting()
                || !player.onGround()
                || player.isUsingItem();

        if (hardBreakMovement) {
            state.stationaryTicks = 0;
            state.progress = 0.0F;
        } else if (stationary) {
            state.stationaryTicks++;
            if (state.stationaryTicks >= OccultCamouflageTuning.ARMING_TICKS) {
                state.progress = Mth.clamp(
                        state.progress + OccultCamouflageTuning.fadeInStep(),
                        0.0F,
                        1.0F);
            } else {
                state.progress = 0.0F;
            }
        } else if (microMovement && state.progress > 0.0F) {
            state.stationaryTicks = OccultCamouflageTuning.ARMING_TICKS;
            state.progress = Math.max(
                    OccultCamouflageTuning.PARTIAL_FLOOR_PROGRESS,
                    state.progress - OccultCamouflageTuning.PARTIAL_DECAY_PER_TICK);
        } else {
            state.stationaryTicks = 0;
            state.progress = 0.0F;
        }

        state.lastPosition = currentPosition;
        updateOccultMarkerEffect(player, state.progress);
        syncOccultCamouflageProgress(player, state, false);
    }

    private static void updateOccultMarkerEffect(Player player, float progress) {
        if (progress <= 0.0F) {
            player.removeEffect(ModMobEffects.OCCULT_CAMOUFLAGE);
            return;
        }

        player.addEffect(new MobEffectInstance(
                ModMobEffects.OCCULT_CAMOUFLAGE,
                SET_PASSIVE_EFFECT_DURATION_TICKS,
                0,
                false,
                false,
                false));
    }

    private static void syncOccultCamouflageProgress(Player player, OccultCamouflageState state, boolean force) {
        if (player.level().isClientSide) {
            return;
        }

        int quantizedProgress = OccultCamouflageTuning.progressToQuantized(state.progress);
        if (!force && quantizedProgress == state.lastSyncedProgress) {
            return;
        }

        state.lastSyncedProgress = quantizedProgress;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new OccultCamouflageSyncPayload(player.getUUID(), quantizedProgress));
    }

    private static boolean isIncandescentHotItem(ItemStack stack) {
        return stack.is(ModItems.INCANDESCENT_INGOT.get())
                || stack.is(ModItems.INCANDESCENT_SWORD.get())
                || stack.is(ModItems.INCANDESCENT_PICKAXE.get())
                || stack.is(ModItems.INCANDESCENT_AXE.get())
                || stack.is(ModItems.INCANDESCENT_THROWING_DAGGER.get());
    }

    private static boolean isIncandescentMeleeItem(ItemStack stack) {
        return stack.is(ModItems.INCANDESCENT_SWORD.get())
                || stack.is(ModItems.INCANDESCENT_PICKAXE.get())
                || stack.is(ModItems.INCANDESCENT_AXE.get());
    }

    private record AssassinDamageSnapshot(int serverTick, float damage) {
    }

    private record AssassinReplayPulse(
            UUID ownerId,
            UUID targetId,
            ResourceKey<Level> dimension,
            int executeAtTick,
            float damage) {
    }

    private static final class OccultCamouflageState {
        private Vec3 lastPosition;
        private int stationaryTicks;
        private float progress;
        private int lastSyncedProgress;

        private OccultCamouflageState(Vec3 lastPosition) {
            this.lastPosition = lastPosition;
            this.stationaryTicks = 0;
            this.progress = 0.0F;
            this.lastSyncedProgress = -1;
        }
    }

    private static final class CobaltArrowRainWave {
        private final ServerLevel level;
        private final Entity owner;
        private final UUID targetUuid;
        private final double centerX;
        private final double spawnY;
        private final double centerZ;
        private int remainingWaves;

        private CobaltArrowRainWave(
                ServerLevel level,
                Entity owner,
                UUID targetUuid,
                double centerX,
                double spawnY,
                double centerZ,
                int remainingWaves) {
            this.level = level;
            this.owner = owner;
            this.targetUuid = targetUuid;
            this.centerX = centerX;
            this.spawnY = spawnY;
            this.centerZ = centerZ;
            this.remainingWaves = remainingWaves;
        }

        private ServerLevel level() {
            return this.level;
        }

        private void spawnWave() {
            RandomSource random = this.level.getRandom();

            for (int arrowIndex = 0; arrowIndex < COBALT_ARROW_RAIN_ARROWS_PER_TICK; arrowIndex++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double radius = COBALT_ARROW_RAIN_RADIUS * Math.sqrt(random.nextDouble());
                double spawnX = this.centerX + Math.cos(angle) * radius;
                double spawnZ = this.centerZ + Math.sin(angle) * radius;

                Arrow rainArrow = new Arrow(
                        this.level,
                        spawnX,
                        this.spawnY + random.nextDouble(),
                        spawnZ,
                        Items.ARROW.getDefaultInstance(),
                        null);
                rainArrow.setOwner(this.owner);
                rainArrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                rainArrow.setBaseDamage(COBALT_ARROW_RAIN_DAMAGE);
                markAsCobaltRainArrow(rainArrow, this.targetUuid);
                rainArrow.shoot(0.0D, -1.0D, 0.0D, COBALT_ARROW_RAIN_VELOCITY, 0.0F);

                this.level.addFreshEntity(rainArrow);
            }

            this.remainingWaves--;
        }

        private boolean isFinished() {
            return this.remainingWaves <= 0;
        }
    }
}
