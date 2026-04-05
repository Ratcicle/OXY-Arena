package com.example.oxyarena.registry;

import com.example.oxyarena.OXYArena;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticleTypes {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(
            Registries.PARTICLE_TYPE,
            OXYArena.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> NEVOA_BORDER = PARTICLE_TYPES.register(
            "nevoa_border",
            () -> new SimpleParticleType(true));

    private ModParticleTypes() {
    }

    public static void register(IEventBus modEventBus) {
        PARTICLE_TYPES.register(modEventBus);
    }
}
