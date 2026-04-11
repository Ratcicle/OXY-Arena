package com.example.oxyarena.registry;

import com.example.oxyarena.OXYArena;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB,
            OXYArena.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> OXY_ARENA = CREATIVE_MODE_TABS.register(
            "oxy_arena",
            ModCreativeModeTabs::createOxyArenaTab);

    private ModCreativeModeTabs() {
    }

    private static CreativeModeTab createOxyArenaTab() {
        return CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.oxyarena.oxy_arena"))
                .icon(() -> ModItems.CITRINE_GEM.get().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    output.accept(ModItems.CITRINE_ORE);
                    output.accept(ModItems.DEEPSLATE_CITRINE_ORE);
                    output.accept(ModItems.COBALT_ORE);
                    output.accept(ModItems.DEEPSLATE_COBALT_ORE);
                    output.accept(ModItems.AMETRA_ORE);
                    output.accept(ModItems.DEEPSLATE_AMETRA_ORE);
                    output.accept(ModItems.INCANDESCENT_ORE);
                    output.accept(ModItems.DEEPSLATE_INCANDESCENT_ORE);
                    output.accept(ModItems.CITRINE_GEM);
                    output.accept(ModItems.AMETRA_GEM);
                    output.accept(ModItems.RAW_COBALT);
                    output.accept(ModItems.COBALT_INGOT);
                    output.accept(ModItems.INCANDESCENT_INGOT);
                    output.accept(ModItems.CITRINE_SWORD);
                    output.accept(ModItems.INCANDESCENT_SWORD);
                    output.accept(ModItems.CITRINE_PICKAXE);
                    output.accept(ModItems.INCANDESCENT_PICKAXE);
                    output.accept(ModItems.CITRINE_AXE);
                    output.accept(ModItems.INCANDESCENT_AXE);
                    output.accept(ModItems.CITRINE_SHOVEL);
                    output.accept(ModItems.CITRINE_HELMET);
                    output.accept(ModItems.CITRINE_CHESTPLATE);
                    output.accept(ModItems.CITRINE_LEGGINGS);
                    output.accept(ModItems.CITRINE_BOOTS);
                    output.accept(ModItems.CITRINE_THROWING_DAGGER);
                    output.accept(ModItems.INCANDESCENT_THROWING_DAGGER);
                    output.accept(ModItems.SMOKE_BOMB);
                    output.accept(ModItems.ESTUS_FLASK);
                    output.accept(ModItems.STORM_CHARGE);
                    output.accept(ModItems.ZEUS_LIGHTNING);
                    output.accept(ModItems.FLAMING_SCYTHE);
                    output.accept(ModItems.LIFEHUNT_SCYTHE);
                    output.accept(ModItems.COBALT_SWORD);
                    output.accept(ModItems.AMETRA_SWORD);
                    output.accept(ModItems.MURASAMA);
                    output.accept(ModItems.KUSABIMARU);
                    output.accept(ModItems.SOUL_REAPER);
                    output.accept(ModItems.BLACK_DIAMOND_SWORD);
                    output.accept(ModItems.CHOCOLATE_SWORD);
                    output.accept(ModItems.ZENITH);
                    output.accept(ModItems.COBALT_PICKAXE);
                    output.accept(ModItems.AMETRA_PICKAXE);
                    output.accept(ModItems.COBALT_AXE);
                    output.accept(ModItems.AMETRA_AXE);
                    output.accept(ModItems.COBALT_SHOVEL);
                    output.accept(ModItems.AMETRA_SHOVEL);
                    output.accept(ModItems.COBALT_BOW);
                    output.accept(ModItems.COBALT_SHIELD);
                    output.accept(ModItems.GRAPPLING_GUN);
                    output.accept(ModItems.COBALT_HELMET);
                    output.accept(ModItems.COBALT_CHESTPLATE);
                    output.accept(ModItems.COBALT_LEGGINGS);
                    output.accept(ModItems.COBALT_BOOTS);
                })
                .build();
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
