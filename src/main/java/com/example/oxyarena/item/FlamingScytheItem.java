package com.example.oxyarena.item;

import java.util.List;

import com.example.oxyarena.event.ModGameEvents;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class FlamingScytheItem extends SwordItem {
    private static final int ACTIVE_DURATION_TICKS = 200;
    private static final float ACTIVE_BURN_SECONDS = ACTIVE_DURATION_TICKS / 20.0F;
    private static final int COOLDOWN_TICKS = 500;

    public FlamingScytheItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(itemStack);
        }

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(itemStack);
        }

        if (!level.isClientSide) {
            ModGameEvents.activateFlamingScythe(player, ACTIVE_DURATION_TICKS);
            player.igniteForSeconds(ACTIVE_BURN_SECONDS);
            level.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.FLINTANDSTEEL_USE,
                    SoundSource.PLAYERS,
                    0.8F,
                    0.95F);
        }

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.flaming_scythe.passive")
                .withStyle(ChatFormatting.RED));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.flaming_scythe.ability")
                .withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.flaming_scythe.cooldown")
                .withStyle(ChatFormatting.GRAY));
    }
}
