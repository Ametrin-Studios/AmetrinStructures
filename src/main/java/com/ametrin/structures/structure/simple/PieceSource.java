package com.ametrin.structures.structure.simple;

import com.ametrin.structures.registry.ASRegistries;
import com.ametrin.structures.structure.Foundation;
import com.ametrin.structures.util.ASCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

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
    /// @return the pieces to add once the first ones are at their start height, for pieces that depend on the terrain. [Growth#NONE] for most sources.
    Growth appendPieces(StructurePiecesBuilder builder, Context context);

    PieceSourceType type();

    /// Every template the source can place, so `/ametrin structures check` can look into them.
    Stream<TemplateEntry> templates();

    /// The structure's settings, for the pieces to be created with.
    ///
    /// @param processors the structure's processors, for template pieces without their own, see [com.ametrin.structures.structure.InlineFromStructureProcessor]
    record Context(Structure.GenerationContext generation, BlockPos origin, Rotation rotation, TerrainAdjustment terrainAdaptation, LiquidSettings liquidSettings, Optional<Foundation> foundation, Optional<Holder<StructureProcessorList>> processors) {
    }

    @FunctionalInterface
    interface Growth {
        Growth NONE = (_, _) -> true;

        /// Appends the rest of the pieces to `builder`, which holds every piece so far at its start height.
        ///
        /// @param origin the context's origin at the start height
        /// @return false if the structure can't generate here after all
        boolean grow(StructurePiecesBuilder builder, BlockPos origin);

        default Growth andThen(Growth next) {
            if (this == NONE) {
                return next;
            }
            return next == NONE ? this : (builder, origin) -> grow(builder, origin) && next.grow(builder, origin);
        }
    }
}
