package com.example.oxyarena.event;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import com.example.oxyarena.registry.ModItems;

public final class ModTooltipEvents {
    private ModTooltipEvents() {
    }

    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        String tooltipKey = getArmorSetTooltipKey(stack);
        if (tooltipKey == null) {
            return;
        }

        List<Component> tooltip = event.getToolTip();
        tooltip.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.AQUA));
    }

    private static String getArmorSetTooltipKey(ItemStack stack) {
        if (stack.is(ModItems.CITRINE_HELMET.get())
                || stack.is(ModItems.CITRINE_CHESTPLATE.get())
                || stack.is(ModItems.CITRINE_LEGGINGS.get())
                || stack.is(ModItems.CITRINE_BOOTS.get())) {
            return "tooltip.oxyarena.citrine_armor.passive";
        }

        if (stack.is(ModItems.COBALT_HELMET.get())
                || stack.is(ModItems.COBALT_CHESTPLATE.get())
                || stack.is(ModItems.COBALT_LEGGINGS.get())
                || stack.is(ModItems.COBALT_BOOTS.get())) {
            return "tooltip.oxyarena.cobalt_armor.passive";
        }

        if (stack.is(ModItems.OCCULT_HELMET.get())
                || stack.is(ModItems.OCCULT_CHESTPLATE.get())
                || stack.is(ModItems.OCCULT_LEGGINGS.get())
                || stack.is(ModItems.OCCULT_BOOTS.get())) {
            return "tooltip.oxyarena.occult_armor.passive";
        }

        if (stack.is(Items.IRON_HELMET)
                || stack.is(Items.IRON_CHESTPLATE)
                || stack.is(Items.IRON_LEGGINGS)
                || stack.is(Items.IRON_BOOTS)) {
            return "tooltip.oxyarena.iron_armor.passive";
        }

        if (stack.is(Items.DIAMOND_HELMET)
                || stack.is(Items.DIAMOND_CHESTPLATE)
                || stack.is(Items.DIAMOND_LEGGINGS)
                || stack.is(Items.DIAMOND_BOOTS)) {
            return "tooltip.oxyarena.diamond_armor.passive";
        }

        if (stack.is(Items.NETHERITE_HELMET)
                || stack.is(Items.NETHERITE_CHESTPLATE)
                || stack.is(Items.NETHERITE_LEGGINGS)
                || stack.is(Items.NETHERITE_BOOTS)) {
            return "tooltip.oxyarena.netherite_armor.passive";
        }

        return null;
    }
}
