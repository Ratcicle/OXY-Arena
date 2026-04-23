package com.example.oxyarena.serverevent;

import javax.annotation.Nullable;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.block.entity.OxydropCrateBlockEntity;
import com.example.oxyarena.registry.ModBlocks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class SupplyExtractionServerEvent implements OxyServerEvent {
    private static final int EVENT_DURATION_TICKS = 20 * 60 * 4;
    private static final int EXTRACTION_DURATION_TICKS = 20 * 35;
    private static final int OPEN_DURATION_TICKS = 20 * 60;
    private static final int POSITION_BROADCAST_INTERVAL_TICKS = 20 * 45;
    private static final int MAX_GROUND_SCAN_Y = 300;
    private static final double EXTRACTION_RADIUS = 4.0D;
    private static final double EXTRACTION_RADIUS_SQR = EXTRACTION_RADIUS * EXTRACTION_RADIUS;
    private static final ResourceLocation BOSSBAR_ID = ResourceLocation.fromNamespaceAndPath(
            OXYArena.MODID,
            "extracao_suprimentos");

    @Nullable
    private CustomBossEvent bossBar;
    @Nullable
    private BlockPos cratePos;
    private State state = State.FINISHED;
    private boolean active;
    private boolean extractionStarted;
    private int timeRemainingTicks;
    private int extractionProgressTicks;
    private int openTicksRemaining;
    private int nextPositionBroadcastTicks;

    @Override
    public String getId() {
        return "extracao_suprimentos";
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("event.oxyarena.extracao_suprimentos");
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

        BlockPos selectedPos = this.findCratePos(overworld);
        if (selectedPos == null) {
            return false;
        }

        this.cleanupStaleRuntimeArtifacts(server);
        overworld.setBlockAndUpdate(selectedPos, ModBlocks.OXYDROP_CRATE.get().defaultBlockState());

        BlockEntity blockEntity = overworld.getBlockEntity(selectedPos);
        if (blockEntity instanceof Container container) {
            container.clearContent();
            blockEntity.setChanged();
        }
        if (blockEntity instanceof OxydropCrateBlockEntity crateBlockEntity) {
            crateBlockEntity.setSupplyExtractionMarker(true);
        }

        this.active = true;
        this.extractionStarted = false;
        this.state = State.LOCKED;
        this.cratePos = selectedPos;
        this.timeRemainingTicks = EVENT_DURATION_TICKS;
        this.extractionProgressTicks = 0;
        this.openTicksRemaining = OPEN_DURATION_TICKS;
        this.nextPositionBroadcastTicks = POSITION_BROADCAST_INTERVAL_TICKS;
        this.bossBar = this.createBossBar(server);
        this.updateBossBar(server);
        this.broadcastStart(server, selectedPos);
        return true;
    }

    @Override
    public void stop(MinecraftServer server, ServerEventStopReason reason) {
        if (!this.active && reason != ServerEventStopReason.SERVER_SHUTDOWN) {
            return;
        }

        this.removeCrate(server);
        this.active = false;
        this.extractionStarted = false;
        this.state = State.FINISHED;
        this.cratePos = null;
        this.timeRemainingTicks = 0;
        this.extractionProgressTicks = 0;
        this.openTicksRemaining = 0;
        this.nextPositionBroadcastTicks = 0;
        this.clearBossBar(server);
        if (reason != ServerEventStopReason.SERVER_SHUTDOWN) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("event.oxyarena.extracao_suprimentos.finished")
                            .withStyle(ChatFormatting.RED),
                    false);
        }
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

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null || this.cratePos == null || !this.isCrateStillPresent(overworld)) {
            this.stop(server, ServerEventStopReason.COMPLETED);
            return;
        }

        if (this.state == State.OPENED) {
            this.tickOpened(server);
        } else if (this.extractionStarted) {
            this.tickExtraction(server, overworld);
        }

        if (this.state != State.OPENED && --this.nextPositionBroadcastTicks <= 0) {
            this.broadcastPosition(server);
            this.nextPositionBroadcastTicks = POSITION_BROADCAST_INTERVAL_TICKS;
        }

        if (server.getTickCount() % 10 == 0) {
            this.updateBossBar(server);
        }
    }

    @Override
    public void onLivingDeath(MinecraftServer server, LivingDeathEvent event) {
    }

    @Override
    public void onPlayerLoggedIn(MinecraftServer server, ServerPlayer player) {
        if (this.bossBar != null) {
            this.bossBar.addPlayer(player);
        }
    }

    @Override
    public void onPlayerChangedDimension(MinecraftServer server, ServerPlayer player) {
        if (this.bossBar != null) {
            this.bossBar.addPlayer(player);
        }
    }

    @Override
    public int getTimeRemainingTicks() {
        return this.timeRemainingTicks;
    }

    @Override
    public Component getStatusText() {
        if (!this.active || this.cratePos == null) {
            return null;
        }

        return Component.translatable(
                "event.oxyarena.extracao_suprimentos.status",
                this.state.translationKey(),
                this.extractionProgressPercent(),
                this.cratePos.getX(),
                this.cratePos.getY(),
                this.cratePos.getZ());
    }

    @Override
    public void cleanupStaleRuntimeArtifacts(MinecraftServer server) {
        this.removeCrate(server);
        this.active = false;
        this.extractionStarted = false;
        this.state = State.FINISHED;
        this.cratePos = null;
        this.timeRemainingTicks = 0;
        this.extractionProgressTicks = 0;
        this.openTicksRemaining = 0;
        this.nextPositionBroadcastTicks = 0;
        this.clearBossBar(server);
    }

    public boolean onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!this.active || this.cratePos == null || !event.getPos().equals(this.cratePos)) {
            return false;
        }

        if (this.state == State.OPENED) {
            return false;
        }

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);

        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) {
            return true;
        }

        if (!this.extractionStarted) {
            this.extractionStarted = true;
            this.state = State.EXTRACTING;
            this.broadcastExtractionStarted(player.getServer(), player);
            player.level().playSound(
                    null,
                    this.cratePos,
                    SoundEvents.BARREL_CLOSE,
                    SoundSource.BLOCKS,
                    0.8F,
                    0.75F);
        } else {
            player.displayClientMessage(
                    Component.translatable(
                            "event.oxyarena.extracao_suprimentos.progress",
                            this.extractionProgressPercent()).withStyle(ChatFormatting.YELLOW),
                    true);
        }

        return true;
    }

    public boolean onBlockBreak(BlockEvent.BreakEvent event) {
        if (!this.active || this.cratePos == null || !event.getPos().equals(this.cratePos)) {
            return false;
        }

        event.getPlayer().displayClientMessage(
                Component.translatable("event.oxyarena.extracao_suprimentos.break_blocked")
                        .withStyle(ChatFormatting.YELLOW),
                true);
        event.setCanceled(true);
        return true;
    }

    private void tickOpened(MinecraftServer server) {
        this.openTicksRemaining--;
        if (this.openTicksRemaining <= 0) {
            this.stop(server, ServerEventStopReason.COMPLETED);
        }
    }

    private void tickExtraction(MinecraftServer server, ServerLevel level) {
        int playersInRadius = this.countPlayersInExtractionRadius(level);
        State previousState = this.state;

        if (playersInRadius == 1) {
            this.state = State.EXTRACTING;
            this.extractionProgressTicks++;
            if (level.getGameTime() % 20 == 0) {
                this.spawnExtractionParticles(level);
            }
        } else if (playersInRadius >= 2) {
            this.state = State.CONTESTED;
            if (previousState != State.CONTESTED) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.translatable("event.oxyarena.extracao_suprimentos.contested")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                        false);
                level.playSound(null, this.cratePos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0F, 0.6F);
            }
            if (level.getGameTime() % 20 == 0) {
                this.spawnContestedParticles(level);
            }
        } else {
            this.state = State.LOCKED;
        }

        if (this.extractionProgressTicks >= EXTRACTION_DURATION_TICKS) {
            this.openCrate(server, level);
        }
    }

    private void openCrate(MinecraftServer server, ServerLevel level) {
        if (this.cratePos == null) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(this.cratePos);
        if (blockEntity instanceof Container container) {
            container.clearContent();
            SupplyExtractionLootPool.fillChest(level.random, container);
            blockEntity.setChanged();
        }

        BlockState stateAtCrate = level.getBlockState(this.cratePos);
        if (stateAtCrate.hasProperty(BarrelBlock.OPEN)) {
            level.setBlock(this.cratePos, stateAtCrate.setValue(BarrelBlock.OPEN, Boolean.TRUE), 3);
        }

        this.state = State.OPENED;
        this.openTicksRemaining = OPEN_DURATION_TICKS;
        level.playSound(null, this.cratePos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 1.2F, 0.9F);
        level.playSound(null, this.cratePos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.7F, 1.4F);
        level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                this.cratePos.getX() + 0.5D,
                this.cratePos.getY() + 0.8D,
                this.cratePos.getZ() + 0.5D,
                18,
                0.35D,
                0.25D,
                0.35D,
                0.02D);
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable(
                        "event.oxyarena.extracao_suprimentos.opened",
                        this.cratePos.getX(),
                        this.cratePos.getY(),
                        this.cratePos.getZ()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                false);
        this.updateBossBar(server);
    }

    private int countPlayersInExtractionRadius(ServerLevel level) {
        if (this.cratePos == null) {
            return 0;
        }

        Vec3 center = Vec3.atCenterOf(this.cratePos);
        int count = 0;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }

            if (player.distanceToSqr(center) <= EXTRACTION_RADIUS_SQR) {
                count++;
            }
        }

        return count;
    }

    @Nullable
    private BlockPos findCratePos(ServerLevel level) {
        ServerEventArea eventArea = ServerEventAreas.getArea(level.getServer(), ServerEventGroup.MAP_ROTATION);
        int startY = Math.min(MAX_GROUND_SCAN_Y, level.getMaxBuildHeight() - 1);

        for (int attempt = 0; attempt < 48; attempt++) {
            int x = eventArea.randomX(level.random);
            int z = eventArea.randomZ(level.random);

            for (int y = startY; y > level.getMinBuildHeight(); y--) {
                BlockPos groundPos = new BlockPos(x, y, z);
                BlockState groundState = level.getBlockState(groundPos);
                if (!this.isValidCrateSupport(groundState)) {
                    continue;
                }

                BlockPos crateCandidatePos = groundPos.above();
                if (level.getBlockState(crateCandidatePos).canBeReplaced()
                        && level.getBlockState(crateCandidatePos.above()).canBeReplaced()) {
                    return crateCandidatePos;
                }

                break;
            }
        }

        return null;
    }

    private boolean isValidCrateSupport(BlockState blockState) {
        if (blockState.isAir() || blockState.is(BlockTags.LEAVES) || blockState.is(BlockTags.LOGS)) {
            return false;
        }

        FluidState fluidState = blockState.getFluidState();
        return fluidState.isEmpty();
    }

    private boolean isCrateStillPresent(ServerLevel level) {
        return this.cratePos != null && level.getBlockState(this.cratePos).is(ModBlocks.OXYDROP_CRATE.get());
    }

    private void removeCrate(MinecraftServer server) {
        if (this.cratePos == null) {
            return;
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null || !this.isCrateStillPresent(overworld)) {
            return;
        }

        BlockEntity blockEntity = overworld.getBlockEntity(this.cratePos);
        if (blockEntity instanceof Container container) {
            container.clearContent();
            blockEntity.setChanged();
        }

        overworld.removeBlock(this.cratePos, false);
    }

    private void broadcastStart(MinecraftServer server, BlockPos pos) {
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable(
                        "event.oxyarena.extracao_suprimentos.started",
                        pos.getX(),
                        pos.getY(),
                        pos.getZ()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                false);
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.extracao_suprimentos.hint")
                        .withStyle(ChatFormatting.YELLOW),
                false);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.MASTER, 0.45F, 1.0F);
        }
    }

    private void broadcastPosition(MinecraftServer server) {
        if (this.cratePos == null) {
            return;
        }

        server.getPlayerList().broadcastSystemMessage(
                Component.translatable(
                        "event.oxyarena.extracao_suprimentos.position",
                        this.cratePos.getX(),
                        this.cratePos.getY(),
                        this.cratePos.getZ()).withStyle(ChatFormatting.YELLOW),
                false);
    }

    private void broadcastExtractionStarted(MinecraftServer server, ServerPlayer player) {
        if (this.cratePos == null) {
            return;
        }

        server.getPlayerList().broadcastSystemMessage(
                Component.translatable(
                        "event.oxyarena.extracao_suprimentos.extracting_started",
                        player.getDisplayName(),
                        this.cratePos.getX(),
                        this.cratePos.getY(),
                        this.cratePos.getZ()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                false);
    }

    private void spawnExtractionParticles(ServerLevel level) {
        if (this.cratePos == null) {
            return;
        }

        level.sendParticles(
                ParticleTypes.ENCHANT,
                this.cratePos.getX() + 0.5D,
                this.cratePos.getY() + 0.75D,
                this.cratePos.getZ() + 0.5D,
                8,
                0.25D,
                0.2D,
                0.25D,
                0.02D);
    }

    private void spawnContestedParticles(ServerLevel level) {
        if (this.cratePos == null) {
            return;
        }

        level.sendParticles(
                ParticleTypes.ANGRY_VILLAGER,
                this.cratePos.getX() + 0.5D,
                this.cratePos.getY() + 1.0D,
                this.cratePos.getZ() + 0.5D,
                3,
                0.25D,
                0.1D,
                0.25D,
                0.0D);
    }

    private CustomBossEvent createBossBar(MinecraftServer server) {
        CustomBossEvents bossEvents = server.getCustomBossEvents();
        CustomBossEvent existingBossBar = bossEvents.get(BOSSBAR_ID);
        if (existingBossBar != null) {
            existingBossBar.removeAllPlayers();
            bossEvents.remove(existingBossBar);
        }

        CustomBossEvent supplyBossBar = bossEvents.create(
                BOSSBAR_ID,
                Component.translatable("event.oxyarena.extracao_suprimentos.bossbar.locked"));
        supplyBossBar.setColor(BossEvent.BossBarColor.YELLOW);
        supplyBossBar.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
        supplyBossBar.setMax(EXTRACTION_DURATION_TICKS);
        supplyBossBar.setValue(0);
        supplyBossBar.setVisible(true);
        server.getPlayerList().getPlayers().forEach(supplyBossBar::addPlayer);
        return supplyBossBar;
    }

    private void clearBossBar(MinecraftServer server) {
        CustomBossEvents bossEvents = server.getCustomBossEvents();
        CustomBossEvent supplyBossBar = this.bossBar != null ? this.bossBar : bossEvents.get(BOSSBAR_ID);
        if (supplyBossBar != null) {
            supplyBossBar.removeAllPlayers();
            bossEvents.remove(supplyBossBar);
        }

        this.bossBar = null;
    }

    private void updateBossBar(MinecraftServer server) {
        if (this.bossBar == null) {
            return;
        }

        switch (this.state) {
            case LOCKED -> {
                this.bossBar.setName(Component.translatable("event.oxyarena.extracao_suprimentos.bossbar.locked"));
                this.bossBar.setMax(EXTRACTION_DURATION_TICKS);
                this.bossBar.setValue(this.extractionProgressTicks);
            }
            case EXTRACTING -> {
                this.bossBar.setName(Component.translatable("event.oxyarena.extracao_suprimentos.bossbar.extracting"));
                this.bossBar.setMax(EXTRACTION_DURATION_TICKS);
                this.bossBar.setValue(this.extractionProgressTicks);
            }
            case CONTESTED -> {
                this.bossBar.setName(Component.translatable("event.oxyarena.extracao_suprimentos.bossbar.contested"));
                this.bossBar.setMax(EXTRACTION_DURATION_TICKS);
                this.bossBar.setValue(this.extractionProgressTicks);
            }
            case OPENED -> {
                this.bossBar.setName(Component.translatable("event.oxyarena.extracao_suprimentos.bossbar.opened"));
                this.bossBar.setMax(OPEN_DURATION_TICKS);
                this.bossBar.setValue(this.openTicksRemaining);
            }
            case FINISHED -> {
                this.bossBar.setMax(EVENT_DURATION_TICKS);
                this.bossBar.setValue(0);
            }
        }

        this.bossBar.setVisible(true);
        server.getPlayerList().getPlayers().forEach(this.bossBar::addPlayer);
    }

    private int extractionProgressPercent() {
        return Math.min(100, this.extractionProgressTicks * 100 / EXTRACTION_DURATION_TICKS);
    }

    private enum State {
        LOCKED("event.oxyarena.extracao_suprimentos.state.locked"),
        EXTRACTING("event.oxyarena.extracao_suprimentos.state.extracting"),
        CONTESTED("event.oxyarena.extracao_suprimentos.state.contested"),
        OPENED("event.oxyarena.extracao_suprimentos.state.opened"),
        FINISHED("event.oxyarena.extracao_suprimentos.state.finished");

        private final String translationKey;

        State(String translationKey) {
            this.translationKey = translationKey;
        }

        private Component translationKey() {
            return Component.translatable(this.translationKey);
        }
    }
}
