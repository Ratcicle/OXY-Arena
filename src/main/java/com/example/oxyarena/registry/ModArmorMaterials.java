package com.example.oxyarena.registry;

import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;

import com.example.oxyarena.OXYArena;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModArmorMaterials {
    public static final int CITRINE_DURABILITY_FACTOR = 13;
    public static final int OCCULT_DURABILITY_FACTOR = 25;
    public static final int COBALT_DURABILITY_FACTOR = 30;

    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(
            Registries.ARMOR_MATERIAL,
            OXYArena.MODID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> CITRINE = ARMOR_MATERIALS.register(
            "citrine",
            () -> new ArmorMaterial(
                    createDefenseMap(2, 5, 5, 2, 5),
                    14,
                    SoundEvents.ARMOR_EQUIP_IRON,
                    () -> Ingredient.of(ModItems.CITRINE_GEM.get()),
                    List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "citrine"))),
                    0.0F,
                    0.0F));
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> COBALT = ARMOR_MATERIALS.register(
            "cobalt",
            () -> new ArmorMaterial(
                    createDefenseMap(3, 6, 7, 3, 7),
                    12,
                    SoundEvents.ARMOR_EQUIP_DIAMOND,
                    () -> Ingredient.of(ModItems.COBALT_INGOT.get()),
                    List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "cobalt"))),
                    1.5F,
                    0.0F));
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> OCCULT = ARMOR_MATERIALS.register(
            "occult",
            () -> new ArmorMaterial(
                    createDefenseMap(3, 6, 8, 3, 8),
                    18,
                    SoundEvents.ARMOR_EQUIP_CHAIN,
                    () -> Ingredient.of(Items.AMETHYST_SHARD, ModItems.AMETRA_GEM.get()),
                    List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "occult"))),
                    0.0F,
                    0.0F));

    private ModArmorMaterials() {
    }

    public static void register(IEventBus modEventBus) {
        ARMOR_MATERIALS.register(modEventBus);
    }

    private static EnumMap<ArmorItem.Type, Integer> createDefenseMap(
            int boots,
            int leggings,
            int chestplate,
            int helmet,
            int body) {
        EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.BOOTS, boots);
        defense.put(ArmorItem.Type.LEGGINGS, leggings);
        defense.put(ArmorItem.Type.CHESTPLATE, chestplate);
        defense.put(ArmorItem.Type.HELMET, helmet);
        defense.put(ArmorItem.Type.BODY, body);
        return defense;
    }
}
