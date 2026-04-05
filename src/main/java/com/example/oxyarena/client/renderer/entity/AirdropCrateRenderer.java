package com.example.oxyarena.client.renderer.entity;

import com.example.oxyarena.entity.event.AirdropCrateEntity;
import com.example.oxyarena.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AirdropCrateRenderer extends EntityRenderer<AirdropCrateEntity> {
    private static final float SPIN_DEGREES_PER_TICK = 2.0F;
    private static final float SWAY_DEGREES = 2.5F;
    private static final float SWAY_SPEED = 0.08F;

    private final BlockRenderDispatcher blockRenderer;

    public AirdropCrateRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(
            AirdropCrateEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        float age = entity.tickCount + partialTick;

        poseStack.pushPose();
        poseStack.translate(0.0D, 0.5D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * SPIN_DEGREES_PER_TICK));
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(age * SWAY_SPEED) * SWAY_DEGREES));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.cos(age * SWAY_SPEED) * (SWAY_DEGREES * 0.5F)));
        poseStack.translate(-0.5D, -0.5D, -0.5D);
        this.blockRenderer.renderSingleBlock(
                ModBlocks.OXYDROP_CRATE.get().defaultBlockState(),
                poseStack,
                buffer,
                packedLight,
                OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(AirdropCrateEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
