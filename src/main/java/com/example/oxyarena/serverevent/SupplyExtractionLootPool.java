package com.example.oxyarena.serverevent;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;

public final class SupplyExtractionLootPool {
    public static final int ROLLS = 8;

    private static final List<AirdropLootEntry> ENTRIES = createEntries();

    private SupplyExtractionLootPool() {
    }

    public static void fillChest(RandomSource random, Container container) {
        AirdropLoots.fillChest(random, container, ENTRIES, ROLLS);
    }

    public static List<AirdropLootEntry> entries() {
        return ENTRIES;
    }

    private static List<AirdropLootEntry> createEntries() {
        List<AirdropLootEntry> entries = new ArrayList<>(AirdropLootPool.entries());
        entries.add(AirdropLootEntry.of(() -> Items.COOKED_BEEF, 8, 16, 32));
        entries.add(AirdropLootEntry.of(() -> Items.GOLDEN_CARROT, 4, 10, 22));
        entries.add(AirdropLootEntry.of(() -> Items.GOLDEN_APPLE, 1, 3, 10));
        return List.copyOf(entries);
    }
}
