package com.example.oxyarena.serverevent;

import java.util.List;

import com.example.oxyarena.registry.ModItems;

import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;

public final class AirdropLootPool {
    public static final int ROLLS = 4;

    private static final List<AirdropLootEntry> ENTRIES = List.of(
            AirdropLootEntry.of(() -> Items.COAL, 10, 30, 60),
            AirdropLootEntry.of(() -> Items.IRON_INGOT, 4, 15, 50),
            AirdropLootEntry.of(() -> Items.GOLD_INGOT, 3, 10, 40),
            AirdropLootEntry.of(() -> Items.ARROW, 10, 32, 38),
            AirdropLootEntry.of(() -> Items.ENDER_PEARL, 3, 12, 35),
            AirdropLootEntry.of(() -> Items.EMERALD, 1, 4, 30),
            AirdropLootEntry.of(() -> Items.DIAMOND, 1, 5, 25),
            AirdropLootEntry.of(() -> Items.FIREWORK_ROCKET, 5, 20, 25),
            AirdropLootEntry.of(() -> Items.IRON_CHESTPLATE, 1, 1, 18),
            AirdropLootEntry.of(() -> Items.GOLDEN_HELMET, 1, 1, 12),
            AirdropLootEntry.of(() -> Items.ANCIENT_DEBRIS, 1, 3, 10),
            AirdropLootEntry.of(() -> Items.DIAMOND_BOOTS, 1, 1, 8),
            AirdropLootEntry.of(() -> Items.ELYTRA, 1, 1, 5),
            AirdropLootEntry.of(() -> Items.MACE, 1, 1, 3),
            AirdropLootEntry.of(ModItems.CITRINE_GEM, 8, 20, 24),
            AirdropLootEntry.of(ModItems.RAW_COBALT, 5, 12, 20),
            AirdropLootEntry.of(ModItems.COBALT_INGOT, 2, 6, 12),
            AirdropLootEntry.of(ModItems.SMOKE_BOMB, 2, 6, 16),
            AirdropLootEntry.of(ModItems.ESTUS_FLASK, 3, 7, 12),
            AirdropLootEntry.of(ModItems.STORM_CHARGE, 2, 4, 10),
            AirdropLootEntry.of(ModItems.CITRINE_THROWING_DAGGER, 4, 10, 16),
            AirdropLootEntry.of(ModItems.GRAPPLING_GUN, 1, 1, 5),
            AirdropLootEntry.of(ModItems.CHOCOLATE_SWORD, 1, 1, 5),
            AirdropLootEntry.of(ModItems.ZEUS_LIGHTNING, 1, 1, 1),
            AirdropLootEntry.of(ModItems.FLAMING_SCYTHE, 1, 1, 1),
            AirdropLootEntry.of(ModItems.LIFEHUNT_SCYTHE, 1, 1, 1),
            AirdropLootEntry.of(ModItems.MURASAMA, 1, 1, 1),
            AirdropLootEntry.of(ModItems.RIVERS_OF_BLOOD, 1, 1, 1),
            AirdropLootEntry.of(ModItems.BLACK_BLADE, 1, 1, 1),
            AirdropLootEntry.of(ModItems.KUSABIMARU, 1, 1, 1),
            AirdropLootEntry.of(ModItems.SOUL_REAPER, 1, 1, 1),
            AirdropLootEntry.of(ModItems.EARTHBREAKER, 1, 1, 1),
            AirdropLootEntry.of(ModItems.ZERO_REVERSE, 1, 1, 1),
            AirdropLootEntry.of(ModItems.NECROMANCER_STAFF, 1, 1, 1),
            AirdropLootEntry.of(ModItems.FROZEN_NEEDLE, 1, 1, 1),
            AirdropLootEntry.of(ModItems.GHOST_SABER, 1, 1, 1),
            AirdropLootEntry.of(ModItems.ZENITH, 1, 1, 1));

    private AirdropLootPool() {
    }

    public static void fillChest(RandomSource random, Container container) {
        AirdropLoots.fillChest(random, container, ENTRIES, ROLLS);
    }

    public static List<AirdropLootEntry> entries() {
        return ENTRIES;
    }
}
