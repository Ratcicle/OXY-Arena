package com.example.oxyarena.combatstatus;

import net.minecraft.resources.ResourceLocation;

public record CombatStatusDefinition(
        ResourceLocation id,
        float maxBuildup,
        int decayDelayTicks,
        float decayPerTick,
        float procFlatDamage,
        float procMaxHealthRatio,
        boolean resetOnProc,
        boolean overflowCarry,
        String damageSource,
        ResourceLocation hudIcon,
        ResourceLocation hudBar,
        String procParticleStyle) {
}
