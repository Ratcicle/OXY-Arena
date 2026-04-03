package com.example.oxyarena.item;

import java.util.List;

import com.example.oxyarena.registry.ModMobEffects;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class LifehuntScytheItem extends SwordItem {
    private static final float PASSIVE_HEAL_AMOUNT = 2.0F;
    private static final float ACTIVE_HEAL_AMOUNT = 6.0F;
    private static final int ACTIVE_DURATION_TICKS = 160;
    private static final int COOLDOWN_TICKS = 1200;

    public LifehuntScytheItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(itemStack);
        }

        if (!level.isClientSide) {
            player.addEffect(new MobEffectInstance(
                    ModMobEffects.LIFEHUNT_BLOODLUST,
                    ACTIVE_DURATION_TICKS,
                    0));
        }

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.postHurtEnemy(stack, target, attacker);

        if (attacker.level().isClientSide || !(attacker instanceof Player player)) {
            return;
        }

        if (player.getAttackStrengthScale(0.5F) < 1.0F) {
            return;
        }

        float healAmount = player.hasEffect(ModMobEffects.LIFEHUNT_BLOODLUST)
                    ? ACTIVE_HEAL_AMOUNT
                    : PASSIVE_HEAL_AMOUNT;
        player.heal(healAmount);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.lifehunt_scythe.passive")
                .withStyle(ChatFormatting.DARK_RED));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.lifehunt_scythe.ability")
                .withStyle(ChatFormatting.RED));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.lifehunt_scythe.cooldown")
                .withStyle(ChatFormatting.GRAY));
    }
}
