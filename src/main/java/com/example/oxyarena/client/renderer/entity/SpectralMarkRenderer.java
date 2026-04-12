package com.example.oxyarena.client.renderer.entity;

import org.joml.Matrix4f;

import com.example.oxyarena.entity.effect.SpectralMarkEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class SpectralMarkRenderer extends EntityRenderer<SpectralMarkEntity> {
    private static final float MARK_BASE_HALF_WIDTH = 0.03F;
    private static final float MARK_BASE_HALF_HEIGHT = 0.08F;
    private static final float MARK_EXPOSED_LENGTH = 0.05F;
    private static final float MARK_INSET_LENGTH = 0.42F;
    private static final float MARK_ALPHA = 0.86F;

    public SpectralMarkRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(
            SpectralMarkEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, entity.yRotO, entity.getYRot())));
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getLocalRoll()));

        SpectralColor color = SpectralColor.fromMix(entity.getColorMix());
        VertexConsumer consumer = buffer.getBuffer(RenderType.leash());
        Matrix4f pose = poseStack.last().pose();
        int fullBright = LightTexture.pack(15, 15);
        float baseZ = MARK_EXPOSED_LENGTH;
        float tipZ = -MARK_INSET_LENGTH;

        addSpikeFaceStrip(
                consumer,
                pose,
                new Vertex(-MARK_BASE_HALF_WIDTH, MARK_BASE_HALF_HEIGHT, baseZ),
                new Vertex(MARK_BASE_HALF_WIDTH, MARK_BASE_HALF_HEIGHT, baseZ),
                new Vertex(MARK_BASE_HALF_WIDTH, -MARK_BASE_HALF_HEIGHT, baseZ),
                new Vertex(-MARK_BASE_HALF_WIDTH, -MARK_BASE_HALF_HEIGHT, baseZ),
                new Vertex(0.0F, 0.0F, tipZ),
                color.red(),
                color.green(),
                color.blue(),
                fullBright);
        addCrossHighlight(
                consumer,
                pose,
                color.red(),
                color.green(),
                color.blue(),
                fullBright,
                tipZ);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SpectralMarkEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    private static void addSpikeFaceStrip(
            VertexConsumer consumer,
            Matrix4f pose,
            Vertex topLeft,
            Vertex topRight,
            Vertex bottomRight,
            Vertex bottomLeft,
            Vertex tip,
            float red,
            float green,
            float blue,
            int packedLight) {
        addVertex(consumer, pose, tip, red, green, blue, packedLight);
        addVertex(consumer, pose, topLeft, red, green, blue, packedLight);
        addVertex(consumer, pose, tip, red, green, blue, packedLight);
        addVertex(consumer, pose, topRight, red, green, blue, packedLight);
        addVertex(consumer, pose, tip, red, green, blue, packedLight);
        addVertex(consumer, pose, bottomRight, red, green, blue, packedLight);
        addVertex(consumer, pose, tip, red, green, blue, packedLight);
        addVertex(consumer, pose, bottomLeft, red, green, blue, packedLight);
        addVertex(consumer, pose, tip, red, green, blue, packedLight);
        addVertex(consumer, pose, topLeft, red, green, blue, packedLight);
    }

    private static void addCrossHighlight(
            VertexConsumer consumer,
            Matrix4f pose,
            float red,
            float green,
            float blue,
            int packedLight,
            float tipZ) {
        float highlightHalfWidth = MARK_BASE_HALF_WIDTH * 0.46F;
        float highlightHalfHeight = MARK_BASE_HALF_HEIGHT * 0.92F;
        float highlightBaseZ = MARK_EXPOSED_LENGTH * 0.65F;
        float highlightTipZ = tipZ * 0.72F;

        addSpikeFaceStrip(
                consumer,
                pose,
                new Vertex(-highlightHalfWidth, highlightHalfHeight, highlightBaseZ),
                new Vertex(highlightHalfWidth, highlightHalfHeight, highlightBaseZ),
                new Vertex(highlightHalfWidth, -highlightHalfHeight, highlightBaseZ),
                new Vertex(-highlightHalfWidth, -highlightHalfHeight, highlightBaseZ),
                new Vertex(0.0F, 0.0F, highlightTipZ),
                Math.min(1.0F, red + 0.08F),
                Math.min(1.0F, green + 0.05F),
                Math.min(1.0F, blue + 0.05F),
                packedLight);
    }

    private static void addVertex(
            VertexConsumer consumer,
            Matrix4f pose,
            Vertex vertex,
            float red,
            float green,
            float blue,
            int packedLight) {
        consumer.addVertex(pose, vertex.x(), vertex.y(), vertex.z())
                .setColor(red, green, blue, MARK_ALPHA)
                .setLight(packedLight);
    }

    private record SpectralColor(float red, float green, float blue) {
        private static SpectralColor fromMix(float mix) {
            float clampedMix = Mth.clamp(mix, 0.0F, 1.0F);
            return new SpectralColor(
                    Mth.lerp(clampedMix, 0.08F, 0.16F),
                    Mth.lerp(clampedMix, 0.86F, 0.98F),
                    Mth.lerp(clampedMix, 0.76F, 1.0F));
        }
    }

    private record Vertex(float x, float y, float z) {
    }
}
