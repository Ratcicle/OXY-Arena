package com.example.oxyarena.serverevent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.example.oxyarena.OXYArena;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;

public final class ServerEventRouletteController {
    private static final int COOLDOWN_TICKS = 20 * 60;
    private static final int AIRDROP_RETRY_TICKS = 20;
    private static final int PRIMARY_EVENT_RETRY_TICKS = 20;

    private final MinecraftServer server;
    private final OxyServerEventManager manager;

    private boolean active;
    private Phase phase = Phase.INACTIVE;
    private int cooldownTicksRemaining;
    private int airdropTriggerTick;
    private boolean cooldownAirdropStarted;
    private int airdropRetryTicks;
    private int primaryEventRetryTicks;

    public ServerEventRouletteController(MinecraftServer server, OxyServerEventManager manager) {
        this.server = server;
        this.manager = manager;
    }

    public boolean isActive() {
        return this.active;
    }

    public boolean start() {
        if (this.active || this.manager.getActiveEvent() != null) {
            return false;
        }

        this.active = true;
        this.phase = Phase.WAITING_FOR_PRIMARY_EVENT;
        this.resetCooldownState();
        if (this.tryStartPrimaryEvent()) {
            return true;
        }

        this.stop();
        return false;
    }

    public boolean stop() {
        if (!this.active) {
            return false;
        }

        this.active = false;
        this.phase = Phase.INACTIVE;
        this.resetCooldownState();
        return true;
    }

    public void tick() {
        if (!this.active) {
            return;
        }

        switch (this.phase) {
            case RUNNING_PRIMARY, INACTIVE -> {
            }
            case COOLDOWN -> this.tickCooldown();
            case WAITING_FOR_PRIMARY_EVENT -> this.tickWaitingForPrimaryEvent();
        }
    }

    public void onEventStarted(OxyServerEvent event) {
        if (!this.active) {
            return;
        }

        ServerEventRotationRole rotationRole = this.getRotationRole(event.getId());
        if (rotationRole == ServerEventRotationRole.PRIMARY) {
            if (event.blocksEventQueue()) {
                this.phase = Phase.RUNNING_PRIMARY;
                this.resetCooldownState();
            } else {
                this.enterCooldown();
            }
            return;
        }

        if (rotationRole == ServerEventRotationRole.COOLDOWN_SPECIAL && this.phase == Phase.COOLDOWN) {
            this.cooldownAirdropStarted = true;
            this.airdropRetryTicks = 0;
        }
    }

    public void onManagedEventEnded(OxyServerEvent event, ServerEventStopReason reason) {
        if (!this.active || reason == ServerEventStopReason.SERVER_SHUTDOWN) {
            return;
        }

        if (this.getRotationRole(event.getId()) == ServerEventRotationRole.PRIMARY) {
            this.enterCooldown();
        }
    }

    @Nullable
    public Component getStatusText() {
        if (!this.active) {
            return null;
        }

        return switch (this.phase) {
            case RUNNING_PRIMARY -> this.getRunningPrimaryStatus();
            case COOLDOWN -> Component.translatable(
                    this.cooldownAirdropStarted
                            ? "commands.oxyarena.event.roulette.phase.cooldown.airdrop_done"
                            : "commands.oxyarena.event.roulette.phase.cooldown.pending_airdrop",
                    this.formatTicks(this.cooldownTicksRemaining));
            case WAITING_FOR_PRIMARY_EVENT -> Component.translatable("commands.oxyarena.event.roulette.phase.waiting");
            case INACTIVE -> null;
        };
    }

