package com.example.oxyarena.client.renderer.entity;

import java.util.UUID;

import com.example.oxyarena.entity.event.CloneThiefEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

public final class CloneThiefRenderer extends LivingEntityRenderer<CloneThiefEntity, PlayerModel<CloneThiefEntity>> {
    private final PlayerModel<CloneThiefEntity> wideModel;
    private final PlayerModel<CloneThiefEntity> slimModel;

    public CloneThiefRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wideModel = this.getModel();
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(
            CloneThiefEntity entity,
            float entityYaw,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        PlayerSkin skin = this.resolveSkin(entity);
        this.model = skin.model() == PlayerSkin.Model.SLIM ? this.slimModel : this.wideModel;
        this.applyModelProperties(this.model, entity);
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(CloneThiefEntity entity) {
        return this.resolveSkin(entity).texture();
    }

    @Override
    protected void scale(CloneThiefEntity entity, PoseStack poseStack, float partialTickTime) {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }

    private void applyModelProperties(PlayerModel<CloneThiefEntity> model, CloneThiefEntity entity) {
        model.setAllVisible(true);
        model.crouching = entity.isCrouching();
        model.swimAmount = 0.0F;
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
    }

    private PlayerSkin resolveSkin(CloneThiefEntity entity) {
        UUID profileUuid = entity.getSkinProfileUuid()
                .orElseGet(() -> entity.getOwnerUuid().orElse(entity.getUUID()));
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            PlayerInfo playerInfo = connection.getPlayerInfo(profileUuid);
            if (playerInfo != null) {
                return playerInfo.getSkin();
            }
        }

        String ownerName = entity.getOwnerName().isBlank() ? profileUuid.toString() : entity.getOwnerName();
        return Minecraft.getInstance().getSkinManager().getInsecureSkin(new GameProfile(profileUuid, ownerName));
    }
}
