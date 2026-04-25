package com.example.oxyarena.event.gameplay;

import com.example.oxyarena.registry.ModMobEffects;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

public final class AntiHealEvents {
    private static final float HEAL_MULTIPLIER = 0.5F;

    private AntiHealEvents() {
    }

    public static void onLivingHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof Player player && player.hasEffect(ModMobEffects.ANTI_HEAL)) {
            event.setAmount(event.getAmount() * HEAL_MULTIPLIER);
        }
    }
}
