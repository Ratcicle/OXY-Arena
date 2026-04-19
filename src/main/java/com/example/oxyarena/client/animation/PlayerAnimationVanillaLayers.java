package com.example.oxyarena.client.animation;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

final class PlayerAnimationVanillaLayers {
    private static LayerBundle wideLayerBundle;
    private static LayerBundle slimLayerBundle;

    private PlayerAnimationVanillaLayers() {
    }

    static void render(
            AbstractClientPlayer player,
            PlayerModel<AbstractClientPlayer> parentModel,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            PlayerAnimationRenderState renderState) {
        LayerBundle layerBundle = player.getSkin().model() == PlayerSkin.Model.SLIM
                ? getSlimLayerBundle()
                : getWideLayerBundle();
        layerBundle.parent.setModel(parentModel);
        layerBundle.armorLayer.render(
                poseStack,
                buffer,
                packedLight,
                player,
                renderState.limbSwing(),
                renderState.limbSwingAmount(),
                renderState.partialTick(),
                renderState.ageInTicks(),
                renderState.netHeadYaw(),
                renderState.headPitch());
        layerBundle.itemInHandLayer.render(
                poseStack,
                buffer,
                packedLight,
                player,
                renderState.limbSwing(),
                renderState.limbSwingAmount(),
                renderState.partialTick(),
                renderState.ageInTicks(),
                renderState.netHeadYaw(),
                renderState.headPitch());
    }

    private static LayerBundle getWideLayerBundle() {
        if (wideLayerBundle == null) {
            wideLayerBundle = createLayerBundle(false);
        }

        return wideLayerBundle;
    }

    private static LayerBundle getSlimLayerBundle() {
        if (slimLayerBundle == null) {
            slimLayerBundle = createLayerBundle(true);
        }

        return slimLayerBundle;
    }

    private static LayerBundle createLayerBundle(boolean slim) {
        Minecraft minecraft = Minecraft.getInstance();
        MutableRenderLayerParent parent = new MutableRenderLayerParent();
        HumanoidArmorLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>, HumanoidArmorModel<AbstractClientPlayer>> armorLayer =
                new HumanoidArmorLayer<>(
                        parent,
                        new HumanoidArmorModel<>(minecraft.getEntityModels().bakeLayer(
                                slim ? ModelLayers.PLAYER_SLIM_INNER_ARMOR : ModelLayers.PLAYER_INNER_ARMOR)),
                        new HumanoidArmorModel<>(minecraft.getEntityModels().bakeLayer(
                                slim ? ModelLayers.PLAYER_SLIM_OUTER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR)),
                        minecraft.getModelManager());
        PlayerItemInHandLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> itemInHandLayer =
                new PlayerItemInHandLayer<>(parent, minecraft.getEntityRenderDispatcher().getItemInHandRenderer());
        return new LayerBundle(parent, armorLayer, itemInHandLayer);
    }

    private record LayerBundle(
            MutableRenderLayerParent parent,
            HumanoidArmorLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>, HumanoidArmorModel<AbstractClientPlayer>> armorLayer,
            PlayerItemInHandLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> itemInHandLayer) {
    }

    private static final class MutableRenderLayerParent
            implements RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
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
}
