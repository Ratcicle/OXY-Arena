package com.example.oxyarena.loot;

import com.example.oxyarena.registry.ModLootModifiers;
import com.example.oxyarena.serverevent.MiningFeverServerEvent;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

public final class DoubleOreDropsLootModifier extends LootModifier {
    public static final MapCodec<DoubleOreDropsLootModifier> CODEC = RecordCodecBuilder.mapCodec(
            instance -> codecStart(instance).apply(instance, DoubleOreDropsLootModifier::new));

    public DoubleOreDropsLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        BlockState blockState = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (blockState == null
                || origin == null
                || !blockState.is(Tags.Blocks.ORES)
                || !MiningFeverServerEvent.isBonusActiveAt(context.getLevel(), origin.x, origin.z)) {
            return generatedLoot;
        }

        for (ItemStack stack : generatedLoot) {
            if (!stack.isEmpty()) {
                stack.setCount(stack.getCount() * 2);
            }
        }

        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return ModLootModifiers.DOUBLE_ORE_DROPS.get();
    }
}
