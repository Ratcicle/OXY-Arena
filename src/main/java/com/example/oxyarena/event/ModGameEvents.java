package com.example.oxyarena.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.example.oxyarena.item.CobaltBowItem;
import com.example.oxyarena.registry.ModItems;
import com.example.oxyarena.registry.ModMobEffects;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class ModGameEvents {
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
    private static final List<CobaltArrowRainWave> COBALT_ARROW_RAIN_WAVES_QUEUE = new ArrayList<>();
    private static final Set<UUID> AMETRA_SWEEP_ATTACKERS = new HashSet<>();
    private static final Map<UUID, Integer> MURASAMA_COMBO_COUNTS = new HashMap<>();
    private static final Set<UUID> MURASAMA_CRIT_ATTACKERS = new HashSet<>();

    private ModGameEvents() {
    }

    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        handleMurasamaDamagePre(event);

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

    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        handleMurasamaDamagePost(event);

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

    public static void onProjectileImpact(ProjectileImpactEvent event) {
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
