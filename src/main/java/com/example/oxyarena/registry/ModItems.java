package com.example.oxyarena.registry;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.item.AmetraSwordItem;
import com.example.oxyarena.item.AssassinDaggerItem;
import com.example.oxyarena.item.BlackDiamondSwordItem;
import com.example.oxyarena.item.ChocolateSwordItem;
import com.example.oxyarena.item.CobaltSwordItem;
import com.example.oxyarena.item.CitrinePickaxeItem;
import com.example.oxyarena.item.CitrineSwordItem;
import com.example.oxyarena.item.CitrineThrowingDaggerItem;
import com.example.oxyarena.item.CobaltBowItem;
import com.example.oxyarena.item.CobaltShieldItem;
import com.example.oxyarena.item.FlamingScytheItem;
import com.example.oxyarena.item.ElementalGauntletItem;
import com.example.oxyarena.item.EstusFlaskItem;
import com.example.oxyarena.item.GrapplingGunItem;
import com.example.oxyarena.item.IncandescentAxeItem;
import com.example.oxyarena.item.IncandescentIngotItem;
import com.example.oxyarena.item.IncandescentPickaxeItem;
import com.example.oxyarena.item.IncandescentSwordItem;
import com.example.oxyarena.item.IncandescentThrowingDaggerItem;
import com.example.oxyarena.item.KusabimaruItem;
import com.example.oxyarena.item.LifehuntScytheItem;
import com.example.oxyarena.item.MurasamaItem;
import com.example.oxyarena.item.RiversOfBloodItem;
import com.example.oxyarena.item.SoulReaperItem;
import com.example.oxyarena.item.SmokeBombItem;
import com.example.oxyarena.item.SpectralBladeItem;
import com.example.oxyarena.item.StormChargeItem;
import com.example.oxyarena.item.ZenithItem;
import com.example.oxyarena.item.ZeusLightningItem;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(OXYArena.MODID);

    public static final DeferredItem<BlockItem> CITRINE_ORE = ITEMS.registerSimpleBlockItem("citrine_ore", ModBlocks.CITRINE_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_CITRINE_ORE = ITEMS.registerSimpleBlockItem("deepslate_citrine_ore", ModBlocks.DEEPSLATE_CITRINE_ORE);
    public static final DeferredItem<BlockItem> COBALT_ORE = ITEMS.registerSimpleBlockItem("cobalt_ore", ModBlocks.COBALT_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_COBALT_ORE = ITEMS.registerSimpleBlockItem("deepslate_cobalt_ore", ModBlocks.DEEPSLATE_COBALT_ORE);
    public static final DeferredItem<BlockItem> AMETRA_ORE = ITEMS.registerSimpleBlockItem("ametra_ore", ModBlocks.AMETRA_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_AMETRA_ORE = ITEMS.registerSimpleBlockItem("deepslate_ametra_ore", ModBlocks.DEEPSLATE_AMETRA_ORE);
    public static final DeferredItem<BlockItem> INCANDESCENT_ORE = ITEMS.registerSimpleBlockItem("incandescent_ore", ModBlocks.INCANDESCENT_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_INCANDESCENT_ORE = ITEMS.registerSimpleBlockItem("deepslate_incandescent_ore", ModBlocks.DEEPSLATE_INCANDESCENT_ORE);
    public static final DeferredItem<Item> CITRINE_GEM = ITEMS.registerSimpleItem("citrine_gem");
    public static final DeferredItem<Item> AMETRA_GEM = ITEMS.registerSimpleItem("ametra_gem");
    public static final DeferredItem<Item> RAW_COBALT = ITEMS.registerSimpleItem("raw_cobalt");
    public static final DeferredItem<Item> COBALT_INGOT = ITEMS.registerSimpleItem("cobalt_ingot");
    public static final DeferredItem<IncandescentIngotItem> INCANDESCENT_INGOT = ITEMS.registerItem(
            "incandescent_ingot",
            IncandescentIngotItem::new,
            new Item.Properties());
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
    public static final DeferredItem<ArmorItem> OCCULT_HELMET = registerArmorItem(
            "occult_helmet",
            ModArmorMaterials.OCCULT,
            ArmorItem.Type.HELMET,
            ModArmorMaterials.OCCULT_DURABILITY_FACTOR);
    public static final DeferredItem<ArmorItem> OCCULT_CHESTPLATE = registerArmorItem(
            "occult_chestplate",
            ModArmorMaterials.OCCULT,
            ArmorItem.Type.CHESTPLATE,
            ModArmorMaterials.OCCULT_DURABILITY_FACTOR);
    public static final DeferredItem<ArmorItem> OCCULT_LEGGINGS = registerArmorItem(
            "occult_leggings",
            ModArmorMaterials.OCCULT,
            ArmorItem.Type.LEGGINGS,
            ModArmorMaterials.OCCULT_DURABILITY_FACTOR);
    public static final DeferredItem<ArmorItem> OCCULT_BOOTS = registerArmorItem(
            "occult_boots",
            ModArmorMaterials.OCCULT,
            ArmorItem.Type.BOOTS,
            ModArmorMaterials.OCCULT_DURABILITY_FACTOR);
    public static final DeferredItem<CitrineThrowingDaggerItem> CITRINE_THROWING_DAGGER = ITEMS.registerItem(
            "citrine_throwing_dagger",
            CitrineThrowingDaggerItem::new,
            new Item.Properties().stacksTo(16));
    public static final DeferredItem<IncandescentThrowingDaggerItem> INCANDESCENT_THROWING_DAGGER = ITEMS.registerItem(
            "incandescent_throwing_dagger",
            IncandescentThrowingDaggerItem::new,
            new Item.Properties().stacksTo(16));
    public static final DeferredItem<SmokeBombItem> SMOKE_BOMB = ITEMS.registerItem(
            "smoke_bomb",
            SmokeBombItem::new,
            new Item.Properties().stacksTo(16));
    public static final DeferredItem<EstusFlaskItem> ESTUS_FLASK = ITEMS.registerItem(
            "estus_flask",
            EstusFlaskItem::new,
            new Item.Properties().stacksTo(16));
    public static final DeferredItem<StormChargeItem> STORM_CHARGE = ITEMS.registerItem(
            "storm_charge",
            StormChargeItem::new,
            new Item.Properties());
    public static final DeferredItem<ZeusLightningItem> ZEUS_LIGHTNING = ITEMS.registerItem(
            "zeus_lightning",
            ZeusLightningItem::new,
            new Item.Properties()
                    .stacksTo(1)
                    .attributes(ZeusLightningItem.createAttributes()));
    public static final DeferredItem<ElementalGauntletItem> ELEMENTAL_GAUNTLET = ITEMS.registerItem(
            "elemental_gauntlet",
            ElementalGauntletItem::new,
            new Item.Properties()
                    .stacksTo(1)
                    .durability(910));
    public static final DeferredItem<FlamingScytheItem> FLAMING_SCYTHE = ITEMS.registerItem(
            "flaming_scythe",
            properties -> new FlamingScytheItem(
                    ModToolTiers.COBALT,
                    properties.attributes(SwordItem.createAttributes(ModToolTiers.COBALT, 5.2F, -2.8F))));
    public static final DeferredItem<LifehuntScytheItem> LIFEHUNT_SCYTHE = ITEMS.registerItem(
            "lifehunt_scythe",
            properties -> new LifehuntScytheItem(
                    ModToolTiers.COBALT,
                    properties.attributes(SwordItem.createAttributes(ModToolTiers.COBALT, 6.2F, -3.2F))));
    public static final DeferredItem<CobaltSwordItem> COBALT_SWORD = ITEMS.registerItem(
            "cobalt_sword",
            properties -> new CobaltSwordItem(
                    ModToolTiers.COBALT,
                    properties.attributes(SwordItem.createAttributes(ModToolTiers.COBALT, 3.0F, -2.4F))));
    public static final DeferredItem<IncandescentSwordItem> INCANDESCENT_SWORD = ITEMS.registerItem(
            "incandescent_sword",
            properties -> new IncandescentSwordItem(
                    ModToolTiers.INCANDESCENT,
                    properties.attributes(SwordItem.createAttributes(ModToolTiers.INCANDESCENT, 3.0F, -2.4F))));
    public static final DeferredItem<AmetraSwordItem> AMETRA_SWORD = ITEMS.registerItem(
            "ametra_sword",
            properties -> new AmetraSwordItem(
                    ModToolTiers.AMETRA,
                    properties.attributes(SwordItem.createAttributes(ModToolTiers.AMETRA, 2.0F, -2.3F))));
    public static final DeferredItem<MurasamaItem> MURASAMA = ITEMS.registerItem(
            "murasama",
            properties -> new MurasamaItem(
                    ModToolTiers.AMETRA,
                    properties.attributes(SwordItem.createAttributes(ModToolTiers.AMETRA, 2.0F, -2.1F))));
    public static final DeferredItem<RiversOfBloodItem> RIVERS_OF_BLOOD = ITEMS.registerItem(
            "rivers_of_blood",
            properties -> new RiversOfBloodItem(
                    ModToolTiers.AMETRA,
                    properties.attributes(SwordItem.createAttributes(ModToolTiers.AMETRA, 2.0F, -2.4F))));
    public static final DeferredItem<KusabimaruItem> KUSABIMARU = ITEMS.registerItem(
            "kusabimaru",
            properties -> new KusabimaruItem(
                    ModToolTiers.AMETRA,
                    properties.attributes(SwordItem.createAttributes(ModToolTiers.AMETRA, 1.5F, -2.3F))));
    public static final DeferredItem<SoulReaperItem> SOUL_REAPER = ITEMS.registerItem(
            "soul_reaper",
            properties -> new SoulReaperItem(
                    ModToolTiers.AMETRA,
                    properties.attributes(SwordItem.createAttributes(ModToolTiers.AMETRA, 2.0F, -3.0F))));
    public static final DeferredItem<SpectralBladeItem> SPECTRAL_BLADE = ITEMS.registerItem(
            "spectral_blade",
            properties -> new SpectralBladeItem(
                    Tiers.IRON,
                    properties.attributes(SpectralBladeItem.createAttributes())));
    public static final DeferredItem<AssassinDaggerItem> ASSASSIN_DAGGER = ITEMS.registerItem(
            "assassin_dagger",
            properties -> new AssassinDaggerItem(
                    Tiers.IRON,
                    properties.attributes(AssassinDaggerItem.createAttributes())));
    public static final DeferredItem<BlackDiamondSwordItem> BLACK_DIAMOND_SWORD = ITEMS.registerItem(
            "black_diamond_sword",
            properties -> new BlackDiamondSwordItem(
                    Tiers.DIAMOND,
                    properties.attributes(SwordItem.createAttributes(Tiers.DIAMOND, 4.0F, -2.4F))));
    public static final DeferredItem<ChocolateSwordItem> CHOCOLATE_SWORD = ITEMS.registerItem(
            "chocolate_sword",
            properties -> new ChocolateSwordItem(
                    Tiers.IRON,
                    properties.attributes(SwordItem.createAttributes(Tiers.IRON, 3.0F, -2.4F))));
    public static final DeferredItem<ZenithItem> ZENITH = ITEMS.registerItem(
            "zenith",
            properties -> new ZenithItem(
                    ModToolTiers.AMETRA,
                    properties.attributes(SwordItem.createAttributes(ModToolTiers.AMETRA, 2.0F, -2.4F))));
    public static final DeferredItem<PickaxeItem> COBALT_PICKAXE = ITEMS.registerItem(
            "cobalt_pickaxe",
            properties -> new PickaxeItem(
                    ModToolTiers.COBALT,
                    properties.attributes(PickaxeItem.createAttributes(ModToolTiers.COBALT, 1.0F, -2.8F))));
    public static final DeferredItem<IncandescentPickaxeItem> INCANDESCENT_PICKAXE = ITEMS.registerItem(
            "incandescent_pickaxe",
            properties -> new IncandescentPickaxeItem(
                    ModToolTiers.INCANDESCENT,
                    properties.attributes(PickaxeItem.createAttributes(ModToolTiers.INCANDESCENT, 1.0F, -2.8F))));
    public static final DeferredItem<PickaxeItem> AMETRA_PICKAXE = ITEMS.registerItem(
            "ametra_pickaxe",
            properties -> new PickaxeItem(
                    ModToolTiers.AMETRA,
                    properties.attributes(PickaxeItem.createAttributes(ModToolTiers.AMETRA, 1.0F, -2.8F))));
    public static final DeferredItem<AxeItem> COBALT_AXE = ITEMS.registerItem(
            "cobalt_axe",
            properties -> new AxeItem(
                    ModToolTiers.COBALT,
                    properties.attributes(AxeItem.createAttributes(ModToolTiers.COBALT, 5.0F, -3.0F))));
    public static final DeferredItem<IncandescentAxeItem> INCANDESCENT_AXE = ITEMS.registerItem(
            "incandescent_axe",
            properties -> new IncandescentAxeItem(
                    ModToolTiers.INCANDESCENT,
                    properties.attributes(AxeItem.createAttributes(ModToolTiers.INCANDESCENT, 5.0F, -3.0F))));
    public static final DeferredItem<AxeItem> AMETRA_AXE = ITEMS.registerItem(
            "ametra_axe",
            properties -> new AxeItem(
                    ModToolTiers.AMETRA,
                    properties.attributes(AxeItem.createAttributes(ModToolTiers.AMETRA, 5.0F, -3.0F))));
    public static final DeferredItem<ShovelItem> COBALT_SHOVEL = ITEMS.registerItem(
            "cobalt_shovel",
            properties -> new ShovelItem(
                    ModToolTiers.COBALT,
                    properties.attributes(ShovelItem.createAttributes(ModToolTiers.COBALT, 1.5F, -3.0F))));
    public static final DeferredItem<ShovelItem> AMETRA_SHOVEL = ITEMS.registerItem(
            "ametra_shovel",
            properties -> new ShovelItem(
                    ModToolTiers.AMETRA,
                    properties.attributes(ShovelItem.createAttributes(ModToolTiers.AMETRA, 1.5F, -3.0F))));
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
    public static final DeferredItem<ShieldItem> COBALT_SHIELD = ITEMS.registerItem(
            "cobalt_shield",
            CobaltShieldItem::new,
            new Item.Properties()
                    .durability(750)
                    .component(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY));
    public static final DeferredItem<GrapplingGunItem> GRAPPLING_GUN = ITEMS.registerItem(
            "grappling_gun",
            GrapplingGunItem::new,
            new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> GRAPPLING_HOOK = ITEMS.registerSimpleItem("grappling_hook");

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(ModItems::modifyDefaultComponents);
    }

    private static void modifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        event.modify(Items.SHIELD, builder -> builder
                .set(DataComponents.MAX_DAMAGE, 20)
                .set(DataComponents.MAX_STACK_SIZE, 1));
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
