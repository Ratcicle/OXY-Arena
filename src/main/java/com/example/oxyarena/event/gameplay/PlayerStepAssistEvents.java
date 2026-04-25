package com.example.oxyarena.event.gameplay;

import com.example.oxyarena.OXYArena;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class PlayerStepAssistEvents {
    private static final double VANILLA_PLAYER_STEP_HEIGHT = 0.6D;
    private static final double BOOTS_STEP_HEIGHT = 1.05D;
    private static final double BOOTS_STEP_HEIGHT_BONUS = BOOTS_STEP_HEIGHT - VANILLA_PLAYER_STEP_HEIGHT;
    private static final ResourceLocation BOOTS_STEP_HEIGHT_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            OXYArena.MODID,
            "movement.boots_step_height");

    private PlayerStepAssistEvents() {
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            updateBootsStepAssist(player);
        }
    }

    private static void updateBootsStepAssist(ServerPlayer player) {
        AttributeInstance stepHeight = player.getAttribute(Attributes.STEP_HEIGHT);
        if (stepHeight == null) {
            return;
        }

        if (canUseBootsStepAssist(player)) {
            stepHeight.addOrUpdateTransientModifier(new AttributeModifier(
                    BOOTS_STEP_HEIGHT_MODIFIER_ID,
                    BOOTS_STEP_HEIGHT_BONUS,
                    AttributeModifier.Operation.ADD_VALUE));
            return;
        }

        stepHeight.removeModifier(BOOTS_STEP_HEIGHT_MODIFIER_ID);
    }

    private static boolean canUseBootsStepAssist(ServerPlayer player) {
        return player.isAlive()
                && !player.isSpectator()
                && !player.getAbilities().flying
                && !player.isPassenger()
                && !player.isFallFlying()
                && !player.isShiftKeyDown()
                && !player.getItemBySlot(EquipmentSlot.FEET).isEmpty()
                && !PlayerSlideEvents.isActive(player)
                && !PlayerMantleEvents.isMantling(player);
    }
}
