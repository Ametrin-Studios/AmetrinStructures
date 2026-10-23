package com.ametrin.structures.structure.jigsaw;

import com.ametrin.structures.structure.InlineFromStructureProcessor;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

import java.util.Optional;
import java.util.function.BooleanSupplier;

public final class JigsawStructureProcessors {
    // Elements can't see their piece, so the piece sets this while placing.
    private static final ThreadLocal<Optional<Holder<StructureProcessorList>>> PLACING = ThreadLocal.withInitial(Optional::empty);

    private JigsawStructureProcessors() {}

    public static Optional<Holder<StructureProcessorList>> get(PoolElementStructurePiece piece) {
        return ((Piece) piece).ametrin$structureProcessors();
    }

    public static void set(PoolElementStructurePiece piece, Optional<Holder<StructureProcessorList>> processors) {
        ((Piece) piece).ametrin$setStructureProcessors(processors);
    }

    public static boolean place(Optional<Holder<StructureProcessorList>> processors, BooleanSupplier place) {
        var previous = PLACING.get();
        PLACING.set(processors);
        try {
            return place.getAsBoolean();
        } finally {
            PLACING.set(previous);
        }
    }

    public static StructureProcessorList resolve(StructureProcessorList own) {
        var structure = PLACING.get();
        if (structure.isEmpty() || !InlineFromStructureProcessor.usesStructures(own.list())) {
            return own;
        }
        return new StructureProcessorList(InlineFromStructureProcessor.resolve(own.list(), structure));
    }

    // Implemented by PoolElementStructurePieceMixin.
    public interface Piece {
        Optional<Holder<StructureProcessorList>> ametrin$structureProcessors();

        void ametrin$setStructureProcessors(Optional<Holder<StructureProcessorList>> processors);
    }
}
