package com.example.oxyarena.client.renderer.item;

import com.example.oxyarena.OXYArena;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ShieldModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

public final class CobaltShieldItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final Material SHIELD_BASE = new Material(
            Sheets.SHIELD_SHEET,
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "entity/cobalt_shield_base"));
    private static final Material SHIELD_BASE_NO_PATTERN = new Material(
            Sheets.SHIELD_SHEET,
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "entity/cobalt_shield_base_nopattern"));

    private static CobaltShieldItemRenderer instance;

    private final ShieldModel shieldModel;

    private CobaltShieldItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.shieldModel = new ShieldModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.SHIELD));
    }

    public static CobaltShieldItemRenderer getInstance() {
        if (instance == null) {
            instance = new CobaltShieldItemRenderer();
        }

        return instance;
    }

    @Override
    public void renderByItem(
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay) {
        BannerPatternLayers bannerPatterns = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
        DyeColor baseColor = stack.get(DataComponents.BASE_COLOR);
        boolean hasPattern = !bannerPatterns.layers().isEmpty() || baseColor != null;

        poseStack.pushPose();
        poseStack.scale(1.0F, -1.0F, -1.0F);

        Material material = hasPattern ? SHIELD_BASE : SHIELD_BASE_NO_PATTERN;
        VertexConsumer vertexConsumer = material.sprite()
                .wrap(ItemRenderer.getFoilBufferDirect(
                        buffer,
                        this.shieldModel.renderType(material.atlasLocation()),
                        true,
                        stack.hasFoil()));

        this.shieldModel.handle().render(poseStack, vertexConsumer, packedLight, packedOverlay);

        if (hasPattern) {
            BannerRenderer.renderPatterns(
                    poseStack,
                    buffer,
                    packedLight,
                    packedOverlay,
                    this.shieldModel.plate(),
                    material,
                    false,
                    baseColor == null ? DyeColor.WHITE : baseColor,
                    bannerPatterns,
                    stack.hasFoil());
        } else {
            this.shieldModel.plate().render(poseStack, vertexConsumer, packedLight, packedOverlay);
        }

        poseStack.popPose();
    }
}
