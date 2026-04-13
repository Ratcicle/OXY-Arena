package com.example.oxyarena.event;

import com.example.oxyarena.event.gameplay.ArmorSetEvents;
import com.example.oxyarena.event.gameplay.CombatWeaponEvents;
import com.example.oxyarena.event.gameplay.CounterMobilityEvents;
import com.example.oxyarena.event.gameplay.MarkReplayEvents;
import com.example.oxyarena.event.gameplay.OccultCamouflageEvents;
import com.example.oxyarena.event.gameplay.ProjectileSpecialEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class ModGameEvents {
    private ModGameEvents() {
    }

    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        CombatWeaponEvents.onLivingDamagePre(event);
        MarkReplayEvents.onLivingDamagePre(event);
    }

    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        CounterMobilityEvents.onLivingIncomingDamage(event);
        CombatWeaponEvents.onLivingIncomingDamage(event);
        ArmorSetEvents.onLivingIncomingDamage(event);
    }

    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        OccultCamouflageEvents.onLivingDamagePost(event);
        CombatWeaponEvents.onLivingDamagePost(event);
        MarkReplayEvents.onLivingDamagePost(event);
    }

    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (CounterMobilityEvents.onProjectileImpact(event)) {
            return;
        }

        ProjectileSpecialEvents.onProjectileImpact(event);
    }

    public static void onSweepAttack(SweepAttackEvent event) {
        CombatWeaponEvents.onSweepAttack(event);
    }

    public static void onBlockDrops(BlockDropsEvent event) {
        ArmorSetEvents.onBlockDrops(event);
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        CounterMobilityEvents.onServerTickPost(event);
        MarkReplayEvents.onServerTickPost(event);
        CombatWeaponEvents.onServerTickPost(event);
        ArmorSetEvents.onServerTickPost(event);
        OccultCamouflageEvents.onServerTickPost(event);
        ProjectileSpecialEvents.onServerTickPost(event);
    }

    public static void clearMurasamaState(Player player) {
        CombatWeaponEvents.clearMurasamaState(player);
    }

    public static void clearRiversOfBloodState(Player player) {
        CombatWeaponEvents.clearRiversOfBloodState(player);
    }

    public static int consumeSpectralBladeMarks(Player player) {
        return MarkReplayEvents.consumeSpectralBladeMarks(player);
    }

    public static void clearSpectralBladeState(Player player) {
        MarkReplayEvents.clearSpectralBladeState(player);
    }

    public static void clearSpectralBladeTarget(LivingEntity target) {
        MarkReplayEvents.clearSpectralBladeTarget(target);
    }

    public static void clearSpectralBladeTracking() {
        MarkReplayEvents.clearSpectralBladeTracking();
    }

    public static int activateAssassinDagger(Player player) {
        return MarkReplayEvents.activateAssassinDagger(player);
    }

    public static void clearAssassinDaggerState(Player player) {
        MarkReplayEvents.clearAssassinDaggerState(player);
    }

    public static void clearAssassinDaggerTarget(LivingEntity target) {
        MarkReplayEvents.clearAssassinDaggerTarget(target);
    }

    public static void clearAssassinDaggerTracking() {
        MarkReplayEvents.clearAssassinDaggerTracking();
    }

    public static void grantStormChargeFallImmunity(Player player, Vec3 explosionPos) {
        CounterMobilityEvents.grantStormChargeFallImmunity(player, explosionPos);
    }

    public static void clearStormChargeState(Player player) {
        CounterMobilityEvents.clearStormChargeState(player);
    }

    public static void activateKusabimaruDeflect(Player player) {
        CounterMobilityEvents.activateKusabimaruDeflect(player);
    }

    public static void clearKusabimaruState(Player player) {
        CounterMobilityEvents.clearKusabimaruState(player);
    }

    public static boolean isKusabimaruStunned(Player player) {
        return CounterMobilityEvents.isKusabimaruStunned(player);
    }

    public static void cancelOccultCamouflage(Player player) {
        OccultCamouflageEvents.cancelOccultCamouflage(player);
    }

    public static void clearOccultCamouflageState(Player player) {
        OccultCamouflageEvents.clearOccultCamouflageState(player);
    }

    public static void clearOccultCamouflageTracking() {
        OccultCamouflageEvents.clearOccultCamouflageTracking();
    }

    public static void syncOccultCamouflageStateTo(ServerPlayer recipient, Player target) {
        OccultCamouflageEvents.syncOccultCamouflageStateTo(recipient, target);
    }

    public static float getOccultCamouflageProgress(Player player) {
        return OccultCamouflageEvents.getOccultCamouflageProgress(player);
    }
}
