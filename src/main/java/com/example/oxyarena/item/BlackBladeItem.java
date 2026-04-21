package com.example.oxyarena.item;

import java.util.List;

import com.example.oxyarena.entity.projectile.BlackBladeProjectile;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

public class BlackBladeItem extends SwordItem {
    private static final int USE_DURATION_TICKS = 72000;
    private static final int MIN_CHARGE_TICKS = 20;
    private static final float SHOOT_POWER = 2.8F;
    private static final float SHOOT_INACCURACY = 0.0F;

    public BlackBladeItem(Tier tier, Properties properties) {
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

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeLeft) {
        if (!(livingEntity instanceof Player player)
                || player.getUsedItemHand() != InteractionHand.MAIN_HAND
                || stack.isEmpty()) {
            return;
        }

        int chargeTicks = this.getUseDuration(stack, livingEntity) - timeLeft;
        if (chargeTicks < MIN_CHARGE_TICKS) {
            return;
        }

        if (level instanceof ServerLevel serverLevel) {
            BlackBladeProjectile projectile = new BlackBladeProjectile(serverLevel, player, stack);
            projectile.shootFromRotation(
                    player,
                    player.getXRot(),
                    player.getYRot(),
                    0.0F,
                    SHOOT_POWER,
                    SHOOT_INACCURACY);
            serverLevel.addFreshEntity(projectile);
            serverLevel.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.WITHER_SHOOT,
                    SoundSource.PLAYERS,
                    0.55F,
                    1.45F);
        }

        player.swing(InteractionHand.MAIN_HAND, true);
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.black_blade.passive")
                .withStyle(ChatFormatting.DARK_PURPLE));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.black_blade.ability")
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.black_blade.detail")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
