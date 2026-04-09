package com.example.oxyarena.registry;

import com.example.oxyarena.OXYArena;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSoundEvents {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(
            Registries.SOUND_EVENT,
            OXYArena.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> PING = SOUND_EVENTS.register(
            "ping",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "ping")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DEFLECT = SOUND_EVENTS.register(
            "deflect",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "deflect")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DEFLECT2 = SOUND_EVENTS.register(
            "deflect2",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "deflect2")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DEFLECT3 = SOUND_EVENTS.register(
            "deflect3",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "deflect3")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DEFLECT4 = SOUND_EVENTS.register(
            "deflect4",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "deflect4")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DEFLECT5 = SOUND_EVENTS.register(
            "deflect5",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "deflect5")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DEFLECT6 = SOUND_EVENTS.register(
            "deflect6",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "deflect6")));

    private ModSoundEvents() {
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
