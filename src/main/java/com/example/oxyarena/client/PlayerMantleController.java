package com.example.oxyarena.client;

import com.example.oxyarena.network.PlayerMantleInputPayload;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PlayerMantleController {
    private static boolean lastJumpDown;
    private static boolean lastShiftDown;

    private PlayerMantleController() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerMantleController::onClientTickPost);
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            lastJumpDown = false;
            lastShiftDown = false;
            return;
        }

        boolean jumpDown = minecraft.options.keyJump.isDown();
        boolean shiftDown = minecraft.options.keyShift.isDown();
        if (jumpDown && !lastJumpDown) {
            PacketDistributor.sendToServer(new PlayerMantleInputPayload(PlayerMantleInputPayload.ACTION_JUMP_PRESSED));
        }
        if (shiftDown && !lastShiftDown) {
            PacketDistributor.sendToServer(new PlayerMantleInputPayload(PlayerMantleInputPayload.ACTION_SHIFT_PRESSED));
        }

        lastJumpDown = jumpDown;
        lastShiftDown = shiftDown;
    }
}
