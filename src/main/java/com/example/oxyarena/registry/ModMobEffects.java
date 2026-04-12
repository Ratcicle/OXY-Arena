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
    public static final DeferredHolder<MobEffect, MobEffect> AMETRA_AWAKENING = MOB_EFFECTS.register(
            "ametra_awakening",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x8E67FF) {
            }.addAttributeModifier(
                    Attributes.ATTACK_DAMAGE,
                    ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "effect.ametra_awakening.damage"),
                    3.0D,
                    AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(
                            Attributes.ATTACK_SPEED,
                            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "effect.ametra_awakening.speed"),
                            -0.3D,
                            AttributeModifier.Operation.ADD_VALUE));
    public static final DeferredHolder<MobEffect, MobEffect> KUSABIMARU_STUN = MOB_EFFECTS.register(
            "kusabimaru_stun",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0xC89A4D) {
            }.addAttributeModifier(
                    Attributes.MOVEMENT_SPEED,
                    ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "effect.kusabimaru_stun.speed"),
                    -1.0D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(
                            Attributes.ATTACK_SPEED,
                            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "effect.kusabimaru_stun.attack_speed"),
                            -1.0D,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    public static final DeferredHolder<MobEffect, MobEffect> OCCULT_CAMOUFLAGE = MOB_EFFECTS.register(
            "occult_camouflage",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x8FE9F1) {
            });

    private ModMobEffects() {
    }

    public static void register(IEventBus modEventBus) {
        MOB_EFFECTS.register(modEventBus);
    }
}
