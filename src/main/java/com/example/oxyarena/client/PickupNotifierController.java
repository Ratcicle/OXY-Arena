package com.example.oxyarena.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.example.oxyarena.Config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class PickupNotifierController {
    private static final int ICON_SIZE = 15;
    private static final int ENTRY_SPACING = 2;
    private static final int ENTRY_PADDING = 4;
    private static final int TEXT_SPACING = 4;
    private static final float TEXT_SCALE = 0.85F;
    private static final int BOTTOM_MARGIN = 38;
    private static final int RIGHT_MARGIN = 8;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private static boolean registered;
    private static PickupNotifierController instance;

    private final List<PickupEntry> entries = new ArrayList<>();

    public static void register() {
        if (registered) {
            return;
        }

        instance = new PickupNotifierController();
        NeoForge.EVENT_BUS.addListener(instance::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(instance::onRenderGuiPost);
        registered = true;
    }

    public static void handlePickup(ItemStack stack) {
        if (!registered || instance == null) {
            return;
        }

        instance.addPickup(stack);
    }

    private void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            entries.clear();
            return;
        }

        if (minecraft.isPaused()) {
            return;
        }

        if (!Config.pickupNotifierEnabled()) {
            entries.clear();
            return;
        }

        Iterator<PickupEntry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            PickupEntry entry = iterator.next();
            entry.remainingTicks = Math.max(0, entry.remainingTicks - 1);
            if (entry.remainingTicks <= 0) {
                iterator.remove();
            }
        }
    }

    private void onRenderGuiPost(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null
                || !Config.pickupNotifierEnabled()
                || entries.isEmpty()
                || minecraft.screen != null
                || minecraft.options.hideGui
                || minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }

        renderEntries(event.getGuiGraphics(), minecraft.font, minecraft.getWindow().getGuiScaledWidth(),
                minecraft.getWindow().getGuiScaledHeight());
    }

    private void addPickup(ItemStack stack) {
        if (!Config.pickupNotifierEnabled() || stack.isEmpty() || stack.getCount() <= 0) {
            return;
        }

        ItemStack displayStack = stack.copy();
        for (int index = 0; index < entries.size(); index++) {
            PickupEntry entry = entries.get(index);
            if (ItemStack.isSameItemSameComponents(entry.stack, displayStack)) {
                entry.stack.grow(displayStack.getCount());
                entry.remainingTicks = refreshEntryTimer(entry.remainingTicks);
                if (index > 0) {
                    entries.remove(index);
                    entries.add(0, entry);
                }
                return;
            }
        }

        entries.add(0, new PickupEntry(displayStack, getConfiguredShowTicks()));
        trimEntries();
    }

    private void trimEntries() {
        int maxEntries = Math.max(1, Config.pickupNotifierMaxEntries());
        while (entries.size() > maxEntries) {
            entries.remove(entries.size() - 1);
        }
    }

    private void renderEntries(GuiGraphics guiGraphics, Font font, int guiWidth, int guiHeight) {
        int baseY = guiHeight - BOTTOM_MARGIN;
        int scaledTextHeight = Math.max(1, Math.round(font.lineHeight * TEXT_SCALE));
        for (int index = 0; index < entries.size(); index++) {
            PickupEntry entry = entries.get(index);
            float alpha = getFadeAlpha(entry.remainingTicks);
            if (alpha <= 0.0F) {
                continue;
            }

            Component text = Component.literal(entry.stack.getHoverName().getString() + " x" + entry.stack.getCount());
            int textWidth = Math.max(1, Math.round(font.width(text) * TEXT_SCALE));
            int entryWidth = ENTRY_PADDING * 2 + ICON_SIZE + TEXT_SPACING + textWidth;
            int x = guiWidth - RIGHT_MARGIN - entryWidth;
            int y = baseY - index * (ICON_SIZE + ENTRY_SPACING);
            int textColor = applyAlpha(TEXT_COLOR, alpha);

            guiGraphics.renderItem(entry.stack, x + ENTRY_PADDING, y + 1);

            int textX = x + ENTRY_PADDING + ICON_SIZE + TEXT_SPACING;
            int textY = y + Math.max(0, (ICON_SIZE - scaledTextHeight) / 2) + 1;
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(textX, textY, 0.0F);
            guiGraphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
            guiGraphics.drawString(font, text, 0, 0, textColor, true);
            guiGraphics.pose().popPose();
        }
    }

    private float getFadeAlpha(int remainingTicks) {
        if (remainingTicks <= 0) {
            return 0.0F;
        }

        int showTicks = getConfiguredShowTicks();
        int fadeTicks = getConfiguredFadeTicks(showTicks);
        if (fadeTicks <= 0) {
            return 1.0F;
        }

        int elapsedTicks = showTicks - remainingTicks;
        float fadeInAlpha = Math.min(1.0F, (elapsedTicks + 1.0F) / fadeTicks);
        float fadeOutAlpha = Math.min(1.0F, remainingTicks / (float)fadeTicks);
        return Math.min(fadeInAlpha, fadeOutAlpha);
    }

    private int refreshEntryTimer(int currentTicks) {
        int showTicks = getConfiguredShowTicks();
        if (currentTicks <= 0) {
            return showTicks;
        }

        return Math.max(currentTicks, getVisibleExtensionTicks(showTicks));
    }

    private int getConfiguredShowTicks() {
        return Math.max(1, Config.pickupNotifierVisibleTicks());
    }

    private int getConfiguredFadeTicks(int showTicks) {
        return Math.max(0, Math.min(Config.pickupNotifierFadeTicks(), showTicks));
    }

    private int getVisibleExtensionTicks(int showTicks) {
        int fadeTicks = getConfiguredFadeTicks(showTicks);
        return Math.max(1, showTicks - fadeTicks);
    }

    private int applyAlpha(int color, float alpha) {
        int alphaChannel = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return (color & 0x00FFFFFF) | (alphaChannel << 24);
    }

    private static final class PickupEntry {
        private final ItemStack stack;
        private int remainingTicks;

        private PickupEntry(ItemStack stack, int remainingTicks) {
            this.stack = stack;
            this.remainingTicks = remainingTicks;
        }
    }
}
