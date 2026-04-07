package com.example.oxyarena.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.example.oxyarena.Config;
import com.example.oxyarena.network.PingLocationRequestPayload;
import com.example.oxyarena.network.PingLocationSyncPayload;
import com.example.oxyarena.registry.ModSoundEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import com.mojang.blaze3d.platform.InputConstants;

public final class PingLocationController {
    private static final boolean DEBUG_FEEDBACK = false;
    private static final KeyMapping PING_KEY = new KeyMapping(
            "key.oxyarena.ping",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            "key.categories.gameplay");
    private static final Map<UUID, ActivePing> ACTIVE_PINGS = new HashMap<>();
    private static int nextSequence = 1;

    private PingLocationController() {
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(PING_KEY);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PingLocationController::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(PingLocationController::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(PingLocationController::onRenderGuiPost);
    }

    public static void handlePing(PingLocationSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!Config.pingEnabled() || minecraft.level == null) {
            return;
        }

        ActivePing existing = ACTIVE_PINGS.get(payload.authorId());
        if (existing != null && payload.sequence() < existing.sequence) {
            return;
        }

        ACTIVE_PINGS.put(
                payload.authorId(),
                new ActivePing(
                        payload.authorId(),
                        payload.sequence(),
                        payload.position(),
                        payload.entityId(),
                        payload.dimensionId(),
                        minecraft.level.getGameTime()));

        if (minecraft.player != null) {
            if (DEBUG_FEEDBACK) {
                if (minecraft.player.getUUID().equals(payload.authorId())) {
                    showDebugMessage("Ping sincronizado com o servidor");
                } else {
                    showDebugMessage("Ping recebido de " + resolveAuthorName(minecraft, payload.authorId()));
                }
            }
            minecraft.player.playNotifySound(
                    ModSoundEvents.PING.get(),
                    SoundSource.PLAYERS,
                    (float)Config.pingVolume(),
                    1.1F);
        }
    }

