package com.example.oxyarena.serverevent;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.example.oxyarena.OXYArena;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class InundacaoServerEvent implements OxyServerEvent {
    private static final int EVENT_DURATION_TICKS = 20 * 60 * 5;
    private static final int MAX_FLOOD_Y = 70;
    private static final int RESTORE_START_Y = 30;
    private static final int RESTORE_END_Y = 62;
    private static final int RISING_LAYER_DELAY_TICKS = 20;
    private static final int DRAINING_LAYER_DELAY_TICKS = 10;
    private static final int EMERGENCY_DRAINING_LAYER_DELAY_TICKS = 1;
    private static final int RESTORE_LAYER_DELAY_TICKS = 2;
    private static final int RISING_BATCH_SIZE = 8192;
    private static final int DRAINING_BATCH_SIZE = 8192;
    private static final int RESTORE_BATCH_SIZE = 8192;
    private static final ResourceLocation BOSSBAR_ID = ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "inundacao");

    private enum Phase {
        IDLE,
        RISING,
        DRAINING,
        EMERGENCY_DRAINING,
        RESTORING_RIVERS
    }

    @Nullable
    private CustomBossEvent bossBar;
    private Phase phase = Phase.IDLE;
    private int timeRemainingTicks;
    private int currentY;
    private int layerCursor;
    private int layerDelayTicks;
    private int areaWidth;
    private int areaDepth;
    private int layerCellCount;
    @Nullable
    private ServerEventArea activeArea;
    private boolean rewardsGranted;
    private final Set<UUID> drownedPlayers = new HashSet<>();

    @Override
    public String getId() {
        return "inundacao";
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("event.oxyarena.inundacao");
    }

    @Override
    public boolean isActive() {
        return this.phase != Phase.IDLE;
    }

    @Override
    public boolean start(MinecraftServer server) {
        if (this.isActive()) {
            return false;
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return false;
        }

        this.cleanupStaleRuntimeArtifacts(server);
        ServerEventArea areaSnapshot = ServerEventAreas.getArea(server, ServerEventGroup.MAP_ROTATION);
        if (!this.setActiveArea(areaSnapshot)) {
            return false;
        }

        InundacaoRuntimeSavedData savedData = InundacaoRuntimeSavedData.get(server);
        savedData.beginTracking(areaSnapshot);
        this.forceTrackedChunks(overworld, savedData);

        this.phase = Phase.RISING;
        this.timeRemainingTicks = EVENT_DURATION_TICKS;
        this.currentY = overworld.getMinBuildHeight();
        this.layerCursor = 0;
        this.layerDelayTicks = 0;
        this.rewardsGranted = false;
        this.drownedPlayers.clear();
        this.bossBar = this.createBossBar(server);
        this.updateBossBar(server);

        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.inundacao.started")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                false);
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.inundacao.warning", MAX_FLOOD_Y)
                        .withStyle(ChatFormatting.BLUE),
                false);
        return true;
    }

    public boolean startEmergencyDrain(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return false;
        }

        if (this.phase == Phase.RESTORING_RIVERS) {
            return false;
        }

        if (this.isActive()) {
            this.clearBossBar(server);
            this.phase = Phase.EMERGENCY_DRAINING;
            this.layerCursor = 0;
            this.layerDelayTicks = 0;
            this.timeRemainingTicks = 0;
            this.rewardsGranted = true;
            this.currentY = Math.min(this.getDrainStartY(overworld.getMinBuildHeight(), InundacaoRuntimeSavedData.get(server)), MAX_FLOOD_Y);
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("event.oxyarena.inundacao.emergency_started")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                    false);
            return true;
        }

        InundacaoRuntimeSavedData savedData = InundacaoRuntimeSavedData.get(server);
        ServerEventArea trackedArea = savedData.getTrackedArea();
        if (trackedArea == null || !savedData.hasTrackedWater()) {
            return false;
        }

        if (!this.setActiveArea(trackedArea)) {
            return false;
        }

        this.forceTrackedChunks(overworld, savedData);
        this.phase = Phase.EMERGENCY_DRAINING;
        this.timeRemainingTicks = 0;
        this.currentY = Math.min(this.getDrainStartY(overworld.getMinBuildHeight(), savedData), MAX_FLOOD_Y);
        this.layerCursor = 0;
        this.layerDelayTicks = 0;
        this.rewardsGranted = true;
        this.drownedPlayers.clear();
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.inundacao.emergency_started")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                false);
        return true;
    }

    public boolean startRiverRestoration(MinecraftServer server) {
        if (this.isActive()) {
            return false;
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return false;
        }

        this.cleanupStaleRuntimeArtifacts(server);
        ServerEventArea areaSnapshot = ServerEventAreas.getArea(server, ServerEventGroup.MAP_ROTATION);
        if (!this.setActiveArea(areaSnapshot)) {
            return false;
        }

        InundacaoRuntimeSavedData savedData = InundacaoRuntimeSavedData.get(server);
        savedData.beginTracking(areaSnapshot);
        this.forceTrackedChunks(overworld, savedData);

        this.phase = Phase.RESTORING_RIVERS;
        this.timeRemainingTicks = 0;
        this.currentY = RESTORE_START_Y;
        this.layerCursor = 0;
        this.layerDelayTicks = 0;
        this.rewardsGranted = true;
        this.drownedPlayers.clear();
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.inundacao.restore_started")
                        .withStyle(ChatFormatting.BLUE),
                false);
        return true;
    }

    @Override
    public void stop(MinecraftServer server, ServerEventStopReason reason) {
        if (!this.isActive() && reason != ServerEventStopReason.SERVER_SHUTDOWN) {
            return;
        }

        if (reason == ServerEventStopReason.SERVER_SHUTDOWN) {
            this.clearBossBar(server);
            this.resetRuntimeState();
            return;
        }

        switch (this.phase) {
            case RISING -> this.startNormalDrain(server);
            case RESTORING_RIVERS -> this.finishRiverRestoration(server, false);
            case DRAINING, EMERGENCY_DRAINING, IDLE -> {
            }
        }
    }

    @Override
    public void tick(MinecraftServer server) {
        switch (this.phase) {
            case RISING -> this.tickRising(server);
            case DRAINING -> this.tickDraining(server, false);
            case EMERGENCY_DRAINING -> this.tickDraining(server, true);
            case RESTORING_RIVERS -> this.tickRiverRestoration(server);
            case IDLE -> {
            }
        }
    }

    @Override
    public void onLivingDeath(MinecraftServer server, LivingDeathEvent event) {
        if (this.phase != Phase.RISING || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (event.getSource().is(DamageTypes.DROWN)) {
            this.drownedPlayers.add(player.getUUID());
            player.sendSystemMessage(
                    Component.translatable("event.oxyarena.inundacao.drown_fail")
                            .withStyle(ChatFormatting.RED));
        }
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
        return switch (this.phase) {
            case RISING -> Component.translatable(
                    "event.oxyarena.inundacao.status.rising",
                    this.currentY,
                    this.formatTicks(this.timeRemainingTicks));
            case DRAINING -> Component.translatable("event.oxyarena.inundacao.status.draining", this.currentY);
            case EMERGENCY_DRAINING -> Component.translatable("event.oxyarena.inundacao.status.emergency", this.currentY);
            case RESTORING_RIVERS -> Component.translatable("event.oxyarena.inundacao.status.restoring", this.currentY);
            case IDLE -> null;
        };
    }

    @Override
    public void cleanupStaleRuntimeArtifacts(MinecraftServer server) {
        this.clearBossBar(server);
        this.resetRuntimeState();

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        InundacaoRuntimeSavedData savedData = InundacaoRuntimeSavedData.get(server);
        if (!savedData.hasTrackedState()) {
            return;
        }

        for (Map.Entry<Integer, BitSet> entry : savedData.copyWaterLayers().entrySet()) {
            this.clearLayerImmediately(overworld, savedData, entry.getKey(), entry.getValue());
        }

        this.releaseTrackedChunks(overworld, savedData);
        savedData.clearAll();
        this.resetRuntimeState();
    }

    private void tickRising(MinecraftServer server) {
        this.timeRemainingTicks--;
        if (this.timeRemainingTicks <= 0) {
            this.startNormalDrain(server);
            return;
        }

        if (server.getTickCount() % 20 == 0) {
            this.updateBossBar(server);
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null || this.activeArea == null || this.currentY > MAX_FLOOD_Y) {
            return;
        }

        if (!this.canProcessLayerThisTick()) {
            return;
        }

        InundacaoRuntimeSavedData savedData = InundacaoRuntimeSavedData.get(server);
        BitSet layerMask = savedData.getLayerMask(this.currentY);
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        boolean changed = false;
        int endCursor = Math.min(this.layerCellCount, this.layerCursor + RISING_BATCH_SIZE);
        for (int index = this.layerCursor; index < endCursor; index++) {
            int localX = index / this.areaDepth;
            int localZ = index % this.areaDepth;
            mutablePos.set(this.activeArea.minX() + localX, this.currentY, this.activeArea.minZ() + localZ);
            BlockState state = overworld.getBlockState(mutablePos);
            if (this.canFloodReplace(state)) {
                overworld.setBlockAndUpdate(mutablePos, Blocks.WATER.defaultBlockState());
                layerMask.set(index);
                changed = true;
            }
        }

        this.layerCursor = endCursor;
        if (changed) {
            savedData.setDirty();
        }

        if (this.layerCursor >= this.layerCellCount) {
            if (layerMask.isEmpty()) {
                savedData.removeLayer(this.currentY);
            }

            if (this.currentY > 0 && this.currentY % 10 == 0) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.translatable("event.oxyarena.inundacao.water_level", this.currentY)
                                .withStyle(ChatFormatting.BLUE),
                        false);
            }

            this.currentY++;
            this.layerCursor = 0;
            this.layerDelayTicks = RISING_LAYER_DELAY_TICKS;
        }
    }

    private void tickDraining(MinecraftServer server, boolean emergency) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            this.finishFloodEvent(server, false);
            return;
        }

        if (!this.canProcessLayerThisTick()) {
            return;
        }

        int minBuildHeight = overworld.getMinBuildHeight();
        if (this.currentY < minBuildHeight) {
            this.finishFloodEvent(server, true);
            return;
        }

        InundacaoRuntimeSavedData savedData = InundacaoRuntimeSavedData.get(server);
        BitSet layerMask = savedData.getExistingLayerMask(this.currentY);
        if (layerMask == null || layerMask.isEmpty()) {
            this.finishDrainLayer(emergency, minBuildHeight);
            return;
        }

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        int processed = 0;
        int nextIndex = layerMask.nextSetBit(this.layerCursor);
        while (nextIndex >= 0 && processed < DRAINING_BATCH_SIZE) {
            int localX = nextIndex / this.areaDepth;
            int localZ = nextIndex % this.areaDepth;
            mutablePos.set(this.activeArea.minX() + localX, this.currentY, this.activeArea.minZ() + localZ);
            if (overworld.getBlockState(mutablePos).is(Blocks.WATER)) {
                overworld.setBlockAndUpdate(mutablePos, Blocks.AIR.defaultBlockState());
            }

            layerMask.clear(nextIndex);
            processed++;
            nextIndex = layerMask.nextSetBit(nextIndex + 1);
        }

        if (layerMask.isEmpty()) {
            savedData.removeLayer(this.currentY);
            this.finishDrainLayer(emergency, minBuildHeight);
            return;
        }

        savedData.setDirty();
        this.layerCursor = nextIndex >= 0 ? nextIndex : this.layerCellCount;
    }

    private void tickRiverRestoration(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null || this.activeArea == null) {
            this.finishRiverRestoration(server, false);
            return;
        }

        if (this.currentY > RESTORE_END_Y) {
            this.finishRiverRestoration(server, true);
            return;
        }

        if (!this.canProcessLayerThisTick()) {
            return;
        }

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        int endCursor = Math.min(this.layerCellCount, this.layerCursor + RESTORE_BATCH_SIZE);
        for (int index = this.layerCursor; index < endCursor; index++) {
            int localX = index / this.areaDepth;
            int localZ = index % this.areaDepth;
            mutablePos.set(this.activeArea.minX() + localX, this.currentY, this.activeArea.minZ() + localZ);
            BlockState state = overworld.getBlockState(mutablePos);
            if ((state.isAir() || state.is(Blocks.CAVE_AIR)) && this.isWaterBiome(overworld, mutablePos)) {
                overworld.setBlockAndUpdate(mutablePos, Blocks.WATER.defaultBlockState());
            }
        }

        this.layerCursor = endCursor;
        if (this.layerCursor >= this.layerCellCount) {
            this.currentY++;
            this.layerCursor = 0;
            this.layerDelayTicks = RESTORE_LAYER_DELAY_TICKS;
        }
    }

    private void startNormalDrain(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        this.clearBossBar(server);
        this.phase = Phase.DRAINING;
        this.layerCursor = 0;
        this.layerDelayTicks = 0;
        this.timeRemainingTicks = 0;
        if (overworld != null) {
            this.currentY = Math.min(this.getDrainStartY(overworld.getMinBuildHeight(), InundacaoRuntimeSavedData.get(server)), MAX_FLOOD_Y);
        }

        if (!this.rewardsGranted) {
            this.rewardsGranted = true;
            this.rewardPlayers(server);
        }

        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.inundacao.drain_started")
                        .withStyle(ChatFormatting.AQUA),
                false);
    }

    private void finishDrainLayer(boolean emergency, int minBuildHeight) {
        this.currentY--;
        this.layerCursor = 0;
        this.layerDelayTicks = emergency ? EMERGENCY_DRAINING_LAYER_DELAY_TICKS : DRAINING_LAYER_DELAY_TICKS;
        if (this.currentY < minBuildHeight) {
            this.layerDelayTicks = 0;
        }
    }

    private void finishFloodEvent(MinecraftServer server, boolean announceFinish) {
        InundacaoRuntimeSavedData savedData = InundacaoRuntimeSavedData.get(server);
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null) {
            this.releaseTrackedChunks(overworld, savedData);
        } else {
            savedData.clearAll();
        }

        savedData.clearAll();
        this.clearBossBar(server);
        this.resetRuntimeState();
        if (announceFinish) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("event.oxyarena.inundacao.finished")
                            .withStyle(ChatFormatting.YELLOW),
                    false);
        }
    }

    private void finishRiverRestoration(MinecraftServer server, boolean announceFinish) {
        InundacaoRuntimeSavedData savedData = InundacaoRuntimeSavedData.get(server);
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null) {
            this.releaseTrackedChunks(overworld, savedData);
        } else {
            savedData.clearAll();
        }

        savedData.clearAll();
        this.clearBossBar(server);
        this.resetRuntimeState();
        if (announceFinish) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("event.oxyarena.inundacao.restore_finished")
                            .withStyle(ChatFormatting.GREEN),
                    false);
        }
    }

    private void rewardPlayers(MinecraftServer server) {
        if (this.activeArea == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.serverLevel().dimension() != Level.OVERWORLD || !this.activeArea.contains(player.getX(), player.getZ())) {
                continue;
            }

            if (this.drownedPlayers.contains(player.getUUID())) {
                player.sendSystemMessage(
                        Component.translatable("event.oxyarena.inundacao.reward_fail")
                                .withStyle(ChatFormatting.RED));
                continue;
            }

            ItemStack reward = this.createRewardBow(server.registryAccess());
            if (!player.getInventory().add(reward.copy())) {
                player.drop(reward.copy(), false);
            }

            player.sendSystemMessage(
                    Component.translatable("event.oxyarena.inundacao.reward_success")
                            .withStyle(ChatFormatting.GOLD));
        }
    }

    private ItemStack createRewardBow(net.minecraft.core.RegistryAccess registryAccess) {
        ItemStack bow = new ItemStack(Items.BOW);
        this.enchantItem(registryAccess, bow, Enchantments.INFINITY, 1);
        this.enchantItem(registryAccess, bow, Enchantments.POWER, 3);
        return bow;
    }

    private void enchantItem(
            net.minecraft.core.RegistryAccess registryAccess,
            ItemStack stack,
            ResourceKey<Enchantment> enchantmentKey,
            int level) {
        Holder<Enchantment> enchantment = registryAccess.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantmentKey);
        EnchantmentHelper.updateEnchantments(stack, mutableEnchantments -> mutableEnchantments.set(enchantment, level));
    }

    private boolean canFloodReplace(BlockState state) {
        return state.isAir()
                || state.is(Blocks.CAVE_AIR)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS);
    }

    private boolean isWaterBiome(ServerLevel level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        String biomePath = biome.unwrapKey().map(key -> key.location().getPath()).orElse("");
        return biomePath.contains("river")
                || biomePath.contains("ocean")
                || biomePath.contains("swamp")
                || biomePath.contains("beach");
    }

    private boolean canProcessLayerThisTick() {
        if (this.layerCursor > 0) {
            return true;
        }

        if (this.layerDelayTicks > 0) {
            this.layerDelayTicks--;
            return false;
        }

        return true;
    }

    private int getDrainStartY(int minBuildHeight, InundacaoRuntimeSavedData savedData) {
        int highestTrackedY = savedData.getHighestTrackedWaterY();
        if (highestTrackedY == Integer.MIN_VALUE) {
            return Math.max(this.currentY, minBuildHeight);
        }

        return Math.max(highestTrackedY, minBuildHeight);
    }

    private boolean setActiveArea(ServerEventArea area) {
        int width = area.maxX() - area.minX() + 1;
        int depth = area.maxZ() - area.minZ() + 1;
        long totalCells = (long)width * depth;
        if (totalCells <= 0L || totalCells > Integer.MAX_VALUE) {
            return false;
        }

        this.activeArea = area;
        this.areaWidth = width;
        this.areaDepth = depth;
        this.layerCellCount = (int)totalCells;
        return true;
    }

    private void resetRuntimeState() {
        this.phase = Phase.IDLE;
        this.timeRemainingTicks = 0;
        this.currentY = 0;
        this.layerCursor = 0;
        this.layerDelayTicks = 0;
        this.areaWidth = 0;
        this.areaDepth = 0;
        this.layerCellCount = 0;
        this.activeArea = null;
        this.rewardsGranted = false;
        this.drownedPlayers.clear();
    }

    private void forceTrackedChunks(ServerLevel level, InundacaoRuntimeSavedData savedData) {
        if (this.activeArea == null) {
            return;
        }

        for (int chunkX = this.activeArea.minChunkX(); chunkX <= this.activeArea.maxChunkX(); chunkX++) {
            for (int chunkZ = this.activeArea.minChunkZ(); chunkZ <= this.activeArea.maxChunkZ(); chunkZ++) {
                long chunkLong = ChunkPos.asLong(chunkX, chunkZ);
                if (!level.getForcedChunks().contains(chunkLong)) {
                    level.setChunkForced(chunkX, chunkZ, true);
                    savedData.rememberForcedChunk(chunkLong);
                }
            }
        }
    }

    private void releaseTrackedChunks(ServerLevel level, InundacaoRuntimeSavedData savedData) {
        for (long chunkLong : savedData.copyForcedChunks()) {
            ChunkPos chunkPos = new ChunkPos(chunkLong);
            level.setChunkForced(chunkPos.x, chunkPos.z, false);
            savedData.forgetForcedChunk(chunkLong);
        }
    }

    private void clearLayerImmediately(ServerLevel level, InundacaoRuntimeSavedData savedData, int y, BitSet mask) {
        if (this.activeArea == null) {
            ServerEventArea trackedArea = savedData.getTrackedArea();
            if (trackedArea == null || !this.setActiveArea(trackedArea)) {
                return;
            }
        }

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int index = mask.nextSetBit(0); index >= 0; index = mask.nextSetBit(index + 1)) {
            int localX = index / this.areaDepth;
            int localZ = index % this.areaDepth;
            mutablePos.set(this.activeArea.minX() + localX, y, this.activeArea.minZ() + localZ);
            if (level.getBlockState(mutablePos).is(Blocks.WATER)) {
                level.setBlockAndUpdate(mutablePos, Blocks.AIR.defaultBlockState());
            }
        }

        savedData.removeLayer(y);
    }

    private CustomBossEvent createBossBar(MinecraftServer server) {
        CustomBossEvents bossEvents = server.getCustomBossEvents();
        CustomBossEvent existingBossBar = bossEvents.get(BOSSBAR_ID);
        if (existingBossBar != null) {
            existingBossBar.removeAllPlayers();
            bossEvents.remove(existingBossBar);
        }

        CustomBossEvent floodBossBar = bossEvents.create(
                BOSSBAR_ID,
                Component.translatable("event.oxyarena.inundacao.bossbar"));
        floodBossBar.setColor(BossEvent.BossBarColor.BLUE);
        floodBossBar.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
        floodBossBar.setMax(EVENT_DURATION_TICKS);
        floodBossBar.setValue(EVENT_DURATION_TICKS);
        floodBossBar.setVisible(true);
        server.getPlayerList().getPlayers().forEach(floodBossBar::addPlayer);
        return floodBossBar;
    }

    private void clearBossBar(MinecraftServer server) {
        CustomBossEvents bossEvents = server.getCustomBossEvents();
        CustomBossEvent floodBossBar = this.bossBar != null ? this.bossBar : bossEvents.get(BOSSBAR_ID);
        if (floodBossBar != null) {
            floodBossBar.removeAllPlayers();
            bossEvents.remove(floodBossBar);
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

    private String formatTicks(int ticks) {
        int totalSeconds = Math.max(0, ticks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return "%d:%02d".formatted(minutes, seconds);
    }
}
