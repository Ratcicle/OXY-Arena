package com.example.oxyarena.mixin.client;

import com.example.oxyarena.client.animation.OxyPlayerAnimatorBridge;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @Redirect(
            method = "setupRotations",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/AbstractClientPlayer;getSwimAmount(F)F"))
    private float oxyarena$suppressVanillaSwimRenderRotation(AbstractClientPlayer player, float partialTick) {
        if (OxyPlayerAnimatorBridge.suppressesVanillaSwimming(player)) {
            return 0.0F;
        }

        return player.getSwimAmount(partialTick);
    }
}
