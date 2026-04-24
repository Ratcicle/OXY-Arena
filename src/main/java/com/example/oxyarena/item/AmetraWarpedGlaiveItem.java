package com.example.oxyarena.item;

import java.util.List;

import com.example.oxyarena.event.gameplay.AmetraWarpedGlaiveEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class AmetraWarpedGlaiveItem extends SwordItem {
    public AmetraWarpedGlaiveItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }

        boolean activated = player.isShiftKeyDown()
                ? AmetraWarpedGlaiveEvents.activateMarkedTeleport(serverPlayer)
                : AmetraWarpedGlaiveEvents.createRift(serverPlayer);
        if (!activated) {
            return InteractionResultHolder.fail(stack);
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.ametra_warped_glaive.rift")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.ametra_warped_glaive.mark")
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.ametra_warped_glaive.teleport")
                .withStyle(ChatFormatting.DARK_PURPLE));
    }
}
