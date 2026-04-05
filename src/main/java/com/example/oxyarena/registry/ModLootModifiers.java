package com.example.oxyarena.registry;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.loot.DoubleOreDropsLootModifier;
import com.mojang.serialization.MapCodec;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModLootModifiers {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> GLOBAL_LOOT_MODIFIER_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, OXYArena.MODID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<DoubleOreDropsLootModifier>> DOUBLE_ORE_DROPS =
            GLOBAL_LOOT_MODIFIER_SERIALIZERS.register("double_ore_drops", () -> DoubleOreDropsLootModifier.CODEC);

    private ModLootModifiers() {
    }

    public static void register(IEventBus modEventBus) {
        GLOBAL_LOOT_MODIFIER_SERIALIZERS.register(modEventBus);
    }
}
