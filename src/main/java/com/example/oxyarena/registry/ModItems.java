package com.example.oxyarena.registry;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.item.CitrinePickaxeItem;
import com.example.oxyarena.item.CitrineSwordItem;
import com.example.oxyarena.item.CitrineThrowingDaggerItem;
import com.example.oxyarena.item.CobaltBowItem;
import com.example.oxyarena.item.FlamingScytheItem;
import com.example.oxyarena.item.GrapplingGunItem;
import com.example.oxyarena.item.LifehuntScytheItem;
import com.example.oxyarena.item.SmokeBombItem;
import com.example.oxyarena.item.ZeusLightningItem;

import net.minecraft.core.Holder;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(OXYArena.MODID);

    public static final DeferredItem<BlockItem> CITRINE_ORE = ITEMS.registerSimpleBlockItem("citrine_ore", ModBlocks.CITRINE_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_CITRINE_ORE = ITEMS.registerSimpleBlockItem("deepslate_citrine_ore", ModBlocks.DEEPSLATE_CITRINE_ORE);
    public static final DeferredItem<BlockItem> COBALT_ORE = ITEMS.registerSimpleBlockItem("cobalt_ore", ModBlocks.COBALT_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_COBALT_ORE = ITEMS.registerSimpleBlockItem("deepslate_cobalt_ore", ModBlocks.DEEPSLATE_COBALT_ORE);
    public static final DeferredItem<Item> CITRINE_GEM = ITEMS.registerSimpleItem("citrine_gem");
    public static final DeferredItem<Item> RAW_COBALT = ITEMS.registerSimpleItem("raw_cobalt");
    public static final DeferredItem<Item> COBALT_INGOT = ITEMS.registerSimpleItem("cobalt_ingot");
    public static final DeferredItem<CitrineSwordItem> CITRINE_SWORD = ITEMS.registerItem(
            "citrine_sword",
            properties -> new CitrineSwordItem(
                    ModToolTiers.CITRINE,
                    properties.attributes(SwordItem.createAttributes(ModToolTiers.CITRINE, 2.7F, -2.4F))));
    public static final DeferredItem<CitrinePickaxeItem> CITRINE_PICKAXE = ITEMS.registerItem(
            "citrine_pickaxe",
            properties -> new CitrinePickaxeItem(
                    ModToolTiers.CITRINE,
                    properties.attributes(PickaxeItem.createAttributes(ModToolTiers.CITRINE, 1.0F, -2.8F))));
    public static final DeferredItem<AxeItem> CITRINE_AXE = ITEMS.registerItem(
            "citrine_axe",
            properties -> new AxeItem(
                    ModToolTiers.CITRINE,
                    properties.attributes(AxeItem.createAttributes(ModToolTiers.CITRINE, 6.0F, -3.1F))));
    public static final DeferredItem<ShovelItem> CITRINE_SHOVEL = ITEMS.registerItem(
            "citrine_shovel",
            properties -> new ShovelItem(
                    ModToolTiers.CITRINE,
                    properties.attributes(ShovelItem.createAttributes(ModToolTiers.CITRINE, 1.5F, -3.0F))));
    public static final DeferredItem<ArmorItem> CITRINE_HELMET = registerArmorItem(
            "citrine_helmet",
            ModArmorMaterials.CITRINE,
            ArmorItem.Type.HELMET,
            ModArmorMaterials.CITRINE_DURABILITY_FACTOR);
    public static final DeferredItem<ArmorItem> CITRINE_CHESTPLATE = registerArmorItem(
            "citrine_chestplate",
            ModArmorMaterials.CITRINE,
            ArmorItem.Type.CHESTPLATE,
            ModArmorMaterials.CITRINE_DURABILITY_FACTOR);
    public static final DeferredItem<ArmorItem> CITRINE_LEGGINGS = registerArmorItem(
            "citrine_leggings",
            ModArmorMaterials.CITRINE,
            ArmorItem.Type.LEGGINGS,
            ModArmorMaterials.CITRINE_DURABILITY_FACTOR);
    public static final DeferredItem<ArmorItem> CITRINE_BOOTS = registerArmorItem(
            "citrine_boots",
            ModArmorMaterials.CITRINE,
            ArmorItem.Type.BOOTS,
            ModArmorMaterials.CITRINE_DURABILITY_FACTOR);
    public static final DeferredItem<CitrineThrowingDaggerItem> CITRINE_THROWING_DAGGER = ITEMS.registerItem(
            "citrine_throwing_dagger",
            CitrineThrowingDaggerItem::new,
            new Item.Properties().stacksTo(16));
    public static final DeferredItem<SmokeBombItem> SMOKE_BOMB = ITEMS.registerItem(
            "smoke_bomb",
            SmokeBombItem::new,
            new Item.Properties().stacksTo(16));
    public static final DeferredItem<ZeusLightningItem> ZEUS_LIGHTNING = ITEMS.registerItem(
            "zeus_lightning",
            ZeusLightningItem::new,
            new Item.Properties()
                    .stacksTo(1)
                    .attributes(ZeusLightningItem.createAttributes()));
    public static final DeferredItem<FlamingScytheItem> FLAMING_SCYTHE = ITEMS.registerItem(
            "flaming_scythe",
            properties -> new FlamingScytheItem(
                    ModToolTiers.COBALT,
                    properties.attributes(SwordItem.createAttributes(ModToolTiers.COBALT, 6.2F, -2.8F))));
    public static final DeferredItem<LifehuntScytheItem> LIFEHUNT_SCYTHE = ITEMS.registerItem(
            "lifehunt_scythe",
            properties -> new LifehuntScytheItem(
                    ModToolTiers.COBALT,
                    properties.attributes(SwordItem.createAttributes(ModToolTiers.COBALT, 7.2F, -3.2F))));
    public static final DeferredItem<SwordItem> COBALT_SWORD = ITEMS.registerItem(
            "cobalt_sword",
            properties -> new SwordItem(
                    ModToolTiers.COBALT,
                    properties.attributes(SwordItem.createAttributes(ModToolTiers.COBALT, 3.0F, -2.4F))));
    public static final DeferredItem<PickaxeItem> COBALT_PICKAXE = ITEMS.registerItem(
            "cobalt_pickaxe",
            properties -> new PickaxeItem(
                    ModToolTiers.COBALT,
                    properties.attributes(PickaxeItem.createAttributes(ModToolTiers.COBALT, 1.0F, -2.8F))));
    public static final DeferredItem<AxeItem> COBALT_AXE = ITEMS.registerItem(
            "cobalt_axe",
            properties -> new AxeItem(
                    ModToolTiers.COBALT,
                    properties.attributes(AxeItem.createAttributes(ModToolTiers.COBALT, 5.0F, -3.0F))));
    public static final DeferredItem<ShovelItem> COBALT_SHOVEL = ITEMS.registerItem(
            "cobalt_shovel",
            properties -> new ShovelItem(
                    ModToolTiers.COBALT,
                    properties.attributes(ShovelItem.createAttributes(ModToolTiers.COBALT, 1.5F, -3.0F))));
    public static final DeferredItem<ArmorItem> COBALT_HELMET = registerArmorItem(
            "cobalt_helmet",
            ModArmorMaterials.COBALT,
            ArmorItem.Type.HELMET,
            ModArmorMaterials.COBALT_DURABILITY_FACTOR);
    public static final DeferredItem<ArmorItem> COBALT_CHESTPLATE = registerArmorItem(
            "cobalt_chestplate",
            ModArmorMaterials.COBALT,
            ArmorItem.Type.CHESTPLATE,
            ModArmorMaterials.COBALT_DURABILITY_FACTOR);
    public static final DeferredItem<ArmorItem> COBALT_LEGGINGS = registerArmorItem(
            "cobalt_leggings",
            ModArmorMaterials.COBALT,
            ArmorItem.Type.LEGGINGS,
            ModArmorMaterials.COBALT_DURABILITY_FACTOR);
    public static final DeferredItem<ArmorItem> COBALT_BOOTS = registerArmorItem(
            "cobalt_boots",
            ModArmorMaterials.COBALT,
            ArmorItem.Type.BOOTS,
            ModArmorMaterials.COBALT_DURABILITY_FACTOR);
    public static final DeferredItem<CobaltBowItem> COBALT_BOW = ITEMS.registerItem(
            "cobalt_bow",
            CobaltBowItem::new,
            new Item.Properties().durability(768));
    public static final DeferredItem<GrapplingGunItem> GRAPPLING_GUN = ITEMS.registerItem(
            "grappling_gun",
            GrapplingGunItem::new,
            new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> GRAPPLING_HOOK = ITEMS.registerSimpleItem("grappling_hook");

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    private static DeferredItem<ArmorItem> registerArmorItem(
            String name,
            Holder<ArmorMaterial> material,
            ArmorItem.Type type,
            int durabilityFactor) {
        return ITEMS.registerItem(
                name,
                properties -> new ArmorItem(
                        material,
                        type,
                        properties.durability(type.getDurability(durabilityFactor))));
    }
}
