package com.example.oxyarena.client;

import com.example.oxyarena.item.NecromancerStaffItem;
import com.example.oxyarena.network.NecromancerStaffSelectSoulPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NecromancerStaffHudController {
    private static final int TEXT_COLOR = 0xFFE7D4FF;
    private static final int SHADOW_COLOR = 0xAA16051F;
    private static final int CROSSHAIR_OFFSET_Y = 14;

    private static boolean registered;

    private NecromancerStaffHudController() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        NeoForge.EVENT_BUS.addListener(NecromancerStaffHudController::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(NecromancerStaffHudController::onRenderGuiPost);
        registered = true;
    }

    private static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null
                || minecraft.screen != null
                || !player.isShiftKeyDown()
                || !NecromancerStaffItem.isStaff(player.getMainHandItem())) {
            return;
        }

        double scrollDelta = event.getScrollDeltaY();
        if (scrollDelta == 0.0D) {
            return;
        }

        int direction = scrollDelta > 0.0D ? -1 : 1;
        PacketDistributor.sendToServer(new NecromancerStaffSelectSoulPayload(direction));
        event.setCanceled(true);
    }

    private static void onRenderGuiPost(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null
                || minecraft.screen != null
                || minecraft.options.hideGui
                || minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (!NecromancerStaffItem.isStaff(stack)) {
            return;
        }

        Component text = NecromancerStaffItem.getSelectedSoulName(stack)
                .map(name -> Component.translatable("hud.oxyarena.necromancer_staff.selected", name))
                .orElseGet(() -> Component.translatable("hud.oxyarena.necromancer_staff.none"));
        renderCenteredText(event.getGuiGraphics(), minecraft.font, text,
                minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
    }

    private static void renderCenteredText(GuiGraphics guiGraphics, Font font, Component text, int guiWidth, int guiHeight) {
        int textWidth = font.width(text);
        int x = (guiWidth - textWidth) / 2;
        int y = guiHeight / 2 + CROSSHAIR_OFFSET_Y;
        guiGraphics.fill(x - 3, y - 2, x + textWidth + 3, y + font.lineHeight + 1, SHADOW_COLOR);
        guiGraphics.drawString(font, text, x, y, TEXT_COLOR, true);
    }
}
