package com.example.oxyarena.registry;

import java.util.function.Supplier;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.block.OxydropCrateBlock;
import com.example.oxyarena.block.SoulReaperFireBlock;
import com.example.oxyarena.block.SoulReaperSoulFireBlock;

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
    public static final DeferredBlock<Block> AMETRA_ORE = register(
            "ametra_ore",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DIAMOND_ORE).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> DEEPSLATE_AMETRA_ORE = register(
            "deepslate_ametra_ore",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_DIAMOND_ORE).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE)));
    public static final DeferredBlock<OxydropCrateBlock> OXYDROP_CRATE = register(
            "oxydrop_crate",
            () -> new OxydropCrateBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL).strength(2.5F, 6.0F)));
    public static final DeferredBlock<SoulReaperFireBlock> SOUL_REAPER_FIRE = register(
            "soul_reaper_fire",
            () -> new SoulReaperFireBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.FIRE)
                            .replaceable()
                            .instabreak()
                            .noLootTable()
                            .noCollission()));
    public static final DeferredBlock<SoulReaperSoulFireBlock> SOUL_REAPER_SOUL_FIRE = register(
            "soul_reaper_soul_fire",
            () -> new SoulReaperSoulFireBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.SOUL_FIRE)
                            .replaceable()
                            .instabreak()
                            .noLootTable()
                            .noCollission()));

    private ModBlocks() {
    }

    private static <T extends Block> DeferredBlock<T> register(String name, Supplier<T> block) {
        return BLOCKS.register(name, block);
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
