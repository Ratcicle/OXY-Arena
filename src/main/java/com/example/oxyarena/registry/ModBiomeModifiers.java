package com.example.oxyarena.registry;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.worldgen.AmetraOreBiomeModifier;
import com.mojang.serialization.MapCodec;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModBiomeModifiers {
    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, OXYArena.MODID);

    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<AmetraOreBiomeModifier>> AMETRA_ORE =
            BIOME_MODIFIER_SERIALIZERS.register("ametra_ore", () -> AmetraOreBiomeModifier.CODEC);

    private ModBiomeModifiers() {
    }

    public static void register(IEventBus modEventBus) {
        BIOME_MODIFIER_SERIALIZERS.register(modEventBus);
    }
}
