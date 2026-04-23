package com.example.oxyarena.client.renderer.block;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.block.entity.OxydropCrateBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import org.joml.Matrix4f;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class OxydropCrateBlockEntityRenderer implements BlockEntityRenderer<OxydropCrateBlockEntity> {
    private static final ResourceLocation BASE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            OXYArena.MODID,
            "textures/block/supply_extraction_base.png");
    private static final float SIZE = 8.0F;
    private static final float HALF_SIZE = SIZE * 0.5F;
    private static final float BASE_Y = 0.0125F;
    private static final float SPIN_DEGREES_PER_TICK = 4.0F;

    public OxydropCrateBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            OxydropCrateBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay) {
        if (!blockEntity.hasSupplyExtractionMarker() || blockEntity.getLevel() == null) {
            return;
        }

        float age = blockEntity.getLevel().getGameTime() + partialTick;
        poseStack.pushPose();
        poseStack.translate(0.5F, BASE_Y, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * SPIN_DEGREES_PER_TICK));

        Matrix4f pose = poseStack.last().pose();
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(BASE_TEXTURE));
        addVertex(consumer, pose, -HALF_SIZE, 0.0F, -HALF_SIZE, 0.0F, 0.0F);
        addVertex(consumer, pose, -HALF_SIZE, 0.0F, HALF_SIZE, 0.0F, 1.0F);
        addVertex(consumer, pose, HALF_SIZE, 0.0F, HALF_SIZE, 1.0F, 1.0F);
        addVertex(consumer, pose, HALF_SIZE, 0.0F, -HALF_SIZE, 1.0F, 0.0F);
        poseStack.popPose();
    }

    private static void addVertex(
            VertexConsumer consumer,
            Matrix4f pose,
            float x,
            float y,
            float z,
            float u,
            float v) {
        consumer.addVertex(pose, x, y, z)
                .setColor(1.0F, 1.0F, 1.0F, 0.92F)
                .setUv(u, v)
                .setUv1(OverlayTexture.NO_OVERLAY & 0xFFFF, OverlayTexture.NO_OVERLAY >> 16 & 0xFFFF)
                .setUv2(LightTexture.FULL_BRIGHT & 0xFFFF, LightTexture.FULL_BRIGHT >> 16 & 0xFFFF)
                .setNormal(0.0F, 1.0F, 0.0F);
    }
}
