package com.example.oxyarena.registry;

import com.example.oxyarena.OXYArena;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMobEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(
            Registries.MOB_EFFECT,
            OXYArena.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> CITRINE_BLADE_RUSH = MOB_EFFECTS.register(
            "citrine_blade_rush",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xF7D35A) {
            }.addAttributeModifier(
                    Attributes.ATTACK_SPEED,
                    ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "effect.citrine_blade_rush"),
                    1.4D,
                    AttributeModifier.Operation.ADD_VALUE));
    public static final DeferredHolder<MobEffect, MobEffect> LIFEHUNT_BLOODLUST = MOB_EFFECTS.register(
            "lifehunt_bloodlust",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x8B1F3D) {
            });

    private ModMobEffects() {
    }

    public static void register(IEventBus modEventBus) {
        MOB_EFFECTS.register(modEventBus);
    }
}
