package com.example.oxyarena.registry;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.block.entity.OxydropCrateBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntityTypes {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(
            Registries.BLOCK_ENTITY_TYPE,
            OXYArena.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OxydropCrateBlockEntity>> OXYDROP_CRATE =
            BLOCK_ENTITY_TYPES.register(
                    "oxydrop_crate",
                    () -> BlockEntityType.Builder.of(OxydropCrateBlockEntity::new, ModBlocks.OXYDROP_CRATE.get()).build(null));

    private ModBlockEntityTypes() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
