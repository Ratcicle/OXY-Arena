package com.example.oxyarena.client.renderer.entity;

import com.example.oxyarena.entity.event.EruptionTntEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.TntMinecartRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class EruptionTntRenderer extends EntityRenderer<EruptionTntEntity> {
    private final BlockRenderDispatcher blockRenderer;

    public EruptionTntRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(
            EruptionTntEntity entity,
            float entityYaw,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.5F, 0.0F);
        int fuse = entity.getFuse();
        if ((float)fuse - partialTicks + 1.0F < 10.0F) {
            float flashScale = 1.0F - ((float)fuse - partialTicks + 1.0F) / 10.0F;
            flashScale = Mth.clamp(flashScale, 0.0F, 1.0F);
            flashScale *= flashScale;
            flashScale *= flashScale;
            float scaled = 1.0F + flashScale * 0.3F;
            poseStack.scale(scaled, scaled, scaled);
        }

        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.translate(-0.5F, -0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        TntMinecartRenderer.renderWhiteSolidBlock(
                this.blockRenderer,
                entity.getBlockState(),
                poseStack,
                buffer,
                packedLight,
                fuse / 5 % 2 == 0);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(EruptionTntEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
