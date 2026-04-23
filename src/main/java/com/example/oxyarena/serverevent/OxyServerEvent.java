package com.example.oxyarena.serverevent;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

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

    default void onPlayerLoggedOut(MinecraftServer server, ServerPlayer player) {
    }

    default void onItemEntityPickup(MinecraftServer server, ItemEntityPickupEvent.Post event) {
    }

    default void onItemToss(MinecraftServer server, ItemTossEvent event) {
    }

    default int getTimeRemainingTicks() {
        return 0;
    }

    default Component getStatusText() {
        return null;
    }

    default boolean blocksEventQueue() {
        return true;
    }

    default void cleanupStaleRuntimeArtifacts(MinecraftServer server) {
    }
}
