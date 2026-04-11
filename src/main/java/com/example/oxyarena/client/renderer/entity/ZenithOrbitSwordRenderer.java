package com.example.oxyarena.client.renderer.entity;

import com.example.oxyarena.entity.effect.ZenithOrbitSwordEntity;
import com.example.oxyarena.registry.ModItems;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

public final class ZenithOrbitSwordRenderer extends EntityRenderer<ZenithOrbitSwordEntity> {
    private static final float TRAIL_START_WIDTH = 0.18F;
    private static final float TRAIL_END_WIDTH = 0.02F;
    private static final float TRAIL_START_ALPHA = 0.72F;
    private static final float TRAIL_END_ALPHA = 0.0F;
    private final ItemRenderer itemRenderer;

    public ZenithOrbitSwordRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(
            ZenithOrbitSwordEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        this.renderTrail(entity, partialTick, poseStack, buffer);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(45.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getRenderSelfSpinDegrees(partialTick)));
        poseStack.scale(1.0F, 1.0F, 1.0F);
        this.itemRenderer.renderStatic(
                entity.getSwordStack(),
                ItemDisplayContext.NONE,
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
    public ResourceLocation getTextureLocation(ZenithOrbitSwordEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    private void renderTrail(
            ZenithOrbitSwordEntity entity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer) {
        java.util.List<Vec3> trailPoints = entity.getRenderTrailPoints(partialTick);
        if (trailPoints.size() < 2) {
            return;
        }

        Vec3 entityRenderPosition = trailPoints.get(0);
        Vec3 cameraPosition = this.entityRenderDispatcher.camera.getPosition();
        TrailColor trailColor = getTrailColor(entity.getSwordStack());
        VertexConsumer consumer = buffer.getBuffer(RenderType.leash());
        Matrix4f pose = poseStack.last().pose();
        int packedLight = LightTexture.pack(15, 15);

        for (int index = 0; index < trailPoints.size(); index++) {
            Vec3 point = trailPoints.get(index);
            Vec3 previousPoint = trailPoints.get(Math.max(index - 1, 0));
            Vec3 nextPoint = trailPoints.get(Math.min(index + 1, trailPoints.size() - 1));
            Vec3 direction = nextPoint.subtract(previousPoint);
            if (direction.lengthSqr() < 1.0E-6D) {
                continue;
            }
            direction = direction.normalize();

            Vec3 toCamera = cameraPosition.subtract(point);
            Vec3 side = direction.cross(toCamera);
            if (side.lengthSqr() < 1.0E-6D) {
                side = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
            }
            if (side.lengthSqr() < 1.0E-6D) {
                side = new Vec3(1.0D, 0.0D, 0.0D);
            } else {
                side = side.normalize();
            }

            float progress = trailPoints.size() == 1 ? 0.0F : (float)index / (float)(trailPoints.size() - 1);
            float width = Mth.lerp(progress, TRAIL_START_WIDTH, TRAIL_END_WIDTH);
            float alpha = Mth.lerp(progress, TRAIL_START_ALPHA, TRAIL_END_ALPHA);
            Vec3 offset = side.scale(width * 0.5F);
            Vec3 localPoint = point.subtract(entityRenderPosition);
            Vec3 leftPoint = localPoint.add(offset);
            Vec3 rightPoint = localPoint.subtract(offset);

            consumer.addVertex(pose, (float)leftPoint.x, (float)leftPoint.y, (float)leftPoint.z)
                    .setColor(trailColor.red(), trailColor.green(), trailColor.blue(), alpha)
                    .setLight(packedLight);
            consumer.addVertex(pose, (float)rightPoint.x, (float)rightPoint.y, (float)rightPoint.z)
                    .setColor(trailColor.red(), trailColor.green(), trailColor.blue(), alpha)
                    .setLight(packedLight);
        }
    }

    private static TrailColor getTrailColor(ItemStack swordStack) {
        Item item = swordStack.getItem();
        if (item == Items.WOODEN_SWORD) {
            return new TrailColor(0.541F, 0.353F, 0.169F);
        }
        if (item == Items.STONE_SWORD) {
            return new TrailColor(0.612F, 0.627F, 0.659F);
        }
        if (item == Items.IRON_SWORD) {
            return new TrailColor(0.839F, 0.871F, 0.910F);
        }
        if (item == Items.GOLDEN_SWORD) {
            return new TrailColor(1.000F, 0.847F, 0.290F);
        }
        if (item == Items.DIAMOND_SWORD) {
            return new TrailColor(0.361F, 0.902F, 1.000F);
        }
        if (item == Items.NETHERITE_SWORD) {
            return new TrailColor(0.427F, 0.400F, 0.478F);
        }
        if (item == ModItems.CITRINE_SWORD.get()) {
            return new TrailColor(1.000F, 0.933F, 0.416F);
        }
        if (item == ModItems.COBALT_SWORD.get()) {
            return new TrailColor(0.239F, 0.455F, 1.000F);
        }
        if (item == ModItems.AMETRA_SWORD.get()) {
            return new TrailColor(0.753F, 0.361F, 1.000F);
        }
        return new TrailColor(1.000F, 1.000F, 1.000F);
    }

    private record TrailColor(float red, float green, float blue) {
    }
}
