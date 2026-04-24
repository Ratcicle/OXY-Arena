package com.example.oxyarena.client.renderer.entity;

import org.joml.Matrix4f;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.entity.effect.DimensionalRiftEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class DimensionalRiftRenderer extends EntityRenderer<DimensionalRiftEntity> {
    private static final ResourceLocation TEXTURE_LOCATION = ResourceLocation.fromNamespaceAndPath(
            OXYArena.MODID,
            "textures/entity/dimensional_rift.png");
    private static final float HALF_WIDTH = 0.75F;
    private static final float HALF_HEIGHT = 1.18F;

    public DimensionalRiftRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(
            DimensionalRiftEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.lerp(partialTick, entity.yRotO, entity.getYRot())));

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(this.getTextureLocation(entity)));
        Matrix4f pose = poseStack.last().pose();
        float alpha = getAlpha(entity, partialTick);
        addVertex(consumer, poseStack, pose, -HALF_WIDTH, -HALF_HEIGHT, 0.0F, 0.0F, 1.0F, alpha);
        addVertex(consumer, poseStack, pose, HALF_WIDTH, -HALF_HEIGHT, 0.0F, 1.0F, 1.0F, alpha);
        addVertex(consumer, poseStack, pose, HALF_WIDTH, HALF_HEIGHT, 0.0F, 1.0F, 0.0F, alpha);
        addVertex(consumer, poseStack, pose, -HALF_WIDTH, HALF_HEIGHT, 0.0F, 0.0F, 0.0F, alpha);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(DimensionalRiftEntity entity) {
        return TEXTURE_LOCATION;
    }

    private static float getAlpha(DimensionalRiftEntity entity, float partialTick) {
        float age = entity.tickCount + partialTick;
        float fadeIn = Mth.clamp(age / 6.0F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp((DimensionalRiftEntity.DURATION_TICKS - age) / 10.0F, 0.0F, 1.0F);
        return Math.min(fadeIn, fadeOut) * 0.88F;
    }

    private static void addVertex(
            VertexConsumer consumer,
            PoseStack poseStack,
            Matrix4f pose,
            float x,
            float y,
            float z,
            float u,
            float v,
            float alpha) {
        consumer.addVertex(pose, x, y, z)
                .setColor(1.0F, 1.0F, 1.0F, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(poseStack.last(), 0.0F, 0.0F, 1.0F);
    }
}
