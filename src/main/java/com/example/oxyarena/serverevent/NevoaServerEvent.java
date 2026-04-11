package com.example.oxyarena.serverevent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.registry.ModItems;
import com.example.oxyarena.registry.ModParticleTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class NevoaServerEvent implements OxyServerEvent {
    private static final int FINAL_ZONE_SIZE = 50;
    private static final int FINAL_ZONE_HALF_SIZE = FINAL_ZONE_SIZE / 2;
    private static final int FINAL_STAND_DURATION_TICKS = 20 * 60 * 2;
    private static final int CLOSING_TICKS_PER_BLOCK = 10;
    private static final int DAMAGE_GRACE_TICKS = 20;
    private static final int DAMAGE_INTERVAL_TICKS = 10;
    private static final int PARTICLE_INTERVAL_TICKS = 5;
    private static final double PARTICLE_VIEW_DISTANCE = 80.0D;
    private static final double PARTICLE_SEGMENT_HALF_LENGTH = 40.0D;
    private static final double PARTICLE_STEP = 3.5D;
    private static final double[] PARTICLE_HEIGHT_OFFSETS = {0.25D, 0.75D, 1.25D, 1.75D, 2.25D, 2.75D, 3.25D, 3.75D, 4.25D};
    private static final int ESTUS_REWARD_NO_DEATHS = 10;
    private static final int ESTUS_REWARD_ONE_DEATH = 5;
    private static final ResourceLocation BOSSBAR_ID = ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "nevoa");

    @Nullable
    private CustomBossEvent bossBar;
    @Nullable
    private ServerEventArea initialArea;
    private final Map<UUID, Integer> exposureTicks = new HashMap<>();
    private final Map<UUID, Integer> deathCounts = new HashMap<>();
    private Phase phase = Phase.IDLE;
    private int timeRemainingTicks;
    private int totalClosingTicks;
    private int closingTicksElapsed;
    private int safeCenterX;
    private int safeCenterZ;
    private double initialMinX;
    private double initialMaxX;
    private double initialMinZ;
    private double initialMaxZ;
    private double currentMinX;
    private double currentMaxX;
    private double currentMinZ;
    private double currentMaxZ;
    private double targetMinX;
    private double targetMaxX;
    private double targetMinZ;
    private double targetMaxZ;

    @Override
    public String getId() {
        return "nevoa";
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("event.oxyarena.nevoa");
    }

    @Override
    public boolean isActive() {
        return this.phase != Phase.IDLE;
    }

    @Override
    public boolean start(MinecraftServer server) {
        if (this.isActive() || server.overworld() == null) {
            return false;
        }

        ServerEventArea eventArea = ServerEventAreas.getArea(server, ServerEventGroup.MAP_ROTATION);
        if (eventArea.maxX() - eventArea.minX() < FINAL_ZONE_SIZE
                || eventArea.maxZ() - eventArea.minZ() < FINAL_ZONE_SIZE) {
            return false;
        }

        this.cleanupStaleRuntimeArtifacts(server);

        RandomSource random = server.overworld().random;
        int minSafeCenterX = eventArea.minX() + FINAL_ZONE_HALF_SIZE;
        int maxSafeCenterX = eventArea.maxX() - FINAL_ZONE_HALF_SIZE;
        int minSafeCenterZ = eventArea.minZ() + FINAL_ZONE_HALF_SIZE;
        int maxSafeCenterZ = eventArea.maxZ() - FINAL_ZONE_HALF_SIZE;
        if (minSafeCenterX > maxSafeCenterX || minSafeCenterZ > maxSafeCenterZ) {
            return false;
        }

        this.initialArea = eventArea;
        this.safeCenterX = random.nextIntBetweenInclusive(minSafeCenterX, maxSafeCenterX);
        this.safeCenterZ = random.nextIntBetweenInclusive(minSafeCenterZ, maxSafeCenterZ);
        this.initialMinX = eventArea.minX();
        this.initialMaxX = eventArea.maxX();
        this.initialMinZ = eventArea.minZ();
        this.initialMaxZ = eventArea.maxZ();
        this.targetMinX = this.safeCenterX - FINAL_ZONE_HALF_SIZE;
        this.targetMaxX = this.safeCenterX + FINAL_ZONE_HALF_SIZE;
        this.targetMinZ = this.safeCenterZ - FINAL_ZONE_HALF_SIZE;
        this.targetMaxZ = this.safeCenterZ + FINAL_ZONE_HALF_SIZE;
        this.currentMinX = this.initialMinX;
        this.currentMaxX = this.initialMaxX;
        this.currentMinZ = this.initialMinZ;
        this.currentMaxZ = this.initialMaxZ;
        this.totalClosingTicks = this.computeTotalClosingTicks();
        this.closingTicksElapsed = 0;
        this.timeRemainingTicks = this.totalClosingTicks;
        this.phase = Phase.CLOSING;
        this.bossBar = this.createBossBar(server);
        this.updateBossBar(server);
        this.deathCounts.clear();

        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.nevoa.started")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                false);
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.nevoa.center", this.safeCenterX, this.safeCenterZ)
                        .withStyle(ChatFormatting.GRAY),
                false);
        this.playStartSound(server);
        return true;
    }

    @Override
    public void stop(MinecraftServer server, ServerEventStopReason reason) {
        if (!this.isActive() && reason != ServerEventStopReason.SERVER_SHUTDOWN) {
            return;
        }

        boolean grantRewards = reason == ServerEventStopReason.COMPLETED;
        if (grantRewards) {
            this.rewardPlayers(server);
        }

        this.phase = Phase.IDLE;
        this.timeRemainingTicks = 0;
        this.totalClosingTicks = 0;
        this.closingTicksElapsed = 0;
        this.initialArea = null;
        this.exposureTicks.clear();
        this.deathCounts.clear();
        this.clearBossBar(server);
        if (reason != ServerEventStopReason.SERVER_SHUTDOWN) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("event.oxyarena.nevoa.finished")
                            .withStyle(ChatFormatting.GRAY),
                    false);
        }
    }

    @Override
    public void tick(MinecraftServer server) {
        if (!this.isActive() || server.overworld() == null || this.initialArea == null) {
            return;
        }

        ServerLevel overworld = server.overworld();
        switch (this.phase) {
            case CLOSING -> this.tickClosing(server, overworld);
            case FINAL_STAND -> this.tickFinalStand(server, overworld);
            case IDLE -> {
            }
        }
    }

    @Override
    public void onLivingDeath(MinecraftServer server, LivingDeathEvent event) {
        if (!this.isActive() || this.initialArea == null || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (player.serverLevel().dimension() != Level.OVERWORLD
                || !this.initialArea.contains(player.getX(), player.getZ())) {
            return;
        }

        this.deathCounts.merge(player.getUUID(), 1, Integer::sum);
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
    public Component getStatusText() {
        return switch (this.phase) {
            case CLOSING -> Component.translatable(
                    "event.oxyarena.nevoa.status.closing",
                    Mth.floor(this.currentMinX),
                    Mth.ceil(this.currentMaxX),
                    Mth.floor(this.currentMinZ),
                    Mth.ceil(this.currentMaxZ),
                    this.formatTicks(this.timeRemainingTicks));
            case FINAL_STAND -> Component.translatable(
                    "event.oxyarena.nevoa.status.final",
                    this.formatTicks(this.timeRemainingTicks));
            case IDLE -> null;
        };
    }

    @Override
    public void cleanupStaleRuntimeArtifacts(MinecraftServer server) {
        this.phase = Phase.IDLE;
        this.timeRemainingTicks = 0;
        this.totalClosingTicks = 0;
        this.closingTicksElapsed = 0;
        this.initialArea = null;
        this.exposureTicks.clear();
        this.deathCounts.clear();
        this.clearBossBar(server);
    }

    private void rewardPlayers(MinecraftServer server) {
        if (this.initialArea == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.serverLevel().dimension() != Level.OVERWORLD
                    || !this.initialArea.contains(player.getX(), player.getZ())) {
                continue;
            }

            int deaths = this.deathCounts.getOrDefault(player.getUUID(), 0);
            int rewardCount = switch (deaths) {
                case 0 -> ESTUS_REWARD_NO_DEATHS;
                case 1 -> ESTUS_REWARD_ONE_DEATH;
                default -> 0;
            };

            if (rewardCount <= 0) {
                player.sendSystemMessage(
                        Component.translatable("event.oxyarena.nevoa.reward_fail")
                                .withStyle(ChatFormatting.RED));
                continue;
            }

            ItemStack reward = new ItemStack(ModItems.ESTUS_FLASK.get(), rewardCount);
            if (!player.getInventory().add(reward.copy())) {
                player.drop(reward.copy(), false);
            }

            player.sendSystemMessage(
                    Component.translatable("event.oxyarena.nevoa.reward_success", rewardCount)
                            .withStyle(ChatFormatting.GOLD));
        }
    }

    private void tickClosing(MinecraftServer server, ServerLevel level) {
        this.closingTicksElapsed = Math.min(this.closingTicksElapsed + 1, this.totalClosingTicks);
        this.recalculateCurrentBounds();
        this.timeRemainingTicks = Math.max(0, this.totalClosingTicks - this.closingTicksElapsed);
        this.applyExposureDamage(level);
        if (server.getTickCount() % PARTICLE_INTERVAL_TICKS == 0) {
            this.spawnBoundaryParticles(level);
        }

        if (server.getTickCount() % 20 == 0) {
            this.updateBossBar(server);
        }

        if (this.closingTicksElapsed >= this.totalClosingTicks) {
            this.startFinalStand(server);
        }
    }

    private void tickFinalStand(MinecraftServer server, ServerLevel level) {
        this.timeRemainingTicks--;
        this.applyExposureDamage(level);
        if (server.getTickCount() % PARTICLE_INTERVAL_TICKS == 0) {
            this.spawnBoundaryParticles(level);
        }

        if (server.getTickCount() % 20 == 0) {
            this.updateBossBar(server);
        }

        if (this.timeRemainingTicks <= 0) {
            this.stop(server, ServerEventStopReason.COMPLETED);
        }
    }

    private void startFinalStand(MinecraftServer server) {
        this.phase = Phase.FINAL_STAND;
        this.currentMinX = this.targetMinX;
        this.currentMaxX = this.targetMaxX;
        this.currentMinZ = this.targetMinZ;
        this.currentMaxZ = this.targetMaxZ;
        this.timeRemainingTicks = FINAL_STAND_DURATION_TICKS;
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.nevoa.final_stand")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                false);
        this.updateBossBar(server);
    }

    private void applyExposureDamage(ServerLevel level) {
        if (this.initialArea == null) {
            return;
        }

        Set<UUID> trackedPlayers = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            if (player.isCreative() || player.isSpectator()) {
                this.exposureTicks.remove(player.getUUID());
                continue;
            }

            if (!this.initialArea.contains(player.getX(), player.getZ())) {
                this.exposureTicks.remove(player.getUUID());
                continue;
            }

            trackedPlayers.add(player.getUUID());
            if (this.isInsideSafeZone(player.getX(), player.getZ())) {
                this.exposureTicks.remove(player.getUUID());
                continue;
            }

            int updatedExposure = this.exposureTicks.merge(player.getUUID(), 1, Integer::sum);
            if (updatedExposure < DAMAGE_GRACE_TICKS) {
                continue;
            }

            if ((updatedExposure - DAMAGE_GRACE_TICKS) % DAMAGE_INTERVAL_TICKS != 0) {
                continue;
            }

            player.hurt(level.damageSources().outOfBorder(), this.getExposureDamage(updatedExposure));
        }

        this.exposureTicks.keySet().retainAll(trackedPlayers);
    }

    private void spawnBoundaryParticles(ServerLevel level) {
        if (this.initialArea == null) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            if (!this.initialArea.contains(player.getX(), player.getZ())) {
                continue;
            }

            this.spawnParticlesForSide(level, player, this.currentMinX, true);
            this.spawnParticlesForSide(level, player, this.currentMaxX, true);
            this.spawnParticlesForSide(level, player, this.currentMinZ, false);
            this.spawnParticlesForSide(level, player, this.currentMaxZ, false);
        }
    }

    private void spawnParticlesForSide(ServerLevel level, ServerPlayer player, double sideCoordinate, boolean verticalSide) {
        double playerPrimaryCoordinate = verticalSide ? player.getX() : player.getZ();
        if (Math.abs(playerPrimaryCoordinate - sideCoordinate) > PARTICLE_VIEW_DISTANCE) {
            return;
        }

        double segmentCenter = verticalSide ? player.getZ() : player.getX();
        double segmentMin = Math.max(
                verticalSide ? this.currentMinZ : this.currentMinX,
                segmentCenter - PARTICLE_SEGMENT_HALF_LENGTH);
        double segmentMax = Math.min(
                verticalSide ? this.currentMaxZ : this.currentMaxX,
                segmentCenter + PARTICLE_SEGMENT_HALF_LENGTH);
        if (segmentMin > segmentMax) {
            return;
        }

        for (double segment = segmentMin; segment <= segmentMax; segment += PARTICLE_STEP) {
            for (double heightOffset : PARTICLE_HEIGHT_OFFSETS) {
                double particleX = verticalSide ? sideCoordinate : segment;
                double particleY = player.getY() + heightOffset;
                double particleZ = verticalSide ? segment : sideCoordinate;
                level.sendParticles(
                        player,
                        ModParticleTypes.NEVOA_BORDER.get(),
                        true,
                        particleX,
                        particleY,
                        particleZ,
                        1,
                        0.18D,
                        0.12D,
                        0.18D,
                        0.02D);
            }
        }
    }

    private boolean isInsideSafeZone(double x, double z) {
        return x >= this.currentMinX
                && x <= this.currentMaxX
                && z >= this.currentMinZ
                && z <= this.currentMaxZ;
    }

    private float getExposureDamage(int exposureTicksOutside) {
        if (exposureTicksOutside >= 160) {
            return 3.0F;
        }

        if (exposureTicksOutside >= 80) {
            return 2.0F;
        }

        return 1.0F;
    }

    private void recalculateCurrentBounds() {
        if (this.totalClosingTicks <= 0) {
            this.currentMinX = this.targetMinX;
            this.currentMaxX = this.targetMaxX;
            this.currentMinZ = this.targetMinZ;
            this.currentMaxZ = this.targetMaxZ;
            return;
        }

        double progress = Mth.clamp((double)this.closingTicksElapsed / (double)this.totalClosingTicks, 0.0D, 1.0D);
        this.currentMinX = Mth.lerp(progress, this.initialMinX, this.targetMinX);
        this.currentMaxX = Mth.lerp(progress, this.initialMaxX, this.targetMaxX);
        this.currentMinZ = Mth.lerp(progress, this.initialMinZ, this.targetMinZ);
        this.currentMaxZ = Mth.lerp(progress, this.initialMaxZ, this.targetMaxZ);
    }

    private int computeTotalClosingTicks() {
        double maxSideDistance = Math.max(
                Math.max(Math.abs(this.initialMinX - this.targetMinX), Math.abs(this.initialMaxX - this.targetMaxX)),
                Math.max(Math.abs(this.initialMinZ - this.targetMinZ), Math.abs(this.initialMaxZ - this.targetMaxZ)));
        return Math.max(1, Mth.ceil(maxSideDistance * CLOSING_TICKS_PER_BLOCK));
    }

    private CustomBossEvent createBossBar(MinecraftServer server) {
        CustomBossEvents bossEvents = server.getCustomBossEvents();
        CustomBossEvent existingBossBar = bossEvents.get(BOSSBAR_ID);
        if (existingBossBar != null) {
            existingBossBar.removeAllPlayers();
            bossEvents.remove(existingBossBar);
        }

        CustomBossEvent nevoaBossBar = bossEvents.create(
                BOSSBAR_ID,
                Component.translatable("event.oxyarena.nevoa.bossbar"));
        nevoaBossBar.setColor(BossEvent.BossBarColor.RED);
        nevoaBossBar.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
        nevoaBossBar.setVisible(true);
        server.getPlayerList().getPlayers().forEach(nevoaBossBar::addPlayer);
        return nevoaBossBar;
    }

    private void clearBossBar(MinecraftServer server) {
        CustomBossEvents bossEvents = server.getCustomBossEvents();
        CustomBossEvent nevoaBossBar = this.bossBar != null ? this.bossBar : bossEvents.get(BOSSBAR_ID);
        if (nevoaBossBar != null) {
            nevoaBossBar.removeAllPlayers();
            bossEvents.remove(nevoaBossBar);
        }

        this.bossBar = null;
    }

    private void updateBossBar(MinecraftServer server) {
        if (this.bossBar == null) {
            return;
        }

        switch (this.phase) {
            case CLOSING -> {
                this.bossBar.setName(Component.translatable("event.oxyarena.nevoa.bossbar"));
                this.bossBar.setMax(Math.max(1, this.totalClosingTicks));
                this.bossBar.setValue(Math.max(0, this.timeRemainingTicks));
            }
            case FINAL_STAND -> {
                this.bossBar.setName(Component.translatable("event.oxyarena.nevoa.bossbar_final"));
                this.bossBar.setMax(FINAL_STAND_DURATION_TICKS);
                this.bossBar.setValue(Math.max(0, this.timeRemainingTicks));
            }
            case IDLE -> {
                return;
            }
        }

        this.bossBar.setVisible(true);
        server.getPlayerList().getPlayers().forEach(this.bossBar::addPlayer);
    }

    private void playStartSound(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.MASTER, 1.0F, 0.5F);
        }
    }

    private String formatTicks(int ticks) {
        int totalSeconds = Math.max(0, ticks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return "%d:%02d".formatted(minutes, seconds);
    }

    private enum Phase {
        IDLE,
        CLOSING,
        FINAL_STAND
    }
}
