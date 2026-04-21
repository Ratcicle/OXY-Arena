package com.example.oxyarena.registry;

import com.example.oxyarena.OXYArena;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

public final class ModDamageTypes {
    public static final ResourceKey<DamageType> BLEED_PROC = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "bleed_proc"));
    public static final ResourceKey<DamageType> FROSTBITE_PROC = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "frostbite_proc"));
    public static final ResourceKey<DamageType> ELEMENTAL_GAUNTLET_PROJECTILE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "elemental_gauntlet_projectile"));
    public static final ResourceKey<DamageType> BLACK_BLADE_PULSE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "black_blade_pulse"));
    public static final ResourceKey<DamageType> BLACK_BLADE_PROJECTILE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "black_blade_projectile"));
    public static final ResourceKey<DamageType> EARTHBREAKER_CRACK = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "earthbreaker_crack"));

    private ModDamageTypes() {
    }
}
