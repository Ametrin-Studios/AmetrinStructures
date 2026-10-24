package com.ametrin.structures.structure.simple;

import com.ametrin.structures.structure.ExtendedStructure;
import com.ametrin.structures.structure.ExtendedStructureSettings;
import com.ametrin.structures.structure.ExtendedTemplateStructurePiece;
import com.ametrin.structures.structure.Foundation;
import com.ametrin.structures.structure.GenerationContexts;
import com.ametrin.structures.structure.StructureSetOrder;
import com.ametrin.structures.structure.filter.PlacementFilter;
import com.ametrin.structures.structure.filter.PlacementFilters;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/// `evaluateGenerationPoint` says why a structure does not fit, which the spread report counts.
@ExtendWith(EphemeralTestServerProvider.class)
class SimpleStructureEvaluateTest {
    private static final StartHeight AT_64 = StartHeight.at(HeightAnchor.absolute(64));
    private static final ExtendedStructureSettings ONLY_WHEN_PICKED_FIRST = new ExtendedStructureSettings(List.of(), Optional.empty(), true);

    @Test
    void generatesAtTheStartHeight(MinecraftServer server) {
        var result = structure(AT_64, List.of()).evaluateGenerationPoint(context(server));
        var generated = assertInstanceOf(SimpleStructure.Evaluation.Generated.class, result);
        assertEquals(64, generated.stub().position().getY());
    }

    @Test
    void namesTheFilterThatRejected(MinecraftServer server) {
        var tooHigh = new PlacementFilters.HeightRange(200, 300);
        var result = structure(AT_64, List.of(new PlacementFilters.HeightRange(0, 100), tooHigh)).evaluateGenerationPoint(context(server));
        assertEquals(tooHigh, assertInstanceOf(SimpleStructure.Evaluation.Filtered.class, result).filter());
    }

    @Test
    void timesEveryStepThatRuns(MinecraftServer server) {
        var steps = new ArrayList<String>();
        var timer = new SimpleStructure.Timer() {
            @Override
            public void pieces(long nanos) {
                steps.add("pieces");
            }

            @Override
            public void startHeight(long nanos) {
                steps.add("start_height");
            }

            @Override
            public void filter(@NonNull PlacementFilter filter, long nanos) {
                steps.add("filter " + ((PlacementFilters.HeightRange) filter).min());
            }
        };
        var filters = List.<PlacementFilter>of(new PlacementFilters.HeightRange(0, 100), new PlacementFilters.HeightRange(200, 300), new PlacementFilters.HeightRange(400, 500));
        structure(AT_64, filters).evaluateGenerationPoint(context(server), timer);
        assertEquals(List.of("pieces", "start_height", "filter 0", "filter 200"), steps);
    }

    @Test
    void reportsAnEmptyStartHeightRange(MinecraftServer server) {
        var empty = StartHeight.between(HeightAnchor.absolute(100), HeightAnchor.absolute(50));
        assertInstanceOf(SimpleStructure.Evaluation.NoStartHeight.class, structure(empty, List.of()).evaluateGenerationPoint(context(server)));
    }

    @Test
    void theStartBiomeIsCheckedBeforeTheFilters(MinecraftServer server) {
        var rejectsEverything = new PlacementFilters.HeightRange(200, 300);
        var result = structure(AT_64, List.of(rejectsEverything)).evaluateGenerationPoint(context(server, biome -> false));
        assertEquals(64, assertInstanceOf(SimpleStructure.Evaluation.WrongBiome.class, result).origin().getY());
    }

    @Test
    void aChunkWithoutAValidBiomeIsRejectedBeforeMeasuringTheTerrain(MinecraftServer server) {
        assertEquals(List.of("biome_precheck"), stepsInAWrongBiome(server, SimpleStructure.ON_SURFACE_START_HEIGHT));
    }

