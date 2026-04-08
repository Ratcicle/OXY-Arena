package com.example.oxyarena.client;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.example.oxyarena.Config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class BridgingAssistController {
    private static final double UPWARD_LOOK_THRESHOLD = 0.35D;
    private static final double DOWNWARD_LOOK_THRESHOLD = -0.35D;
    private static final double DOWNWARD_GROUND_SUPPRESS_THRESHOLD = -0.6D;
    private static final List<Direction> HORIZONTAL_DIRECTIONS = Arrays.asList(
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST);

    private static AssistTarget currentAssistTarget;
    private static InteractionHand currentAssistHand;

    private BridgingAssistController() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(BridgingAssistController::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(BridgingAssistController::onInteractionKeyMappingTriggered);
        NeoForge.EVENT_BUS.addListener(BridgingAssistController::onRenderLevelStage);
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!Config.bridgingAssistEnabled()) {
            clearAssistTarget();
            return;
        }

        LocalPlayer player = minecraft.player;
        MultiPlayerGameMode gameMode = minecraft.gameMode;
        if (player == null
                || minecraft.level == null
                || gameMode == null
                || minecraft.screen != null
                || player.isSpectator()
                || player.isHandsBusy()
                || gameMode.isDestroying()
                || minecraft.hitResult == null
                || minecraft.hitResult.getType() != HitResult.Type.MISS) {
            clearAssistTarget();
            return;
        }

        InteractionHand hand = resolveAssistHand(player);
        if (hand == null) {
            clearAssistTarget();
            return;
        }

        AssistTarget target = resolveAssistTarget(player, hand);
        if (target == null) {
            clearAssistTarget();
            return;
        }

        currentAssistTarget = target;
        currentAssistHand = hand;
    }

    private static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || !Config.bridgingAssistEnabled() || currentAssistTarget == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null
                || minecraft.hitResult == null
                || minecraft.hitResult.getType() != HitResult.Type.MISS
                || minecraft.player == null
                || minecraft.gameMode == null
                || minecraft.player.isSpectator()
                || minecraft.player.isHandsBusy()
                || minecraft.gameMode.isDestroying()
                || event.getHand() != currentAssistHand) {
            return;
        }

        ItemStack stack = minecraft.player.getItemInHand(event.getHand());
        if (!isSupportedBlockItem(stack)) {
            return;
        }

        BlockHitResult hitResult = currentAssistTarget.toHitResult();
        int previousCount = stack.getCount();
        InteractionResult result = minecraft.gameMode.useItemOn(minecraft.player, event.getHand(), hitResult);
        if (!result.consumesAction()) {
            return;
        }

        if (result.shouldSwing()) {
            minecraft.player.swing(event.getHand());
            if (!stack.isEmpty() && (stack.getCount() != previousCount || minecraft.gameMode.hasInfiniteItems())) {
                minecraft.gameRenderer.itemInHandRenderer.itemUsed(event.getHand());
            }
        }

        event.setSwingHand(false);
        event.setCanceled(true);
        clearAssistTarget();
    }

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || !Config.bridgingAssistEnabled()
                || !Config.bridgingAssistShowOutline()
                || currentAssistTarget == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        Vec3 cameraPos = event.getCamera().getPosition();
        BlockPos placePos = currentAssistTarget.placePos;
        AABB box = new AABB(placePos).move(-cameraPos.x, -cameraPos.y, -cameraPos.z).inflate(0.002D);
        LevelRenderer.renderLineBox(
                event.getPoseStack(),
                minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines()),
                box,
                0.35F,
                0.85F,
                1.0F,
                0.45F);
        minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    private static InteractionHand resolveAssistHand(LocalPlayer player) {
        if (isSupportedBlockItem(player.getMainHandItem())) {
            return InteractionHand.MAIN_HAND;
        }

        if (isSupportedBlockItem(player.getOffhandItem())) {
            return InteractionHand.OFF_HAND;
        }

        return null;
    }

    private static AssistTarget resolveAssistTarget(LocalPlayer player, InteractionHand hand) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = start.add(look.scale(player.blockInteractionRange()));
        ItemStack stack = player.getItemInHand(hand);

        // Looking clearly upward should prefer tower-building instead of snapping to a nearby side face.
        if (Config.bridgingAssistVerticalEnabled() && look.y >= UPWARD_LOOK_THRESHOLD) {
            return traverseForTarget(
                    start,
                    end,
                    new TraversalContext(player, hand, stack, new Direction[] {Direction.UP}));
        }

        if (Config.bridgingAssistVerticalEnabled() && look.y <= DOWNWARD_LOOK_THRESHOLD) {
            AssistTarget downwardTarget = traverseForTarget(
                    start,
                    end,
                    new TraversalContext(player, hand, stack, new Direction[] {Direction.DOWN}));
            if (isUsefulDownwardTarget(player, downwardTarget)) {
                return downwardTarget;
            }
        }

        AssistTarget horizontalTarget = traverseForTarget(
                start,
                end,
                new TraversalContext(player, hand, stack, getHorizontalPriority(player, look)));
        if (shouldSuppressGroundAssist(player, look, horizontalTarget)) {
            return null;
        }
        if (horizontalTarget != null || !Config.bridgingAssistVerticalEnabled()) {
            return horizontalTarget;
        }

        return traverseForTarget(
                start,
                end,
                new TraversalContext(player, hand, stack, getVerticalPriority(player, look)));
    }

    private static AssistTarget traverseForTarget(Vec3 start, Vec3 end, TraversalContext context) {
        return BlockGetter.traverseBlocks(
                start,
                end,
                context,
                (traversalContext, pos) -> findAssistTarget(traversalContext, pos),
                traversalContext -> null);
    }

    private static AssistTarget findAssistTarget(TraversalContext context, BlockPos pos) {
        if (!context.player.level().getWorldBorder().isWithinBounds(pos)) {
            return null;
        }

        BlockState candidateState = context.player.level().getBlockState(pos);
        if (!candidateState.isAir()) {
            return null;
        }

        if (new AABB(pos).intersects(context.player.getBoundingBox())) {
            return null;
        }

        for (Direction face : context.priority) {
            BlockPos supportPos = pos.relative(face.getOpposite());
            if (!context.player.canInteractWithBlock(supportPos, 0.0D) || !isValidSupport(context.player, supportPos)) {
                continue;
            }

            AssistTarget target = new AssistTarget(pos.immutable(), supportPos.immutable(), face);
            if (canPlaceAt(context.player, context.hand, context.stack, target)) {
                return target;
            }
        }

        return null;
    }

    private static boolean canPlaceAt(LocalPlayer player, InteractionHand hand, ItemStack stack, AssistTarget target) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }

        BlockPlaceContext context = new BlockPlaceContext(new UseOnContext(player, hand, target.toHitResult()));
        if (!context.canPlace()) {
            return false;
        }

        BlockState placementState = blockItem.getBlock().getStateForPlacement(context);
        if (placementState == null) {
            return false;
        }

        return placementState.canSurvive(context.getLevel(), context.getClickedPos())
                && context.getLevel().isUnobstructed(placementState, context.getClickedPos(), CollisionContext.of(player));
    }

    private static boolean isValidSupport(LocalPlayer player, BlockPos pos) {
        BlockState state = player.level().getBlockState(pos);
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && !state.canBeReplaced()
                && !state.getCollisionShape(player.level(), pos).isEmpty();
    }

    private static boolean isSupportedBlockItem(ItemStack stack) {
        return !stack.isEmpty()
                && stack.isItemEnabled(Minecraft.getInstance().level.enabledFeatures())
                && stack.getItem() instanceof BlockItem;
    }

    private static Direction[] getHorizontalPriority(LocalPlayer player, Vec3 look) {
        double horizontalLengthSqr = look.x * look.x + look.z * look.z;
        Vec3 basis = horizontalLengthSqr < 1.0E-6D
                ? Vec3.atLowerCornerOf(player.getDirection().getNormal())
                : new Vec3(look.x, 0.0D, look.z).normalize();

        return HORIZONTAL_DIRECTIONS.stream()
                .sorted(Comparator.comparingDouble((Direction direction) -> dotHorizontal(direction, basis)).reversed())
                .toArray(Direction[]::new);
    }

    private static Direction[] getVerticalPriority(LocalPlayer player, Vec3 look) {
        Direction vertical = look.y >= 0.0D ? Direction.UP : Direction.DOWN;
        Direction[] horizontal = getHorizontalPriority(player, look);
        return new Direction[] {
                vertical,
                horizontal[0],
                horizontal[1],
                horizontal[2],
                horizontal[3],
                vertical.getOpposite()
        };
    }

    private static double dotHorizontal(Direction direction, Vec3 basis) {
        return direction.getStepX() * basis.x + direction.getStepZ() * basis.z;
    }

    private static boolean shouldSuppressGroundAssist(LocalPlayer player, Vec3 look, AssistTarget target) {
        if (target == null
                || !player.onGround()
                || look.y > DOWNWARD_GROUND_SUPPRESS_THRESHOLD
                || target.face.getAxis().isVertical()
                || !isValidSupport(player, target.placePos.below())) {
            return false;
        }

        double dx = Math.abs((target.placePos.getX() + 0.5D) - player.getX());
        double dz = Math.abs((target.placePos.getZ() + 0.5D) - player.getZ());
        return Math.max(dx, dz) <= 1.25D;
    }

    private static boolean isUsefulDownwardTarget(LocalPlayer player, AssistTarget target) {
        if (target == null || target.face != Direction.DOWN) {
            return false;
        }

        double dx = Math.abs((target.placePos.getX() + 0.5D) - player.getX());
        double dz = Math.abs((target.placePos.getZ() + 0.5D) - player.getZ());
        return target.placePos.getY() < player.blockPosition().getY() && Math.max(dx, dz) <= 2.0D;
    }

    private static void clearAssistTarget() {
        currentAssistTarget = null;
        currentAssistHand = null;
    }

    private record TraversalContext(LocalPlayer player, InteractionHand hand, ItemStack stack, Direction[] priority) {
    }

    private record AssistTarget(BlockPos placePos, BlockPos supportPos, Direction face) {
        private BlockHitResult toHitResult() {
            Vec3 clickLocation = Vec3.atCenterOf(supportPos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5D));
            return new BlockHitResult(clickLocation, face, supportPos, false);
        }
    }
}
