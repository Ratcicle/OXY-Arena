package com.example.oxyarena.command;

import java.util.stream.Collectors;

import com.example.oxyarena.serverevent.OxyServerEvent;
import com.example.oxyarena.serverevent.OxyServerEventManager;
import com.example.oxyarena.serverevent.OxyServerEventRegistry;
import com.example.oxyarena.serverevent.ServerEventStopReason;
import com.mojang.brigadier.CommandDispatcher;
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
                .then(Commands.literal("stop").executes(context -> stopActiveEvent(context.getSource())));

        for (String eventId : OxyServerEventRegistry.getRegisteredEventIds()) {
            eventRoot.then(Commands.literal(eventId)
                    .then(Commands.literal("start").executes(context -> startEvent(context.getSource(), eventId)))
                    .then(Commands.literal("stop").executes(context -> stopSpecificEvent(context.getSource(), eventId))));
        }

        dispatcher.register(eventRoot);
    }

    private static int listEvents(CommandSourceStack source) {
        String registeredEvents = OxyServerEventRegistry.getRegisteredEventIds().stream()
                .collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.translatable("commands.oxyarena.event.list", registeredEvents), false);
        return 1;
    }

    private static int showStatus(CommandSourceStack source) {
        OxyServerEventManager eventManager = OxyServerEventManager.get(source.getServer());
        OxyServerEvent activeEvent = eventManager.getActiveEvent();
        if (activeEvent == null) {
            source.sendSuccess(() -> Component.translatable("commands.oxyarena.event.status.inactive"), false);
            return 0;
        }

        String remainingTime = formatTicks(activeEvent.getTimeRemainingTicks());
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.oxyarena.event.status.active",
                        activeEvent.getDisplayName(),
                        remainingTime),
                false);
        return 1;
    }

    private static int startEvent(CommandSourceStack source, String eventId) throws CommandSyntaxException {
        OxyServerEventManager eventManager = OxyServerEventManager.get(source.getServer());
        if (!eventManager.startEvent(eventId)) {
            OxyServerEvent activeEvent = eventManager.getActiveEvent();
            if (activeEvent != null) {
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

        OxyServerEvent activeEvent = eventManager.getActiveEvent();
        source.sendSuccess(
                () -> Component.translatable("commands.oxyarena.event.start.success", activeEvent.getDisplayName()),
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
        source.sendSuccess(
                () -> Component.translatable("commands.oxyarena.event.stop.success", activeEvent.getDisplayName()),
                true);
        return 1;
    }

    private static int stopSpecificEvent(CommandSourceStack source, String eventId) {
        OxyServerEventManager eventManager = OxyServerEventManager.get(source.getServer());
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
        source.sendSuccess(
                () -> Component.translatable("commands.oxyarena.event.stop.success", activeEvent.getDisplayName()),
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
