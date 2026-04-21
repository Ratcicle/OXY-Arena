package com.example.oxyarena.client.renderer.entity;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.entity.projectile.BlackBladeProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class BlackBladeProjectileRenderer extends EntityRenderer<BlackBladeProjectile> {
    private static final ResourceLocation TEXTURE_LOCATION = ResourceLocation.fromNamespaceAndPath(
            OXYArena.MODID,
            "textures/entity/projectiles/black_blade_projectile.png");
    private static final float HALF_SIZE = 0.48F;

    public BlackBladeProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(
            BlackBladeProjectile entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(this.getFacingRotation(entity));

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(this.getTextureLocation(entity)));
        Matrix4f pose = poseStack.last().pose();
        addVertex(consumer, poseStack, pose, -HALF_SIZE, -HALF_SIZE, 0.0F, 0.0F, 1.0F);
        addVertex(consumer, poseStack, pose, HALF_SIZE, -HALF_SIZE, 0.0F, 1.0F, 1.0F);
        addVertex(consumer, poseStack, pose, HALF_SIZE, HALF_SIZE, 0.0F, 1.0F, 0.0F);
        addVertex(consumer, poseStack, pose, -HALF_SIZE, HALF_SIZE, 0.0F, 0.0F, 0.0F);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(BlackBladeProjectile entity) {
        return TEXTURE_LOCATION;
    }

    private Quaternionf getFacingRotation(BlackBladeProjectile entity) {
        Vec3 motion = entity.getDeltaMovement();
        if (motion.lengthSqr() > 1.0E-7D) {
            Vector3f localTextureTop = new Vector3f(0.0F, 1.0F, 0.0F);
            Vector3f flightDirection = new Vector3f(
                    (float)motion.x,
                    (float)motion.y,
                    (float)motion.z).normalize();
            return new Quaternionf().rotationTo(localTextureTop, flightDirection);
        }

        return new Quaternionf();
    }

    private static void addVertex(
            VertexConsumer consumer,
            PoseStack poseStack,
            Matrix4f pose,
            float x,
            float y,
            float z,
            float u,
            float v) {
        consumer.addVertex(pose, x, y, z)
                .setColor(1.0F, 1.0F, 1.0F, 1.0F)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(poseStack.last(), 0.0F, 0.0F, 1.0F);
    }
}
