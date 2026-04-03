package com.example.oxyarena.client.renderer.entity;

import org.joml.Matrix4f;

import com.example.oxyarena.entity.projectile.GrapplingHook;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GrapplingHookRenderer extends EntityRenderer<GrapplingHook> {
    private static final int ROPE_STEPS = 24;
    private static final float ROPE_HALF_WIDTH = 0.025F / 2.0F;

    private final ItemRenderer itemRenderer;

    public GrapplingHookRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(
            GrapplingHook entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        this.renderRope(entity, partialTick, poseStack, buffer);

        float hookYRot = entity.isAnchored()
                ? entity.getAnchorYRot()
                : Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        float hookXRot = entity.isAnchored()
                ? entity.getAnchorXRot()
                : Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(hookYRot - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(hookXRot - 90.0F));
        poseStack.scale(0.95F, 0.95F, 0.95F);
        this.itemRenderer.renderStatic(
                entity.getItem(),
                ItemDisplayContext.FIXED,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                entity.getId());
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(GrapplingHook entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    private void renderRope(
            GrapplingHook entity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer) {
        Entity owner = entity.getOwner();
        if (owner == null) {
            return;
        }

        double hookX = Mth.lerp((double)partialTick, entity.xo, entity.getX());
        double hookY = Mth.lerp((double)partialTick, entity.yo, entity.getY());
        double hookZ = Mth.lerp((double)partialTick, entity.zo, entity.getZ());
        Vec3 ownerHoldPosition = owner.getRopeHoldPosition(partialTick);

        float deltaX = (float)(ownerHoldPosition.x - hookX);
        float deltaY = (float)(ownerHoldPosition.y - hookY);
        float deltaZ = (float)(ownerHoldPosition.z - hookZ);

        float horizontalDistanceSqr = deltaX * deltaX + deltaZ * deltaZ;
        float sideX = 0.0F;
        float sideZ = 0.0F;
        if (horizontalDistanceSqr > 1.0E-6F) {
            float inverseHorizontalDistance = Mth.invSqrt(horizontalDistanceSqr) * ROPE_HALF_WIDTH;
            sideX = deltaZ * inverseHorizontalDistance;
            sideZ = deltaX * inverseHorizontalDistance;
        }

        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.leash());
        Matrix4f pose = poseStack.last().pose();

        BlockPos hookLightPos = BlockPos.containing(entity.getEyePosition(partialTick));
        BlockPos ownerLightPos = BlockPos.containing(owner.getEyePosition(partialTick));
        int hookBlockLight = this.getBlockLightLevel(entity, hookLightPos);
        int ownerBlockLight = owner.level().getBrightness(LightLayer.BLOCK, ownerLightPos);
        int hookSkyLight = this.getSkyLightLevel(entity, hookLightPos);
        int ownerSkyLight = owner.level().getBrightness(LightLayer.SKY, ownerLightPos);

        for (int step = 0; step <= ROPE_STEPS; step++) {
            addRopeVertexPair(
                    vertexConsumer,
                    pose,
                    deltaX,
                    deltaY,
                    deltaZ,
                    hookBlockLight,
                    ownerBlockLight,
                    hookSkyLight,
                    ownerSkyLight,
                    0.025F,
                    0.025F,
                    sideX,
                    sideZ,
                    step,
                    false);
        }

        for (int step = ROPE_STEPS; step >= 0; step--) {
            addRopeVertexPair(
                    vertexConsumer,
                    pose,
                    deltaX,
                    deltaY,
                    deltaZ,
                    hookBlockLight,
                    ownerBlockLight,
                    hookSkyLight,
                    ownerSkyLight,
                    0.025F,
                    0.0F,
                    sideX,
                    sideZ,
                    step,
                    true);
        }
    }

    private static void addRopeVertexPair(
            VertexConsumer buffer,
            Matrix4f pose,
            float endX,
            float endY,
            float endZ,
            int startBlockLight,
            int endBlockLight,
            int startSkyLight,
            int endSkyLight,
            float startYOffset,
            float endYOffset,
            float sideX,
            float sideZ,
            int step,
            boolean reverse) {
        float progress = (float)step / (float)ROPE_STEPS;
        int blockLight = (int)Mth.lerp(progress, (float)startBlockLight, (float)endBlockLight);
        int skyLight = (int)Mth.lerp(progress, (float)startSkyLight, (float)endSkyLight);
        int packedLight = LightTexture.pack(blockLight, skyLight);
        float brightness = step % 2 == (reverse ? 1 : 0) ? 0.7F : 1.0F;
        float red = 0.5F * brightness;
        float green = 0.4F * brightness;
        float blue = 0.3F * brightness;
        float x = endX * progress;
        float y = endY * progress;
        float z = endZ * progress;

        buffer.addVertex(pose, x - sideX, y + endYOffset, z + sideZ)
                .setColor(red, green, blue, 1.0F)
                .setLight(packedLight);
        buffer.addVertex(pose, x + sideX, y + startYOffset - endYOffset, z - sideZ)
                .setColor(red, green, blue, 1.0F)
                .setLight(packedLight);
    }
}
