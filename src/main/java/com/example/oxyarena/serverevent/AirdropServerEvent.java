package com.example.oxyarena.serverevent;

import java.util.UUID;

import javax.annotation.Nullable;

import com.example.oxyarena.entity.event.AirdropCrateEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class AirdropServerEvent implements OxyServerEvent {
    private static final ServerEventArea EVENT_AREA = ServerEventAreas.MAIN_EVENT_AREA;
    private static final int DROP_STEP_INTERVAL_TICKS = 20;
    private static final int MIN_START_HEIGHT = 120;
    private static final int MIN_DROP_HEIGHT_ABOVE_GROUND = 24;
    private static final int MAX_GROUND_SCAN_Y = 300;

    private boolean active;
    private int timeRemainingTicks;
    private int dropStepCooldownTicks;
    @Nullable
    private BlockPos landingPos;
    @Nullable
    private UUID crateUuid;
    @Nullable
    private Integer forcedChunkX;
    @Nullable
    private Integer forcedChunkZ;

    @Override
    public String getId() {
        return "airdrop";
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("event.oxyarena.airdrop");
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

        BlockPos selectedLandingPos = this.findLandingPos(overworld);
        if (selectedLandingPos == null) {
            return false;
        }

        double spawnY = Math.max(MIN_START_HEIGHT, selectedLandingPos.getY() + MIN_DROP_HEIGHT_ABOVE_GROUND);
        AirdropCrateEntity crate = new AirdropCrateEntity(
                overworld,
                selectedLandingPos.getX() + 0.5D,
                spawnY,
                selectedLandingPos.getZ() + 0.5D);
        if (!overworld.addFreshEntity(crate)) {
            return false;
        }

        this.active = true;
        this.landingPos = selectedLandingPos.immutable();
        this.crateUuid = crate.getUUID();
        this.dropStepCooldownTicks = DROP_STEP_INTERVAL_TICKS;
        this.timeRemainingTicks = Math.max(
                1,
                (int)Math.ceil((spawnY - selectedLandingPos.getY()) * DROP_STEP_INTERVAL_TICKS));
        this.setForcedChunk(overworld, selectedLandingPos);
        this.broadcastStart(server, selectedLandingPos);
        return true;
    }

    @Override
    public void stop(MinecraftServer server, ServerEventStopReason reason) {
        if (!this.active && reason != ServerEventStopReason.SERVER_SHUTDOWN) {
            return;
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null) {
            AirdropCrateEntity crate = this.getCrate(overworld);
            if (crate != null) {
                crate.discard();
            }

            this.releaseForcedChunk(overworld);
        }

        this.active = false;
        this.timeRemainingTicks = 0;
        this.dropStepCooldownTicks = 0;
        this.landingPos = null;
        this.crateUuid = null;
    }

    @Override
    public void tick(MinecraftServer server) {
        if (!this.active) {
            return;
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null || this.landingPos == null) {
            this.stop(server, ServerEventStopReason.MANUAL);
            return;
        }

        this.timeRemainingTicks = Math.max(0, this.timeRemainingTicks - 1);
        AirdropCrateEntity crate = this.getCrate(overworld);
        if (crate == null) {
            this.stop(server, ServerEventStopReason.MANUAL);
            return;
        }

        if (--this.dropStepCooldownTicks > 0) {
            return;
        }

        this.dropStepCooldownTicks = DROP_STEP_INTERVAL_TICKS;
        double nextY = Math.max(this.landingPos.getY(), crate.getY() - 1.0D);
        crate.setPos(crate.getX(), nextY, crate.getZ());
        crate.hasImpulse = true;

        if (nextY <= this.landingPos.getY()) {
            this.finishDrop(server, overworld, crate, this.landingPos);
            this.stop(server, ServerEventStopReason.COMPLETED);
        }
    }

    @Override
    public void onLivingDeath(MinecraftServer server, LivingDeathEvent event) {
    }

    @Override
    public int getTimeRemainingTicks() {
        return this.timeRemainingTicks;
    }

    @Override
    public void cleanupStaleRuntimeArtifacts(MinecraftServer server) {
        this.active = false;
        this.timeRemainingTicks = 0;
        this.dropStepCooldownTicks = 0;
        this.landingPos = null;
        this.crateUuid = null;

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            this.forcedChunkX = null;
            this.forcedChunkZ = null;
            return;
        }

        for (AirdropCrateEntity crate : overworld.getEntitiesOfClass(
                AirdropCrateEntity.class,
                this.getLoadedWorldBounds(overworld))) {
            overworld.setChunkForced(crate.chunkPosition().x, crate.chunkPosition().z, false);
            crate.discard();
        }

        this.releaseForcedChunk(overworld);
    }

    private void finishDrop(
            MinecraftServer server,
            ServerLevel level,
            AirdropCrateEntity crate,
            BlockPos chestPos) {
        crate.discard();
        level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());

        BlockEntity blockEntity = level.getBlockEntity(chestPos);
        if (blockEntity instanceof ChestBlockEntity chestBlockEntity) {
            AirdropLootPool.fillChest(level.random, chestBlockEntity);
            chestBlockEntity.setChanged();
        }

        LightningBolt lightningBolt = EntityType.LIGHTNING_BOLT.create(level);
        if (lightningBolt != null) {
            lightningBolt.moveTo(chestPos.getX() + 0.5D, chestPos.getY(), chestPos.getZ() + 0.5D);
            level.addFreshEntity(lightningBolt);
        }

        server.getPlayerList().broadcastSystemMessage(
                Component.translatable(
                        "event.oxyarena.airdrop.landed",
                        chestPos.getX(),
                        chestPos.getY(),
                        chestPos.getZ()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                false);
    }

    @Nullable
    private BlockPos findLandingPos(ServerLevel level) {
        int startY = Math.min(MAX_GROUND_SCAN_Y, level.getMaxBuildHeight() - 1);

        for (int attempt = 0; attempt < 24; attempt++) {
            int x = EVENT_AREA.randomX(level.random);
            int z = EVENT_AREA.randomZ(level.random);

            for (int y = startY; y > level.getMinBuildHeight(); y--) {
                BlockPos groundPos = new BlockPos(x, y, z);
                BlockState groundState = level.getBlockState(groundPos);
                if (!this.isValidLandingSupport(groundState)) {
                    continue;
                }

                BlockPos chestPos = groundPos.above();
                if (level.getBlockState(chestPos).canBeReplaced()) {
                    return chestPos;
                }

                break;
            }
        }

        return null;
    }

    private boolean isValidLandingSupport(BlockState blockState) {
        if (blockState.isAir() || blockState.is(BlockTags.LEAVES) || blockState.is(BlockTags.LOGS)) {
            return false;
        }

        FluidState fluidState = blockState.getFluidState();
        return fluidState.isEmpty();
    }

    private void broadcastStart(MinecraftServer server, BlockPos landingPos) {
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable(
                        "event.oxyarena.airdrop.started",
                        landingPos.getX(),
                        landingPos.getZ()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                false);
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.airdrop.look_up").withStyle(ChatFormatting.YELLOW),
                false);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(SoundEvents.ENDER_DRAGON_GROWL, SoundSource.MASTER, 0.5F, 1.0F);
        }
    }

    @Nullable
    private AirdropCrateEntity getCrate(ServerLevel level) {
        if (this.crateUuid == null) {
            return null;
        }

        Entity entity = level.getEntity(this.crateUuid);
        return entity instanceof AirdropCrateEntity crate ? crate : null;
    }

    private void setForcedChunk(ServerLevel level, BlockPos pos) {
        this.releaseForcedChunk(level);
        this.forcedChunkX = pos.getX() >> 4;
        this.forcedChunkZ = pos.getZ() >> 4;
        level.setChunkForced(this.forcedChunkX, this.forcedChunkZ, true);
    }

    private void releaseForcedChunk(ServerLevel level) {
        if (this.forcedChunkX != null && this.forcedChunkZ != null) {
            level.setChunkForced(this.forcedChunkX, this.forcedChunkZ, false);
        }

        this.forcedChunkX = null;
        this.forcedChunkZ = null;
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
