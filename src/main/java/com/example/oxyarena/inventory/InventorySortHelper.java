package com.example.oxyarena.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BeaconBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DeadBushBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.KelpPlantBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.MangrovePropaguleBlock;
import net.minecraft.world.level.block.MangroveRootsBlock;
import net.minecraft.world.level.block.MossBlock;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.Tags;

public final class InventorySortHelper {
    private InventorySortHelper() {
    }

    public static void sortFromClickedSlot(ServerPlayer player, int menuId, int clickedSlotIndex) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu.containerId != menuId || !menu.isValidSlotIndex(clickedSlotIndex) || !menu.stillValid(player)) {
            return;
        }

        Slot clickedSlot = menu.getSlot(clickedSlotIndex);
        List<Slot> sortableSlots = resolveSortableSlots(menu, player, clickedSlot);
        if (sortableSlots.isEmpty()) {
            return;
        }

        List<ItemStack> extractedStacks = new ArrayList<>();
        for (Slot slot : sortableSlots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }

            extractedStacks.add(stack.copy());
            slot.set(ItemStack.EMPTY);
        }

        if (extractedStacks.isEmpty()) {
            menu.broadcastChanges();
            return;
        }

        extractedStacks.sort(Comparator.comparing(InventorySortHelper::createSortKey));

        List<ItemStack> mergedStacks = mergeStacks(extractedStacks);
        fillSlots(sortableSlots, mergedStacks);
        menu.broadcastChanges();
    }

    private static List<Slot> resolveSortableSlots(AbstractContainerMenu menu, ServerPlayer player, Slot clickedSlot) {
        List<Slot> sortableSlots = new ArrayList<>();
        Inventory playerInventory = player.getInventory();
        Container clickedContainer = clickedSlot.container;

        if (clickedContainer == playerInventory) {
            for (Slot slot : menu.slots) {
                if (slot.container == playerInventory && slot.getClass() == Slot.class) {
                    int containerSlot = slot.getContainerSlot();
                    if (containerSlot >= 9 && containerSlot < 36) {
                        sortableSlots.add(slot);
                    }
                }
            }

            return sortableSlots;
        }

        if (clickedSlot.getClass() != Slot.class) {
            return List.of();
        }

        for (Slot slot : menu.slots) {
            if (slot.container == clickedContainer && slot.getClass() == Slot.class) {
                sortableSlots.add(slot);
            }
        }

        return sortableSlots;
    }

    private static List<ItemStack> mergeStacks(List<ItemStack> sortedStacks) {
        List<ItemStack> mergedStacks = new ArrayList<>();
        for (ItemStack stack : sortedStacks) {
            ItemStack remaining = stack.copy();
            for (ItemStack mergedStack : mergedStacks) {
                if (!ItemStack.isSameItemSameComponents(mergedStack, remaining)) {
                    continue;
                }

                int transferable = Math.min(
                        remaining.getCount(),
                        mergedStack.getMaxStackSize() - mergedStack.getCount());
                if (transferable <= 0) {
                    continue;
                }

                mergedStack.grow(transferable);
                remaining.shrink(transferable);
                if (remaining.isEmpty()) {
                    break;
                }
            }

            if (!remaining.isEmpty()) {
                mergedStacks.add(remaining);
            }
        }

        return mergedStacks;
    }

    private static void fillSlots(List<Slot> slots, List<ItemStack> mergedStacks) {
        int stackIndex = 0;
        for (Slot slot : slots) {
            if (stackIndex >= mergedStacks.size()) {
                slot.set(ItemStack.EMPTY);
                continue;
            }

            ItemStack source = mergedStacks.get(stackIndex);
            int moveCount = Math.min(source.getCount(), slot.getMaxStackSize(source));
            ItemStack placed = source.copy();
            placed.setCount(moveCount);
            slot.set(placed);
            source.shrink(moveCount);

            if (source.isEmpty()) {
                stackIndex++;
            }
        }
    }

    private static SortKey createSortKey(ItemStack stack) {
        SortCategory category = resolveCategory(stack);
        return new SortKey(
                category.rank,
                resolveSubcategoryRank(category, stack),
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                String.valueOf(stack.getComponentsPatch()));
    }

    private static SortCategory resolveCategory(ItemStack stack) {
        if (isFood(stack)) {
            return SortCategory.FOOD;
        }

        if (isMaterial(stack)) {
            return SortCategory.MATERIALS;
        }

        if (isNatural(stack)) {
            return SortCategory.NATURALS;
        }

        if (isTool(stack)) {
            return SortCategory.TOOLS;
        }

        if (isCombat(stack)) {
            return SortCategory.COMBAT;
        }

        if (isUtility(stack)) {
            return SortCategory.UTILITIES;
        }

        if (isBlock(stack)) {
            return SortCategory.BLOCKS;
        }

        return SortCategory.UTILITIES;
    }

    private static int resolveSubcategoryRank(SortCategory category, ItemStack stack) {
        return switch (category) {
            case BLOCKS -> resolveBlockSubcategory(stack);
            case MATERIALS -> resolveMaterialSubcategory(stack);
            case COMBAT -> resolveCombatSubcategory(stack);
            case TOOLS -> resolveToolSubcategory(stack);
            case FOOD -> resolveFoodSubcategory(stack);
            case UTILITIES -> resolveUtilitySubcategory(stack);
            case NATURALS -> resolveNaturalSubcategory(stack);
        };
    }

    private static int resolveBlockSubcategory(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return 2;
        }

        Block block = blockItem.getBlock();
        if (isDecorationBlock(block)) {
            return 1;
        }

        if (isManufacturedBlock(block)) {
            return 2;
        }

        return 0;
    }

    private static int resolveMaterialSubcategory(ItemStack stack) {
        if (stack.is(Tags.Items.ORES) || stack.is(Tags.Items.RAW_MATERIALS) || isRawMaterialBlock(stack)) {
            return 0;
        }

        if (stack.is(Tags.Items.INGOTS) || stack.is(Tags.Items.NUGGETS) || stack.is(ItemTags.COALS)) {
            return 1;
        }

        if (stack.is(Tags.Items.GEMS)) {
            return 2;
        }

        if (stack.is(Tags.Items.DUSTS) || stack.is(Tags.Items.DYES) || stack.is(Items.BONE_MEAL)) {
            return 3;
        }

        return 4;
    }

    private static int resolveCombatSubcategory(ItemStack stack) {
        if (isMeleeWeapon(stack)) {
            return 0;
        }

        if (isRangedWeapon(stack)) {
            return 1;
        }

        if (isAmmunition(stack)) {
            return 2;
        }

        if (stack.getItem() instanceof ArmorItem || stack.is(Tags.Items.ARMORS)) {
            return 3;
        }

        if (stack.getItem() instanceof ShieldItem || stack.is(Tags.Items.TOOLS_SHIELD)) {
            return 4;
        }

        return 5;
    }

    private static int resolveToolSubcategory(ItemStack stack) {
        if (stack.getItem() instanceof PickaxeItem || stack.is(ItemTags.PICKAXES) || stack.canPerformAction(ItemAbilities.PICKAXE_DIG)) {
            return 0;
        }

        if (stack.getItem() instanceof AxeItem || stack.is(ItemTags.AXES) || stack.canPerformAction(ItemAbilities.AXE_DIG)) {
            return 1;
        }

        if (stack.getItem() instanceof ShovelItem || stack.is(ItemTags.SHOVELS) || stack.canPerformAction(ItemAbilities.SHOVEL_DIG)) {
            return 2;
        }

        if (stack.getItem() instanceof HoeItem || stack.is(ItemTags.HOES) || stack.canPerformAction(ItemAbilities.HOE_DIG) || stack.canPerformAction(ItemAbilities.HOE_TILL)) {
            return 3;
        }

        return 4;
    }

    private static int resolveFoodSubcategory(ItemStack stack) {
        if (stack.is(Tags.Items.FOODS_COOKED_MEAT)
                || stack.is(Tags.Items.FOODS_COOKED_FISH)
                || stack.is(Tags.Items.FOODS_BREAD)
                || stack.is(Tags.Items.FOODS_COOKIE)
                || stack.is(Tags.Items.FOODS_PIE)
                || stack.is(Tags.Items.FOODS_SOUP)
                || stack.is(Tags.Items.FOODS_CANDY)
                || stack.is(Tags.Items.FOODS_GOLDEN)) {
            return 0;
        }

        if (stack.is(Tags.Items.FOODS_RAW_MEAT)
                || stack.is(Tags.Items.FOODS_RAW_FISH)
                || stack.is(Tags.Items.FOODS_FRUIT)
                || stack.is(Tags.Items.FOODS_VEGETABLE)
                || stack.is(Tags.Items.FOODS_BERRY)) {
            return 1;
        }

        return 2;
    }

    private static int resolveUtilitySubcategory(ItemStack stack) {
        if (isStorageOrInteractionUtility(stack)) {
            return 0;
        }

        if (isLightingUtility(stack)) {
            return 1;
        }

        if (isFluidOrContainerUtility(stack)) {
            return 2;
        }

        return 3;
    }

    private static int resolveNaturalSubcategory(ItemStack stack) {
        if (stack.is(ItemTags.SAPLINGS) || isPropagule(stack)) {
            return 0;
        }

        if (stack.is(Tags.Items.SEEDS)) {
            return 1;
        }

        if (stack.is(ItemTags.FLOWERS) || isPlantBlock(stack)) {
            return 2;
        }

        if (stack.is(ItemTags.LEAVES) || isLeafOrVineBlock(stack)) {
            return 3;
        }

        if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.LOGS_THAT_BURN) || isNaturalLogBlock(stack)) {
            return 4;
        }

        return 5;
    }

    private static boolean isFood(ItemStack stack) {
        return stack.has(DataComponents.FOOD) || stack.is(Tags.Items.FOODS);
    }

    private static boolean isMaterial(ItemStack stack) {
        return stack.is(Tags.Items.ORES)
                || stack.is(Tags.Items.RAW_MATERIALS)
                || stack.is(Tags.Items.INGOTS)
                || stack.is(Tags.Items.NUGGETS)
                || stack.is(Tags.Items.GEMS)
                || stack.is(Tags.Items.DUSTS)
                || stack.is(Tags.Items.DYES)
                || stack.is(ItemTags.COALS)
                || isRawMaterialBlock(stack)
                || stack.is(Items.BONE)
                || stack.is(Items.BONE_MEAL)
                || stack.is(Items.STRING)
                || stack.is(Items.LEATHER)
                || stack.is(Items.PAPER)
                || stack.is(Items.GUNPOWDER)
                || stack.is(Items.SLIME_BALL)
                || stack.is(Items.CLAY_BALL)
                || stack.is(Items.BRICK)
                || stack.is(Items.NETHER_BRICK)
                || stack.is(Items.BLAZE_ROD)
                || stack.is(Items.PRISMARINE_SHARD)
                || stack.is(Items.PRISMARINE_CRYSTALS)
                || stack.is(Items.FLINT)
                || stack.is(Tags.Items.RODS_WOODEN);
    }

    private static boolean isNatural(ItemStack stack) {
        return stack.is(ItemTags.SAPLINGS)
                || stack.is(Tags.Items.SEEDS)
                || stack.is(ItemTags.FLOWERS)
                || stack.is(ItemTags.LEAVES)
                || stack.is(ItemTags.LOGS)
                || stack.is(ItemTags.LOGS_THAT_BURN)
                || isNaturalBlock(stack);
    }

    private static boolean isTool(ItemStack stack) {
        return stack.getItem() instanceof PickaxeItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof ShovelItem
                || stack.getItem() instanceof HoeItem
                || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES)
                || stack.canPerformAction(ItemAbilities.PICKAXE_DIG)
                || stack.canPerformAction(ItemAbilities.AXE_DIG)
                || stack.canPerformAction(ItemAbilities.SHOVEL_DIG)
                || stack.canPerformAction(ItemAbilities.HOE_DIG)
                || stack.canPerformAction(ItemAbilities.HOE_TILL);
    }

    private static boolean isCombat(ItemStack stack) {
        return isMeleeWeapon(stack)
                || isRangedWeapon(stack)
                || isAmmunition(stack)
                || stack.getItem() instanceof ArmorItem
                || stack.is(Tags.Items.ARMORS)
                || stack.getItem() instanceof ShieldItem
                || stack.is(Tags.Items.TOOLS_SHIELD);
    }

    private static boolean isUtility(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof BucketItem
                || item instanceof PotionItem
                || stack.canPerformAction(ItemAbilities.FISHING_ROD_CAST)
                || stack.canPerformAction(ItemAbilities.FIRESTARTER_LIGHT)
                || stack.canPerformAction(ItemAbilities.BRUSH_BRUSH)
                || stack.canPerformAction(ItemAbilities.SPYGLASS_SCOPE)
                || stack.canPerformAction(ItemAbilities.SHEARS_HARVEST)
                || stack.canPerformAction(ItemAbilities.SHEARS_CARVE)
                || stack.canPerformAction(ItemAbilities.SHEARS_TRIM)
                || stack.is(Items.GLASS_BOTTLE)
                || stack.is(Items.EXPERIENCE_BOTTLE)
                || isUtilityBlock(stack);
    }

    private static boolean isBlock(ItemStack stack) {
        return stack.getItem() instanceof BlockItem;
    }

    private static boolean isMeleeWeapon(ItemStack stack) {
        return stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof TridentItem
                || stack.is(ItemTags.SWORDS)
                || stack.is(Tags.Items.MELEE_WEAPON_TOOLS)
                || stack.is(Tags.Items.TOOLS_MACE)
                || stack.canPerformAction(ItemAbilities.SWORD_SWEEP);
    }

    private static boolean isRangedWeapon(ItemStack stack) {
        return stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem
                || stack.is(Tags.Items.RANGED_WEAPON_TOOLS)
                || stack.is(Tags.Items.TOOLS_BOW)
                || stack.is(Tags.Items.TOOLS_CROSSBOW)
                || stack.is(Tags.Items.TOOLS_SPEAR)
                || stack.canPerformAction(ItemAbilities.TRIDENT_THROW);
    }

    private static boolean isAmmunition(ItemStack stack) {
        return stack.is(ItemTags.ARROWS) || stack.is(Items.FIREWORK_ROCKET);
    }

    private static boolean isRawMaterialBlock(ItemStack stack) {
        return stack.is(Tags.Items.STORAGE_BLOCKS_RAW_COPPER)
                || stack.is(Tags.Items.STORAGE_BLOCKS_RAW_GOLD)
                || stack.is(Tags.Items.STORAGE_BLOCKS_RAW_IRON)
                || stack.is(Tags.Items.STORAGE_BLOCKS_BONE_MEAL)
                || stack.is(Tags.Items.STORAGE_BLOCKS_COAL)
                || stack.is(Tags.Items.STORAGE_BLOCKS_COPPER)
                || stack.is(Tags.Items.STORAGE_BLOCKS_DIAMOND)
                || stack.is(Tags.Items.STORAGE_BLOCKS_EMERALD)
                || stack.is(Tags.Items.STORAGE_BLOCKS_GOLD)
                || stack.is(Tags.Items.STORAGE_BLOCKS_IRON)
                || stack.is(Tags.Items.STORAGE_BLOCKS_LAPIS)
                || stack.is(Tags.Items.STORAGE_BLOCKS_NETHERITE)
                || stack.is(Tags.Items.STORAGE_BLOCKS_REDSTONE);
    }

    private static boolean isStorageOrInteractionUtility(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }

        Block block = blockItem.getBlock();
        return block instanceof ChestBlock
                || block instanceof BarrelBlock
                || block instanceof ShulkerBoxBlock
                || block instanceof EnderChestBlock
                || block instanceof CrafterBlock
                || block instanceof CraftingTableBlock
                || block instanceof AbstractFurnaceBlock
                || block instanceof HopperBlock
                || block instanceof DispenserBlock
                || block instanceof DropperBlock
                || block instanceof AnvilBlock
                || block instanceof BrewingStandBlock
                || block instanceof EnchantingTableBlock
                || block instanceof SmithingTableBlock
                || block instanceof CartographyTableBlock
                || block instanceof GrindstoneBlock
                || block instanceof StonecutterBlock
                || block instanceof BeaconBlock
                || block instanceof BellBlock
                || block instanceof RespawnAnchorBlock;
    }

    private static boolean isLightingUtility(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }

        Block block = blockItem.getBlock();
        return block instanceof TorchBlock
                || block instanceof LanternBlock
                || block instanceof CampfireBlock
                || block instanceof CandleBlock
                || block instanceof CandleCakeBlock
                || block instanceof LightBlock;
    }

    private static boolean isFluidOrContainerUtility(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof BucketItem
                || item instanceof PotionItem
                || stack.is(Items.GLASS_BOTTLE)
                || stack.is(Items.EXPERIENCE_BOTTLE);
    }

    private static boolean isUtilityBlock(ItemStack stack) {
        return isStorageOrInteractionUtility(stack) || isLightingUtility(stack);
    }

    private static boolean isNaturalBlock(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }

        Block block = blockItem.getBlock();
        return block instanceof SaplingBlock
                || block instanceof MangrovePropaguleBlock
                || block instanceof FlowerBlock
                || block instanceof PinkPetalsBlock
                || block instanceof BushBlock
                || block instanceof DeadBushBlock
                || block instanceof LeavesBlock
                || block instanceof VineBlock
                || block instanceof MossBlock
                || block instanceof GrowingPlantBlock
                || block instanceof KelpBlock
                || block instanceof KelpPlantBlock
                || block instanceof SeaPickleBlock
                || block instanceof MangroveRootsBlock
                || stack.is(ItemTags.LOGS)
                || stack.is(ItemTags.LOGS_THAT_BURN);
    }

    private static boolean isPlantBlock(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }

        Block block = blockItem.getBlock();
        return block instanceof FlowerBlock
                || block instanceof BushBlock
                || block instanceof PinkPetalsBlock
                || block instanceof GrowingPlantBlock
                || block instanceof KelpBlock
                || block instanceof KelpPlantBlock
                || block instanceof SeaPickleBlock;
    }

    private static boolean isLeafOrVineBlock(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }

        Block block = blockItem.getBlock();
        return block instanceof LeavesBlock
                || block instanceof VineBlock
                || block instanceof MossBlock;
    }

    private static boolean isNaturalLogBlock(ItemStack stack) {
        return stack.is(ItemTags.LOGS) || stack.is(ItemTags.LOGS_THAT_BURN);
    }

    private static boolean isPropagule(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }

        return blockItem.getBlock() instanceof MangrovePropaguleBlock;
    }

    private static boolean isDecorationBlock(Block block) {
        return block instanceof SlabBlock
                || block instanceof StairBlock
                || block instanceof WallBlock
                || block instanceof FenceBlock
                || block instanceof FenceGateBlock
                || block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof CarpetBlock
                || block instanceof BannerBlock
                || block instanceof BedBlock
                || block instanceof BaseRailBlock
                || block instanceof IronBarsBlock;
    }

    private static boolean isManufacturedBlock(Block block) {
        return block instanceof CraftingTableBlock
                || block instanceof SmithingTableBlock
                || block instanceof StonecutterBlock
                || block instanceof GrindstoneBlock
                || block instanceof CartographyTableBlock;
    }

    private enum SortCategory {
        BLOCKS(0),
        MATERIALS(1),
        COMBAT(2),
        TOOLS(3),
        FOOD(4),
        UTILITIES(5),
        NATURALS(6);

        private final int rank;

        SortCategory(int rank) {
            this.rank = rank;
        }
    }

    private static final class SortKey implements Comparable<SortKey> {
        private final int categoryRank;
        private final int subcategoryRank;
        private final String itemRegistryId;
        private final String componentsPatch;

        private SortKey(int categoryRank, int subcategoryRank, String itemRegistryId, String componentsPatch) {
            this.categoryRank = categoryRank;
            this.subcategoryRank = subcategoryRank;
            this.itemRegistryId = itemRegistryId;
            this.componentsPatch = componentsPatch;
        }

        @Override
        public int compareTo(SortKey other) {
            int categoryComparison = Integer.compare(this.categoryRank, other.categoryRank);
            if (categoryComparison != 0) {
                return categoryComparison;
            }

            int subcategoryComparison = Integer.compare(this.subcategoryRank, other.subcategoryRank);
            if (subcategoryComparison != 0) {
                return subcategoryComparison;
            }

            int itemComparison = this.itemRegistryId.compareTo(other.itemRegistryId);
            if (itemComparison != 0) {
                return itemComparison;
            }

            return this.componentsPatch.compareTo(other.componentsPatch);
        }
    }
}