    private void tickCooldown() {
        if (this.cooldownTicksRemaining > 0) {
            this.cooldownTicksRemaining--;
        }

        int cooldownElapsedTicks = COOLDOWN_TICKS - this.cooldownTicksRemaining;
        if (!this.cooldownAirdropStarted && cooldownElapsedTicks >= this.airdropTriggerTick) {
            if (this.airdropRetryTicks > 0) {
                this.airdropRetryTicks--;
            } else if (this.manager.getActiveEvent() == null) {
                if (this.tryStartEvent("airdrop")) {
                    this.cooldownAirdropStarted = true;
                    this.airdropRetryTicks = 0;
                } else {
                    this.airdropRetryTicks = AIRDROP_RETRY_TICKS;
                }
            }
        }

        if (this.cooldownTicksRemaining <= 0) {
            this.phase = Phase.WAITING_FOR_PRIMARY_EVENT;
            this.primaryEventRetryTicks = 0;
            this.tickWaitingForPrimaryEvent();
        }
    }

    private void tickWaitingForPrimaryEvent() {
        if (this.manager.getActiveEvent() != null) {
            return;
        }

        if (this.primaryEventRetryTicks > 0) {
            this.primaryEventRetryTicks--;
            return;
        }

        if (!this.tryStartPrimaryEvent()) {
            this.primaryEventRetryTicks = PRIMARY_EVENT_RETRY_TICKS;
        }
    }

    private boolean tryStartPrimaryEvent() {
        RandomSource random = this.server.overworld() != null ? this.server.overworld().random : RandomSource.create();
        List<OxyServerEventDefinition> candidates = new ArrayList<>(
                OxyServerEventRegistry.getDefinitionsInGroup(ServerEventGroup.MAP_ROTATION, ServerEventRotationRole.PRIMARY));
        while (!candidates.isEmpty()) {
            OxyServerEventDefinition candidate = candidates.remove(random.nextInt(candidates.size()));
            if (this.tryStartEvent(candidate.id())) {
                return true;
            }
        }

        return false;
    }

    private boolean tryStartEvent(String eventId) {
        try {
            return this.manager.startEvent(eventId);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            OXYArena.LOGGER.warn("Failed to start roulette event {}", eventId, exception);
            return false;
        }
    }

    private void enterCooldown() {
        this.phase = Phase.COOLDOWN;
        this.cooldownTicksRemaining = COOLDOWN_TICKS;
        this.primaryEventRetryTicks = 0;
        this.cooldownAirdropStarted = false;
        this.airdropRetryTicks = 0;

        RandomSource random = this.server.overworld() != null ? this.server.overworld().random : RandomSource.create();
        this.airdropTriggerTick = random.nextIntBetweenInclusive(1, COOLDOWN_TICKS);
        this.server.getPlayerList().broadcastSystemMessage(
                Component.translatable(
                        "event.oxyarena.roulette.cooldown_started",
                        this.formatTicks(COOLDOWN_TICKS)),
                false);
    }

    private void resetCooldownState() {
        this.cooldownTicksRemaining = 0;
        this.airdropTriggerTick = 0;
        this.cooldownAirdropStarted = false;
        this.airdropRetryTicks = 0;
        this.primaryEventRetryTicks = 0;
    }

    private ServerEventRotationRole getRotationRole(String eventId) {
        Optional<OxyServerEventDefinition> definition = OxyServerEventRegistry.getDefinition(eventId);
        return definition.map(OxyServerEventDefinition::rotationRole).orElse(ServerEventRotationRole.MANUAL_ONLY);
    }

    @Nullable
    private Component getRunningPrimaryStatus() {
        OxyServerEvent activeEvent = this.manager.getActiveEvent();
        if (activeEvent == null) {
            return Component.translatable("commands.oxyarena.event.roulette.phase.waiting");
        }

        return Component.translatable(
                "commands.oxyarena.event.roulette.phase.running",
                activeEvent.getDisplayName());
    }

    private String formatTicks(int ticks) {
        int totalSeconds = Math.max(0, ticks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return "%d:%02d".formatted(minutes, seconds);
    }

    private enum Phase {
        INACTIVE,
        WAITING_FOR_PRIMARY_EVENT,
        RUNNING_PRIMARY,
        COOLDOWN
    }
}
