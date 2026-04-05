package com.example.oxyarena.serverevent;

import java.util.List;

import javax.annotation.Nullable;

import com.example.oxyarena.entity.event.BobBossEntity;
import com.example.oxyarena.registry.ModEntityTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class MinibossServerEvent implements OxyServerEvent {
    private static final int MAX_SPAWN_ATTEMPTS = 50;

    @Override
    public String getId() {
        return "miniboss";
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("event.oxyarena.miniboss");
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public boolean start(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return false;
        }

        BlockPos spawnPos = this.findSpawnPos(overworld);
        if (spawnPos == null) {
            return false;
        }

        BobBossEntity bob = ModEntityTypes.BOB_BOSS.get().create(overworld);
        if (bob == null) {
            return false;
        }

        bob.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, overworld.random.nextFloat() * 360.0F, 0.0F);
        bob.prepareAsBoss();
        if (!overworld.addFreshEntity(bob)) {
            return false;
        }

        this.broadcastStart(server, spawnPos);
        return true;
    }

    @Override
    public void stop(MinecraftServer server, ServerEventStopReason reason) {
        stopAllBobs(server, reason);
    }

    @Override
    public void tick(MinecraftServer server) {
    }

    @Override
    public boolean blocksEventQueue() {
        return false;
    }

    @Override
    public void onLivingDeath(MinecraftServer server, LivingDeathEvent event) {
    }

    @Override
    public void cleanupStaleRuntimeArtifacts(MinecraftServer server) {
        stopAllBobs(server, ServerEventStopReason.SERVER_SHUTDOWN);
    }

    public static int stopAllBobs(MinecraftServer server, ServerEventStopReason reason) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return 0;
        }

        List<BobBossEntity> bobs = getBobs(overworld);
        for (BobBossEntity bob : bobs) {
            bob.discard();
        }

        return bobs.size();
    }

    public static int countActiveBobs(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        return overworld == null ? 0 : getBobs(overworld).size();
    }

    private static List<BobBossEntity> getBobs(ServerLevel level) {
        return level.getEntitiesOfClass(BobBossEntity.class, getLoadedWorldBounds(level));
    }

    @Nullable
    private BlockPos findSpawnPos(ServerLevel level) {
        ServerEventArea eventArea = ServerEventAreas.getArea(level.getServer(), ServerEventGroup.MAP_ROTATION);
        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
            int x = eventArea.randomX(level.random);
            int z = eventArea.randomZ(level.random);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos spawnPos = new BlockPos(x, y, z);
            if (this.isValidSpawnPos(level, spawnPos)) {
                return spawnPos;
            }
        }

        return null;
    }

    private boolean isValidSpawnPos(ServerLevel level, BlockPos spawnPos) {
        if (!level.getBlockState(spawnPos).canBeReplaced() || !level.getBlockState(spawnPos.above()).canBeReplaced()) {
            return false;
        }

        BlockState supportState = level.getBlockState(spawnPos.below());
        FluidState supportFluid = supportState.getFluidState();
        return !supportState.isAir() && supportFluid.isEmpty();
    }

    private void broadcastStart(MinecraftServer server, BlockPos spawnPos) {
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable(
                        "event.oxyarena.miniboss.spawned",
                        spawnPos.getX(),
                        spawnPos.getY(),
                        spawnPos.getZ())
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                false);
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.miniboss.reward_hint")
                        .withStyle(ChatFormatting.GOLD),
                false);
    }

    private static AABB getLoadedWorldBounds(ServerLevel level) {
        return new AABB(
                level.getWorldBorder().getMinX(),
                level.getMinBuildHeight(),
                level.getWorldBorder().getMinZ(),
                level.getWorldBorder().getMaxX(),
                level.getMaxBuildHeight(),
                level.getWorldBorder().getMaxZ());
    }
}
