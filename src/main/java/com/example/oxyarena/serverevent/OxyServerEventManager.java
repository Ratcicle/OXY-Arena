package com.example.oxyarena.serverevent;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import net.minecraft.util.RandomSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

public final class OxyServerEventManager {
    private static final Map<MinecraftServer, OxyServerEventManager> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final Map<String, OxyServerEvent> registeredEvents;
    private final ServerEventRouletteController rouletteController;
    @Nullable
    private OxyServerEvent activeEvent;

    private OxyServerEventManager(MinecraftServer server) {
        this.server = server;
        this.registeredEvents = OxyServerEventRegistry.createEventInstances();
        this.rouletteController = new ServerEventRouletteController(server, this);
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

    public Collection<OxyServerEventDefinition> getRegisteredEventDefinitions() {
        return OxyServerEventRegistry.getRegisteredEventDefinitions();
    }

    public ServerEventRouletteController getRouletteController() {
        return this.rouletteController;
    }

    @Nullable
    public OxyServerEvent getActiveEvent() {
        return this.activeEvent;
    }

    @Nullable
    public OxyServerEvent getRegisteredEvent(String eventId) {
        return this.registeredEvents.get(eventId);
    }

    public boolean startEvent(String eventId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        OxyServerEvent event = this.registeredEvents.get(eventId);
        if (event == null) {
            return false;
        }

        if (event.blocksEventQueue() && this.activeEvent != null) {
            return false;
        }

        if (!event.start(this.server)) {
            return false;
        }

        if (event.blocksEventQueue()) {
            this.activeEvent = event;
        }

        this.rouletteController.onEventStarted(event);
        return true;
    }

    public Optional<String> startRandomEvent(ServerEventGroup group)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (this.activeEvent != null) {
            return Optional.empty();
        }

        RandomSource random = this.server.overworld() != null ? this.server.overworld().random : RandomSource.create();
        Set<String> attemptedIds = new HashSet<>();
        while (attemptedIds.size() < OxyServerEventRegistry.getDefinitionsInGroup(group).size()) {
            Optional<OxyServerEventDefinition> definition = OxyServerEventRegistry.pickRandomDefinition(
                    group,
                    random,
                    attemptedIds);
            if (definition.isEmpty()) {
                return Optional.empty();
            }

            attemptedIds.add(definition.get().id());
            if (this.startEvent(definition.get().id())) {
                return Optional.of(definition.get().id());
            }
        }

        return Optional.empty();
    }

    public boolean activateExistingEvent(String eventId) {
        OxyServerEvent event = this.registeredEvents.get(eventId);
        if (event == null || !event.blocksEventQueue() || !event.isActive() || this.activeEvent != null) {
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
        event.stop(this.server, reason);
        if (!event.isActive()) {
            this.activeEvent = null;
            this.rouletteController.onManagedEventEnded(event, reason);
        }
        return true;
    }

    public boolean stopEvent(String eventId, ServerEventStopReason reason) {
        OxyServerEvent event = this.registeredEvents.get(eventId);
        if (event == null) {
            return false;
        }

        if (event.blocksEventQueue()) {
            if (this.activeEvent != event) {
                return false;
            }
        }

        event.stop(this.server, reason);
        if (this.activeEvent == event && !event.isActive()) {
            this.activeEvent = null;
            this.rouletteController.onManagedEventEnded(event, reason);
        }
        return true;
    }

    public void tick() {
        if (this.activeEvent != null) {
            OxyServerEvent event = this.activeEvent;
            event.tick(this.server);
            if (this.activeEvent == event && !event.isActive()) {
                this.activeEvent = null;
                this.rouletteController.onManagedEventEnded(event, ServerEventStopReason.COMPLETED);
            }
        }

        this.rouletteController.tick();
    }

    public void onLivingDeath(LivingDeathEvent event) {
        if (this.activeEvent == null) {
            return;
        }

        OxyServerEvent activeManagedEvent = this.activeEvent;
        activeManagedEvent.onLivingDeath(this.server, event);
        if (this.activeEvent == activeManagedEvent && !activeManagedEvent.isActive()) {
            this.activeEvent = null;
            this.rouletteController.onManagedEventEnded(activeManagedEvent, ServerEventStopReason.COMPLETED);
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

    public void onPlayerLoggedOut(ServerPlayer player) {
        if (this.activeEvent != null) {
            this.activeEvent.onPlayerLoggedOut(this.server, player);
        }
    }

    public void onItemEntityPickup(ItemEntityPickupEvent.Post event) {
        if (this.activeEvent != null) {
            this.activeEvent.onItemEntityPickup(this.server, event);
        }
    }

    public void onItemToss(ItemTossEvent event) {
        if (this.activeEvent != null) {
            this.activeEvent.onItemToss(this.server, event);
        }
    }

    public void onServerStopping() {
        this.rouletteController.stop();
        this.stopActiveEvent(ServerEventStopReason.SERVER_SHUTDOWN);
        this.registeredEvents.values().forEach(event -> event.stop(this.server, ServerEventStopReason.SERVER_SHUTDOWN));
        this.activeEvent = null;
    }
}
