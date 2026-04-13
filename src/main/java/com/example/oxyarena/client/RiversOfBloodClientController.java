package com.example.oxyarena.client;

import com.example.oxyarena.item.RiversOfBloodItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class RiversOfBloodClientController {
    private RiversOfBloodClientController() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RiversOfBloodClientController::onClientTickPost);
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        RiversOfBloodItem.tickCorpsePilerState(player, minecraft.level.getGameTime());

        ItemStack mainHandStack = player.getMainHandItem();
        if (!RiversOfBloodItem.consumePendingClientAttack(player, mainHandStack)) {
            return;
        }

        if (minecraft.screen != null
                || minecraft.gameMode == null
                || player.isSpectator()
                || player.isHandsBusy()
                || minecraft.gameMode.isDestroying()) {
            return;
        }

        minecraft.startAttack();
    }
}
