package com.example.oxyarena.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.oxyarena.network.ProtectiveBubbleSyncPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ProtectiveBubbleRenderer {
    private static final int LATITUDE_SEGMENTS = 10;
    private static final int LONGITUDE_SEGMENTS = 10;
    private static final float BASE_RADIUS = 0.86F;
    private static final float HEIGHT_SCALE = 1.16F;
    private static final float CENTER_Y = 0.92F;
    private static final float RED = 0.08F;
    private static final float GREEN = 0.98F;
    private static final float BLUE = 1.0F;
    private static final float INTERIOR_ALPHA = 0.08F;
    private static final float EDGE_ALPHA = 0.38F;
    private static final Map<UUID, ClientBubbleState> ACTIVE_BUBBLES = new HashMap<>();

    private static boolean registered;

    private ProtectiveBubbleRenderer() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        NeoForge.EVENT_BUS.addListener(ProtectiveBubbleRenderer::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(ProtectiveBubbleRenderer::onRenderPlayerPost);
        registered = true;
    }

    public static void handleSync(ProtectiveBubbleSyncPayload payload) {
        if (payload.active()) {
            ACTIVE_BUBBLES.computeIfAbsent(payload.playerId(), ignored -> new ClientBubbleState());
            return;
        }

        ACTIVE_BUBBLES.remove(payload.playerId());
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            ACTIVE_BUBBLES.clear();
            return;
        }

        ACTIVE_BUBBLES.entrySet().removeIf(entry -> {
            ClientBubbleState state = entry.getValue();
            state.ageTicks++;
            return minecraft.level.getPlayerByUUID(entry.getKey()) == null;
        });
    }

    private static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player) || !ACTIVE_BUBBLES.containsKey(player.getUUID())) {
            return;
        }

        ClientBubbleState state = ACTIVE_BUBBLES.get(player.getUUID());
        if (state == null) {
            return;
        }

        renderBubble(player, state, event.getPoseStack(), event.getMultiBufferSource().getBuffer(RenderType.debugQuads()), event.getPartialTick());
    }

    private static void renderBubble(
            AbstractClientPlayer player,
            ClientBubbleState state,
            PoseStack poseStack,
            VertexConsumer consumer,
            float partialTick) {
        Vec3 cameraPosition = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 bubbleCenter = player.getPosition(partialTick).add(0.0D, CENTER_Y, 0.0D);
        Vec3 viewDirection = cameraPosition.subtract(bubbleCenter).normalize();
        if (viewDirection.lengthSqr() <= 1.0E-6D) {
            viewDirection = new Vec3(0.0D, 0.0D, 1.0D);
        }

        float age = state.ageTicks + partialTick;
        float pulse = 1.0F + Mth.sin(age * 0.12F) * 0.025F;
        PoseStack.Pose pose = poseStack.last();

        for (int latitude = 0; latitude < LATITUDE_SEGMENTS; latitude++) {
            float theta0 = (float)Math.PI * latitude / LATITUDE_SEGMENTS;
            float theta1 = (float)Math.PI * (latitude + 1) / LATITUDE_SEGMENTS;
            for (int longitude = 0; longitude < LONGITUDE_SEGMENTS; longitude++) {
                float phi0 = (float)(Math.PI * 2.0D) * longitude / LONGITUDE_SEGMENTS;
                float phi1 = (float)(Math.PI * 2.0D) * (longitude + 1) / LONGITUDE_SEGMENTS;

                BubbleVertex vertex00 = bubbleVertex(theta0, phi0, pulse, viewDirection);
                BubbleVertex vertex10 = bubbleVertex(theta1, phi0, pulse, viewDirection);
                BubbleVertex vertex11 = bubbleVertex(theta1, phi1, pulse, viewDirection);
                BubbleVertex vertex01 = bubbleVertex(theta0, phi1, pulse, viewDirection);

                addVertex(consumer, pose, vertex00);
                addVertex(consumer, pose, vertex10);
                addVertex(consumer, pose, vertex11);
                addVertex(consumer, pose, vertex01);
            }
        }
    }

    private static BubbleVertex bubbleVertex(float theta, float phi, float pulse, Vec3 viewDirection) {
        float sinTheta = Mth.sin(theta);
        float normalX = sinTheta * Mth.cos(phi);
        float normalY = Mth.cos(theta);
        float normalZ = sinTheta * Mth.sin(phi);
        float radius = BASE_RADIUS * pulse;
        float x = normalX * radius;
        float y = CENTER_Y + normalY * radius * HEIGHT_SCALE;
        float z = normalZ * radius;

        float facing = Math.abs((float)(normalX * viewDirection.x + normalY * viewDirection.y + normalZ * viewDirection.z));
        float rim = 1.0F - Mth.clamp(facing, 0.0F, 1.0F);
        float alpha = Mth.lerp(rim * rim, INTERIOR_ALPHA, EDGE_ALPHA);
        return new BubbleVertex(x, y, z, alpha);
    }

    private static void addVertex(VertexConsumer consumer, PoseStack.Pose pose, BubbleVertex vertex) {
        consumer.addVertex(pose, vertex.x(), vertex.y(), vertex.z())
                .setColor(RED, GREEN, BLUE, vertex.alpha());
    }

    private record BubbleVertex(float x, float y, float z, float alpha) {
    }

    private static final class ClientBubbleState {
        private int ageTicks;
    }
}
