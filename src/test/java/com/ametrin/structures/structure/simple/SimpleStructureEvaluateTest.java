package com.ametrin.structures.structure.simple;

import com.ametrin.structures.structure.ExtendedStructureSettings;
import com.ametrin.structures.structure.Foundation;
import com.ametrin.structures.structure.GenerationContexts;
import com.ametrin.structures.structure.filter.PlacementFilter;
import com.ametrin.structures.structure.filter.PlacementFilters;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/// `evaluateGenerationPoint` says why a structure does not fit, which the spread report counts.
@ExtendWith(EphemeralTestServerProvider.class)
class SimpleStructureEvaluateTest {
    private static final StartHeight AT_64 = StartHeight.at(HeightAnchor.absolute(64));

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
    void findValidGenerationPointNeedsTheBiome(MinecraftServer server) {
        assertTrue(structure(AT_64, List.of()).findValidGenerationPoint(context(server)).isPresent());
        assertTrue(structure(AT_64, List.of()).findValidGenerationPoint(context(server, biome -> false)).isEmpty());
    }

    @Test
    void findGenerationPointAgreesWithEvaluate(MinecraftServer server) {
        assertTrue(structure(AT_64, List.of()).findGenerationPoint(context(server)).isPresent());
        assertTrue(structure(AT_64, List.of(new PlacementFilters.HeightRange(200, 300))).findGenerationPoint(context(server)).isEmpty());
    }

    private static SimpleStructure structure(StartHeight startHeight, List<PlacementFilter> filters) {
        return new SimpleStructure(
                new Structure.StructureSettings(HolderSet.empty()),
                new ExtendedStructureSettings(filters),
                new PieceSources.SingleSource(TemplateEntry.of(Identifier.withDefaultNamespace("igloo/top"))),
                Optional.empty(),
                startHeight,
                HeightMode.CORNER,
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
}
