package com.example.oxyarena.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.example.oxyarena.network.OccultCamouflageSyncPayload;
import com.example.oxyarena.registry.ModItems;
import com.example.oxyarena.util.OccultCamouflageTuning;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
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
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.common.NeoForge;

public final class OccultCamouflageController {
    private static final float MIN_RENDER_PROGRESS = 0.01F;
    private static final float INTERIOR_RED = 0.56F;
    private static final float INTERIOR_GREEN = 0.66F;
    private static final float INTERIOR_BLUE = 0.69F;
    private static final float EDGE_RED = 0.63F;
    private static final float EDGE_GREEN = 0.93F;
    private static final float EDGE_BLUE = 0.98F;
    private static final float SHIMMER_RED = 0.78F;
    private static final float SHIMMER_GREEN = 1.0F;
    private static final float SHIMMER_BLUE = 0.98F;
    private static final int STALE_STATE_TICKS = 40;
    private static final Map<UUID, ClientCloakState> CLOAK_STATES = new HashMap<>();
    private static final Map<AbstractClientPlayer, Boolean> INVISIBILITY_RESTORE = new IdentityHashMap<>();

    private OccultCamouflageController() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(OccultCamouflageController::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(OccultCamouflageController::onRenderPlayerPre);
        NeoForge.EVENT_BUS.addListener(OccultCamouflageController::onRenderArm);
        NeoForge.EVENT_BUS.addListener(OccultCamouflageController::onRenderFramePost);
    }

    public static void handleSync(OccultCamouflageSyncPayload payload) {
        ClientCloakState state = CLOAK_STATES.computeIfAbsent(payload.playerId(), ignored -> new ClientCloakState());
        state.targetProgress = OccultCamouflageTuning.quantizedToProgress(payload.quantizedProgress());
        state.hasAuthoritativeProgress = true;
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            CLOAK_STATES.clear();
            INVISIBILITY_RESTORE.clear();
            return;
        }

