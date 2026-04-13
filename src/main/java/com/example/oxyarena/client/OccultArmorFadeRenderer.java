package com.example.oxyarena.client;

import com.example.oxyarena.util.OccultCamouflageTuning;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

final class OccultArmorFadeRenderer {
    private static ArmorLayerBundle wideArmorLayer;
    private static ArmorLayerBundle slimArmorLayer;

    private OccultArmorFadeRenderer() {
    }

    static void renderArmor(
            AbstractClientPlayer player,
            PlayerModel<AbstractClientPlayer> parentModel,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            OccultCamouflageController.PlayerAnimationState animationState,
            float alpha) {
        if (alpha <= OccultCamouflageTuning.CROSSFADE_EPSILON) {
            return;
        }

        ArmorLayerBundle layerBundle = player.getSkin().model() == PlayerSkin.Model.SLIM ? getSlimArmorLayer() : getWideArmorLayer();
        layerBundle.parent.setModel(parentModel);
        layerBundle.layer.render(
                poseStack,
                new AlphaScaledBufferSource(buffer, alpha),
                packedLight,
                player,
                animationState.limbSwing(),
                animationState.limbSwingAmount(),
                animationState.partialTick(),
                animationState.ageInTicks(),
                animationState.netHeadYaw(),
                animationState.headPitch());
    }

    private static ArmorLayerBundle getWideArmorLayer() {
        if (wideArmorLayer == null) {
            wideArmorLayer = createArmorLayer(false);
        }

        return wideArmorLayer;
    }

    private static ArmorLayerBundle getSlimArmorLayer() {
        if (slimArmorLayer == null) {
            slimArmorLayer = createArmorLayer(true);
        }

        return slimArmorLayer;
    }

    private static ArmorLayerBundle createArmorLayer(boolean slim) {
        Minecraft minecraft = Minecraft.getInstance();
        MutableRenderLayerParent parent = new MutableRenderLayerParent();
        HumanoidArmorLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>, HumanoidArmorModel<AbstractClientPlayer>> layer =
                new HumanoidArmorLayer<>(
                        parent,
                        new HumanoidArmorModel<>(minecraft.getEntityModels().bakeLayer(
                                slim ? ModelLayers.PLAYER_SLIM_INNER_ARMOR : ModelLayers.PLAYER_INNER_ARMOR)),
                        new HumanoidArmorModel<>(minecraft.getEntityModels().bakeLayer(
                                slim ? ModelLayers.PLAYER_SLIM_OUTER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR)),
                        minecraft.getModelManager());
        return new ArmorLayerBundle(parent, layer);
    }

    private record ArmorLayerBundle(
            MutableRenderLayerParent parent,
            HumanoidArmorLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>, HumanoidArmorModel<AbstractClientPlayer>> layer) {
    }

    private static final class MutableRenderLayerParent implements RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private PlayerModel<AbstractClientPlayer> model;

        void setModel(PlayerModel<AbstractClientPlayer> model) {
            this.model = model;
        }

        @Override
        public PlayerModel<AbstractClientPlayer> getModel() {
            return this.model;
        }

        @Override
        public ResourceLocation getTextureLocation(AbstractClientPlayer entity) {
            return entity.getSkin().texture();
        }
    }

    private record AlphaScaledBufferSource(MultiBufferSource delegate, float alphaScale) implements MultiBufferSource {
        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return new AlphaScaledVertexConsumer(this.delegate.getBuffer(renderType), this.alphaScale);
        }
    }

    private static final class AlphaScaledVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float alphaScale;

        private AlphaScaledVertexConsumer(VertexConsumer delegate, float alphaScale) {
            this.delegate = delegate;
            this.alphaScale = alphaScale;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            int scaledAlpha = Mth.clamp(Math.round(alpha * this.alphaScale), 0, 255);
            this.delegate.setColor(red, green, blue, scaledAlpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            this.delegate.setNormal(normalX, normalY, normalZ);
            return this;
        }
    }
}
