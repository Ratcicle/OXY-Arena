package com.example.oxyarena.entity.event;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;

final class CloneInventorySnapshot {
    private static final String ITEMS_TAG = "Items";
    private static final String SLOT_TAG = "Slot";

    private final NonNullList<ItemStack> items = NonNullList.withSize(Inventory.INVENTORY_SIZE, ItemStack.EMPTY);

    void copyFromPlayer(Player player) {
        for (int slot = 0; slot < this.items.size(); slot++) {
            this.items.set(slot, player.getInventory().items.get(slot).copy());
        }
    }

    void saveToTag(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag itemsTag = new ListTag();
        for (int slot = 0; slot < this.items.size(); slot++) {
            ItemStack stack = this.items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }

            CompoundTag stackTag = (CompoundTag)stack.save(registries, new CompoundTag());
            stackTag.putInt(SLOT_TAG, slot);
            itemsTag.add(stackTag);
        }
        tag.put(ITEMS_TAG, itemsTag);
    }

    void loadFromTag(CompoundTag tag, HolderLookup.Provider registries) {
        this.clear();
        if (!tag.contains(ITEMS_TAG, Tag.TAG_LIST)) {
            return;
        }

        ListTag itemsTag = tag.getList(ITEMS_TAG, Tag.TAG_COMPOUND);
        for (int index = 0; index < itemsTag.size(); index++) {
            CompoundTag stackTag = itemsTag.getCompound(index);
            int slot = stackTag.getInt(SLOT_TAG);
            if (slot < 0 || slot >= this.items.size()) {
                continue;
            }

            this.items.set(slot, ItemStack.parse(registries, stackTag).orElse(ItemStack.EMPTY));
        }
    }

    void clear() {
        for (int slot = 0; slot < this.items.size(); slot++) {
            this.items.set(slot, ItemStack.EMPTY);
        }
    }

    boolean addToInventory(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }

        for (int slot = 0; slot < this.items.size(); slot++) {
            ItemStack existing = this.items.get(slot);
            if (existing.isEmpty()) {
                this.items.set(slot, stack.copy());
                return true;
            }

            if (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                int transfer = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(transfer);
                stack.shrink(transfer);
                if (stack.isEmpty()) {
                    return true;
                }
            }
        }

        return stack.isEmpty();
    }

    ItemStack getItem(int slot) {
        return slot >= 0 && slot < this.items.size() ? this.items.get(slot) : ItemStack.EMPTY;
    }

    int findBestMeleeSlot() {
        int bestSlot = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int slot = 0; slot < this.items.size(); slot++) {
            ItemStack stack = this.items.get(slot);
            double score = getMeleeScore(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    int findBestBowSlot() {
        int bestSlot = -1;
        int bestPriority = Integer.MIN_VALUE;
        for (int slot = 0; slot < this.items.size(); slot++) {
            ItemStack stack = this.items.get(slot);
            if (!(stack.getItem() instanceof BowItem)) {
                continue;
            }

            int priority = stack.isDamaged() ? 0 : 1;
            if (priority > bestPriority) {
                bestPriority = priority;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    int findFirstArrowSlot() {
        for (int slot = 0; slot < this.items.size(); slot++) {
            if (this.items.get(slot).getItem() instanceof ArrowItem) {
                return slot;
            }
        }
        return -1;
    }

    int findBestToolSlot(BlockState state) {
        for (int slot = 0; slot < this.items.size(); slot++) {
            ItemStack stack = this.items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }

            if (state.is(BlockTags.MINEABLE_WITH_PICKAXE) && stack.canPerformAction(ItemAbilities.PICKAXE_DIG)) {
                return slot;
            }
            if (state.is(BlockTags.MINEABLE_WITH_AXE) && stack.canPerformAction(ItemAbilities.AXE_DIG)) {
                return slot;
            }
            if (state.is(BlockTags.MINEABLE_WITH_SHOVEL) && stack.canPerformAction(ItemAbilities.SHOVEL_DIG)) {
                return slot;
            }
        }
        return -1;
    }

    int findBestBuildingBlockSlot() {
        int bestSlot = -1;
        int bestCount = 0;
        for (int slot = 0; slot < this.items.size(); slot++) {
            ItemStack stack = this.items.get(slot);
            if (!(stack.getItem() instanceof BlockItem blockItem)) {
                continue;
            }

            BlockState state = blockItem.getBlock().defaultBlockState();
            if (state.isAir()
                    || !state.getFluidState().isEmpty()
                    || state.hasBlockEntity()
                    || !state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                    || !state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
                continue;
            }

            if (stack.getCount() > bestCount) {
                bestSlot = slot;
                bestCount = stack.getCount();
            }
        }
        return bestSlot;
    }

    ItemStack consumeArrow(int slot) {
        if (slot < 0 || slot >= this.items.size()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = this.items.get(slot);
        if (!(stack.getItem() instanceof ArrowItem)) {
            return ItemStack.EMPTY;
        }

        ItemStack consumed = stack.copyWithCount(1);
        stack.shrink(1);
        if (stack.isEmpty()) {
            this.items.set(slot, ItemStack.EMPTY);
        }
        return consumed;
    }

    BlockState consumeBuildingBlockState(int slot) {
        if (slot < 0 || slot >= this.items.size()) {
            return null;
        }

        ItemStack stack = this.items.get(slot);
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }

        BlockState state = blockItem.getBlock().defaultBlockState();
        stack.shrink(1);
        if (stack.isEmpty()) {
            this.items.set(slot, ItemStack.EMPTY);
        }
        return state;
    }

    void damageItemInSlot(int slot, CloneThiefEntity clone, int amount) {
        if (slot < 0 || slot >= this.items.size()) {
            return;
        }

        ItemStack stack = this.items.get(slot);
        if (stack.isEmpty() || !stack.isDamageableItem()) {
            return;
        }

        stack.hurtAndBreak(amount, clone, EquipmentSlot.MAINHAND);
        if (stack.isEmpty() || stack.getDamageValue() >= stack.getMaxDamage()) {
            this.items.set(slot, ItemStack.EMPTY);
        }
    }

    private static double getMeleeScore(ItemStack stack) {
        if (stack.isEmpty()
                || stack.getItem() instanceof BowItem
                || stack.getItem() instanceof ArrowItem
                || stack.getItem() instanceof BlockItem) {
            return Double.NEGATIVE_INFINITY;
        }

        double score = stack.getAttributeModifiers().compute(0.0D, EquipmentSlot.MAINHAND);
        if (score <= 0.0D) {
            if (stack.canPerformAction(ItemAbilities.PICKAXE_DIG)
                    || stack.canPerformAction(ItemAbilities.AXE_DIG)
                    || stack.canPerformAction(ItemAbilities.SHOVEL_DIG)) {
                score = 1.0D;
            } else {
                return Double.NEGATIVE_INFINITY;
            }
        }

        if (!stack.isDamageableItem()) {
            score += 0.05D;
        } else {
            score += (double)(stack.getMaxDamage() - stack.getDamageValue()) / Math.max(1.0D, stack.getMaxDamage()) * 0.05D;
        }

        return score;
    }
}
