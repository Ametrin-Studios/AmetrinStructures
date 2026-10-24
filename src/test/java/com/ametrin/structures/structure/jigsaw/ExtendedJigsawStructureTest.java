package com.ametrin.structures.structure.jigsaw;

import com.ametrin.structures.structure.ExtendedStructure;
import com.ametrin.structures.structure.filter.PlacementFilters;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.ProcessorLists;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Optional;
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

    @Test
    void theSizeIsSampledFromItsProvider(MinecraftServer server) {
        var result = structure(server, builder -> builder.size(0, 0)).evaluateGenerationPoint(context(server));
        var stub = assertInstanceOf(ExtendedStructure.Evaluation.Generated.class, result).stub();
        // Vanilla skips assembly entirely at depth 0, start piece included.
        assertTrue(stub.getPiecesBuilder().isEmpty());
    }

    @Test
    void theLastVerticalPlacementWins(MinecraftServer server) {
        assertEquals(Optional.empty(), structure(server, builder -> builder.surface(3).verticalPlacement(64)).projectStartToHeightmap());
        assertEquals(Optional.of(Heightmap.Types.OCEAN_FLOOR_WG), structure(server, builder -> builder.verticalPlacement(64).oceanFloor(3)).projectStartToHeightmap());
    }

    @Test
    void theStructuresProcessorsAreHandedToItsPieces(MinecraftServer server) {
        var mossify = mossify(server);
        var structure = structure(server, builder -> builder.processors(mossify));
        var stub = assertInstanceOf(ExtendedStructure.Evaluation.Generated.class, structure.evaluateGenerationPoint(context(server))).stub();
        for (var piece : stub.getPiecesBuilder().build().pieces()) {
            assertEquals(Optional.of(mossify), JigsawStructureProcessors.get((PoolElementStructurePiece) piece));
        }
    }

    @Test
    void thePiecesKeepTheStructuresProcessorsWhenSaved(MinecraftServer server) {
        var mossify = mossify(server);
        var structure = structure(server, builder -> builder.processors(mossify));
        var stub = assertInstanceOf(ExtendedStructure.Evaluation.Generated.class, structure.evaluateGenerationPoint(context(server))).stub();
        var piece = stub.getPiecesBuilder().build().pieces().getFirst();
        var context = new StructurePieceSerializationContext(server.getResourceManager(), server.registryAccess(), server.getStructureManager());
        var reloaded = new PoolElementStructurePiece(context, piece.createTag(context));
        assertEquals(Optional.of(mossify), JigsawStructureProcessors.get(reloaded));
    }

    @Test
    void anElementWithoutProcessorsRunsTheStructuresWhilePlacing(MinecraftServer server) {
        var mossify = mossify(server);
        var element = new ExtendedSinglePoolElement(Either.left(Identifier.withDefaultNamespace("igloo/top")), Holder.direct(new StructureProcessorList(List.of())),
                StructureTemplatePool.Projection.RIGID, Optional.empty(), false, Optional.empty());
        assertTrue(JigsawStructureProcessors.place(Optional.of(mossify),
                () -> element.getSettings(Rotation.NONE, BoundingBox.infinite(), LiquidSettings.IGNORE_WATERLOGGING, false).getProcessors().containsAll(mossify.value().list())));
        assertFalse(element.getSettings(Rotation.NONE, BoundingBox.infinite(), LiquidSettings.IGNORE_WATERLOGGING, false).getProcessors().containsAll(mossify.value().list()));
    }

    private static Holder<StructureProcessorList> mossify(MinecraftServer server) {
        return server.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST).getOrThrow(ProcessorLists.MOSSIFY_10_PERCENT);
    }

    private static ExtendedJigsawStructure structure(MinecraftServer server, Consumer<ExtendedJigsawStructure.Builder> configure) {
        var builder = ExtendedJigsawStructure.builder(Identifier.fromNamespaceAndPath("test", "town"), POOL).size(1).verticalPlacement(64);
        configure.accept(builder);
        return builder.build(new BootstrapContext<>() {
            @Override
            public Holder.Reference<Structure> register(ResourceKey<Structure> key, Structure value, Lifecycle lifecycle) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <S> HolderGetter<S> lookup(ResourceKey<? extends Registry<? extends S>> key) {
                return server.registryAccess().lookupOrThrow(key);
            }
        });
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
