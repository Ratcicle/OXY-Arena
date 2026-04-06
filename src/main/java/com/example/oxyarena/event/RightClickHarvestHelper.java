package com.example.oxyarena.event;

import java.util.ArrayList;
import java.util.List;

import com.example.oxyarena.Config;
import com.example.oxyarena.OXYArena;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class RightClickHarvestHelper {
    public static final TagKey<Block> BLACKLIST = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "right_click_harvest_blacklist"));

    private RightClickHarvestHelper() {
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        Player player = event.getEntity();
        if (player.isSpectator() || player.isShiftKeyDown() || !Config.rightClickHarvestEnabled()) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (state.is(BLACKLIST)) {
            return;
        }

        boolean areaHarvest = player.getMainHandItem().canPerformAction(ItemAbilities.HOE_TILL);
        List<BlockPos> harvestTargets = getHarvestTargets(pos, state, areaHarvest);
        boolean harvestedAny = false;
        for (BlockPos targetPos : harvestTargets) {
            BlockState targetState = level.getBlockState(targetPos);
            if (targetState.is(BLACKLIST)) {
                continue;
            }

            HarvestPlan plan = HarvestPlan.tryCreate(level, targetPos, targetState);
            if (plan == null) {
                continue;
            }

            if (tryHarvest(level, targetPos, targetState, player, plan)) {
                harvestedAny = true;
                if (areaHarvest) {
                    damageHarvestHoe(player);
                    if (!player.getMainHandItem().canPerformAction(ItemAbilities.HOE_TILL)) {
                        break;
                    }
                }
            }
        }

        if (!harvestedAny) {
            return;
        }

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    private static void damageHarvestHoe(Player player) {
        if (player.level() instanceof ServerLevel && !player.getMainHandItem().isEmpty()) {
            player.getMainHandItem().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
    }

    private static List<BlockPos> getHarvestTargets(BlockPos centerPos, BlockState centerState, boolean areaHarvest) {
        List<BlockPos> targets = new ArrayList<>();
        targets.add(centerPos);
        if (!areaHarvest) {
            return targets;
        }

        if (centerState.getBlock() instanceof CocoaBlock) {
            Direction facing = centerState.getValue(CocoaBlock.FACING);
            if (facing.getAxis() == Direction.Axis.X) {
                addPlaneTargets(targets, centerPos, 0, 1, 1);
            } else {
                addPlaneTargets(targets, centerPos, 1, 1, 0);
            }
            return targets;
        }

        addPlaneTargets(targets, centerPos, 1, 0, 1);
        return targets;
    }

    private static void addPlaneTargets(List<BlockPos> targets, BlockPos centerPos, int xRadius, int yRadius, int zRadius) {
        for (int xOffset = -xRadius; xOffset <= xRadius; xOffset++) {
            for (int yOffset = -yRadius; yOffset <= yRadius; yOffset++) {
                for (int zOffset = -zRadius; zOffset <= zRadius; zOffset++) {
                    if (xOffset == 0 && yOffset == 0 && zOffset == 0) {
                        continue;
                    }

                    targets.add(centerPos.offset(xOffset, yOffset, zOffset));
                }
            }
        }
    }

    private static boolean tryHarvest(ServerLevel level, BlockPos pos, BlockState state, Player player, HarvestPlan plan) {
        BlockEvent.BreakEvent breakEvent = new BlockEvent.BreakEvent(level, pos, state, player);
        if (NeoForge.EVENT_BUS.post(breakEvent).isCanceled()) {
            return false;
        }

        ItemStack tool = player.getMainHandItem();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, pos, blockEntity, player, tool));
        consumeReplantItem(drops, plan.replantItem());

        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        BlockState placedAgainst = getPlacedAgainstState(level, pos, state);
        if (!level.setBlock(pos, plan.replantedState(), 3)) {
            return false;
        }

        BlockEvent.EntityPlaceEvent placeEvent = new BlockEvent.EntityPlaceEvent(snapshot, placedAgainst, player);
        if (NeoForge.EVENT_BUS.post(placeEvent).isCanceled()) {
            snapshot.restore();
            return false;
        }

        dropHarvestLoot(level, pos, state, blockEntity, player, tool, drops);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, plan.replantedState()));
        return true;
    }

    private static BlockState getPlacedAgainstState(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos supportPos;
        if (state.getBlock() instanceof CocoaBlock) {
            supportPos = pos.relative(state.getValue(CocoaBlock.FACING));
        } else {
            supportPos = pos.below();
        }

        return level.getBlockState(supportPos);
    }

    private static void dropHarvestLoot(ServerLevel level, BlockPos pos, BlockState state, BlockEntity blockEntity,
            Player player, ItemStack tool, List<ItemStack> drops) {
        List<ItemEntity> capturedDrops = createDropEntities(level, pos, drops);
        CommonHooks.handleBlockDrops(level, pos, state, blockEntity, capturedDrops, player, tool);
    }

    private static List<ItemEntity> createDropEntities(ServerLevel level, BlockPos pos, List<ItemStack> drops) {
        List<ItemEntity> entities = new ArrayList<>();
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            return entities;
        }

        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }

            double itemHeight = (double) EntityType.ITEM.getHeight() / 2.0D;
            double x = (double) pos.getX() + 0.5D + Mth.nextDouble(level.random, -0.25D, 0.25D);
            double y = (double) pos.getY() + 0.5D + Mth.nextDouble(level.random, -0.25D, 0.25D) - itemHeight;
            double z = (double) pos.getZ() + 0.5D + Mth.nextDouble(level.random, -0.25D, 0.25D);
            ItemEntity entity = new ItemEntity(level, x, y, z, drop);
            entity.setDefaultPickUpDelay();
            entities.add(entity);
        }

        return entities;
    }

    private static void consumeReplantItem(List<ItemStack> drops, ItemStack replantItem) {
        if (replantItem.isEmpty()) {
            return;
        }

        int remainingToConsume = 1;
        for (ItemStack drop : drops) {
            if (!ItemStack.isSameItemSameComponents(drop, replantItem)) {
                continue;
            }

            int consumed = Math.min(remainingToConsume, drop.getCount());
            drop.shrink(consumed);
            remainingToConsume -= consumed;
            if (remainingToConsume <= 0) {
                break;
            }
        }

        drops.removeIf(ItemStack::isEmpty);
    }

    private record HarvestPlan(BlockState replantedState, ItemStack replantItem) {
        private static HarvestPlan tryCreate(ServerLevel level, BlockPos pos, BlockState state) {
            if (state.getBlock() instanceof CropBlock cropBlock && cropBlock.isMaxAge(state)) {
                return new HarvestPlan(cropBlock.getStateForAge(0), cropBlock.getCloneItemStack(level, pos, state));
            }

            if (state.getBlock() instanceof NetherWartBlock && state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE) {
                return new HarvestPlan(state.setValue(NetherWartBlock.AGE, Integer.valueOf(0)), new ItemStack(Items.NETHER_WART));
            }

            if (state.getBlock() instanceof CocoaBlock && state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE) {
                return new HarvestPlan(state.setValue(CocoaBlock.AGE, Integer.valueOf(0)), new ItemStack(Items.COCOA_BEANS));
            }

            return null;
        }
    }
}
