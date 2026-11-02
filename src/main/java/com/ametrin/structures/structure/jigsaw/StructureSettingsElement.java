package com.ametrin.structures.structure.jigsaw;

import com.ametrin.structures.structure.Foundation;
import com.ametrin.structures.structure.InlineFromStructureProcessor;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

import java.util.Optional;

/// A pool element that takes its structure's processors and foundation when a [JigsawSource] places it.
///
/// Pool elements are shared between structures, so return a copy with the settings rather than changing this element. The piece saves the copy.
public interface StructureSettingsElement {
    /// @param processors the structure's processors
    /// @param foundation the structure's foundation
    /// @return the element to place, or this one if it doesn't use them
    StructurePoolElement withStructureSettings(Optional<Holder<StructureProcessorList>> processors, Optional<Foundation> foundation);

    /// The processors an element with `own` processors runs, see [InlineFromStructureProcessor].
    /// Returns `own` or the structure's holder where it can, so a saved piece only stores the list's id.
    static Holder<StructureProcessorList> processors(Holder<StructureProcessorList> own, Optional<Holder<StructureProcessorList>> structure) {
        if (structure.isEmpty() || !InlineFromStructureProcessor.usesStructures(own.value().list())) {
            return own;
        }
        if (own.value().list().isEmpty()) {
            return structure.get();
        }
        return Holder.direct(new StructureProcessorList(InlineFromStructureProcessor.resolve(own.value().list(), structure)));
    }
}
