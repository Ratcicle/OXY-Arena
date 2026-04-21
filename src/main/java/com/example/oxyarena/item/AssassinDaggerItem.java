package com.example.oxyarena.item;

import java.util.List;

import com.example.oxyarena.event.ModGameEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

public final class AssassinDaggerItem extends SwordItem {
    private static final double ATTACK_DAMAGE_BONUS = 3.0D;
    private static final double ATTACK_SPEED_BONUS = -2.0D;
    private static final int ABILITY_COOLDOWN_TICKS = 100;

    public AssassinDaggerItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
                .add(
                        Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(
                                BASE_ATTACK_DAMAGE_ID,
                                ATTACK_DAMAGE_BONUS,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(
                        Attributes.ATTACK_SPEED,
                        new AttributeModifier(
                                BASE_ATTACK_SPEED_ID,
                                ATTACK_SPEED_BONUS,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
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

        if (!level.isClientSide && ModGameEvents.activateAssassinDagger(player) > 0) {
            player.getCooldowns().addCooldown(this, ABILITY_COOLDOWN_TICKS);
            player.awardStat(Stats.ITEM_USED.get(this));
            return InteractionResultHolder.success(itemStack);
        }

        return InteractionResultHolder.fail(itemStack);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.assassin_dagger.passive")
                .withStyle(ChatFormatting.RED));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.assassin_dagger.ability")
                .withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.assassin_dagger.cooldown")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.assassin_dagger.detail")
                .withStyle(ChatFormatting.GRAY));
    }
}
