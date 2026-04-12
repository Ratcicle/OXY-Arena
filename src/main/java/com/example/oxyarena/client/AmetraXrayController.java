package com.example.oxyarena.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.example.oxyarena.Config;
import com.example.oxyarena.OXYArena;
import com.example.oxyarena.registry.ModBlocks;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class AmetraXrayController {
    private static final KeyMapping XRAY_KEY = new KeyMapping(
            "key.oxyarena.ametra_xray",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            "key.categories.gameplay");
    private static final int HORIZONTAL_SCAN_RADIUS = 48;
    private static final int RESCAN_INTERVAL_TICKS = 20;
    private static final double RESCAN_MOVE_DISTANCE_SQR = 64.0D;
    private static final List<BlockPos> FOUND_ORES = new ArrayList<>();

    private static boolean enabled;
    private static long lastScanGameTime = Long.MIN_VALUE;
    private static Vec3 lastScanCenter = Vec3.ZERO;

    private AmetraXrayController() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(AmetraXrayController::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(AmetraXrayController::onRenderLevelStage);
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(XRAY_KEY);
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            enabled = false;
            FOUND_ORES.clear();
            return;
        }

        while (XRAY_KEY.consumeClick()) {
            enabled = !enabled;
            if (!enabled) {
                FOUND_ORES.clear();
                showStatusMessage("message.oxyarena.ametra_xray.disabled");
                continue;
            }

            refreshNearbyAmetra(minecraft.level, minecraft.player);
        }

        if (!enabled) {
            return;
        }

        long gameTime = minecraft.level.getGameTime();
        if (gameTime - lastScanGameTime < RESCAN_INTERVAL_TICKS
                && minecraft.player.position().distanceToSqr(lastScanCenter) < RESCAN_MOVE_DISTANCE_SQR) {
            return;
        }

        refreshNearbyAmetra(minecraft.level, minecraft.player);
    }

    private static void refreshNearbyAmetra(ClientLevel level, LocalPlayer player) {
        FOUND_ORES.clear();

        int minY = Math.max(level.getMinBuildHeight(), Math.min(Config.ametraOreMinY(), Config.ametraOreMaxY()));
        int maxY = Math.min(level.getMaxBuildHeight() - 1, Math.max(Config.ametraOreMinY(), Config.ametraOreMaxY()));
        if (minY > maxY) {
            lastScanGameTime = level.getGameTime();
            lastScanCenter = player.position();
            showStatusMessage("message.oxyarena.ametra_xray.none");
            return;
        }

        BlockPos playerPos = player.blockPosition();
        int minX = playerPos.getX() - HORIZONTAL_SCAN_RADIUS;
        int maxX = playerPos.getX() + HORIZONTAL_SCAN_RADIUS;
        int minZ = playerPos.getZ() - HORIZONTAL_SCAN_RADIUS;
        int maxZ = playerPos.getZ() + HORIZONTAL_SCAN_RADIUS;
        int minChunkX = SectionCoord.blockToSectionCoord(minX);
        int maxChunkX = SectionCoord.blockToSectionCoord(maxX);
        int minChunkZ = SectionCoord.blockToSectionCoord(minZ);
        int maxChunkZ = SectionCoord.blockToSectionCoord(maxZ);
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null || chunk.isEmpty()) {
                    continue;
                }

                int startX = Math.max(minX, chunk.getPos().getMinBlockX());
                int endX = Math.min(maxX, chunk.getPos().getMaxBlockX());
                int startZ = Math.max(minZ, chunk.getPos().getMinBlockZ());
                int endZ = Math.min(maxZ, chunk.getPos().getMaxBlockZ());

                for (int x = startX; x <= endX; x++) {
                    for (int z = startZ; z <= endZ; z++) {
                        for (int y = minY; y <= maxY; y++) {
                            mutablePos.set(x, y, z);
                            BlockState state = chunk.getBlockState(mutablePos);
                            if (state.is(ModBlocks.AMETRA_ORE.get()) || state.is(ModBlocks.DEEPSLATE_AMETRA_ORE.get())) {
                                FOUND_ORES.add(mutablePos.immutable());
                            }
                        }
                    }
                }
            }
        }

        FOUND_ORES.sort(Comparator.comparingDouble(pos -> pos.distSqr(playerPos)));
        lastScanGameTime = level.getGameTime();
        lastScanCenter = player.position();

        if (FOUND_ORES.isEmpty()) {
            showStatusMessage("message.oxyarena.ametra_xray.none");
            return;
        }

        showStatusMessage("message.oxyarena.ametra_xray.enabled", FOUND_ORES.size(), HORIZONTAL_SCAN_RADIUS);
    }

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!enabled || FOUND_ORES.isEmpty() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        Vec3 cameraPos = event.getCamera().getPosition();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        boolean renderedFill = false;
        boolean renderedLines = false;
        for (BlockPos orePos : FOUND_ORES) {
            AABB worldBox = new AABB(orePos);
            if (!event.getFrustum().isVisible(worldBox.inflate(0.5D))) {
                continue;
            }

            AABB box = worldBox.move(-cameraPos.x, -cameraPos.y, -cameraPos.z).inflate(0.01D);
            DebugRenderer.renderFilledBox(
                    event.getPoseStack(),
                    minecraft.renderBuffers().bufferSource(),
                    box,
                    0.15F,
                    0.95F,
                    0.85F,
                    0.16F);
            LevelRenderer.renderLineBox(
                    event.getPoseStack(),
                    minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines()),
                    box,
                    0.25F,
                    1.0F,
                    0.9F,
                    0.95F);
            renderedFill = true;
            renderedLines = true;
        }

        if (renderedFill) {
            minecraft.renderBuffers().bufferSource().endBatch(RenderType.debugFilledBox());
        }
        if (renderedLines) {
            minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void showStatusMessage(String translationKey, Object... args) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(translationKey, args), true);
        }
    }

    private static final class SectionCoord {
        private SectionCoord() {
        }

        private static int blockToSectionCoord(int blockCoord) {
            return Mth.floorDiv(blockCoord, 16);
        }
    }
}
