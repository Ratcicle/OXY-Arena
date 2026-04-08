package com.example.oxyarena.event;

import com.example.oxyarena.network.ItemPickupNotificationPayload;
import com.example.oxyarena.serverevent.OxyServerEventManager;
import com.example.oxyarena.serverevent.PlayerHuntServerEvent;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingGetProjectileEvent;
import net.neoforged.neoforge.event.entity.player.ArrowNockEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class ModServerEventHooks {
    private ModServerEventHooks() {
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        OxyServerEventManager.get(event.getServer()).tick();
        PlayerHuntServerEvent.tickPersistentPlayerEffects(event.getServer());
        FallingTreeHelper.onServerTickPost(event);
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof Level level) || level.isClientSide()) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            ModGameEvents.clearMurasamaState(player);
        }

        OxyServerEventManager.get(level.getServer()).onLivingDeath(event);
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            OxyServerEventManager.get(player.getServer()).onPlayerLoggedIn(player);
            PlayerHuntServerEvent.refreshPersistentPlayerState(player.getServer(), player);
        }
    }

    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            ModGameEvents.clearMurasamaState(player);
            OxyServerEventManager.get(player.getServer()).onPlayerChangedDimension(player);
            PlayerHuntServerEvent.refreshPersistentPlayerState(player.getServer(), player);
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModGameEvents.clearMurasamaState(player);
        }
    }

    public static void onItemEntityPickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        ItemStack originalStack = event.getOriginalStack();
        int pickedUpCount = originalStack.getCount() - event.getCurrentStack().getCount();
        if (pickedUpCount <= 0) {
            return;
        }

        ItemStack pickedUpStack = originalStack.copy();
        pickedUpStack.setCount(pickedUpCount);
        PacketDistributor.sendToPlayer(player, new ItemPickupNotificationPayload(pickedUpStack));
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        RightClickHarvestHelper.onRightClickBlock(event);
    }

    public static void onArrowNock(ArrowNockEvent event) {
        if (event.hasAmmo() || !hasInfinityBow(event.getEntity(), event.getBow())) {
            return;
        }

        event.getEntity().startUsingItem(event.getHand());
        event.setAction(InteractionResultHolder.consume(event.getBow()));
    }

    public static void onLivingGetProjectile(LivingGetProjectileEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !event.getProjectileItemStack().isEmpty()
                || !hasInfinityBow(player, event.getProjectileWeaponItemStack())) {
            return;
        }

        event.setProjectileItemStack(Items.ARROW.getDefaultInstance().copy());
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        FallingTreeHelper.onBlockBreak(event);
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        FallingTreeHelper.onServerStopping(event);
        OxyServerEventManager.get(event.getServer()).onServerStopping();
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        FallingTreeHelper.onServerStopped(event);
        OxyServerEventManager.remove(event.getServer());
    }

    private static boolean hasInfinityBow(Player player, ItemStack weaponStack) {
        if (weaponStack.isEmpty() || !(weaponStack.getItem() instanceof BowItem)) {
            return false;
        }

        Holder<Enchantment> infinity = player.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.INFINITY);
        return weaponStack.getEnchantmentLevel(infinity) > 0;
    }
}
