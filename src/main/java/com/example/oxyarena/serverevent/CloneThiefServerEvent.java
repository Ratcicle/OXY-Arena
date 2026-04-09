package com.example.oxyarena.serverevent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.entity.event.CloneThiefEntity;
import com.example.oxyarena.registry.ModEntityTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class CloneThiefServerEvent implements OxyServerEvent {
    private static final int EVENT_DURATION_TICKS = 20 * 60 * 5;
    private static final int MAX_SPAWN_ATTEMPTS = 24;
    private static final int MIN_SPAWN_OFFSET = 2;
    private static final int MAX_SPAWN_OFFSET = 6;
    private static final ResourceLocation BOSSBAR_ID = ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "clones");

    @Nullable
    private CustomBossEvent bossBar;
    private boolean active;
    private int timeRemainingTicks;
    private final Map<UUID, UUID> cloneUuidsByOwner = new LinkedHashMap<>();

    @Override
    public String getId() {
        return "clones";
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("event.oxyarena.clones");
    }

    @Override
    public boolean isActive() {
        return this.active;
    }

    @Override
    public boolean start(MinecraftServer server) {
        if (this.active) {
            return false;
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return false;
        }

        this.cleanupStaleRuntimeArtifacts(server);
        List<ServerPlayer> eligiblePlayers = this.getEligiblePlayers(server);
        if (eligiblePlayers.isEmpty()) {
            return false;
        }

        int spawnedClones = 0;
        for (ServerPlayer player : eligiblePlayers) {
            if (this.spawnCloneForPlayer(overworld, player)) {
                spawnedClones++;
            }
        }

        if (spawnedClones <= 0) {
            return false;
        }

        this.active = true;
        this.timeRemainingTicks = EVENT_DURATION_TICKS;
        this.bossBar = this.createBossBar(server);
        this.updateBossBar(server);
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.clones.started")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                false);
        return true;
    }

    @Override
    public void stop(MinecraftServer server, ServerEventStopReason reason) {
        if (!this.active && reason != ServerEventStopReason.SERVER_SHUTDOWN) {
            return;
        }

        this.removeTrackedClones(server, reason == ServerEventStopReason.COMPLETED);
        this.active = false;
        this.timeRemainingTicks = 0;
        this.clearBossBar(server);
    }

    @Override
    public void tick(MinecraftServer server) {
        if (!this.active) {
            return;
        }

        this.timeRemainingTicks--;
        this.pruneMissingClones(server);
        if (this.timeRemainingTicks <= 0) {
            this.stop(server, ServerEventStopReason.COMPLETED);
            return;
        }

        if (server.getTickCount() % 20 == 0) {
            this.updateBossBar(server);
        }
    }

    @Override
    public void onLivingDeath(MinecraftServer server, LivingDeathEvent event) {
        if (!(event.getEntity() instanceof CloneThiefEntity clone)) {
            return;
        }

        UUID cloneUuid = clone.getUUID();
        clone.getOwnerUuid().ifPresentOrElse(
                ownerUuid -> this.cloneUuidsByOwner.remove(ownerUuid, cloneUuid),
                () -> this.cloneUuidsByOwner.values().removeIf(cloneUuid::equals));
    }

    @Override
    public void onPlayerLoggedIn(MinecraftServer server, ServerPlayer player) {
        if (this.bossBar != null) {
            this.bossBar.addPlayer(player);
        }
    }

    @Override
    public void onPlayerChangedDimension(MinecraftServer server, ServerPlayer player) {
        if (this.bossBar != null) {
            this.bossBar.addPlayer(player);
        }
    }

    @Override
    public int getTimeRemainingTicks() {
        return this.timeRemainingTicks;
    }

    @Override
    public void cleanupStaleRuntimeArtifacts(MinecraftServer server) {
        this.active = false;
        this.timeRemainingTicks = 0;
        this.clearBossBar(server);
        this.cloneUuidsByOwner.clear();
        stopAllClones(server, false);
    }

    public static int stopAllClones(MinecraftServer server, boolean destroyStolenItems) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return 0;
        }

        List<CloneThiefEntity> clones = getClones(overworld);
        for (CloneThiefEntity clone : clones) {
            if (destroyStolenItems) {
                clone.destroyStolenItem();
            } else {
                clone.dropStolenItemManually();
            }
            clone.discard();
        }

        return clones.size();
    }

    private boolean spawnCloneForPlayer(ServerLevel level, ServerPlayer player) {
        CloneThiefEntity clone = ModEntityTypes.CLONE_THIEF.get().create(level);
        if (clone == null) {
            return false;
        }

        BlockPos spawnPos = this.findSpawnPosNearPlayer(level, player, clone);
        if (spawnPos == null) {
            return false;
        }

        clone.prepareClone(player);
        clone.moveTo(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F,
                0.0F);
        if (!level.addFreshEntity(clone)) {
            return false;
        }

        this.cloneUuidsByOwner.put(player.getUUID(), clone.getUUID());
        return true;
    }

    @Nullable
    private BlockPos findSpawnPosNearPlayer(ServerLevel level, ServerPlayer player, CloneThiefEntity clone) {
        BlockPos center = player.blockPosition();
        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
            int offsetX = level.random.nextBoolean()
                    ? level.random.nextIntBetweenInclusive(MIN_SPAWN_OFFSET, MAX_SPAWN_OFFSET)
                    : -level.random.nextIntBetweenInclusive(MIN_SPAWN_OFFSET, MAX_SPAWN_OFFSET);
            int offsetZ = level.random.nextBoolean()
                    ? level.random.nextIntBetweenInclusive(MIN_SPAWN_OFFSET, MAX_SPAWN_OFFSET)
                    : -level.random.nextIntBetweenInclusive(MIN_SPAWN_OFFSET, MAX_SPAWN_OFFSET);

            for (int yOffset : new int[] { 0, 1, -1, 2, -2, 3, -3 }) {
                BlockPos spawnPos = center.offset(offsetX, yOffset, offsetZ);
                if (!CloneThiefEntity.isValidSpawnPos(level, spawnPos, clone.getType())) {
                    continue;
                }

                if (spawnPos.distSqr(center) > 144.0D) {
                    continue;
                }

                return spawnPos;
            }
        }

        return null;
    }

    private List<ServerPlayer> getEligiblePlayers(MinecraftServer server) {
        ServerEventArea eventArea = ServerEventAreas.getArea(server, ServerEventGroup.MAP_ROTATION);
        return server.getPlayerList().getPlayers().stream()
                .filter(player -> !player.isCreative())
                .filter(player -> !player.isSpectator())
                .filter(player -> player.serverLevel().dimension() == Level.OVERWORLD)
                .filter(player -> eventArea.contains(player.getX(), player.getZ()))
                .toList();
    }

    private void pruneMissingClones(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            this.cloneUuidsByOwner.clear();
            return;
        }

        this.cloneUuidsByOwner.entrySet().removeIf(entry -> !(overworld.getEntity(entry.getValue()) instanceof CloneThiefEntity));
    }

    private void removeTrackedClones(MinecraftServer server, boolean destroyStolenItems) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            this.cloneUuidsByOwner.clear();
            return;
        }

        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(this.cloneUuidsByOwner.entrySet())) {
            Entity entity = overworld.getEntity(entry.getValue());
            if (!(entity instanceof CloneThiefEntity clone)) {
                continue;
            }

            if (destroyStolenItems && clone.hasStolenItem()) {
                ServerPlayer owner = overworld.getServer().getPlayerList().getPlayer(entry.getKey());
                if (owner != null) {
                    owner.sendSystemMessage(
                            Component.translatable("event.oxyarena.clones.lost", clone.getStolenStack().getHoverName())
                                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                }
                clone.destroyStolenItem();
            } else {
                clone.dropStolenItemManually();
            }

            clone.discard();
        }

        this.cloneUuidsByOwner.clear();
    }

    private CustomBossEvent createBossBar(MinecraftServer server) {
        CustomBossEvents bossEvents = server.getCustomBossEvents();
        CustomBossEvent existingBossBar = bossEvents.get(BOSSBAR_ID);
        if (existingBossBar != null) {
            existingBossBar.removeAllPlayers();
            bossEvents.remove(existingBossBar);
        }

        CustomBossEvent cloneBossBar = bossEvents.create(
                BOSSBAR_ID,
                Component.translatable("event.oxyarena.clones.bossbar"));
        cloneBossBar.setColor(BossEvent.BossBarColor.PURPLE);
        cloneBossBar.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
        cloneBossBar.setMax(EVENT_DURATION_TICKS);
        cloneBossBar.setValue(EVENT_DURATION_TICKS);
        cloneBossBar.setVisible(true);
        server.getPlayerList().getPlayers().forEach(cloneBossBar::addPlayer);
        return cloneBossBar;
    }

    private void clearBossBar(MinecraftServer server) {
        CustomBossEvents bossEvents = server.getCustomBossEvents();
        CustomBossEvent cloneBossBar = this.bossBar != null ? this.bossBar : bossEvents.get(BOSSBAR_ID);
        if (cloneBossBar != null) {
            cloneBossBar.removeAllPlayers();
            bossEvents.remove(cloneBossBar);
        }

        this.bossBar = null;
    }

    private void updateBossBar(MinecraftServer server) {
        if (this.bossBar == null) {
            return;
        }

        this.bossBar.setMax(EVENT_DURATION_TICKS);
        this.bossBar.setValue(this.timeRemainingTicks);
        this.bossBar.setVisible(true);
        server.getPlayerList().getPlayers().forEach(this.bossBar::addPlayer);
    }

    private static List<CloneThiefEntity> getClones(ServerLevel level) {
        return level.getEntitiesOfClass(CloneThiefEntity.class, getLoadedWorldBounds(level));
    }

    private static AABB getLoadedWorldBounds(ServerLevel level) {
        return new AABB(
                level.getWorldBorder().getMinX(),
                level.getMinBuildHeight(),
                level.getWorldBorder().getMinZ(),
                level.getWorldBorder().getMaxX(),
                level.getMaxBuildHeight(),
                level.getWorldBorder().getMaxZ());
    }
}
