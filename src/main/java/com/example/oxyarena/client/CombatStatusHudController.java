package com.example.oxyarena.client;

import java.util.LinkedHashMap;
import java.util.Map;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.network.CombatStatusSyncPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class CombatStatusHudController {
    private static final ResourceLocation BLEED_STATUS_ID =
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "bleed");
    private static final int ICON_SIZE = 15;
    private static final int BAR_WIDTH = 64;
    private static final int BAR_HEIGHT = 5;
    private static final int TEXTURE_SIZE = 16;
    private static final int STATUS_SPACING = 4;
    private static final int ICON_TO_BAR_SPACING = 4;
    private static final int RIGHT_MARGIN = 10;
    private static final int BOTTOM_MARGIN = 24;
    private static final int BACKGROUND_COLOR = 0xAA22060A;
    private static final int BORDER_COLOR = 0xCC050105;
    private static final int BLEED_FILL_COLOR = 0xFFE11124;
    private static final int BLEED_HIGHLIGHT_COLOR = 0xFFFF3A48;

    private static final Map<ResourceLocation, StatusHudVisual> VISUALS = Map.of(
            BLEED_STATUS_ID,
            new StatusHudVisual(
                    ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "textures/gui/bleed_icon.png"),
                    ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "textures/gui/bleed_bar.png")));

    private static final Map<ResourceLocation, Integer> ACTIVE_PROGRESS = new LinkedHashMap<>();

    private static boolean registered;

    private CombatStatusHudController() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        NeoForge.EVENT_BUS.addListener(CombatStatusHudController::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(CombatStatusHudController::onRenderGuiPost);
        registered = true;
    }

    public static void handleSync(CombatStatusSyncPayload payload) {
        if (!VISUALS.containsKey(payload.statusId())) {
            return;
        }

        if (payload.quantizedProgress() <= 0) {
            ACTIVE_PROGRESS.remove(payload.statusId());
            return;
        }

        ACTIVE_PROGRESS.put(payload.statusId(), Math.max(0, Math.min(100, payload.quantizedProgress())));
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            ACTIVE_PROGRESS.clear();
        }
    }

    private static void onRenderGuiPost(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null
                || ACTIVE_PROGRESS.isEmpty()
                || minecraft.screen != null
                || minecraft.options.hideGui
                || minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }

        renderStatuses(event.getGuiGraphics(), minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
    }

    private static void renderStatuses(GuiGraphics guiGraphics, int guiWidth, int guiHeight) {
        int totalWidth = ICON_SIZE + ICON_TO_BAR_SPACING + BAR_WIDTH;
        int startX = guiWidth - RIGHT_MARGIN - totalWidth;
        int startY = guiHeight - BOTTOM_MARGIN - ICON_SIZE;

        int index = 0;
        for (Map.Entry<ResourceLocation, Integer> entry : ACTIVE_PROGRESS.entrySet()) {
            StatusHudVisual visual = VISUALS.get(entry.getKey());
            if (visual == null) {
                continue;
            }

            int y = startY - index * (ICON_SIZE + STATUS_SPACING);
            renderSingleStatus(guiGraphics, visual, startX, y, entry.getValue());
            index++;
        }
    }

    private static void renderSingleStatus(
            GuiGraphics guiGraphics,
            StatusHudVisual visual,
            int x,
            int y,
            int progress) {
        guiGraphics.blit(
                visual.iconTexture(),
                x,
                y,
                0.0F,
                0.0F,
                ICON_SIZE,
                ICON_SIZE,
                TEXTURE_SIZE,
                TEXTURE_SIZE);

        int barX = x + ICON_SIZE + ICON_TO_BAR_SPACING;
        int barY = y + (ICON_SIZE - BAR_HEIGHT) / 2;
        guiGraphics.fill(barX - 1, barY - 1, barX + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, BORDER_COLOR);
        guiGraphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, BACKGROUND_COLOR);

        int filledWidth = Math.max(0, Math.min(BAR_WIDTH, Math.round(progress / 100.0F * BAR_WIDTH)));
        if (filledWidth > 0) {
            guiGraphics.fill(barX, barY, barX + filledWidth, barY + BAR_HEIGHT, BLEED_FILL_COLOR);
            guiGraphics.fill(barX, barY, barX + filledWidth, barY + 1, BLEED_HIGHLIGHT_COLOR);
        }
    }

    private record StatusHudVisual(ResourceLocation iconTexture, ResourceLocation barTexture) {
    }
}
