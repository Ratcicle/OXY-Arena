package com.example.oxyarena.serverevent;

import java.util.function.Supplier;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

record AirdropLootEntry(Supplier<? extends Item> itemSupplier, int minCount, int maxCount, int weight) {
    static AirdropLootEntry of(Supplier<? extends Item> itemSupplier, int minCount, int maxCount, int weight) {
        return new AirdropLootEntry(itemSupplier, minCount, maxCount, weight);
    }

    ItemStack createStack(RandomSource random) {
        int count = this.minCount == this.maxCount
                ? this.minCount
                : random.nextIntBetweenInclusive(this.minCount, this.maxCount);
        return new ItemStack(this.itemSupplier.get(), count);
    }
}
