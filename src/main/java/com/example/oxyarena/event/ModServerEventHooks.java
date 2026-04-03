package com.example.oxyarena.event;

import com.example.oxyarena.serverevent.OxyServerEventManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class ModServerEventHooks {
    private ModServerEventHooks() {
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        OxyServerEventManager.get(event.getServer()).tick();
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof Level level) || level.isClientSide()) {
            return;
        }

        OxyServerEventManager.get(level.getServer()).onLivingDeath(event);
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            OxyServerEventManager.get(player.getServer()).onPlayerLoggedIn(player);
        }
    }

    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            OxyServerEventManager.get(player.getServer()).onPlayerChangedDimension(player);
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        OxyServerEventManager.get(event.getServer()).onServerStopping();
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        OxyServerEventManager.remove(event.getServer());
    }
}
