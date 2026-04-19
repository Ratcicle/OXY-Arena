package com.example.oxyarena.client.renderer.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.oxyarena.entity.effect.GhostSaberEchoEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public final class GhostSaberEchoRenderer extends EntityRenderer<GhostSaberEchoEntity> {
    private static final ResourceLocation WHITE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "neoforge",
            "textures/white.png");
    private static final float CORE_ALPHA = 0.58F;
    private static final float BLOOM_ALPHA = 0.24F;
    private static final float CORE_RED = 0.88F;
    private static final float CORE_GREEN = 0.96F;
    private static final float CORE_BLUE = 1.0F;

    private final PlayerModel<AbstractClientPlayer> wideModel;
    private final PlayerModel<AbstractClientPlayer> slimModel;
    private final Map<UUID, EchoPoseSnapshot> poseSnapshots = new HashMap<>();

    public GhostSaberEchoRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.wideModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(
            GhostSaberEchoEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        float age = entity.tickCount + partialTick;
        float fadeIn = Mth.clamp(age / 4.0F, 0.0F, 1.0F);
        float lateFade = 1.0F - Mth.clamp((age - 32.0F) / 8.0F, 0.0F, 0.72F);
        float alpha = fadeIn * lateFade;
        if (alpha <= 0.02F) {
            return;
        }

        EchoPoseSnapshot snapshot = this.poseSnapshots.computeIfAbsent(entity.getUUID(), ignored -> this.capturePose(entity, partialTick));
        PlayerModel<AbstractClientPlayer> model = snapshot.slim() ? this.slimModel : this.wideModel;

        poseStack.pushPose();
        Vec3 vanillaRenderPosition = new Vec3(
                Mth.lerp(partialTick, entity.xo, entity.getX()),
                Mth.lerp(partialTick, entity.yo, entity.getY()),
                Mth.lerp(partialTick, entity.zo, entity.getZ()));
        Vec3 smoothRenderPosition = entity.getSmoothRenderPosition(partialTick);
        Vec3 renderCorrection = smoothRenderPosition.subtract(vanillaRenderPosition);
        poseStack.translate(renderCorrection.x, renderCorrection.y, renderCorrection.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.lerp(partialTick, entity.yRotO, entity.getYRot())));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
        poseStack.translate(0.0F, -1.501F, 0.0F);

        int fullBright = LightTexture.pack(15, 15);
        int overlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
        this.applySnapshot(model, snapshot);
        this.renderSilhouetteLayer(model, poseStack, buffer, fullBright, overlay, CORE_ALPHA * alpha, 1.0F);

        poseStack.pushPose();
        poseStack.scale(1.035F, 1.035F, 1.035F);
        this.applySnapshot(model, snapshot);
        this.renderSilhouetteLayer(model, poseStack, buffer, fullBright, overlay, BLOOM_ALPHA * alpha, 1.0F);
        poseStack.popPose();

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(GhostSaberEchoEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    private EchoPoseSnapshot capturePose(GhostSaberEchoEntity entity, float partialTick) {
        UUID ownerId = entity.getOwnerUuid().orElse(null);
        AbstractClientPlayer owner = this.findOwner(ownerId);
        boolean slim = this.isSlim(ownerId, owner);
        PlayerModel<AbstractClientPlayer> model = slim ? this.slimModel : this.wideModel;
        if (owner == null) {
            this.prepareNeutralModel(model);
            return EchoPoseSnapshot.capture(model, slim);
        }

        this.prepareModelFromOwner(model, owner, partialTick);
        return EchoPoseSnapshot.capture(model, slim);
    }

    private AbstractClientPlayer findOwner(UUID ownerId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ownerId == null || minecraft.level == null) {
            return null;
        }

        Player player = minecraft.level.getPlayerByUUID(ownerId);
        return player instanceof AbstractClientPlayer clientPlayer ? clientPlayer : null;
    }

    private boolean isSlim(UUID ownerId, AbstractClientPlayer owner) {
        if (owner != null) {
            return owner.getSkin().model() == PlayerSkin.Model.SLIM;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        if (ownerId != null && connection != null) {
            PlayerInfo playerInfo = connection.getPlayerInfo(ownerId);
            if (playerInfo != null) {
                return playerInfo.getSkin().model() == PlayerSkin.Model.SLIM;
            }
        }
        return false;
    }

    private void prepareModelFromOwner(PlayerModel<AbstractClientPlayer> model, AbstractClientPlayer owner, float partialTick) {
        this.prepareNeutralModel(model);
        model.crouching = owner.isCrouching();
        model.riding = owner.isPassenger() && owner.getVehicle() != null && owner.getVehicle().shouldRiderSit();
        model.young = owner.isBaby();
        model.attackTime = owner.getAttackAnim(partialTick);

        HumanoidModel.ArmPose mainArmPose = getArmPose(owner, InteractionHand.MAIN_HAND);
        HumanoidModel.ArmPose offArmPose = getArmPose(owner, InteractionHand.OFF_HAND);
        if (mainArmPose.isTwoHanded()) {
            offArmPose = owner.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        }

        if (owner.getMainArm() == HumanoidArm.RIGHT) {
            model.rightArmPose = mainArmPose;
            model.leftArmPose = offArmPose;
        } else {
            model.rightArmPose = offArmPose;
            model.leftArmPose = mainArmPose;
        }

        float bodyRot = Mth.rotLerp(partialTick, owner.yBodyRotO, owner.yBodyRot);
        float headRot = Mth.rotLerp(partialTick, owner.yHeadRotO, owner.yHeadRot);
        float headBodyDiff = Mth.wrapDegrees(headRot - bodyRot);
        float xRot = Mth.lerp(partialTick, owner.xRotO, owner.getXRot());
        float walkSpeed = owner.walkAnimation.speed(partialTick);
        float walkPosition = owner.walkAnimation.position(partialTick);
        walkSpeed = Math.min(walkSpeed, 1.0F);
        model.prepareMobModel(owner, walkPosition, walkSpeed, partialTick);
        model.setupAnim(owner, walkPosition, walkSpeed, owner.tickCount + partialTick, headBodyDiff, xRot);
    }

    private void prepareNeutralModel(PlayerModel<AbstractClientPlayer> model) {
        model.setAllVisible(true);
        model.hat.visible = false;
        model.jacket.visible = false;
        model.leftPants.visible = false;
        model.rightPants.visible = false;
        model.leftSleeve.visible = false;
        model.rightSleeve.visible = false;
        model.crouching = false;
        model.riding = false;
        model.young = false;
        model.attackTime = 0.0F;
        model.swimAmount = 0.0F;
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
    }

    private void applySnapshot(PlayerModel<AbstractClientPlayer> model, EchoPoseSnapshot snapshot) {
        this.prepareNeutralModel(model);
        snapshot.head().apply(model.head);
        snapshot.body().apply(model.body);
        snapshot.rightArm().apply(model.rightArm);
        snapshot.leftArm().apply(model.leftArm);
        snapshot.rightLeg().apply(model.rightLeg);
        snapshot.leftLeg().apply(model.leftLeg);
    }

    private void renderSilhouetteLayer(
            PlayerModel<AbstractClientPlayer> model,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int overlay,
            float alpha,
        float colorScale) {
        int color = argb(alpha, CORE_RED * colorScale, CORE_GREEN * colorScale, CORE_BLUE);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(WHITE_TEXTURE));
        model.renderToBuffer(poseStack, consumer, packedLight, overlay, color);
    }

    private static HumanoidModel.ArmPose getArmPose(AbstractClientPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) {
            return HumanoidModel.ArmPose.EMPTY;
        }

        if (player.getUsedItemHand() == hand && player.getUseItemRemainingTicks() > 0) {
            UseAnim useAnimation = stack.getUseAnimation();
            if (useAnimation == UseAnim.BLOCK) {
                return HumanoidModel.ArmPose.BLOCK;
            }
            if (useAnimation == UseAnim.BOW) {
                return HumanoidModel.ArmPose.BOW_AND_ARROW;
            }
            if (useAnimation == UseAnim.SPEAR) {
                return HumanoidModel.ArmPose.THROW_SPEAR;
            }
            if (useAnimation == UseAnim.CROSSBOW && hand == player.getUsedItemHand()) {
                return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
            }
            if (useAnimation == UseAnim.SPYGLASS) {
                return HumanoidModel.ArmPose.SPYGLASS;
            }
            if (useAnimation == UseAnim.TOOT_HORN) {
                return HumanoidModel.ArmPose.TOOT_HORN;
            }
            if (useAnimation == UseAnim.BRUSH) {
                return HumanoidModel.ArmPose.BRUSH;
            }
        } else if (!player.swinging && stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }

        HumanoidModel.ArmPose extensionPose = IClientItemExtensions.of(stack).getArmPose(player, hand, stack);
        return extensionPose != null ? extensionPose : HumanoidModel.ArmPose.ITEM;
    }

    private static int argb(float alpha, float red, float green, float blue) {
        return ((Mth.clamp((int)(alpha * 255.0F), 0, 255) & 255) << 24)
                | ((Mth.clamp((int)(red * 255.0F), 0, 255) & 255) << 16)
                | ((Mth.clamp((int)(green * 255.0F), 0, 255) & 255) << 8)
                | (Mth.clamp((int)(blue * 255.0F), 0, 255) & 255);
    }

    private record EchoPoseSnapshot(
            boolean slim,
            PartSnapshot head,
            PartSnapshot body,
            PartSnapshot rightArm,
            PartSnapshot leftArm,
            PartSnapshot rightLeg,
            PartSnapshot leftLeg) {
        private static EchoPoseSnapshot capture(PlayerModel<AbstractClientPlayer> model, boolean slim) {
            return new EchoPoseSnapshot(
                    slim,
                    PartSnapshot.capture(model.head),
                    PartSnapshot.capture(model.body),
                    PartSnapshot.capture(model.rightArm),
                    PartSnapshot.capture(model.leftArm),
                    PartSnapshot.capture(model.rightLeg),
                    PartSnapshot.capture(model.leftLeg));
        }
    }

    private record PartSnapshot(float x, float y, float z, float xRot, float yRot, float zRot) {
        private static PartSnapshot capture(ModelPart part) {
            return new PartSnapshot(part.x, part.y, part.z, part.xRot, part.yRot, part.zRot);
        }

        private void apply(ModelPart part) {
            part.x = this.x;
            part.y = this.y;
            part.z = this.z;
            part.xRot = this.xRot;
            part.yRot = this.yRot;
            part.zRot = this.zRot;
        }
    }
}
