package com.example.oxyarena.serverevent;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.example.oxyarena.OXYArena;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class PlayerHuntServerEvent implements OxyServerEvent {
    private static final int EVENT_DURATION_TICKS = 20 * 60 * 5;
    private static final int POSITION_BROADCAST_INTERVAL_TICKS = 20 * 60;
    private static final int GLOW_REFRESH_DURATION_TICKS = 40;
    private static final int EFFECT_REFRESH_DURATION_TICKS = 600;
    private static final ResourceLocation BOSSBAR_ID = ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "caca");
    private static final String TARGET_GLOW_TAG = OXYArena.MODID + ".caca_target_glow";
    private static final String SURVIVOR_TAG = OXYArena.MODID + ".vencedor_caca";

    @Nullable
    private CustomBossEvent bossBar;
    private boolean active;
    private int timeRemainingTicks;
    @Nullable
    private UUID targetUuid;
    private String targetName = "";

    @Override
    public String getId() {
        return "caca";
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("event.oxyarena.caca");
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

        List<ServerPlayer> eligiblePlayers = this.getEligiblePlayers(server);
        if (eligiblePlayers.size() < 2) {
            return false;
        }

        ServerPlayer chosenTarget = eligiblePlayers.get(server.overworld().random.nextInt(eligiblePlayers.size()));
        this.active = true;
        this.timeRemainingTicks = EVENT_DURATION_TICKS;
        this.targetUuid = chosenTarget.getUUID();
        this.targetName = chosenTarget.getGameProfile().getName();
        this.bossBar = this.createBossBar(server);
        this.updateBossBar(server);
        this.refreshTargetGlow(server);

        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.caca.started", this.targetName)
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                false);
        return true;
    }

    @Override
    public void stop(MinecraftServer server, ServerEventStopReason reason) {
        if (!this.active && reason != ServerEventStopReason.SERVER_SHUTDOWN) {
            return;
        }

        this.clearTargetGlow(server);
        this.active = false;
        this.timeRemainingTicks = 0;
        this.clearBossBar(server);
        this.targetUuid = null;
        this.targetName = "";
    }

    @Override
    public void tick(MinecraftServer server) {
        if (!this.active) {
            return;
        }

        this.timeRemainingTicks--;
        if (this.timeRemainingTicks <= 0) {
            this.handleTargetSurvival(server);
            return;
        }

        if (server.getTickCount() % 20 == 0) {
            this.updateBossBar(server);
            this.refreshTargetGlow(server);
        }

        if (this.timeRemainingTicks > 0 && this.timeRemainingTicks % POSITION_BROADCAST_INTERVAL_TICKS == 0) {
            this.broadcastTargetPosition(server);
        }
    }

    @Override
    public void onLivingDeath(MinecraftServer server, LivingDeathEvent event) {
        if (!this.active || !(event.getEntity() instanceof ServerPlayer target) || this.targetUuid == null) {
            return;
        }

        if (!this.targetUuid.equals(target.getUUID())) {
            return;
        }

        ServerPlayer killer = this.getKiller(event);
        if (killer != null && !killer.getUUID().equals(this.targetUuid)) {
            this.handleHunterVictory(server, killer);
            return;
        }

        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.caca.continues", this.targetName)
                        .withStyle(ChatFormatting.YELLOW),
                false);
    }

    @Override
    public void onPlayerLoggedIn(MinecraftServer server, ServerPlayer player) {
        if (this.bossBar != null) {
            this.bossBar.addPlayer(player);
        }

        if (this.targetUuid != null && this.targetUuid.equals(player.getUUID())) {
            this.refreshTargetGlow(server);
        }
    }

    @Override
    public void onPlayerChangedDimension(MinecraftServer server, ServerPlayer player) {
        if (this.bossBar != null) {
            this.bossBar.addPlayer(player);
        }

        if (this.targetUuid != null && this.targetUuid.equals(player.getUUID())) {
            this.refreshTargetGlow(server);
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
        this.clearBossBar(server);
        this.targetUuid = null;
        this.targetName = "";
    }

    public static void tickPersistentPlayerEffects(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refreshPersistentPlayerState(server, player);
        }
    }

    public static void refreshPersistentPlayerState(MinecraftServer server, ServerPlayer player) {
        if (player.getTags().contains(SURVIVOR_TAG)) {
            refreshSurvivorBuffs(player);
        }

        if (player.getTags().contains(TARGET_GLOW_TAG) && !isActiveTarget(server, player.getUUID())) {
            clearTargetGlow(player);
        }
    }

    private static boolean isActiveTarget(MinecraftServer server, UUID playerUuid) {
        OxyServerEvent activeEvent = OxyServerEventManager.get(server).getActiveEvent();
        return activeEvent instanceof PlayerHuntServerEvent playerHuntEvent
                && playerHuntEvent.isActive()
                && playerUuid.equals(playerHuntEvent.targetUuid);
    }

    private static void refreshSurvivorBuffs(ServerPlayer player) {
        MobEffectInstance speedEffect = player.getEffect(MobEffects.MOVEMENT_SPEED);
        if (speedEffect == null || speedEffect.getDuration() <= 20) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, EFFECT_REFRESH_DURATION_TICKS, 0, false, false, false));
        }

        MobEffectInstance jumpEffect = player.getEffect(MobEffects.JUMP);
        if (jumpEffect == null || jumpEffect.getDuration() <= 20) {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, EFFECT_REFRESH_DURATION_TICKS, 0, false, false, false));
        }
    }

    private static void clearTargetGlow(ServerPlayer player) {
        player.removeEffect(MobEffects.GLOWING);
        player.removeTag(TARGET_GLOW_TAG);
    }

    private List<ServerPlayer> getEligiblePlayers(MinecraftServer server) {
        ServerEventArea eventArea = this.getEventArea(server);
        return server.getPlayerList().getPlayers().stream()
                .filter(player -> !player.isCreative())
                .filter(player -> !player.isSpectator())
                .filter(player -> player.serverLevel().dimension() == Level.OVERWORLD)
                .filter(player -> eventArea.contains(player.getX(), player.getZ()))
                .toList();
    }

    private void refreshTargetGlow(MinecraftServer server) {
        ServerPlayer target = this.getTargetPlayer(server);
        if (target == null) {
            return;
        }

        target.addTag(TARGET_GLOW_TAG);
        if (!target.isAlive()) {
            return;
        }

        MobEffectInstance glowingEffect = target.getEffect(MobEffects.GLOWING);
        if (glowingEffect == null || glowingEffect.getDuration() <= 20) {
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_REFRESH_DURATION_TICKS, 0, false, false, false));
        }
    }

    private void clearTargetGlow(MinecraftServer server) {
        ServerPlayer target = this.getTargetPlayer(server);
        if (target != null) {
            clearTargetGlow(target);
        }
    }

    private void handleHunterVictory(MinecraftServer server, ServerPlayer killer) {
        String huntedPlayerName = this.targetName;
        String killerName = killer.getGameProfile().getName();
        this.stop(server, ServerEventStopReason.COMPLETED);
        this.giveHunterRewards(killer);
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.caca.hunter_win", killerName, huntedPlayerName)
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                false);
    }

    private void handleTargetSurvival(MinecraftServer server) {
        String huntedPlayerName = this.targetName;
        ServerPlayer target = this.getTargetPlayer(server);
        this.stop(server, ServerEventStopReason.COMPLETED);

        if (target == null) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("event.oxyarena.caca.target_offline", huntedPlayerName)
                            .withStyle(ChatFormatting.RED),
                    false);
            return;
        }

        this.giveSurvivorRewards(target);
        target.serverLevel().playSound(
                null,
                target.getX(),
                target.getY(),
                target.getZ(),
                SoundEvents.WITHER_SPAWN,
                SoundSource.AMBIENT,
                1.0F,
                1.0F);
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.caca.survivor_win", huntedPlayerName)
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                false);
        target.sendSystemMessage(Component.translatable("event.oxyarena.caca.survivor_reward")
                .withStyle(ChatFormatting.GOLD));
    }

    @Nullable
    private ServerPlayer getTargetPlayer(MinecraftServer server) {
        return this.targetUuid != null ? server.getPlayerList().getPlayer(this.targetUuid) : null;
    }

    @Nullable
    private ServerPlayer getKiller(LivingDeathEvent event) {
        Entity attacker = event.getSource().getEntity();
        return attacker instanceof ServerPlayer player ? player : null;
    }

    private ServerEventArea getEventArea(MinecraftServer server) {
        return ServerEventAreas.getArea(server, ServerEventGroup.MAP_ROTATION);
    }

    private void broadcastTargetPosition(MinecraftServer server) {
        ServerPlayer target = this.getTargetPlayer(server);
        if (target == null) {
            return;
        }

        server.getPlayerList().broadcastSystemMessage(
                Component.translatable(
                        "event.oxyarena.caca.position",
                        this.targetName,
                        (int)Math.floor(target.getX()),
                        (int)Math.floor(target.getZ()))
                        .withStyle(ChatFormatting.RED),
                false);
    }

    private void giveHunterRewards(ServerPlayer killer) {
        this.giveOrDrop(killer, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.STRONG_REGENERATION));
        this.giveOrDrop(killer, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.STRENGTH));
        this.giveOrDrop(killer, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.SWIFTNESS));
    }

    private void giveSurvivorRewards(ServerPlayer target) {
        this.giveOrDrop(target, new ItemStack(Items.NETHERITE_INGOT, 2));
        target.addTag(SURVIVOR_TAG);
        refreshSurvivorBuffs(target);
    }

    private void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack.copy())) {
            player.drop(stack.copy(), false);
        }
    }

    private CustomBossEvent createBossBar(MinecraftServer server) {
        CustomBossEvents bossEvents = server.getCustomBossEvents();
        CustomBossEvent existingBossBar = bossEvents.get(BOSSBAR_ID);
        if (existingBossBar != null) {
            existingBossBar.removeAllPlayers();
            bossEvents.remove(existingBossBar);
        }

        CustomBossEvent playerHuntBossBar = bossEvents.create(
                BOSSBAR_ID,
                Component.translatable("event.oxyarena.caca.bossbar", this.targetName));
        playerHuntBossBar.setColor(BossEvent.BossBarColor.RED);
        playerHuntBossBar.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
        playerHuntBossBar.setMax(EVENT_DURATION_TICKS);
        playerHuntBossBar.setValue(EVENT_DURATION_TICKS);
        playerHuntBossBar.setVisible(true);
        server.getPlayerList().getPlayers().forEach(playerHuntBossBar::addPlayer);
        return playerHuntBossBar;
    }

    private void clearBossBar(MinecraftServer server) {
        CustomBossEvents bossEvents = server.getCustomBossEvents();
        CustomBossEvent playerHuntBossBar = this.bossBar != null ? this.bossBar : bossEvents.get(BOSSBAR_ID);
        if (playerHuntBossBar != null) {
            playerHuntBossBar.removeAllPlayers();
            bossEvents.remove(playerHuntBossBar);
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
}
