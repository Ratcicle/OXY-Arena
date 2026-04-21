package com.example.oxyarena.event.gameplay;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.example.oxyarena.event.EarthbreakerCrackHelper;
import com.example.oxyarena.event.SoulReaperFireHelper;
import com.example.oxyarena.registry.ModDamageTypes;
import com.example.oxyarena.registry.ModItems;
import com.example.oxyarena.registry.ModMobEffects;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class CombatWeaponEvents {
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

    private static final Set<UUID> AMETRA_SWEEP_ATTACKERS = new HashSet<>();
    private static final Map<UUID, Integer> MURASAMA_COMBO_COUNTS = new HashMap<>();
    private static final Set<UUID> MURASAMA_CRIT_ATTACKERS = new HashSet<>();
    private static final Map<UUID, Integer> FLAMING_SCYTHE_ACTIVE_UNTIL = new HashMap<>();

    private CombatWeaponEvents() {
    }

    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        handleMurasamaDamagePre(event);

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
                || !isFlamingScytheActive(player)) {
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
        handleCobaltSwordArmorPenetration(event);
        handleCobaltShieldShockwave(event);
    }

    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        handleMurasamaDamagePost(event);
        handleBlackBladeDamagePost(event);
        handleBlackDiamondSwordDamagePost(event);
        handleFlamingScytheDamagePost(event);
        handleIncandescentDamagePost(event);
        handleAmetraDamagePost(event);
    }

    public static void onSweepAttack(SweepAttackEvent event) {
        if (isAmetraSwordAwakened(event.getEntity())) {
            event.setSweeping(false);
        }
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        tickIncandescentMainHandDamage(event);
        BlackBladeDamageHelper.onServerTickPost(event);
        pruneFlamingScytheState(event);
        SoulReaperFireHelper.onServerTickPost(event);
        EarthbreakerCrackHelper.onServerTickPost(event);
    }

    public static void clearMurasamaState(Player player) {
        UUID playerId = player.getUUID();
        MURASAMA_COMBO_COUNTS.remove(playerId);
        MURASAMA_CRIT_ATTACKERS.remove(playerId);
    }

    public static void activateFlamingScythe(Player player, int durationTicks) {
        if (!(player.level() instanceof ServerLevel serverLevel) || durationTicks <= 0) {
            return;
        }

        FLAMING_SCYTHE_ACTIVE_UNTIL.put(
                player.getUUID(),
                serverLevel.getServer().getTickCount() + durationTicks);
    }

    public static void clearFlamingScytheState(Player player) {
        FLAMING_SCYTHE_ACTIVE_UNTIL.remove(player.getUUID());
    }

    public static void clearFlamingScytheTracking() {
        FLAMING_SCYTHE_ACTIVE_UNTIL.clear();
    }

    public static void clearBlackBladeTracking() {
        BlackBladeDamageHelper.clearAll();
    }

    private static boolean isMainHandFlamingScythe(Player player) {
        return player.getMainHandItem().is(ModItems.FLAMING_SCYTHE.get());
    }

    private static boolean isFlamingScytheActive(Player player) {
        if (!isMainHandFlamingScythe(player) || !(player.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        Integer activeUntil = FLAMING_SCYTHE_ACTIVE_UNTIL.get(player.getUUID());
        if (activeUntil == null) {
            return false;
        }

        if (serverLevel.getServer().getTickCount() > activeUntil) {
            FLAMING_SCYTHE_ACTIVE_UNTIL.remove(player.getUUID());
            return false;
        }

        return true;
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

    private static void handleAmetraDamagePost(LivingDamageEvent.Post event) {
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

    private static void handleBlackBladeDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target)
                || !(target.level() instanceof ServerLevel serverLevel)
                || event.getNewDamage() <= 0.0F
                || event.getSource().is(ModDamageTypes.BLACK_BLADE_PULSE)
                || event.getSource().is(ModDamageTypes.BLACK_BLADE_PROJECTILE)
                || !(event.getSource().getEntity() instanceof Player attacker)
                || event.getSource().getDirectEntity() != attacker
                || !attacker.getMainHandItem().is(ModItems.BLACK_BLADE.get())
                || attacker == target) {
            return;
        }

        BlackBladeDamageHelper.schedulePassiveDamage(serverLevel, target, attacker);
    }

    private static void handleFlamingScytheDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target)
                || event.getNewDamage() <= 0.0F
                || !(event.getSource().getEntity() instanceof Player attacker)
                || event.getSource().getDirectEntity() != attacker
                || !attacker.getMainHandItem().is(ModItems.FLAMING_SCYTHE.get())
                || !isFlamingScytheActive(attacker)
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
            float armor = (float)target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
            float armorToughness = (float)target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
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

    private static void pruneFlamingScytheState(ServerTickEvent.Post event) {
        int currentTick = event.getServer().getTickCount();
        FLAMING_SCYTHE_ACTIVE_UNTIL.entrySet().removeIf(entry -> currentTick > entry.getValue());
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
}
