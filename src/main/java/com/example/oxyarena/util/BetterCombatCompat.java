package com.example.oxyarena.util;

import java.lang.reflect.Field;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public final class BetterCombatCompat {
    private static final String BETTER_COMBAT_MOD_ID = "bettercombat";
    private static final String COMPONENT_HOLDER_CLASS = "net.bettercombat.api.component.BetterCombatDataComponents";
    private static final String WEAPON_PRESET_FIELD = "WEAPON_PRESET_ID";

    private static boolean componentResolved;
    @Nullable
    private static DataComponentType<ResourceLocation> weaponPresetComponent;

    private BetterCombatCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(BETTER_COMBAT_MOD_ID);
    }

    public static boolean canOverrideWeaponPreset() {
        return getWeaponPresetComponent() != null;
    }

    public static void setWeaponPreset(ItemStack stack, ResourceLocation presetId) {
        DataComponentType<ResourceLocation> component = getWeaponPresetComponent();
        if (component == null) {
            return;
        }

        stack.set(component, presetId);
    }

    public static void clearWeaponPreset(ItemStack stack) {
        DataComponentType<ResourceLocation> component = getWeaponPresetComponent();
        if (component == null) {
            return;
        }

        stack.remove(component);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static DataComponentType<ResourceLocation> getWeaponPresetComponent() {
        if (componentResolved) {
            return weaponPresetComponent;
        }

        componentResolved = true;
        if (!isLoaded()) {
            return null;
        }

        try {
            Class<?> holderClass = Class.forName(COMPONENT_HOLDER_CLASS);
            Field field = holderClass.getField(WEAPON_PRESET_FIELD);
            Object value = field.get(null);
            if (value instanceof DataComponentType<?> component) {
                weaponPresetComponent = (DataComponentType<ResourceLocation>)component;
            }
        } catch (ReflectiveOperationException ignored) {
            weaponPresetComponent = null;
        }

        return weaponPresetComponent;
    }
}
