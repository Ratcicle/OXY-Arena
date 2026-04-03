package com.example.oxyarena.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class CobaltBowItem extends BowItem {
    private static final String ARROW_RAIN_TAG = "OxyArenaCobaltArrowRain";

    public CobaltBowItem(Properties properties) {
        super(properties);
    }

    @Override
    public AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectileStack, ItemStack weaponStack) {
        arrow.getPersistentData().putBoolean(ARROW_RAIN_TAG, true);
        return arrow;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.cobalt_bow.passive")
                .withStyle(ChatFormatting.AQUA));
    }

    public static boolean hasArrowRain(AbstractArrow arrow) {
        return arrow.getPersistentData().getBoolean(ARROW_RAIN_TAG);
    }
}
