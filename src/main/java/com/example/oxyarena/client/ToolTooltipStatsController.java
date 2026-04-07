package com.example.oxyarena.client;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Optional;

import com.example.oxyarena.OXYArena;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.neoforged.neoforge.client.event.AddAttributeTooltipsEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.NeoForge;

public final class ToolTooltipStatsController {
    private static final DecimalFormat MINING_SPEED_FORMAT = new DecimalFormat(
            "0.#",
            DecimalFormatSymbols.getInstance(Locale.ROOT));

    private static boolean registered;

    private ToolTooltipStatsController() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        NeoForge.EVENT_BUS.addListener(ToolTooltipStatsController::onAddAttributeTooltips);
        registered = true;
    }

    private static void onAddAttributeTooltips(AddAttributeTooltipsEvent event) {
        if (!event.shouldShow()) {
            return;
        }

        ItemStack stack = event.getStack();
        if (!isEligibleTool(stack)) {
            return;
        }

        Optional<Float> miningSpeed = getMiningSpeed(stack);
        if (miningSpeed.isEmpty()) {
            return;
        }

        event.addTooltipLines(Component.translatable(
                "tooltip." + OXYArena.MODID + ".mining_speed",
                MINING_SPEED_FORMAT.format(miningSpeed.get()))
                .withStyle(net.minecraft.ChatFormatting.DARK_GREEN));
    }

    private static boolean isEligibleTool(ItemStack stack) {
        if (stack.get(DataComponents.TOOL) == null) {
            return false;
        }

        return stack.canPerformAction(ItemAbilities.PICKAXE_DIG)
                || stack.canPerformAction(ItemAbilities.AXE_DIG)
                || stack.canPerformAction(ItemAbilities.SHOVEL_DIG)
                || stack.canPerformAction(ItemAbilities.HOE_DIG)
                || stack.canPerformAction(ItemAbilities.HOE_TILL);
    }

    private static Optional<Float> getMiningSpeed(ItemStack stack) {
        Tool tool = stack.get(DataComponents.TOOL);
        if (tool == null || tool.rules().isEmpty()) {
            return Optional.empty();
        }

        float maxSpeed = 0.0F;
        for (Tool.Rule rule : tool.rules()) {
            if (rule.speed().isEmpty()) {
                continue;
            }

            maxSpeed = Math.max(maxSpeed, rule.speed().get());
        }

        return maxSpeed > 0.0F ? Optional.of(maxSpeed) : Optional.empty();
    }
}
