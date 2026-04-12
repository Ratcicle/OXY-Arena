package com.example.oxyarena.registry;

import com.example.oxyarena.OXYArena;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

public final class ModDamageTypes {
    public static final ResourceKey<DamageType> ELEMENTAL_GAUNTLET_PROJECTILE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "elemental_gauntlet_projectile"));

    private ModDamageTypes() {
    }
}
