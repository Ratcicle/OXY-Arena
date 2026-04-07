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

    private ModSoundEvents() {
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
