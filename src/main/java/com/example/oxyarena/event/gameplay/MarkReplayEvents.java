package com.example.oxyarena.event.gameplay;

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

import com.example.oxyarena.entity.effect.SpectralMarkEntity;
import com.example.oxyarena.registry.ModItems;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class MarkReplayEvents {
    private static final double SPECTRAL_BLADE_CONSUME_RANGE_SQR = 64.0D;
    private static final double ASSASSIN_DAGGER_REPLAY_RANGE_SQR = 64.0D;
    private static final int ASSASSIN_DAGGER_HISTORY_TICKS = 40;
    private static final float ASSASSIN_DAGGER_REPLAY_DAMAGE_MULTIPLIER = 0.5F;
    private static final double ASSASSIN_DAGGER_BACKSTAB_DOT_THRESHOLD = -0.35D;

    private static final Set<UUID> SPECTRAL_BLADE_BURST_ATTACKERS = new HashSet<>();
    private static final Map<UUID, UUID> SPECTRAL_BLADE_LAST_TARGETS = new HashMap<>();
    private static final Set<UUID> ASSASSIN_DAGGER_REPLAY_ATTACKERS = new HashSet<>();
    private static final Map<UUID, UUID> ASSASSIN_DAGGER_LAST_TARGETS = new HashMap<>();
    private static final Map<UUID, Map<UUID, Deque<AssassinDamageSnapshot>>> ASSASSIN_DAGGER_DAMAGE_HISTORY = new HashMap<>();
    private static final List<AssassinReplayPulse> ASSASSIN_DAGGER_PENDING_REPLAY_PULSES = new ArrayList<>();

    private MarkReplayEvents() {
    }

    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        handleAssassinDaggerDamagePre(event);
    }

    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        handleSpectralBladeDamagePost(event);
        handleAssassinDaggerDamagePost(event);
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        int currentTick = event.getServer().getTickCount();
        cleanupAssassinDaggerHistory(currentTick);
        tickAssassinDaggerReplay(event);
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

    private static LivingEntity findLoadedLivingEntity(MinecraftServer server, UUID entityId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity instanceof LivingEntity livingEntity && livingEntity.isAlive()) {
                return livingEntity;
            }
        }

        return null;
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
}
