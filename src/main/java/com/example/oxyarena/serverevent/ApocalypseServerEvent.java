package com.example.oxyarena.serverevent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.example.oxyarena.OXYArena;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class ApocalypseServerEvent implements OxyServerEvent {
    private static final int MAX_EVENT_ZOMBIES = 1000;
    private static final int ZOMBIES_PER_WAVE = 10;
    private static final int SPAWN_INTERVAL_TICKS = 20;
    private static final int EVENT_DURATION_TICKS = 20 * 60 * 5;
    private static final int GOLDEN_APPLE_REWARD_COUNT = 5;
    private static final ResourceLocation BOSSBAR_ID = ResourceLocation.fromNamespaceAndPath(OXYArena.MODID,
            "apocalipse");
    private static final String EVENT_ZOMBIE_TAG = OXYArena.MODID + ".apocalipse_zombie";

    private final Set<UUID> infectedPlayers = new HashSet<>();
    @Nullable
    private CustomBossEvent bossBar;
    private boolean active;
    private int timeRemainingTicks;
    private int spawnCooldownTicks;

    @Override
    public String getId() {
        return "apocalipse";
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("event.oxyarena.apocalipse");
    }

    @Override
    public boolean isActive() {
        return this.active;
    }

    @Override
    public boolean start(MinecraftServer server) {
        if (this.active) {
            return false;
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return false;
        }

        this.cleanupStaleRuntimeArtifacts(server);
        this.active = true;
        this.timeRemainingTicks = EVENT_DURATION_TICKS;
        this.spawnCooldownTicks = SPAWN_INTERVAL_TICKS;
        this.infectedPlayers.clear();
        this.bossBar = this.createBossBar(server);
        this.updateBossBar(server);
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.apocalipse.started")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                false);
        return true;
    }

    @Override
    public void stop(MinecraftServer server, ServerEventStopReason reason) {
        if (!this.active && reason != ServerEventStopReason.SERVER_SHUTDOWN) {
            return;
        }

        this.active = false;
        this.timeRemainingTicks = 0;
        this.spawnCooldownTicks = 0;
        this.removeEventZombies(server);
        this.clearBossBar(server);

        if (reason == ServerEventStopReason.SERVER_SHUTDOWN) {
            this.infectedPlayers.clear();
            return;
        }

        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.apocalipse.finished")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                false);
        this.rewardPlayers(server);
        this.infectedPlayers.clear();
    }

    @Override
    public void tick(MinecraftServer server) {
        if (!this.active) {
            return;
        }

        this.timeRemainingTicks--;
        if (this.timeRemainingTicks <= 0) {
            this.stop(server, ServerEventStopReason.COMPLETED);
            return;
        }

        if (--this.spawnCooldownTicks <= 0) {
            this.spawnCooldownTicks = SPAWN_INTERVAL_TICKS;
            this.spawnWave(server);
        }

        this.updateBossBar(server);
    }

    @Override
    public void onLivingDeath(MinecraftServer server, LivingDeathEvent event) {
        if (!this.active || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof Zombie zombie && zombie.getTags().contains(EVENT_ZOMBIE_TAG)) {
            this.infectedPlayers.add(player.getUUID());
            player.sendSystemMessage(Component.translatable("event.oxyarena.apocalipse.infected")
                    .withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public void onPlayerLoggedIn(MinecraftServer server, ServerPlayer player) {
        if (this.active && this.bossBar != null) {
            this.bossBar.addPlayer(player);
        }
    }

    @Override
    public void onPlayerChangedDimension(MinecraftServer server, ServerPlayer player) {
        if (this.active && this.bossBar != null) {
            this.bossBar.addPlayer(player);
        }
    }

    @Override
    public int getTimeRemainingTicks() {
        return this.timeRemainingTicks;
    }

    @Override
    public void cleanupStaleRuntimeArtifacts(MinecraftServer server) {
        this.active = false;
        this.timeRemainingTicks = 0;
        this.spawnCooldownTicks = 0;
        this.infectedPlayers.clear();
        this.removeEventZombies(server);
        this.clearBossBar(server);
    }

    private void spawnWave(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        int currentZombies = this.countEventZombiesInArea(overworld);
        if (currentZombies >= MAX_EVENT_ZOMBIES) {
            return;
        }

        int zombiesToSpawn = Math.min(ZOMBIES_PER_WAVE, MAX_EVENT_ZOMBIES - currentZombies);
        ServerEventArea eventArea = this.getEventArea(server);
        for (int zombieIndex = 0; zombieIndex < zombiesToSpawn; zombieIndex++) {
            int x = eventArea.randomX(overworld.random);
            int z = eventArea.randomZ(overworld.random);
            BlockPos spawnPos = new BlockPos(x, overworld.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);

            Zombie zombie = EntityType.ZOMBIE.create(
                    overworld,
                    mob -> {
                        mob.addTag(EVENT_ZOMBIE_TAG);
                        mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                        mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
                    },
                    spawnPos,
                    MobSpawnType.EVENT,
                    false,
                    false);

            if (zombie != null) {
                overworld.addFreshEntity(zombie);
            }
        }
    }

    private int countEventZombiesInArea(ServerLevel level) {
        return level.getEntitiesOfClass(Zombie.class, this.getEventArea(level),
                zombie -> zombie.getTags().contains(EVENT_ZOMBIE_TAG)).size();
    }

    private void removeEventZombies(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        for (Zombie zombie : overworld.getEntitiesOfClass(
                Zombie.class,
                this.getLoadedWorldBounds(overworld),
                entity -> entity.getTags().contains(EVENT_ZOMBIE_TAG))) {
            zombie.discard();
        }
    }

    private void rewardPlayers(MinecraftServer server) {
        ItemStack rewardStack = new ItemStack(Items.GOLDEN_APPLE, GOLDEN_APPLE_REWARD_COUNT);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!this.isPlayerInRewardArea(player)) {
                continue;
            }

            if (this.infectedPlayers.contains(player.getUUID())) {
                player.sendSystemMessage(Component.translatable("event.oxyarena.apocalipse.reward_fail")
                        .withStyle(ChatFormatting.RED));
                continue;
            }

            ItemStack rewardCopy = rewardStack.copy();
            if (!player.getInventory().add(rewardCopy)) {
                player.drop(rewardCopy, false);
            }

            player.sendSystemMessage(Component.translatable(
                    "event.oxyarena.apocalipse.reward_success",
                    GOLDEN_APPLE_REWARD_COUNT).withStyle(ChatFormatting.GOLD));
        }
    }

    private CustomBossEvent createBossBar(MinecraftServer server) {
        CustomBossEvents bossEvents = server.getCustomBossEvents();
        CustomBossEvent existingBossBar = bossEvents.get(BOSSBAR_ID);
        if (existingBossBar != null) {
            existingBossBar.removeAllPlayers();
            bossEvents.remove(existingBossBar);
        }

        CustomBossEvent apocalypseBossBar = bossEvents.create(BOSSBAR_ID, this.getDisplayName());
        apocalypseBossBar.setColor(BossEvent.BossBarColor.RED);
        apocalypseBossBar.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
        apocalypseBossBar.setMax(EVENT_DURATION_TICKS);
        apocalypseBossBar.setValue(EVENT_DURATION_TICKS);
        apocalypseBossBar.setVisible(true);
        server.getPlayerList().getPlayers().forEach(apocalypseBossBar::addPlayer);
        return apocalypseBossBar;
    }

    private void clearBossBar(MinecraftServer server) {
        CustomBossEvents bossEvents = server.getCustomBossEvents();
        CustomBossEvent apocalypseBossBar = this.bossBar != null ? this.bossBar : bossEvents.get(BOSSBAR_ID);
        if (apocalypseBossBar != null) {
            apocalypseBossBar.removeAllPlayers();
            bossEvents.remove(apocalypseBossBar);
        }
        this.bossBar = null;
    }

    private void updateBossBar(MinecraftServer server) {
        if (this.bossBar == null) {
            return;
        }

        this.bossBar.setMax(EVENT_DURATION_TICKS);
        this.bossBar.setValue(this.timeRemainingTicks);
        this.bossBar.setVisible(true);
        server.getPlayerList().getPlayers().forEach(this.bossBar::addPlayer);
    }

    private boolean isPlayerInRewardArea(ServerPlayer player) {
        return player.serverLevel().dimension() == Level.OVERWORLD
                && this.getEventArea(player.getServer()).contains(player.getX(), player.getZ());
    }

    private AABB getEventArea(ServerLevel level) {
        return this.getEventArea(level.getServer()).createAabb(level);
    }

    private ServerEventArea getEventArea(MinecraftServer server) {
        return ServerEventAreas.getArea(server, ServerEventGroup.MAP_ROTATION);
    }

    private AABB getLoadedWorldBounds(ServerLevel level) {
        return new AABB(
                level.getWorldBorder().getMinX(),
                level.getMinBuildHeight(),
                level.getWorldBorder().getMinZ(),
                level.getWorldBorder().getMaxX(),
                level.getMaxBuildHeight(),
                level.getWorldBorder().getMaxZ());
    }
}
