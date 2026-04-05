package com.example.oxyarena.command;

import java.util.stream.Collectors;

import com.example.oxyarena.serverevent.InundacaoServerEvent;
import com.example.oxyarena.serverevent.OxyServerEvent;
import com.example.oxyarena.serverevent.OxyServerEventManager;
import com.example.oxyarena.serverevent.OxyServerEventRegistry;
import com.example.oxyarena.serverevent.MinibossServerEvent;
import com.example.oxyarena.serverevent.ServerEventArea;
import com.example.oxyarena.serverevent.ServerEventAreas;
import com.example.oxyarena.serverevent.ServerEventGroup;
import com.example.oxyarena.serverevent.ServerEventStopReason;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class EventCommands {
    private static final int EVENT_PERMISSION_LEVEL = 2;

    private EventCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> eventRoot = Commands.literal("evento")
                .requires(source -> source.hasPermission(EVENT_PERMISSION_LEVEL))
                .then(Commands.literal("list").executes(context -> listEvents(context.getSource())))
                .then(Commands.literal("status").executes(context -> showStatus(context.getSource())))
                .then(buildAreaCommand())
                .then(Commands.literal("stop").executes(context -> stopActiveEvent(context.getSource())));

        for (String eventId : OxyServerEventRegistry.getRegisteredEventIds()) {
            LiteralArgumentBuilder<CommandSourceStack> eventCommand = Commands.literal(eventId)
                    .then(Commands.literal("start").executes(context -> startEvent(context.getSource(), eventId)))
                    .then(Commands.literal("stop").executes(context -> stopSpecificEvent(context.getSource(), eventId)));

            if ("inundacao".equals(eventId)) {
                eventCommand = eventCommand
                        .then(Commands.literal("limpar_tudo").executes(context -> startInundacaoEmergencyDrain(context.getSource())))
                        .then(Commands.literal("restaurar_rios").executes(context -> startInundacaoRiverRestore(context.getSource())));
            }

            eventRoot.then(eventCommand);
        }

        dispatcher.register(eventRoot);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAreaCommand() {
        return Commands.literal("area")
                .executes(context -> showArea(context.getSource()))
                .then(Commands.literal("show").executes(context -> showArea(context.getSource())))
                .then(Commands.literal("reset").executes(context -> resetArea(context.getSource())))
                .then(Commands.literal("set")
                        .then(Commands.argument("minX", IntegerArgumentType.integer())
                                .then(Commands.argument("maxX", IntegerArgumentType.integer())
                                        .then(Commands.argument("minZ", IntegerArgumentType.integer())
                                                .then(Commands.argument("maxZ", IntegerArgumentType.integer())
                                                        .executes(context -> setArea(
                                                                context.getSource(),
                                                                IntegerArgumentType.getInteger(context, "minX"),
                                                                IntegerArgumentType.getInteger(context, "maxX"),
                                                                IntegerArgumentType.getInteger(context, "minZ"),
                                                                IntegerArgumentType.getInteger(context, "maxZ"))))))));
    }

    private static int listEvents(CommandSourceStack source) {
        String registeredEvents = OxyServerEventRegistry.getRegisteredEventIds().stream()
                .collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.translatable("commands.oxyarena.event.list", registeredEvents), false);
        return 1;
    }

    private static int showArea(CommandSourceStack source) {
        ServerEventArea area = ServerEventAreas.getArea(source.getServer(), ServerEventGroup.MAP_ROTATION);
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.oxyarena.event.area.current",
                        area.minX(),
                        area.maxX(),
                        area.minZ(),
                        area.maxZ()),
                false);
        return 1;
    }

    private static int setArea(CommandSourceStack source, int minX, int maxX, int minZ, int maxZ) {
        ServerEventArea area = new ServerEventArea(minX, maxX, minZ, maxZ);
        ServerEventAreas.setArea(source.getServer(), ServerEventGroup.MAP_ROTATION, area);
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.oxyarena.event.area.set.success",
                        area.minX(),
                        area.maxX(),
                        area.minZ(),
                        area.maxZ()),
                true);
        return 1;
    }

    private static int resetArea(CommandSourceStack source) {
        ServerEventAreas.resetArea(source.getServer(), ServerEventGroup.MAP_ROTATION);
        ServerEventArea area = ServerEventAreas.getArea(source.getServer(), ServerEventGroup.MAP_ROTATION);
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.oxyarena.event.area.reset.success",
                        area.minX(),
                        area.maxX(),
                        area.minZ(),
                        area.maxZ()),
                true);
        return 1;
    }

    private static int showStatus(CommandSourceStack source) {
        OxyServerEventManager eventManager = OxyServerEventManager.get(source.getServer());
        OxyServerEvent activeEvent = eventManager.getActiveEvent();
        if (activeEvent == null) {
            source.sendSuccess(() -> Component.translatable("commands.oxyarena.event.status.inactive"), false);
            return 0;
        }

        Component statusText = activeEvent.getStatusText();
        if (statusText != null) {
            source.sendSuccess(
                    () -> Component.translatable(
                            "commands.oxyarena.event.status.active_detail",
                            activeEvent.getDisplayName(),
                            statusText),
                    false);
            return 1;
        }

        source.sendSuccess(
                () -> Component.translatable(
                        "commands.oxyarena.event.status.active",
                        activeEvent.getDisplayName(),
                        formatTicks(activeEvent.getTimeRemainingTicks())),
                false);
        return 1;
    }

    private static int startEvent(CommandSourceStack source, String eventId) throws CommandSyntaxException {
        OxyServerEventManager eventManager = OxyServerEventManager.get(source.getServer());
        OxyServerEvent requestedEvent = eventManager.getRegisteredEvent(eventId);
        if (!eventManager.startEvent(eventId)) {
            OxyServerEvent activeEvent = eventManager.getActiveEvent();
            if (requestedEvent != null && requestedEvent.blocksEventQueue() && activeEvent != null) {
                source.sendFailure(Component.translatable(
                        "commands.oxyarena.event.start.already_active",
                        activeEvent.getDisplayName()));
            } else {
                source.sendFailure(Component.translatable(
                        "commands.oxyarena.event.start.failed",
                        Component.translatable("event.oxyarena." + eventId)));
            }
            return 0;
        }

        source.sendSuccess(
                () -> Component.translatable(
                        "commands.oxyarena.event.start.success",
                        requestedEvent != null
                                ? requestedEvent.getDisplayName()
                                : Component.translatable("event.oxyarena." + eventId)),
                true);
        return 1;
    }

    private static int stopActiveEvent(CommandSourceStack source) {
        OxyServerEventManager eventManager = OxyServerEventManager.get(source.getServer());
        OxyServerEvent activeEvent = eventManager.getActiveEvent();
        if (activeEvent == null) {
            source.sendFailure(Component.translatable("commands.oxyarena.event.stop.none_active"));
            return 0;
        }

        eventManager.stopActiveEvent(ServerEventStopReason.MANUAL);
        OxyServerEvent currentActiveEvent = eventManager.getActiveEvent();
        if (currentActiveEvent == activeEvent && currentActiveEvent.getStatusText() != null) {
            source.sendSuccess(
                    () -> Component.translatable(
                            "commands.oxyarena.event.stop.transition",
                            currentActiveEvent.getDisplayName(),
                            currentActiveEvent.getStatusText()),
                    true);
            return 1;
        }

        source.sendSuccess(
                () -> Component.translatable("commands.oxyarena.event.stop.success", activeEvent.getDisplayName()),
                true);
        return 1;
    }

    private static int stopSpecificEvent(CommandSourceStack source, String eventId) {
        OxyServerEventManager eventManager = OxyServerEventManager.get(source.getServer());
        OxyServerEvent requestedEvent = eventManager.getRegisteredEvent(eventId);
        if (requestedEvent != null && !requestedEvent.blocksEventQueue()) {
            if ("miniboss".equals(eventId)) {
                int removedCount = MinibossServerEvent.stopAllBobs(source.getServer(), ServerEventStopReason.MANUAL);
                if (removedCount <= 0) {
                    source.sendFailure(Component.translatable("commands.oxyarena.event.stop.miniboss.none_active"));
                    return 0;
                }

                source.sendSuccess(
                        () -> Component.translatable("commands.oxyarena.event.stop.miniboss.success", removedCount),
                        true);
                return 1;
            }

            eventManager.stopEvent(eventId, ServerEventStopReason.MANUAL);
            source.sendSuccess(
                    () -> Component.translatable("commands.oxyarena.event.stop.success", requestedEvent.getDisplayName()),
                    true);
            return 1;
        }

        OxyServerEvent activeEvent = eventManager.getActiveEvent();
        if (activeEvent == null) {
            source.sendFailure(Component.translatable("commands.oxyarena.event.stop.none_active"));
            return 0;
        }

        if (!eventId.equals(activeEvent.getId())) {
            source.sendFailure(Component.translatable(
                    "commands.oxyarena.event.stop.different_active",
                    activeEvent.getDisplayName()));
            return 0;
        }

        eventManager.stopEvent(eventId, ServerEventStopReason.MANUAL);
        OxyServerEvent currentActiveEvent = eventManager.getActiveEvent();
        if (currentActiveEvent == activeEvent && currentActiveEvent.getStatusText() != null) {
            source.sendSuccess(
                    () -> Component.translatable(
                            "commands.oxyarena.event.stop.transition",
                            currentActiveEvent.getDisplayName(),
                            currentActiveEvent.getStatusText()),
                    true);
            return 1;
        }

        source.sendSuccess(
                () -> Component.translatable("commands.oxyarena.event.stop.success", activeEvent.getDisplayName()),
                true);
        return 1;
    }

    private static int startInundacaoEmergencyDrain(CommandSourceStack source) {
        OxyServerEventManager eventManager = OxyServerEventManager.get(source.getServer());
        OxyServerEvent activeEvent = eventManager.getActiveEvent();
        if (activeEvent != null && !"inundacao".equals(activeEvent.getId())) {
            source.sendFailure(Component.translatable(
                    "commands.oxyarena.event.stop.different_active",
                    activeEvent.getDisplayName()));
            return 0;
        }

        OxyServerEvent registeredEvent = eventManager.getRegisteredEvent("inundacao");
        if (!(registeredEvent instanceof InundacaoServerEvent inundacaoEvent)
                || !inundacaoEvent.startEmergencyDrain(source.getServer())) {
            source.sendFailure(Component.translatable("commands.oxyarena.event.inundacao.emergency.failed"));
            return 0;
        }

        if (activeEvent == null && !eventManager.activateExistingEvent("inundacao")) {
            source.sendFailure(Component.translatable("commands.oxyarena.event.inundacao.emergency.failed"));
            return 0;
        }

        source.sendSuccess(
                () -> Component.translatable("commands.oxyarena.event.inundacao.emergency.success"),
                true);
        return 1;
    }

    private static int startInundacaoRiverRestore(CommandSourceStack source) {
        OxyServerEventManager eventManager = OxyServerEventManager.get(source.getServer());
        OxyServerEvent activeEvent = eventManager.getActiveEvent();
        if (activeEvent != null) {
            source.sendFailure(Component.translatable(
                    "commands.oxyarena.event.start.already_active",
                    activeEvent.getDisplayName()));
            return 0;
        }

        OxyServerEvent registeredEvent = eventManager.getRegisteredEvent("inundacao");
        if (!(registeredEvent instanceof InundacaoServerEvent inundacaoEvent)
                || !inundacaoEvent.startRiverRestoration(source.getServer())
                || !eventManager.activateExistingEvent("inundacao")) {
            source.sendFailure(Component.translatable("commands.oxyarena.event.inundacao.restore.failed"));
            return 0;
        }

        source.sendSuccess(
                () -> Component.translatable("commands.oxyarena.event.inundacao.restore.success"),
                true);
        return 1;
    }

    private static String formatTicks(int ticks) {
        int totalSeconds = Math.max(0, ticks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return "%d:%02d".formatted(minutes, seconds);
    }
}