        updateLocalPrediction(minecraft);
        tickRenderProgress(minecraft);
        cleanupStateCache(minecraft);
    }

    private static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }

        float progress = getInterpolatedRenderProgress(player, event.getPartialTick());
        if (progress <= MIN_RENDER_PROGRESS) {
            return;
        }

        float cloakWeight = getCloakWeight(progress);
        if (cloakWeight <= OccultCamouflageTuning.CROSSFADE_EPSILON) {
            return;
        }

        float normalWeight = getNormalWeight(cloakWeight);
        renderCamouflagedPlayer(
                player,
                event.getRenderer(),
                event.getPoseStack(),
                event.getMultiBufferSource(),
                event.getPackedLight(),
                event.getPartialTick(),
                normalWeight,
                cloakWeight);
        INVISIBILITY_RESTORE.putIfAbsent(player, player.isInvisible());
        player.setInvisible(true);
        event.setCanceled(true);
    }

    private static void onRenderArm(RenderArmEvent event) {
        AbstractClientPlayer player = event.getPlayer();
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        float progress = getInterpolatedRenderProgress(player, partialTick);
        if (progress <= MIN_RENDER_PROGRESS) {
            return;
        }

        if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) {
            return;
        }

        float cloakWeight = getCloakWeight(progress);
        if (cloakWeight <= OccultCamouflageTuning.CROSSFADE_EPSILON) {
            return;
        }

        float normalWeight = getNormalWeight(cloakWeight);
        event.setCanceled(true);
        renderCamouflagedArm(
                player,
                renderer,
                event.getArm(),
                event.getPoseStack(),
                event.getMultiBufferSource(),
                event.getPackedLight(),
                partialTick,
                normalWeight,
                cloakWeight);
    }

    private static void onRenderFramePost(RenderFrameEvent.Post event) {
        for (Map.Entry<AbstractClientPlayer, Boolean> entry : INVISIBILITY_RESTORE.entrySet()) {
            entry.getKey().setInvisible(entry.getValue());
        }

        INVISIBILITY_RESTORE.clear();
    }

    private static void updateLocalPrediction(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        ClientCloakState state = CLOAK_STATES.computeIfAbsent(player.getUUID(), ignored -> new ClientCloakState());
        Vec3 currentPosition = player.position();
        if (!state.initialized) {
            state.initialized = true;
            state.lastPosition = currentPosition;
        }

        if (!player.isAlive() || player.isSpectator() || !hasFullOccultSet(player)) {
            state.stationaryTicks = 0;
            state.predictedProgress = 0.0F;
            state.lastPosition = currentPosition;
            reconcileWithAuthoritative(state);
            return;
        }

        double deltaMovementSqr = currentPosition.distanceToSqr(state.lastPosition);
        boolean stationary = deltaMovementSqr <= OccultCamouflageTuning.MOVEMENT_EPSILON_SQR;
        boolean microMovement = OccultCamouflageTuning.isMicroMovement(deltaMovementSqr, player.isCrouching());
        boolean hardBreak = OccultCamouflageTuning.isNormalMovement(deltaMovementSqr, player.isCrouching())
                || player.isSprinting()
                || !player.onGround()
                || player.isUsingItem()
                || player.swinging
                || player.hurtTime > 0
                || minecraft.options.keyAttack.isDown()
                || minecraft.options.keyUse.isDown();

        if (hardBreak) {
            state.stationaryTicks = 0;
            state.predictedProgress = 0.0F;
        } else if (stationary) {
            state.stationaryTicks++;
            if (state.stationaryTicks >= OccultCamouflageTuning.ARMING_TICKS) {
                state.predictedProgress = Mth.clamp(
                        state.predictedProgress + OccultCamouflageTuning.fadeInStep(),
                        0.0F,
                        1.0F);
            } else {
                state.predictedProgress = 0.0F;
            }
        } else if (microMovement && state.predictedProgress > 0.0F) {
            state.stationaryTicks = OccultCamouflageTuning.ARMING_TICKS;
            state.predictedProgress = Math.max(
                    OccultCamouflageTuning.PARTIAL_FLOOR_PROGRESS,
                    state.predictedProgress - OccultCamouflageTuning.PARTIAL_DECAY_PER_TICK);
        } else {
            state.stationaryTicks = 0;
            state.predictedProgress = 0.0F;
        }

        state.lastPosition = currentPosition;
        reconcileWithAuthoritative(state);
    }

    private static void reconcileWithAuthoritative(ClientCloakState state) {
        if (!state.hasAuthoritativeProgress) {
            return;
        }

        float difference = Math.abs(state.predictedProgress - state.targetProgress);
        float factor = difference > OccultCamouflageTuning.NETWORK_RECONCILE_THRESHOLD
                ? OccultCamouflageTuning.LOCAL_HARD_RECONCILE_FACTOR
                : OccultCamouflageTuning.LOCAL_SOFT_RECONCILE_FACTOR;
        state.predictedProgress = Mth.lerp(factor, state.predictedProgress, state.targetProgress);
    }

    private static void tickRenderProgress(Minecraft minecraft) {
        LocalPlayer localPlayer = minecraft.player;
        UUID localPlayerId = localPlayer == null ? null : localPlayer.getUUID();

        for (Map.Entry<UUID, ClientCloakState> entry : CLOAK_STATES.entrySet()) {
            ClientCloakState state = entry.getValue();
            state.previousRenderProgress = state.renderProgress;
            float desiredProgress = entry.getKey().equals(localPlayerId) ? state.predictedProgress : state.targetProgress;
            float lerpFactor = desiredProgress > state.renderProgress
                    ? OccultCamouflageTuning.RENDER_LERP_IN_PER_TICK
                    : OccultCamouflageTuning.RENDER_LERP_OUT_PER_TICK;
            state.renderProgress = Mth.lerp(lerpFactor, state.renderProgress, desiredProgress);
            if (Math.abs(state.renderProgress - desiredProgress) < 0.001F) {
                state.renderProgress = desiredProgress;
            }
        }
    }

    private static void cleanupStateCache(Minecraft minecraft) {
        if (minecraft.level == null) {
            CLOAK_STATES.clear();
            return;
        }

        Set<UUID> activePlayers = new HashSet<>();
        minecraft.level.players().forEach(player -> activePlayers.add(player.getUUID()));
        CLOAK_STATES.entrySet().removeIf(entry -> {
            ClientCloakState state = entry.getValue();
            if (activePlayers.contains(entry.getKey())) {
                state.unseenTicks = 0;
                return false;
            }

            state.unseenTicks++;
            return state.unseenTicks > STALE_STATE_TICKS;
        });
    }

    private static float getInterpolatedRenderProgress(AbstractClientPlayer player, float partialTick) {
        ClientCloakState state = CLOAK_STATES.get(player.getUUID());
        if (state == null) {
            return 0.0F;
        }

        return Mth.lerp(partialTick, state.previousRenderProgress, state.renderProgress);
    }

    private static void renderCamouflagedPlayer(
            AbstractClientPlayer player,
            PlayerRenderer renderer,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            float partialTick,
            float normalWeight,
            float cloakWeight) {
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        preparePlayerModel(model, player);

        poseStack.pushPose();
        PlayerAnimationState animationState = applyPlayerRenderTransforms(player, poseStack, partialTick, model);
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        ResourceLocation skinTexture = player.getSkin().texture();

        if (normalWeight > MIN_RENDER_PROGRESS) {
            renderNormalPlayerPass(model, poseStack, buffer, packedLight, overlay, skinTexture, normalWeight);
            OccultArmorFadeRenderer.renderArmor(
                    player,
                    model,
                    poseStack,
                    buffer,
                    packedLight,
                    animationState,
                    normalWeight);
        }

        if (cloakWeight > MIN_RENDER_PROGRESS) {
            renderCloakPasses(model, poseStack, buffer, packedLight, overlay, player.tickCount + partialTick, cloakWeight);
        }
        poseStack.popPose();
    }

    private static void renderCamouflagedArm(
            AbstractClientPlayer player,
            PlayerRenderer renderer,
            HumanoidArm arm,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            float partialTick,
            float normalWeight,
            float cloakWeight) {
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        preparePlayerModel(model, player);
        model.attackTime = 0.0F;
        model.crouching = false;
        model.swimAmount = 0.0F;
        model.setupAnim(player, 0.0F, 0.0F, player.tickCount + partialTick, 0.0F, 0.0F);

        ModelPart armPart = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        ModelPart sleevePart = arm == HumanoidArm.RIGHT ? model.rightSleeve : model.leftSleeve;
        armPart.xRot = 0.0F;
        sleevePart.xRot = 0.0F;
        int overlay = OverlayTexture.NO_OVERLAY;
        ResourceLocation skinTexture = player.getSkin().texture();

        poseStack.pushPose();
        if (normalWeight > MIN_RENDER_PROGRESS) {
            renderNormalArmPass(armPart, sleevePart, poseStack, buffer, packedLight, overlay, skinTexture, normalWeight);
        }

        if (cloakWeight > MIN_RENDER_PROGRESS) {
            renderArmCloakPasses(
                    armPart,
                    poseStack,
                    buffer,
                    packedLight,
                    overlay,
                    player.tickCount + partialTick,
                    cloakWeight);
        }
        poseStack.popPose();
    }

    private static void renderNormalPlayerPass(
            PlayerModel<AbstractClientPlayer> model,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int overlay,
            ResourceLocation skinTexture,
            float alpha) {
        VertexConsumer normalBuffer = buffer.getBuffer(RenderType.entityTranslucent(skinTexture));
        model.renderToBuffer(poseStack, normalBuffer, packedLight, overlay, argb(alpha, 1.0F, 1.0F, 1.0F));
    }

    private static void renderNormalArmPass(
            ModelPart armPart,
            ModelPart sleevePart,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int overlay,
            ResourceLocation skinTexture,
            float alpha) {
        int armColor = argb(alpha, 1.0F, 1.0F, 1.0F);
        VertexConsumer normalBuffer = buffer.getBuffer(RenderType.entityTranslucent(skinTexture));
        armPart.render(poseStack, normalBuffer, packedLight, overlay, armColor);

        boolean originalSleeveVisibility = sleevePart.visible;
        sleevePart.visible = true;
        sleevePart.render(poseStack, normalBuffer, packedLight, overlay, armColor);
        sleevePart.visible = originalSleeveVisibility;
    }

    private static void renderCloakPasses(
            PlayerModel<AbstractClientPlayer> model,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int overlay,
            float animationTick,
            float progress) {
        int interiorColor = argb(progress * OccultCamouflageTuning.INTERIOR_ALPHA_SCALE, INTERIOR_RED, INTERIOR_GREEN, INTERIOR_BLUE);
        int edgeColor = argb(progress * OccultCamouflageTuning.EDGE_ALPHA_SCALE, EDGE_RED, EDGE_GREEN, EDGE_BLUE);
        int shimmerColor = argb(progress * OccultCamouflageTuning.SHIMMER_ALPHA_SCALE, SHIMMER_RED, SHIMMER_GREEN, SHIMMER_BLUE);

        if (OccultCamouflageRenderTypes.areShadersReady()) {
            VertexConsumer interiorBuffer = buffer.getBuffer(OccultCamouflageRenderTypes.interior());
            model.renderToBuffer(poseStack, interiorBuffer, packedLight, overlay, interiorColor);

            poseStack.pushPose();
            poseStack.scale(
                    OccultCamouflageTuning.EDGE_SHELL_SCALE,
                    OccultCamouflageTuning.EDGE_SHELL_SCALE,
                    OccultCamouflageTuning.EDGE_SHELL_SCALE);
            VertexConsumer edgeBuffer = buffer.getBuffer(OccultCamouflageRenderTypes.edge());
            model.renderToBuffer(poseStack, edgeBuffer, packedLight, overlay, edgeColor);
            poseStack.popPose();

            VertexConsumer shimmerBuffer = buffer.getBuffer(OccultCamouflageRenderTypes.distortion());
            model.renderToBuffer(poseStack, shimmerBuffer, packedLight, overlay, shimmerColor);
            return;
        }

        VertexConsumer interiorBuffer = buffer.getBuffer(RenderType.entityTranslucent(OccultCamouflageRenderTypes.MASK_TEXTURE));
        model.renderToBuffer(poseStack, interiorBuffer, packedLight, overlay, interiorColor);

        poseStack.pushPose();
        poseStack.scale(
                OccultCamouflageTuning.EDGE_SHELL_SCALE,
                OccultCamouflageTuning.EDGE_SHELL_SCALE,
                OccultCamouflageTuning.EDGE_SHELL_SCALE);
        VertexConsumer edgeBuffer = buffer.getBuffer(RenderType.entityTranslucentEmissive(OccultCamouflageRenderTypes.MASK_TEXTURE));
        model.renderToBuffer(poseStack, edgeBuffer, packedLight, overlay, edgeColor);
        poseStack.popPose();

        VertexConsumer shimmerBuffer = buffer.getBuffer(RenderType.energySwirl(
                OccultCamouflageRenderTypes.NOISE_TEXTURE,
                animationTick * OccultCamouflageTuning.SHIMMER_U_SPEED % 1.0F,
                animationTick * OccultCamouflageTuning.SHIMMER_V_SPEED % 1.0F));
        model.renderToBuffer(poseStack, shimmerBuffer, packedLight, overlay, shimmerColor);
    }

    private static void renderArmCloakPasses(
            ModelPart armPart,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int overlay,
            float animationTick,
            float progress) {
        int interiorColor = argb(progress * OccultCamouflageTuning.INTERIOR_ALPHA_SCALE, INTERIOR_RED, INTERIOR_GREEN, INTERIOR_BLUE);
        int edgeColor = argb(progress * OccultCamouflageTuning.EDGE_ALPHA_SCALE, EDGE_RED, EDGE_GREEN, EDGE_BLUE);
        int shimmerColor = argb(progress * OccultCamouflageTuning.SHIMMER_ALPHA_SCALE, SHIMMER_RED, SHIMMER_GREEN, SHIMMER_BLUE);

        if (OccultCamouflageRenderTypes.areShadersReady()) {
            armPart.render(
                    poseStack,
                    buffer.getBuffer(OccultCamouflageRenderTypes.interior()),
                    packedLight,
                    overlay,
                    interiorColor);

            poseStack.pushPose();
            poseStack.scale(
                    OccultCamouflageTuning.EDGE_SHELL_SCALE,
                    OccultCamouflageTuning.EDGE_SHELL_SCALE,
                    OccultCamouflageTuning.EDGE_SHELL_SCALE);
            armPart.render(
                    poseStack,
                    buffer.getBuffer(OccultCamouflageRenderTypes.edge()),
                    packedLight,
                    overlay,
                    edgeColor);
            poseStack.popPose();

            armPart.render(
                    poseStack,
                    buffer.getBuffer(OccultCamouflageRenderTypes.distortion()),
                    packedLight,
                    overlay,
                    shimmerColor);
            return;
        }

        armPart.render(
                poseStack,
                buffer.getBuffer(RenderType.entityTranslucent(OccultCamouflageRenderTypes.MASK_TEXTURE)),
                packedLight,
                overlay,
                interiorColor);

        poseStack.pushPose();
        poseStack.scale(
                OccultCamouflageTuning.EDGE_SHELL_SCALE,
                OccultCamouflageTuning.EDGE_SHELL_SCALE,
                OccultCamouflageTuning.EDGE_SHELL_SCALE);
        armPart.render(
                poseStack,
                buffer.getBuffer(RenderType.entityTranslucentEmissive(OccultCamouflageRenderTypes.MASK_TEXTURE)),
                packedLight,
                overlay,
                edgeColor);
        poseStack.popPose();

        armPart.render(
                poseStack,
                buffer.getBuffer(RenderType.energySwirl(
                        OccultCamouflageRenderTypes.NOISE_TEXTURE,
                        animationTick * OccultCamouflageTuning.SHIMMER_U_SPEED % 1.0F,
                        animationTick * OccultCamouflageTuning.SHIMMER_V_SPEED % 1.0F)),
                packedLight,
                overlay,
                shimmerColor);
    }

    private static boolean hasFullOccultSet(AbstractClientPlayer player) {
        return player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(ModItems.OCCULT_HELMET.get())
                && player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(ModItems.OCCULT_CHESTPLATE.get())
                && player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).is(ModItems.OCCULT_LEGGINGS.get())
                && player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).is(ModItems.OCCULT_BOOTS.get());
    }

    private static void preparePlayerModel(PlayerModel<AbstractClientPlayer> model, AbstractClientPlayer player) {
        if (player.isSpectator()) {
            model.setAllVisible(false);
            return;
        }

        model.setAllVisible(true);
        model.hat.visible = false;
        model.jacket.visible = false;
        model.leftPants.visible = false;
        model.rightPants.visible = false;
        model.leftSleeve.visible = false;
        model.rightSleeve.visible = false;
        model.crouching = player.isCrouching();
        model.riding = player.isPassenger() && player.getVehicle() != null && player.getVehicle().shouldRiderSit();
        model.young = player.isBaby();
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

    private static PlayerAnimationState applyPlayerRenderTransforms(
            AbstractClientPlayer player,
            PoseStack poseStack,
            float partialTick,
            PlayerModel<AbstractClientPlayer> model) {
        boolean shouldSit = model.riding;
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
        return new PlayerAnimationState(walkPosition, walkSpeed, partialTick, bob, headBodyDiff, xRot);
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

    private static float getCloakWeight(float progress) {
        float clampedProgress = Mth.clamp(progress, 0.0F, 1.0F);
        return clampedProgress * clampedProgress * (3.0F - 2.0F * clampedProgress);
    }

    private static float getNormalWeight(float cloakWeight) {
        return 1.0F - Mth.clamp(cloakWeight, 0.0F, 1.0F);
    }

    private static int argb(float alpha, float red, float green, float blue) {
        return ((Mth.clamp((int)(alpha * 255.0F), 0, 255) & 255) << 24)
                | ((Mth.clamp((int)(red * 255.0F), 0, 255) & 255) << 16)
                | ((Mth.clamp((int)(green * 255.0F), 0, 255) & 255) << 8)
                | (Mth.clamp((int)(blue * 255.0F), 0, 255) & 255);
    }

    private static final class ClientCloakState {
        private boolean initialized;
        private boolean hasAuthoritativeProgress;
        private Vec3 lastPosition = Vec3.ZERO;
        private int stationaryTicks;
        private int unseenTicks;
        private float targetProgress;
        private float predictedProgress;
        private float previousRenderProgress;
        private float renderProgress;
    }

    record PlayerAnimationState(
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
    }
}
