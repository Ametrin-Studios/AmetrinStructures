package com.ametrin.structures.structure.simple;

import com.ametrin.structures.registry.ASRegistries;
import com.ametrin.structures.util.ASCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

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

    /// Appends the pieces to `builder`. `context.origin()` has Y 0; the structure moves the pieces to
    /// their start height afterward. Sources that combine others pass them the same `context`.
    void appendPieces(List<StructurePiece> builder, Context context);

    PieceSourceType type();

    /// Every template the source can place, so `/ametrin structures check` can look into them.
    Stream<TemplateEntry> templates();

    /// @param structureProcessors the structure's processors, for templates without their own and to expand [InlineFromStructureProcessor] markers
    record Context(Structure.GenerationContext generation, BlockPos origin, Rotation rotation,
                   Optional<Holder<StructureProcessorList>> structureProcessors) {
    }
}
