package com.example.oxyarena.registry;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

public final class ModToolTiers {
    public static final SimpleTier CITRINE = new SimpleTier(
            BlockTags.INCORRECT_FOR_IRON_TOOL,
            220,
            5.5F,
            1.8F,
            13,
            () -> Ingredient.of(ModItems.CITRINE_GEM.get()));
    public static final SimpleTier COBALT = new SimpleTier(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
            1400,
            7.5F,
            2.8F,
            11,
            () -> Ingredient.of(ModItems.COBALT_INGOT.get()));

    private ModToolTiers() {
    }
}
