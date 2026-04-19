package com.example.oxyarena.client.animation;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.client.animation.definition.AnimationDefinition;
import com.example.oxyarena.client.animation.definition.PlayerAnimationBone;
import com.example.oxyarena.client.animation.runtime.AnimationInstance;
import com.example.oxyarena.client.animation.runtime.AnimationModelPatch;
import com.example.oxyarena.client.animation.runtime.PlayerAnimationPose;
import com.example.oxyarena.network.PlayerAnimationPlayPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.common.NeoForge;

public final class PlayerAnimationController {
    private static final Map<UUID, AnimationInstance> ACTIVE_INSTANCES = new HashMap<>();
    private static final float MIN_RENDER_WEIGHT = 0.001F;

    private PlayerAnimationController() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerAnimationController::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(PlayerAnimationController::onRenderPlayerPre);
        NeoForge.EVENT_BUS.addListener(PlayerAnimationController::onRenderArm);
    }

    public static void handlePlayPayload(PlayerAnimationPlayPayload payload) {
        play(payload.playerId(), payload.animationId());
    }

    public static void play(UUID playerId, ResourceLocation animationId) {
        AnimationDefinition definition = PlayerAnimationDataManager.get(animationId);
        if (definition == null) {
            OXYArena.LOGGER.warn("Ignoring unknown OXY player animation {}", animationId);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            OXYArena.LOGGER.debug("Ignoring OXY player animation {} before the client level is ready", animationId);
            return;
        }

        ACTIVE_INSTANCES.put(playerId, new AnimationInstance(definition, minecraft.level.getGameTime()));
    }

    public static boolean hasActiveAnimation(UUID playerId) {
        return ACTIVE_INSTANCES.containsKey(playerId);
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            ACTIVE_INSTANCES.clear();
            return;
        }

        long gameTime = minecraft.level.getGameTime();
        for (Iterator<Map.Entry<UUID, AnimationInstance>> iterator = ACTIVE_INSTANCES.entrySet().iterator();
                iterator.hasNext();) {
            Map.Entry<UUID, AnimationInstance> entry = iterator.next();
            if (entry.getValue().isExpired(gameTime) || minecraft.level.getPlayerByUUID(entry.getKey()) == null) {
                iterator.remove();
            }
        }
    }

    private static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (event.isCanceled() || !(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        AnimationInstance instance = ACTIVE_INSTANCES.get(player.getUUID());
        if (instance == null) {
            return;
        }

        long gameTime = minecraft.level.getGameTime();
        if (instance.isExpired(gameTime)) {
            ACTIVE_INSTANCES.remove(player.getUUID());
            return;
        }

        float partialTick = event.getPartialTick();
        float weight = instance.blendWeight(gameTime, partialTick);
        if (weight <= MIN_RENDER_WEIGHT) {
            return;
        }

        PlayerAnimationPose pose = instance.sample(gameTime, partialTick);
        if (pose.isEmpty()) {
            return;
        }

        renderAnimatedPlayer(
                player,
                event.getRenderer(),
                event.getPoseStack(),
                event.getMultiBufferSource(),
                event.getPackedLight(),
                partialTick,
                instance,
                pose,
                weight);
        event.setCanceled(true);
    }

    private static void onRenderArm(RenderArmEvent event) {
        if (event.isCanceled()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        AbstractClientPlayer player = event.getPlayer();
        AnimationInstance instance = ACTIVE_INSTANCES.get(player.getUUID());
        if (instance == null) {
            return;
        }

        long gameTime = minecraft.level.getGameTime();
        if (instance.isExpired(gameTime)) {
            ACTIVE_INSTANCES.remove(player.getUUID());
            return;
        }

        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(true);
        float weight = instance.blendWeight(gameTime, partialTick);
        if (weight <= MIN_RENDER_WEIGHT) {
            return;
        }

        PlayerAnimationPose pose = instance.sample(gameTime, partialTick);
        PlayerAnimationBone animatedBone = boneForArm(event.getArm());
        if (!pose.transforms().containsKey(animatedBone)) {
            return;
        }

        if (!(minecraft.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) {
            return;
        }

        renderAnimatedFirstPersonArm(
                player,
                renderer,
                event.getArm(),
                event.getPoseStack(),
                event.getMultiBufferSource(),
                event.getPackedLight(),
                partialTick,
                instance,
                pose,
                weight);
        event.setCanceled(true);
    }

    private static void renderAnimatedPlayer(
            AbstractClientPlayer player,
            PlayerRenderer renderer,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            float partialTick,
            AnimationInstance instance,
            PlayerAnimationPose pose,
            float weight) {
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        preparePlayerModel(model, player);
        suppressVanillaArmPoseForAnimatedBones(model, pose);

        poseStack.pushPose();
        PlayerAnimationRenderState renderState = applyPlayerRenderTransforms(player, poseStack, partialTick, model);
        AnimationModelPatch.apply(model, pose, instance.definition().apply(), weight);
        renderPlayerBody(player, model, poseStack, buffer, packedLight);
        if (!player.isSpectator()) {
            PlayerAnimationVanillaLayers.render(player, model, poseStack, buffer, packedLight, renderState);
        }
        poseStack.popPose();
    }

    private static void renderAnimatedFirstPersonArm(
            AbstractClientPlayer player,
            PlayerRenderer renderer,
            HumanoidArm arm,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            float partialTick,
            AnimationInstance instance,
            PlayerAnimationPose pose,
            float weight) {
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        preparePlayerModel(model, player);
        suppressVanillaArmPoseForAnimatedBones(model, pose);

        model.attackTime = 0.0F;
        model.crouching = false;
        model.swimAmount = 0.0F;
        model.setupAnim(player, 0.0F, 0.0F, player.tickCount + partialTick, 0.0F, 0.0F);
        AnimationModelPatch.apply(model, pose, instance.definition().apply(), weight);

        ModelPart armPart = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        ModelPart sleevePart = arm == HumanoidArm.RIGHT ? model.rightSleeve : model.leftSleeve;
        ResourceLocation skinTexture = player.getSkin().texture();

        poseStack.pushPose();
        armPart.render(
                poseStack,
                buffer.getBuffer(RenderType.entitySolid(skinTexture)),
                packedLight,
                OverlayTexture.NO_OVERLAY);
        sleevePart.render(
                poseStack,
                buffer.getBuffer(RenderType.entityTranslucent(skinTexture)),
                packedLight,
                OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private static void preparePlayerModel(PlayerModel<AbstractClientPlayer> model, AbstractClientPlayer player) {
        resetPlayerModelPose(model);
        if (player.isSpectator()) {
            model.setAllVisible(false);
            model.head.visible = true;
            model.hat.visible = true;
            return;
        }

        model.setAllVisible(true);
        model.hat.visible = player.isModelPartShown(PlayerModelPart.HAT);
        model.jacket.visible = player.isModelPartShown(PlayerModelPart.JACKET);
        model.leftPants.visible = player.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
        model.rightPants.visible = player.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
        model.leftSleeve.visible = player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        model.rightSleeve.visible = player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        model.crouching = player.isCrouching();

        HumanoidModel.ArmPose mainArmPose = getArmPose(player, InteractionHand.MAIN_HAND);
        HumanoidModel.ArmPose offArmPose = getArmPose(player, InteractionHand.OFF_HAND);
        if (mainArmPose.isTwoHanded()) {
            offArmPose = player.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        }

        if (player.getMainArm() == HumanoidArm.RIGHT) {
            model.rightArmPose = mainArmPose;
            model.leftArmPose = offArmPose;
        } else {
            model.rightArmPose = offArmPose;
            model.leftArmPose = mainArmPose;
        }
    }

    private static void suppressVanillaArmPoseForAnimatedBones(
            PlayerModel<AbstractClientPlayer> model,
            PlayerAnimationPose pose) {
        if (pose.transforms().containsKey(PlayerAnimationBone.RIGHT_ARM)) {
            model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        }
        if (pose.transforms().containsKey(PlayerAnimationBone.LEFT_ARM)) {
            model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        }
    }

    private static PlayerAnimationBone boneForArm(HumanoidArm arm) {
        return arm == HumanoidArm.RIGHT ? PlayerAnimationBone.RIGHT_ARM : PlayerAnimationBone.LEFT_ARM;
    }

    private static void resetPlayerModelPose(PlayerModel<AbstractClientPlayer> model) {
        model.head.resetPose();
        model.hat.resetPose();
        model.body.resetPose();
        model.rightArm.resetPose();
        model.leftArm.resetPose();
        model.rightLeg.resetPose();
        model.leftLeg.resetPose();
        model.jacket.resetPose();
        model.rightSleeve.resetPose();
        model.leftSleeve.resetPose();
        model.rightPants.resetPose();
        model.leftPants.resetPose();
    }

    private static PlayerAnimationRenderState applyPlayerRenderTransforms(
            AbstractClientPlayer player,
            PoseStack poseStack,
            float partialTick,
            PlayerModel<AbstractClientPlayer> model) {
        model.attackTime = player.getAttackAnim(partialTick);
        boolean shouldSit = player.isPassenger() && player.getVehicle() != null && player.getVehicle().shouldRiderSit();
        model.riding = shouldSit;
        model.young = player.isBaby();

        float bodyRot = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float headRot = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
        float headBodyDiff = headRot - bodyRot;
        if (shouldSit && player.getVehicle() instanceof net.minecraft.world.entity.LivingEntity vehicle) {
            bodyRot = Mth.rotLerp(partialTick, vehicle.yBodyRotO, vehicle.yBodyRot);
            headBodyDiff = headRot - bodyRot;
            float wrapped = Mth.wrapDegrees(headBodyDiff);
            wrapped = Mth.clamp(wrapped, -85.0F, 85.0F);
            bodyRot = headRot - wrapped;
            if (wrapped * wrapped > 2500.0F) {
                bodyRot += wrapped * 0.2F;
            }

            headBodyDiff = headRot - bodyRot;
        }

        float xRot = Mth.lerp(partialTick, player.xRotO, player.getXRot());
        if (LivingEntityRenderer.isEntityUpsideDown(player)) {
            xRot *= -1.0F;
            headBodyDiff *= -1.0F;
        }

        headBodyDiff = Mth.wrapDegrees(headBodyDiff);
        if (player.hasPose(Pose.SLEEPING) && player.getBedOrientation() != null) {
            float eyeOffset = player.getEyeHeight(Pose.STANDING) - 0.1F;
            poseStack.translate(
                    -player.getBedOrientation().getStepX() * eyeOffset,
                    0.0F,
                    -player.getBedOrientation().getStepZ() * eyeOffset);
        }

        float entityScale = player.getScale();
        poseStack.scale(entityScale, entityScale, entityScale);
        float bob = player.tickCount + partialTick;
        setupPlayerRotations(player, poseStack, bob, bodyRot, partialTick, entityScale);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
        poseStack.translate(0.0F, -1.501F, 0.0F);

        float walkSpeed = 0.0F;
        float walkPosition = 0.0F;
        if (!shouldSit && player.isAlive()) {
            walkSpeed = player.walkAnimation.speed(partialTick);
            walkPosition = player.walkAnimation.position(partialTick);
            if (player.isBaby()) {
                walkPosition *= 3.0F;
            }

            walkSpeed = Math.min(walkSpeed, 1.0F);
        }

        model.prepareMobModel(player, walkPosition, walkSpeed, partialTick);
        model.setupAnim(player, walkPosition, walkSpeed, bob, headBodyDiff, xRot);
        return new PlayerAnimationRenderState(walkPosition, walkSpeed, partialTick, bob, headBodyDiff, xRot);
    }

    private static void setupPlayerRotations(
            AbstractClientPlayer player,
            PoseStack poseStack,
            float bob,
            float bodyRot,
            float partialTick,
            float scale) {
        float swimAmount = player.getSwimAmount(partialTick);
        float viewXRot = player.getViewXRot(partialTick);
        if (player.isFallFlying()) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyRot));
            float fallFlyingTicks = player.getFallFlyingTicks() + partialTick;
            float flightProgress = Mth.clamp(fallFlyingTicks * fallFlyingTicks / 100.0F, 0.0F, 1.0F);
            if (!player.isAutoSpinAttack()) {
                poseStack.mulPose(Axis.XP.rotationDegrees(flightProgress * (-90.0F - viewXRot)));
            }

            Vec3 viewVector = player.getViewVector(partialTick);
            Vec3 motion = player.getDeltaMovementLerped(partialTick);
            double motionHorizontal = motion.horizontalDistanceSqr();
            double viewHorizontal = viewVector.horizontalDistanceSqr();
            if (motionHorizontal > 0.0D && viewHorizontal > 0.0D) {
                double dot = (motion.x * viewVector.x + motion.z * viewVector.z) / Math.sqrt(motionHorizontal * viewHorizontal);
                double cross = motion.x * viewVector.z - motion.z * viewVector.x;
                poseStack.mulPose(Axis.YP.rotation((float)(Math.signum(cross) * Math.acos(dot))));
            }
        } else if (swimAmount > 0.0F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyRot));
            float swimTargetRot = player.isInWater()
                    || player.isInFluidType((fluidType, height) -> player.canSwimInFluidType(fluidType))
                            ? -90.0F - player.getXRot()
                            : -90.0F;
            float swimRot = Mth.lerp(swimAmount, 0.0F, swimTargetRot);
            poseStack.mulPose(Axis.XP.rotationDegrees(swimRot));
            if (player.isVisuallySwimming()) {
                poseStack.translate(0.0F, -1.0F, 0.3F);
            }
        } else {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyRot));
            if (player.deathTime > 0) {
                float deathRotation = ((float)player.deathTime + partialTick - 1.0F) / 20.0F * 1.6F;
                deathRotation = Mth.sqrt(deathRotation);
                deathRotation = Math.min(deathRotation, 1.0F);
                poseStack.mulPose(Axis.ZP.rotationDegrees(deathRotation * 90.0F));
            } else if (player.isAutoSpinAttack()) {
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F - player.getXRot()));
                poseStack.mulPose(Axis.YP.rotationDegrees((player.tickCount + partialTick) * -75.0F));
            } else if (player.hasPose(Pose.SLEEPING) && player.getBedOrientation() != null) {
                poseStack.mulPose(Axis.YP.rotationDegrees(sleepDirectionToRotation(player.getBedOrientation())));
                poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(270.0F));
            } else if (LivingEntityRenderer.isEntityUpsideDown(player)) {
                poseStack.translate(0.0F, (player.getBbHeight() + 0.1F) / scale, 0.0F);
                poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
            } else if (player.isFullyFrozen()) {
                bodyRot += (float)(Math.cos(player.tickCount * 3.25D) * Math.PI * 0.4F);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyRot));
            }
        }
    }

    private static float sleepDirectionToRotation(net.minecraft.core.Direction facing) {
        return switch (facing) {
            case SOUTH -> 90.0F;
            case WEST -> 0.0F;
            case NORTH -> 270.0F;
            case EAST -> 180.0F;
            default -> 0.0F;
        };
    }

    private static void renderPlayerBody(
            AbstractClientPlayer player,
            PlayerModel<AbstractClientPlayer> model,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        RenderType renderType = getPlayerRenderType(player, model);
        if (renderType == null) {
            return;
        }

        VertexConsumer vertexConsumer = buffer.getBuffer(renderType);
        boolean translucent = player.isInvisible() && !player.isInvisibleTo(Minecraft.getInstance().player);
        int color = translucent ? 654311423 : -1;
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        model.renderToBuffer(poseStack, vertexConsumer, packedLight, overlay, color);
    }

    private static RenderType getPlayerRenderType(AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> model) {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation skinTexture = player.getSkin().texture();
        boolean bodyVisible = !player.isInvisible();
        boolean translucent = !bodyVisible && !player.isInvisibleTo(minecraft.player);
        boolean glowing = minecraft.shouldEntityAppearGlowing(player);
        if (translucent) {
            return RenderType.itemEntityTranslucentCull(skinTexture);
        }
        if (bodyVisible) {
            return model.renderType(skinTexture);
        }
        return glowing ? RenderType.outline(skinTexture) : null;
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
}
