package com.example.oxyarena.registry;

import java.util.function.Supplier;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.block.OxydropCrateBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(OXYArena.MODID);

    public static final DeferredBlock<Block> CITRINE_ORE = register(
            "citrine_ore",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).requiresCorrectToolForDrops().strength(2.5F, 6.0F)));
    public static final DeferredBlock<Block> DEEPSLATE_CITRINE_ORE = register(
            "deepslate_citrine_ore",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE).requiresCorrectToolForDrops().strength(4.0F, 6.0F).sound(SoundType.DEEPSLATE)));
    public static final DeferredBlock<Block> COBALT_ORE = register(
            "cobalt_ore",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).requiresCorrectToolForDrops().strength(3.5F, 6.0F)));
    public static final DeferredBlock<Block> DEEPSLATE_COBALT_ORE = register(
            "deepslate_cobalt_ore",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE).requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.DEEPSLATE)));
    public static final DeferredBlock<OxydropCrateBlock> OXYDROP_CRATE = register(
            "oxydrop_crate",
            () -> new OxydropCrateBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL).strength(2.5F, 6.0F)));

    private ModBlocks() {
    }

    private static <T extends Block> DeferredBlock<T> register(String name, Supplier<T> block) {
        return BLOCKS.register(name, block);
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
