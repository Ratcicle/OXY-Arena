package com.example.oxyarena.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class ChocolateSwordItem extends SwordItem {
    private static final int SPEED_DURATION_TICKS = 100;
    public static final FoodProperties FOOD_PROPERTIES = new FoodProperties.Builder()
            .nutrition(4)
            .saturationModifier(0.5F)
            .alwaysEdible()
            .build();

    public ChocolateSwordItem(Tier tier, Item.Properties properties) {
        super(tier, properties.food(FOOD_PROPERTIES));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        return ItemUtils.startUsingInstantly(level, player, usedHand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        livingEntity.eat(level, stack.copy(), FOOD_PROPERTIES);
        if (!level.isClientSide && livingEntity instanceof Player player) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED,
                    SPEED_DURATION_TICKS,
                    0,
                    false,
                    true,
                    true));
        }
        return stack;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.chocolate_sword.food")
                .withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.chocolate_sword.speed")
                .withStyle(ChatFormatting.GRAY));
    }
}
