package com.example.oxyarena.serverevent;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public final class AirdropLoots {
    private AirdropLoots() {
    }

    public static void fillChest(
            RandomSource random,
            Container container,
            List<AirdropLootEntry> entries,
            int rolls) {
        List<AirdropLootEntry> availableEntries = new ArrayList<>(entries);
        List<ItemStack> selectedStacks = new ArrayList<>(rolls);
        List<Integer> usedSlots = new ArrayList<>(rolls);

        for (int roll = 0; roll < rolls && !availableEntries.isEmpty(); roll++) {
            AirdropLootEntry selectedEntry = pickDistinctEntry(random, availableEntries);
            availableEntries.remove(selectedEntry);
            selectedStacks.add(selectedEntry.createStack(random));
        }

        for (ItemStack stack : selectedStacks) {
            int slot = random.nextInt(container.getContainerSize());
            while (usedSlots.contains(slot)) {
                slot = random.nextInt(container.getContainerSize());
            }

            usedSlots.add(slot);
            container.setItem(slot, stack);
        }
    }

    private static AirdropLootEntry pickDistinctEntry(RandomSource random, List<AirdropLootEntry> entries) {
        int totalWeight = 0;
        for (AirdropLootEntry entry : entries) {
            totalWeight += entry.weight();
        }

        int randomWeight = random.nextInt(totalWeight);
        int cumulativeWeight = 0;
        for (AirdropLootEntry entry : entries) {
            cumulativeWeight += entry.weight();
            if (randomWeight < cumulativeWeight) {
                return entry;
            }
        }

        return entries.getLast();
    }
}
