package com.example.oxyarena.item;

import java.util.List;

import com.example.oxyarena.event.SoulReaperFireHelper;
import com.example.oxyarena.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public class SoulReaperItem extends SwordItem {
    private static final String ALTERED_TAG = "SoulReaperAltered";

    public SoulReaperItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    public static boolean isAltered(ItemStack stack) {
        if (!stack.is(ModItems.SOUL_REAPER.get())) {
            return false;
        }

        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .getBoolean(ALTERED_TAG);
    }

    public static void setAltered(ItemStack stack, boolean altered) {
        if (!stack.is(ModItems.SOUL_REAPER.get())) {
            return;
        }

        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (altered) {
                tag.putBoolean(ALTERED_TAG, true);
            } else {
                tag.remove(ALTERED_TAG);
            }
        });
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

        boolean wasAltered = isAltered(itemStack);
        setAltered(itemStack, !wasAltered);

        if (!level.isClientSide) {
            SoulReaperFireHelper.activate(player, wasAltered);
        }

        player.getCooldowns().addCooldown(this, SoulReaperFireHelper.cooldownTicks());
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.soul_reaper.ability")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.soul_reaper.detail")
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.soul_reaper.damage")
                .withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.soul_reaper.cooldown")
                .withStyle(ChatFormatting.GRAY));
    }
}