    @Test
    void aStartHeightWithoutTerrainSkipsThePrecheck(MinecraftServer server) {
        assertEquals(List.of("pieces", "start_height"), stepsInAWrongBiome(server, AT_64));
    }

    private static List<String> stepsInAWrongBiome(MinecraftServer server, StartHeight startHeight) {
        var steps = new ArrayList<String>();
        var timer = new SimpleStructure.Timer() {
            @Override
            public void biomePrecheck(long nanos) {
                steps.add("biome_precheck");
            }

            @Override
            public void pieces(long nanos) {
                steps.add("pieces");
            }

            @Override
            public void startHeight(long nanos) {
                steps.add("start_height");
            }
        };
        var result = structure(startHeight, List.of()).evaluateGenerationPoint(context(server, biome -> false), timer);
        assertInstanceOf(SimpleStructure.Evaluation.WrongBiome.class, result);
        return steps;
    }

    @Test
    void thePrecheckFindsAValidBiomeAnywhereInTheRange(MinecraftServer server) {
        var biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
        var plains = biomes.getOrThrow(Biomes.PLAINS);
        var desert = biomes.getOrThrow(Biomes.DESERT);
        for (int y : new int[]{-64, 63, 319}) {
            var context = context(server, plainsOnlyAt(y, plains, desert), biome -> biome.is(Biomes.PLAINS));
            assertTrue(ExtendedStructure.hasValidBiome(context, 0, 0, -64, 319), "plains at " + y);
            assertFalse(ExtendedStructure.hasValidBiome(context, 0, 0, y + 4, y + 100), "range above plains at " + y);
            assertFalse(ExtendedStructure.hasValidBiome(context, 0, 0, y - 100, y - 4), "range below plains at " + y);
        }
    }

    @Test
    void heightmapAnchorsCanStartAnywhereOnTheirHeightmap(MinecraftServer server) {
        var world = new WorldGenerationContext(context(server).chunkGenerator(), LevelHeightAccessor.create(-64, 384));
        var range = StartHeight.between(HeightAnchor.aboveBottom(8), HeightAnchor.surface(-24)).bounds(world).orElseThrow();
        assertEquals(world.getMinGenY() + 8, range.minInclusive());
        assertEquals(world.getMinGenY() + world.getGenDepth() - 24, range.maxInclusive());
        assertEquals(Optional.empty(), StartHeight.of(ConstantHeight.of(VerticalAnchor.absolute(0))).bounds(world));
    }

    @Test
    void findValidGenerationPointNeedsTheBiome(MinecraftServer server) {
        assertTrue(structure(AT_64, List.of()).findValidGenerationPoint(context(server)).isPresent());
        assertTrue(structure(AT_64, List.of()).findValidGenerationPoint(context(server, biome -> false)).isEmpty());
    }

    @Test
    void findGenerationPointAgreesWithEvaluate(MinecraftServer server) {
        assertTrue(structure(AT_64, List.of()).findGenerationPoint(context(server)).isPresent());
        assertTrue(structure(AT_64, List.of(new PlacementFilters.HeightRange(200, 300))).findGenerationPoint(context(server)).isEmpty());
    }

    @Test
    void onlyWhenPickedFirstGeneratesOnlyWhereTheSetTriesItFirst(MinecraftServer server) {
        var first = structure(AT_64, ONLY_WHEN_PICKED_FIRST);
        var second = structure(AT_64, ONLY_WHEN_PICKED_FIRST);
        var set = new StructureSet(
                List.of(StructureSet.entry(Holder.direct(first)), StructureSet.entry(Holder.direct(second))),
                new RandomSpreadStructurePlacement(8, 4, RandomSpreadType.LINEAR, 0));
        var context = context(server, registriesWith(set), new FixedBiomeSource(plains(server)), biome -> true);
        var firstIsPicked = StructureSetOrder.of(set, context.seed(), context.chunkPos()).getFirst().structure().value() == first;
        var picked = firstIsPicked ? first : second;
        var other = firstIsPicked ? second : first;
        assertInstanceOf(ExtendedStructure.Evaluation.Generated.class, picked.evaluateGenerationPoint(context));
        assertInstanceOf(ExtendedStructure.Evaluation.NotPickedFirst.class, other.evaluateGenerationPoint(context));
    }

