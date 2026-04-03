package com.example.oxyarena.serverevent;

import java.util.Collection;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class OxyServerEventManager {
    private static final Map<MinecraftServer, OxyServerEventManager> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final Map<String, OxyServerEvent> registeredEvents;
    @Nullable
    private OxyServerEvent activeEvent;

    private OxyServerEventManager(MinecraftServer server) {
        this.server = server;
        this.registeredEvents = OxyServerEventRegistry.createEventInstances();
        this.registeredEvents.values().forEach(event -> event.cleanupStaleRuntimeArtifacts(server));
    }

    public static OxyServerEventManager get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, OxyServerEventManager::new);
    }

    public static void remove(MinecraftServer server) {
        INSTANCES.remove(server);
    }

    public Collection<String> getRegisteredEventIds() {
        return this.registeredEvents.keySet();
    }

    @Nullable
    public OxyServerEvent getActiveEvent() {
        return this.activeEvent;
    }

    public boolean startEvent(String eventId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (this.activeEvent != null) {
            return false;
        }

        OxyServerEvent event = this.registeredEvents.get(eventId);
        if (event == null || !event.start(this.server)) {
            return false;
        }

        this.activeEvent = event;
        return true;
    }

    public boolean stopActiveEvent(ServerEventStopReason reason) {
        if (this.activeEvent == null) {
            return false;
        }

        OxyServerEvent event = this.activeEvent;
        this.activeEvent = null;
        event.stop(this.server, reason);
        return true;
    }

    public boolean stopEvent(String eventId, ServerEventStopReason reason) {
        OxyServerEvent event = this.registeredEvents.get(eventId);
        if (event == null || this.activeEvent != event) {
            return false;
        }

        this.activeEvent = null;
        event.stop(this.server, reason);
        return true;
    }

    public void tick() {
        if (this.activeEvent == null) {
            return;
        }

        this.activeEvent.tick(this.server);
        if (!this.activeEvent.isActive()) {
            this.activeEvent = null;
        }
    }

    public void onLivingDeath(LivingDeathEvent event) {
        if (this.activeEvent == null) {
            return;
        }

        this.activeEvent.onLivingDeath(this.server, event);
        if (!this.activeEvent.isActive()) {
            this.activeEvent = null;
        }
    }

    public void onPlayerLoggedIn(ServerPlayer player) {
        if (this.activeEvent != null) {
            this.activeEvent.onPlayerLoggedIn(this.server, player);
        }
    }

    public void onPlayerChangedDimension(ServerPlayer player) {
        if (this.activeEvent != null) {
            this.activeEvent.onPlayerChangedDimension(this.server, player);
        }
    }

    public void onServerStopping() {
        this.stopActiveEvent(ServerEventStopReason.SERVER_SHUTDOWN);
    }
}
