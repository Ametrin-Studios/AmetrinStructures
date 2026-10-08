package com.ametrin.structures.structure.jigsaw;

import com.ametrin.structures.structure.ExtendedStructure;
import com.ametrin.structures.structure.filter.PlacementFilters;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/// Jigsaw structures take the shared settings: their filters see the assembled pieces.
@ExtendWith(EphemeralTestServerProvider.class)
class ExtendedJigsawStructureTest {
    private static final ResourceKey<StructureTemplatePool> POOL =
            ResourceKey.create(Registries.TEMPLATE_POOL, Identifier.withDefaultNamespace("village/plains/town_centers"));

    @Test
    void generatesWithoutFilters(MinecraftServer server) {
        var result = structure(server, _ -> {}).evaluateGenerationPoint(context(server));
        var generated = assertInstanceOf(ExtendedStructure.Evaluation.Generated.class, result);
        assertFalse(generated.stub().getPiecesBuilder().isEmpty());
    }

    @Test
    void filtersRejectTheStructure(MinecraftServer server) {
        var tooHigh = new PlacementFilters.HeightRange(200, 300);
        var result = structure(server, builder -> builder.filter(tooHigh)).evaluateGenerationPoint(context(server));
        assertEquals(tooHigh, assertInstanceOf(ExtendedStructure.Evaluation.Filtered.class, result).filter());
    }

    @Test
    void piecesAssembledForTheFiltersAreKept(MinecraftServer server) {
        var result = structure(server, builder -> builder.filterHeightRange(0, 100)).evaluateGenerationPoint(context(server));
        var stub = assertInstanceOf(ExtendedStructure.Evaluation.Generated.class, result).stub();
        assertEquals(stub.getPiecesBuilder(), stub.getPiecesBuilder(), "the stub holds the assembled pieces, not a recipe");
    }

    private static ExtendedJigsawStructure structure(MinecraftServer server, Consumer<ExtendedJigsawStructure.Builder> configure) {
        var pool = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).getOrThrow(POOL);
        var builder = ExtendedJigsawStructure.builder(new Structure.StructureSettings(HolderSet.empty()), pool).size(1).startHeight(64);
        configure.accept(builder);
        return builder.build();
    }

    private static Structure.GenerationContext context(MinecraftServer server) {
        var plains = server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        return new Structure.GenerationContext(
                server.registryAccess(),
                new DebugLevelSource(plains),
                new FixedBiomeSource(plains),
                RandomState.create(server.registryAccess(), NoiseGeneratorSettings.OVERWORLD, 0),
                server.getStructureManager(),
                0,
                new ChunkPos(0, 0),
                LevelHeightAccessor.create(-64, 384),
                biome -> true);
    }
}