    @Test
    void onlyWhenPickedFirstOutsideASetGenerates(MinecraftServer server) {
        assertInstanceOf(ExtendedStructure.Evaluation.Generated.class, structure(AT_64, ONLY_WHEN_PICKED_FIRST).evaluateGenerationPoint(context(server)));
    }

    @Test
    void theStructureHandsItsProcessorsToItsPieces(MinecraftServer server) {
        var processors = Holder.direct(new StructureProcessorList(List.of(new BlockIgnoreProcessor(List.of(Blocks.STONE)))));
        var structure = structure(AT_64, new ExtendedStructureSettings(List.of(), Optional.of(processors)));
        var stub = assertInstanceOf(ExtendedStructure.Evaluation.Generated.class, structure.evaluateGenerationPoint(context(server))).stub();
        var piece = assertInstanceOf(ExtendedTemplateStructurePiece.class, stub.getPiecesBuilder().build().pieces().getFirst());
        assertEquals(Optional.of(processors), piece.structureProcessors());
    }

    private static SimpleStructure structure(StartHeight startHeight, List<PlacementFilter> filters) {
        return structure(startHeight, new ExtendedStructureSettings(filters, Optional.empty()));
    }

    private static SimpleStructure structure(StartHeight startHeight, ExtendedStructureSettings extendedSettings) {
        return new SimpleStructure(
                new Structure.StructureSettings(HolderSet.empty()),
                extendedSettings,
                new PieceSources.SingleSource(TemplateEntry.of(Identifier.withDefaultNamespace("igloo/top"))),
                startHeight,
                LiquidSettings.IGNORE_WATERLOGGING,
                Optional.<Foundation>empty(),
                Optional.empty());
    }

    private static Structure.GenerationContext context(MinecraftServer server) {
        return context(server, biome -> biome.is(Biomes.PLAINS));
    }

    private static Structure.GenerationContext context(MinecraftServer server, Predicate<Holder<Biome>> validBiome) {
        return GenerationContexts.overPlains(server, 0, validBiome);
    }

    private static Structure.GenerationContext context(MinecraftServer server, BiomeSource biomeSource, Predicate<Holder<Biome>> validBiome) {
        return context(server, server.registryAccess(), biomeSource, validBiome);
    }

    private static Structure.GenerationContext context(
            MinecraftServer server, RegistryAccess registries, BiomeSource biomeSource, Predicate<Holder<Biome>> validBiome) {
        return GenerationContexts.create(server, registries, new DebugLevelSource(plains(server)), biomeSource, 0, validBiome);
    }

    private static Holder.Reference<Biome> plains(MinecraftServer server) {
        return server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
    }

    private static RegistryAccess registriesWith(StructureSet set) {
        var sets = new MappedRegistry<>(Registries.STRUCTURE_SET, Lifecycle.stable());
        Registry.register(sets, Identifier.fromNamespaceAndPath("test", "set"), set);
        return new RegistryAccess.ImmutableRegistryAccess(List.of(sets));
    }

    private static BiomeSource plainsOnlyAt(int y, Holder<Biome> plains, Holder<Biome> other) {
        return new BiomeSource() {
            @Override
            protected MapCodec<? extends BiomeSource> codec() {
                throw new UnsupportedOperationException();
            }

            @Override
            protected Stream<Holder<Biome>> collectPossibleBiomes() {
                return Stream.of(plains, other);
            }

            @Override
            public BiomeResolver createResolver(Climate.Sampler sampler) {
                return (quartX, quartY, quartZ) -> quartY == QuartPos.fromBlock(y) ? plains : other;
            }
        };
    }
}
