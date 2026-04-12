package com.example.oxyarena.client.renderer.entity;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.entity.projectile.ElementalGauntletProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class ElementalGauntletProjectileRenderer extends EntityRenderer<ElementalGauntletProjectile> {
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[] {
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "textures/entity/projectiles/gauntlet_projectile_0.png"),
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "textures/entity/projectiles/gauntlet_projectile_1.png"),
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "textures/entity/projectiles/gauntlet_projectile_2.png"),
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "textures/entity/projectiles/gauntlet_projectile_3.png") };
    private static final int FRAME_COUNT = 4;
    private static final float FRAME_ASPECT_RATIO = 32.0F / 32.0F;
    private static final float HALF_WIDTH = 0.34F;
    private static final float HALF_HEIGHT = HALF_WIDTH * FRAME_ASPECT_RATIO;
    private static final float FRAME_TIME = 2.0F;

    public ElementalGauntletProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(
            ElementalGauntletProjectile entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
        int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(this.getFacingRotation(entity, partialTick));

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(this.getTextureLocation(entity)));
        Matrix4f pose = poseStack.last().pose();
        int frame = getAnimationFrame(entity, partialTick);
        float minV = frame / (float)FRAME_COUNT;
        float maxV = (frame + 1.0F) / (float)FRAME_COUNT;

        addVertex(consumer, poseStack, pose, -HALF_WIDTH, -HALF_HEIGHT, 0.0F, 0.0F, maxV);
        addVertex(consumer, poseStack, pose, HALF_WIDTH, -HALF_HEIGHT, 0.0F, 1.0F, maxV);
        addVertex(consumer, poseStack, pose, HALF_WIDTH, HALF_HEIGHT, 0.0F, 1.0F, minV);
        addVertex(consumer, poseStack, pose, -HALF_WIDTH, HALF_HEIGHT, 0.0F, 0.0F, minV);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ElementalGauntletProjectile entity) {
        return TEXTURES[Mth.clamp(entity.getVariant(), 0, TEXTURES.length - 1)];
    }

    private static int getAnimationFrame(ElementalGauntletProjectile entity, float partialTick) {
        return Mth.floor((entity.tickCount + partialTick) / FRAME_TIME) % FRAME_COUNT;
    }

    private Quaternionf getFacingRotation(ElementalGauntletProjectile entity, float partialTick) {
        Entity owner = entity.getOwner();
        if (owner != null) {
            Vec3 targetDirection = owner.getEyePosition(partialTick).subtract(entity.getPosition(partialTick));
            if (targetDirection.lengthSqr() > 1.0E-7D) {
                Vector3f localForward = new Vector3f(0.0F, 0.0F, 1.0F);
                Vector3f ownerDirection = new Vector3f(
                        (float)targetDirection.x,
                        (float)targetDirection.y,
                        (float)targetDirection.z).normalize();
                return new Quaternionf().rotationTo(localForward, ownerDirection);
            }
        }

        return this.entityRenderDispatcher.cameraOrientation();
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
