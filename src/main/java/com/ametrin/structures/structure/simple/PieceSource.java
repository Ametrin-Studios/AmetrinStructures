package com.ametrin.structures.structure.simple;

import com.ametrin.structures.registry.ASRegistries;
import com.ametrin.structures.structure.Foundation;
import com.ametrin.structures.structure.filter.TerrainSampler;
import com.ametrin.structures.util.ASCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public interface PieceSource {
    Codec<PieceSource> CODEC = Codec.either(
                    ASCodecs.<PieceSource, PieceSourceType>dispatch(() -> ASRegistries.PIECE_SOURCE_TYPES, PieceSource::type, PieceSourceType::codec),
                    TemplateEntry.CODEC)
            .xmap(
                    either -> either.map(Function.identity(), PieceSources.SingleSource::new),
                    source -> source instanceof PieceSources.SingleSource(
                            TemplateEntry template
                    ) ? Either.<PieceSource, TemplateEntry>right(template) : Either.<PieceSource, TemplateEntry>left(source));

    /// Appends the pieces to `builder`. `context.origin()` has Y 0. The structure moves the pieces to their start height afterward, measuring it under them. Sources that combine others pass them the same `context`.
    ///
    /// Pieces that depend on the terrain or on the other pieces can be added in a step registered with [Context#afterPlacement].
    void appendPieces(StructurePiecesBuilder builder, Context context);

    PieceSourceType type();

    /// Every template the source can place, so `/ametrin structures check` can look into them.
    Stream<TemplateEntry> templates();

    /// The structure's settings, for the pieces to be created with.
    ///
    /// @param processors     the structure's processors, for template pieces without their own, see [com.ametrin.structures.structure.InlineFromStructureProcessor]
    /// @param terrain        the structure's cached terrain lookups, shared with its start height and filters. Use it instead of the chunk generator.
    /// @param placementSteps the steps registered with [#afterPlacement], in order
    record Context(Structure.GenerationContext generation, BlockPos origin, Rotation rotation, TerrainAdjustment terrainAdaptation, LiquidSettings liquidSettings, Optional<Foundation> foundation, Optional<Holder<StructureProcessorList>> processors, TerrainSampler terrain, List<PlacementStep> placementSteps) {
        /// With a new terrain sampler on the world surface and no steps yet.
        public Context(Structure.GenerationContext generation, BlockPos origin, Rotation rotation, TerrainAdjustment terrainAdaptation, LiquidSettings liquidSettings, Optional<Foundation> foundation, Optional<Holder<StructureProcessorList>> processors) {
            this(generation, origin, rotation, terrainAdaptation, liquidSettings, foundation, processors, new TerrainSampler(generation, Heightmap.Types.WORLD_SURFACE_WG), new ArrayList<>());
        }

        /// Runs `step` once every source appended its pieces and they're at their start height. Steps run in the order they're registered, and a step can register more.
        public void afterPlacement(PlacementStep step) {
            placementSteps.add(step);
        }

        /// Runs the registered steps, for the structure once the pieces are at their start height.
        ///
        /// @param origin the origin at the start height
        /// @return false if a step rejected the spot
        public boolean place(StructurePiecesBuilder builder, BlockPos origin) {
            for (int i = 0; i < placementSteps.size(); i++) {
                if (!placementSteps.get(i).place(builder, origin)) {
                    return false;
                }
            }
            return true;
        }
    }

    @FunctionalInterface
    interface PlacementStep {
        /// @param builder holds every piece of the structure, at its start height
        /// @param origin  the context's origin at the start height
        /// @return false if the structure can't generate here after all
        boolean place(StructurePiecesBuilder builder, BlockPos origin);
    }
}
