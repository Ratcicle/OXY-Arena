package com.example.oxyarena.item;

import java.util.List;

import com.example.oxyarena.event.ModGameEvents;

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

public class GhostSaberItem extends SwordItem {
    public static final int ABILITY_COOLDOWN_TICKS = 240;

    public GhostSaberItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        if (level.isClientSide) {
            return InteractionResultHolder.consume(stack);
        }

        if (!(player instanceof ServerPlayer serverPlayer) || !ModGameEvents.activateGhostSaber(serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }

        player.getCooldowns().addCooldown(this, ABILITY_COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.ghost_saber.ability")
                .withStyle(ChatFormatting.WHITE));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.ghost_saber.detail")
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.ghost_saber.reset")
                .withStyle(ChatFormatting.AQUA));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.ghost_saber.cooldown")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
