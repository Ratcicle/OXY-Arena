package com.example.oxyarena.serverevent;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public interface OxyServerEvent {
    String getId();

    Component getDisplayName();

    boolean isActive();

    boolean start(MinecraftServer server) throws CommandSyntaxException;

    void stop(MinecraftServer server, ServerEventStopReason reason);

    void tick(MinecraftServer server);

    void onLivingDeath(MinecraftServer server, LivingDeathEvent event);

    default void onPlayerLoggedIn(MinecraftServer server, ServerPlayer player) {
    }

    default void onPlayerChangedDimension(MinecraftServer server, ServerPlayer player) {
    }

    default int getTimeRemainingTicks() {
        return 0;
    }

    default void cleanupStaleRuntimeArtifacts(MinecraftServer server) {
    }
}
