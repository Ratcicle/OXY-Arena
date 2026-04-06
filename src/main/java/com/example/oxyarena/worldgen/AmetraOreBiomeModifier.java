package com.example.oxyarena.worldgen;

import java.util.List;

import com.example.oxyarena.Config;
import com.example.oxyarena.registry.ModBiomeModifiers;
import com.example.oxyarena.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

public record AmetraOreBiomeModifier(HolderSet<Biome> biomes) implements BiomeModifier {
    public static final MapCodec<AmetraOreBiomeModifier> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(AmetraOreBiomeModifier::biomes))
            .apply(instance, AmetraOreBiomeModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.ADD || !biomes.contains(biome) || !Config.ametraOreGenerationEnabled()) {
            return;
        }

        var generationSettings = builder.getGenerationSettings();
        var features = generationSettings.getFeatures(GenerationStep.Decoration.UNDERGROUND_ORES);

        Holder<PlacedFeature> primary = createPrimaryFeature();
        if (primary != null) {
            features.add(primary);
        }

        Holder<PlacedFeature> medium = createMediumFeature();
        if (medium != null) {
            features.add(medium);
        }

        Holder<PlacedFeature> buried = createBuriedFeature();
        if (buried != null) {
            features.add(buried);
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return ModBiomeModifiers.AMETRA_ORE.get();
    }

    private static Holder<PlacedFeature> createPrimaryFeature() {
        int count = Config.ametraOrePrimaryCount();
        if (count <= 0) {
            return null;
        }

        return Holder.direct(new PlacedFeature(
                Holder.direct(new ConfiguredFeature<>(Feature.ORE, createOreConfiguration((float) Config.ametraOreExposedDiscardChance()))),
                List.of(
                        CountPlacement.of(count),
                        InSquarePlacement.spread(),
                        HeightRangePlacement.triangle(VerticalAnchor.absolute(normalizedMinY()), VerticalAnchor.absolute(normalizedMaxY())),
                        BiomeFilter.biome())));
    }

    private static Holder<PlacedFeature> createMediumFeature() {
        int count = Config.ametraOreMediumCount();
        if (count <= 0) {
            return null;
        }

        int deepMaxY = normalizedDeepMaxY();
        if (deepMaxY < normalizedMinY()) {
            return null;
        }

        return Holder.direct(new PlacedFeature(
                Holder.direct(new ConfiguredFeature<>(Feature.ORE, createOreConfiguration((float) Config.ametraOreExposedDiscardChance()))),
                List.of(
                        CountPlacement.of(count),
                        InSquarePlacement.spread(),
                        HeightRangePlacement.uniform(VerticalAnchor.absolute(normalizedMinY()), VerticalAnchor.absolute(deepMaxY)),
                        BiomeFilter.biome())));
    }

    private static Holder<PlacedFeature> createBuriedFeature() {
        int count = Config.ametraOreBuriedCount();
        if (count <= 0) {
            return null;
        }

        return Holder.direct(new PlacedFeature(
                Holder.direct(new ConfiguredFeature<>(Feature.ORE, createOreConfiguration((float) Config.ametraOreBuriedDiscardChance()))),
                List.of(
                        CountPlacement.of(count),
                        InSquarePlacement.spread(),
                        HeightRangePlacement.triangle(VerticalAnchor.absolute(normalizedMinY()), VerticalAnchor.absolute(normalizedMaxY())),
                        BiomeFilter.biome())));
    }

    private static OreConfiguration createOreConfiguration(float discardChanceOnAirExposure) {
        return new OreConfiguration(
                List.of(
                        OreConfiguration.target(new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES), ModBlocks.AMETRA_ORE.get().defaultBlockState()),
                        OreConfiguration.target(new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES), ModBlocks.DEEPSLATE_AMETRA_ORE.get().defaultBlockState())),
                Config.ametraOreVeinSize(),
                discardChanceOnAirExposure);
    }

    private static int normalizedMinY() {
        return Math.min(Config.ametraOreMinY(), Config.ametraOreMaxY());
    }

    private static int normalizedMaxY() {
        return Math.max(Config.ametraOreMinY(), Config.ametraOreMaxY());
    }

    private static int normalizedDeepMaxY() {
        return Math.min(Config.ametraOreDeepMaxY(), normalizedMaxY());
    }
}
