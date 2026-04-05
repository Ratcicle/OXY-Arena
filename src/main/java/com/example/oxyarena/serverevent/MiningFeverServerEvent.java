package com.example.oxyarena.serverevent;

import javax.annotation.Nullable;

import com.example.oxyarena.OXYArena;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class MiningFeverServerEvent implements OxyServerEvent {
    private static final int EVENT_DURATION_TICKS = 20 * 60 * 5;
    private static final ResourceLocation BOSSBAR_ID = ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "mineracao");

    @Nullable
    private CustomBossEvent bossBar;
    private boolean active;
    private int timeRemainingTicks;

    @Override
    public String getId() {
        return "mineracao";
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("event.oxyarena.mineracao");
    }

    @Override
    public boolean isActive() {
        return this.active;
    }

    @Override
    public boolean start(MinecraftServer server) {
        if (this.active || server.overworld() == null) {
            return false;
        }

        this.cleanupStaleRuntimeArtifacts(server);
        this.active = true;
        this.timeRemainingTicks = EVENT_DURATION_TICKS;
        this.bossBar = this.createBossBar(server);
        this.updateBossBar(server);
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.mineracao.started")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                false);
        return true;
    }

    @Override
    public void stop(MinecraftServer server, ServerEventStopReason reason) {
        if (!this.active && reason != ServerEventStopReason.SERVER_SHUTDOWN) {
            return;
        }

        this.active = false;
        this.timeRemainingTicks = 0;
        this.clearBossBar(server);
        if (reason != ServerEventStopReason.SERVER_SHUTDOWN) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("event.oxyarena.mineracao.finished")
                            .withStyle(ChatFormatting.RED),
                    false);
        }
    }

    @Override
    public void tick(MinecraftServer server) {
        if (!this.active) {
            return;
        }

        this.timeRemainingTicks--;
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
    }

    public static boolean isBonusActiveAt(ServerLevel level, double x, double z) {
        if (level.dimension() != Level.OVERWORLD) {
            return false;
        }

        OxyServerEvent activeEvent = OxyServerEventManager.get(level.getServer()).getActiveEvent();
        return activeEvent instanceof MiningFeverServerEvent miningFeverEvent
                && miningFeverEvent.isActive()
                && ServerEventAreas.getArea(level.getServer(), ServerEventGroup.MAP_ROTATION).contains(x, z);
    }

    private CustomBossEvent createBossBar(MinecraftServer server) {
        CustomBossEvents bossEvents = server.getCustomBossEvents();
        CustomBossEvent existingBossBar = bossEvents.get(BOSSBAR_ID);
        if (existingBossBar != null) {
            existingBossBar.removeAllPlayers();
            bossEvents.remove(existingBossBar);
        }

        CustomBossEvent miningBossBar = bossEvents.create(
                BOSSBAR_ID,
                Component.translatable("event.oxyarena.mineracao.bossbar"));
        miningBossBar.setColor(BossEvent.BossBarColor.YELLOW);
        miningBossBar.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
        miningBossBar.setMax(EVENT_DURATION_TICKS);
        miningBossBar.setValue(EVENT_DURATION_TICKS);
        miningBossBar.setVisible(true);
        server.getPlayerList().getPlayers().forEach(miningBossBar::addPlayer);
        return miningBossBar;
    }

    private void clearBossBar(MinecraftServer server) {
        CustomBossEvents bossEvents = server.getCustomBossEvents();
        CustomBossEvent miningBossBar = this.bossBar != null ? this.bossBar : bossEvents.get(BOSSBAR_ID);
        if (miningBossBar != null) {
            miningBossBar.removeAllPlayers();
            bossEvents.remove(miningBossBar);
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
}
