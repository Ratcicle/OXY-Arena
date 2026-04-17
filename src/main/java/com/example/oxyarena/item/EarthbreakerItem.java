package com.example.oxyarena.item;

import java.util.List;

import com.example.oxyarena.event.EarthbreakerCrackHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public class EarthbreakerItem extends SwordItem {
    private static final int USE_DURATION_TICKS = 72000;

    public EarthbreakerItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION_TICKS;
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

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeLeft) {
        if (!(livingEntity instanceof Player player)
                || player.getUsedItemHand() != InteractionHand.MAIN_HAND
                || player.getCooldowns().isOnCooldown(this)) {
            return;
        }

        int chargeTicks = this.getUseDuration(stack, livingEntity) - timeLeft;
        if (chargeTicks < EarthbreakerCrackHelper.minimumChargeTicks()) {
            return;
        }

        if (!level.isClientSide) {
            EarthbreakerCrackHelper.activate(player, chargeTicks);
        }

        player.swing(InteractionHand.MAIN_HAND, true);
        player.getCooldowns().addCooldown(this, EarthbreakerCrackHelper.cooldownTicks());
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.earthbreaker.ability")
                .withStyle(ChatFormatting.DARK_GREEN));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.earthbreaker.detail")
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.earthbreaker.cooldown")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
