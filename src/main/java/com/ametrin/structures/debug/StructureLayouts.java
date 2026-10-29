package com.ametrin.structures.debug;

import com.ametrin.structures.structure.ExtendedStructure;
import com.ametrin.structures.structure.ExtendedTemplateStructurePiece;
import com.ametrin.structures.structure.StructureBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;

import java.util.Optional;

/// Lays out structures at a chosen seed, ignoring the biome, the filters and [StructureBuilder#onlyWhenPickedFirst], to test how they assemble.
final class StructureLayouts {
    private StructureLayouts() {}

    /// The structure's pieces at `chunk` with `seed`, or empty when it lays out none.
    static Optional<PiecesContainer> layOut(ServerLevel level, Structure structure, ChunkPos chunk, long seed) {
        var context = StructureSpread.context(level, seed, chunk, _ -> true);
        var stub = structure instanceof ExtendedStructure extended ? extended.layOutUnchecked(context) : structure.findValidGenerationPoint(context);
        return stub.map(found -> found.getPiecesBuilder().build()).filter(pieces -> !pieces.isEmpty());
    }

    /// A piece's template, or for a piece that isn't one, its pool element or piece type.
    static String name(StructurePiece piece) {
        return switch (piece) {
            case PoolElementStructurePiece pooled when pooled.getElement() instanceof SinglePoolElement single -> single.getTemplateLocation().toString();
            case PoolElementStructurePiece pooled -> String.valueOf(BuiltInRegistries.STRUCTURE_POOL_ELEMENT.getKey(pooled.getElement().getType()));
            case ExtendedTemplateStructurePiece template -> template.templateId().toString();
            default -> String.valueOf(BuiltInRegistries.STRUCTURE_PIECE.getKey(piece.getType()));
        };
    }
}
