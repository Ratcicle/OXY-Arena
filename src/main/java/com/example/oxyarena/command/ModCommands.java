package com.example.oxyarena.command;

import java.util.Collection;
import java.util.List;

import com.example.oxyarena.registry.ModItems;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModCommands {
    private static final int DEBUG_PERMISSION_LEVEL = 2;

    private ModCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        EventCommands.register(dispatcher);
        dispatcher.register(
                Commands.literal("oxyarena")
                        .then(Commands.literal("debug")
                                .requires(source -> source.hasPermission(DEBUG_PERMISSION_LEVEL))
                                .then(Commands.literal("reset_abilities")
                                        .executes(context -> resetAbilities(
                                                context.getSource(),
                                                context.getSource().getPlayerOrException()))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(context -> resetAbilities(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "targets")))))));
    }

    private static int resetAbilities(CommandSourceStack source, ServerPlayer player) {
        return resetAbilities(source, List.of(player));
    }

    private static int resetAbilities(CommandSourceStack source, Collection<ServerPlayer> players) {
        int resetCount = 0;

        for (ServerPlayer player : players) {
            clearModItemCooldowns(player);
            resetCount++;
        }

        int finalResetCount = resetCount;
        source.sendSuccess(
                () -> Component.translatable("commands.oxyarena.reset_abilities.success", finalResetCount),
                true);
        return resetCount;
    }

    private static void clearModItemCooldowns(ServerPlayer player) {
        for (DeferredHolder<Item, ? extends Item> item : ModItems.ITEMS.getEntries()) {
            player.getCooldowns().removeCooldown(item.get());
        }
    }
}
