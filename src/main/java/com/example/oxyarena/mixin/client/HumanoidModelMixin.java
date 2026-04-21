package com.example.oxyarena.mixin.client;

import com.example.oxyarena.client.animation.OxyPlayerAnimatorBridge;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Redirect(
            method = "prepareMobModel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getSwimAmount(F)F"))
    private float oxyarena$suppressVanillaSwimModelPose(LivingEntity entity, float partialTick) {
        if (entity instanceof Player player && OxyPlayerAnimatorBridge.suppressesVanillaSwimming(player)) {
            return 0.0F;
        }

        return entity.getSwimAmount(partialTick);
    }
}
