package com.example.oxyarena.client;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.network.AmetraGlaiveCooldownSyncPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class AmetraGlaiveCooldownHudController {
    private static final ResourceLocation COOLDOWN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            OXYArena.MODID,
            "textures/gui/ametra_glaive_cooldown.png");
    private static final int TEXTURE_SIZE = 16;
    private static final int DISPLAY_SCALE = 3;
    private static final int DISPLAY_SIZE = TEXTURE_SIZE * DISPLAY_SCALE;
    private static final int LEFT_MARGIN = 10;
    private static final int BOTTOM_MARGIN = -12;

    private static int cooldownDurationTicks;
    private static int cooldownElapsedTicks;
    private static boolean registered;

    private AmetraGlaiveCooldownHudController() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        NeoForge.EVENT_BUS.addListener(AmetraGlaiveCooldownHudController::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(AmetraGlaiveCooldownHudController::onRenderGuiPost);
        registered = true;
    }

    public static void handleSync(AmetraGlaiveCooldownSyncPayload payload) {
        cooldownDurationTicks = Math.max(0, payload.durationTicks());
        cooldownElapsedTicks = 0;
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            clearCooldown();
            return;
        }

        if (cooldownDurationTicks <= 0) {
            return;
        }

        cooldownElapsedTicks++;
        if (cooldownElapsedTicks >= cooldownDurationTicks) {
            clearCooldown();
        }
    }

    private static void onRenderGuiPost(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || cooldownDurationTicks <= 0
                || minecraft.screen != null
                || minecraft.options.hideGui
                || minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }

        int x = LEFT_MARGIN;
        int y = minecraft.getWindow().getGuiScaledHeight() - BOTTOM_MARGIN - DISPLAY_SIZE;
        renderCooldown(event.getGuiGraphics(), x, y);
    }

    private static void renderCooldown(GuiGraphics guiGraphics, int x, int y) {
        int filledWidth = Math.max(
                0,
                Math.min(TEXTURE_SIZE, Math.round(cooldownElapsedTicks / (float)cooldownDurationTicks * TEXTURE_SIZE)));
        if (filledWidth <= 0) {
            return;
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        guiGraphics.pose().scale(DISPLAY_SCALE, DISPLAY_SCALE, 1.0F);
        guiGraphics.blit(
                COOLDOWN_TEXTURE,
                0,
                0,
                0.0F,
                0.0F,
                filledWidth,
                TEXTURE_SIZE,
                TEXTURE_SIZE,
                TEXTURE_SIZE);
        guiGraphics.pose().popPose();
    }

    private static void clearCooldown() {
        cooldownDurationTicks = 0;
        cooldownElapsedTicks = 0;
    }
}