    private static void trySendPing() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!Config.pingEnabled()
                || minecraft.screen != null
                || minecraft.player == null
                || minecraft.level == null
                || minecraft.gameMode == null
                || minecraft.player.isSpectator()
                || minecraft.player.isCreative()) {
            if (DEBUG_FEEDBACK && minecraft.player != null) {
                showDebugMessage("Ping bloqueado pelo contexto atual");
            }
            return;
        }

        HitResult hitResult = performPingRaycast(minecraft);
        if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
            if (DEBUG_FEEDBACK) {
                showDebugMessage("Ping sem alvo");
            }
            return;
        }

        Vec3 targetPos = hitResult.getLocation();
        Optional<UUID> entityId = Optional.empty();
        if (hitResult instanceof EntityHitResult entityHitResult) {
            Entity entity = entityHitResult.getEntity();
            if (entity != null) {
                entityId = Optional.of(entity.getUUID());
                targetPos = entity.getBoundingBox().getCenter();
            }
        }

        PacketDistributor.sendToServer(new PingLocationRequestPayload(
                targetPos,
                entityId,
                minecraft.level.dimension().location(),
                nextSequence++));
        if (DEBUG_FEEDBACK) {
            showDebugMessage("Ping enviado ao servidor");
        }
    }

    private static HitResult performPingRaycast(Minecraft minecraft) {
        if (minecraft.player == null) {
            return null;
        }

        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(true);
        double range = Config.pingRaycastDistance();
        Entity player = minecraft.player;
        Vec3 eyePosition = player.getEyePosition(partialTick);
        HitResult blockHit = player.pick(range, partialTick, false);
        double maxDistanceSqr = range * range;
        double blockDistanceSqr = maxDistanceSqr;
        double effectiveRange = range;

        if (blockHit.getType() != HitResult.Type.MISS) {
            blockDistanceSqr = blockHit.getLocation().distanceToSqr(eyePosition);
            effectiveRange = Math.sqrt(blockDistanceSqr);
        }

        Vec3 lookVector = player.getViewVector(partialTick);
        Vec3 end = eyePosition.add(lookVector.scale(effectiveRange));
        AABB searchBox = player.getBoundingBox().expandTowards(lookVector.scale(effectiveRange)).inflate(1.0D, 1.0D, 1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player,
                eyePosition,
                end,
                searchBox,
                entity -> !entity.isSpectator() && entity.isPickable(),
                blockDistanceSqr);
        if (entityHit != null && entityHit.getLocation().distanceToSqr(eyePosition) < blockDistanceSqr) {
            return entityHit;
        }

        return blockHit;
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!Config.pingEnabled() || minecraft.level == null) {
            ACTIVE_PINGS.clear();
            return;
        }

        while (PING_KEY.consumeClick()) {
            trySendPing();
        }

        long expireBefore = minecraft.level.getGameTime() - Math.max(1, Config.pingDurationSeconds()) * 20L;
        Iterator<ActivePing> iterator = ACTIVE_PINGS.values().iterator();
        while (iterator.hasNext()) {
            ActivePing ping = iterator.next();
            if (ping.createdGameTime < expireBefore) {
                iterator.remove();
            }
        }
    }

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !Config.pingEnabled()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        VertexConsumer consumer = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        boolean rendered = false;

        for (ActivePing ping : ACTIVE_PINGS.values()) {
            ping.onScreen = false;

            if (!ping.dimensionId.equals(minecraft.level.dimension().location())) {
                continue;
            }

            Vec3 worldPos = ping.resolveWorldPosition(minecraft.level);
            ping.lastWorldPos = worldPos;
            ping.distance = minecraft.player.position().distanceTo(worldPos);
            if (ping.distance > Config.pingMaxDistance()) {
                continue;
            }

            ProjectionResult projection = projectToScreen(worldPos.add(0.0D, 1.1D, 0.0D), event, minecraft);
            ping.onScreen = projection.visible();
            ping.screenX = projection.x();
            ping.screenY = projection.y();

            double size = Mth.clamp(ping.distance * 0.012D, 0.18D, 0.45D);
            AABB markerBounds = new AABB(
                    worldPos.x - size,
                    worldPos.y,
                    worldPos.z - size,
                    worldPos.x + size,
                    worldPos.y + size * 2.7D,
                    worldPos.z + size);
            if (!event.getFrustum().isVisible(markerBounds.inflate(0.5D))) {
                continue;
            }

            poseStack.pushPose();
            Vec3 offset = worldPos.subtract(cameraPos);
            poseStack.translate(offset.x, offset.y, offset.z);
            LevelRenderer.renderLineBox(
                    poseStack,
                    consumer,
                    -size,
                    0.0D,
                    -size,
                    size,
                    size * 1.1D,
                    size,
                    0.25F,
                    0.85F,
                    1.0F,
                    0.95F);
            LevelRenderer.renderLineBox(
                    poseStack,
                    consumer,
                    -size * 0.15D,
                    size * 1.1D,
                    -size * 0.15D,
                    size * 0.15D,
                    size * 2.7D,
                    size * 0.15D,
                    1.0F,
                    1.0F,
                    1.0F,
                    0.9F);
            poseStack.popPose();
            rendered = true;
        }

        if (rendered) {
            minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
        }
    }

    private static void onRenderGuiPost(RenderGuiEvent.Post event) {
        if (!Config.pingEnabled()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int guiWidth = guiGraphics.guiWidth();
        int guiHeight = guiGraphics.guiHeight();

        for (ActivePing ping : ACTIVE_PINGS.values()) {
            if (!ping.onScreen || ping.distance > Config.pingMaxDistance()) {
                continue;
            }

            Component label = buildHudLabel(minecraft, ping);
            if (label == null) {
                continue;
            }

            int textWidth = minecraft.font.width(label);
            int x = Mth.clamp(Mth.floor(ping.screenX - textWidth / 2.0F), 4, guiWidth - textWidth - 4);
            int y = Mth.clamp(Mth.floor(ping.screenY - 18.0F), 4, guiHeight - 12);
            guiGraphics.drawString(minecraft.font, label, x, y, 0xFFFFFF, true);
        }
    }

    private static Component buildHudLabel(Minecraft minecraft, ActivePing ping) {
        StringBuilder label = new StringBuilder();
        if (Config.pingShowAuthorName()) {
            label.append(resolveAuthorName(minecraft, ping.authorId));
        }

        if (Config.pingShowDistance()) {
            if (!label.isEmpty()) {
                label.append(" - ");
            }
            label.append(Math.round(ping.distance)).append("m");
        }

        if (label.isEmpty()) {
            return null;
        }

        return Component.literal(label.toString());
    }

    private static String resolveAuthorName(Minecraft minecraft, UUID authorId) {
        if (minecraft.getConnection() != null) {
            PlayerInfo playerInfo = minecraft.getConnection().getPlayerInfo(authorId);
            if (playerInfo != null) {
                return playerInfo.getProfile().getName();
            }
        }

        if (minecraft.level != null && minecraft.level.getPlayerByUUID(authorId) != null) {
            return minecraft.level.getPlayerByUUID(authorId).getName().getString();
        }

        return "Player";
    }

    private static void showDebugMessage(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(message), true);
        }
    }

    private static ProjectionResult projectToScreen(Vec3 worldPos, RenderLevelStageEvent event, Minecraft minecraft) {
        Vec3 cameraPos = event.getCamera().getPosition();
        Vector4f clip = new Vector4f(
                (float)(worldPos.x - cameraPos.x),
                (float)(worldPos.y - cameraPos.y),
                (float)(worldPos.z - cameraPos.z),
                1.0F);
        clip.mul(event.getModelViewMatrix());
        clip.mul(event.getProjectionMatrix());
        if (clip.w <= 0.0F) {
            return ProjectionResult.hidden();
        }

        float invW = 1.0F / clip.w;
        float ndcX = clip.x * invW;
        float ndcY = clip.y * invW;
        float ndcZ = clip.z * invW;
        if (ndcZ < -1.0F || ndcZ > 1.0F) {
            return ProjectionResult.hidden();
        }

        float screenX = (ndcX * 0.5F + 0.5F) * minecraft.getWindow().getGuiScaledWidth();
        float screenY = (1.0F - (ndcY * 0.5F + 0.5F)) * minecraft.getWindow().getGuiScaledHeight();
        boolean visible = screenX >= 0.0F
                && screenX <= minecraft.getWindow().getGuiScaledWidth()
                && screenY >= 0.0F
                && screenY <= minecraft.getWindow().getGuiScaledHeight();
        return new ProjectionResult(screenX, screenY, visible);
    }

    private record ProjectionResult(float x, float y, boolean visible) {
        private static ProjectionResult hidden() {
            return new ProjectionResult(0.0F, 0.0F, false);
        }
    }

    private static final class ActivePing {
        private final UUID authorId;
        private final int sequence;
        private final Vec3 basePosition;
        private final Optional<UUID> entityId;
        private final ResourceLocation dimensionId;
        private final long createdGameTime;
        private Vec3 lastWorldPos;
        private double distance;
        private float screenX;
        private float screenY;
        private boolean onScreen;

        private ActivePing(
                UUID authorId,
                int sequence,
                Vec3 basePosition,
                Optional<UUID> entityId,
                ResourceLocation dimensionId,
                long createdGameTime) {
            this.authorId = authorId;
            this.sequence = sequence;
            this.basePosition = basePosition;
            this.entityId = entityId;
            this.dimensionId = dimensionId;
            this.createdGameTime = createdGameTime;
            this.lastWorldPos = basePosition;
        }

        private Vec3 resolveWorldPosition(ClientLevel level) {
            if (entityId.isPresent()) {
                for (Entity entity : level.entitiesForRendering()) {
                    if (entity.getUUID().equals(entityId.get())) {
                        return entity.getBoundingBox().getCenter();
                    }
                }
            }

            return basePosition;
        }
    }
}
