package com.ametrin.structures.structure.simple;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Processors on a template replace the structure's processors.
/// In a [SimpleStructurePiece] this marker pulls them back in. It does nothing anywhere else.
public final class InlineFromStructureProcessor implements StructureProcessor {
    public static final InlineFromStructureProcessor INSTANCE = new InlineFromStructureProcessor();
    public static final MapCodec<InlineFromStructureProcessor> CODEC = MapCodec.unit(INSTANCE);

    private InlineFromStructureProcessor() {}

    public static Optional<Holder<StructureProcessorList>> resolve(
            Optional<Holder<StructureProcessorList>> template, Optional<Holder<StructureProcessorList>> structure) {
        if (template.isEmpty()) {
            return structure;
        }
        if (!usesStructures(template)) {
            return template;
        }
        List<StructureProcessor> expanded = new ArrayList<>();
        for (StructureProcessor processor : template.get().value().list()) {
            if (processor instanceof InlineFromStructureProcessor) {
                structure.ifPresent(list -> expanded.addAll(list.value().list()));
            } else {
                expanded.add(processor);
            }
        }
        return Optional.of(Holder.direct(new StructureProcessorList(expanded)));
    }

    /// Whether a template with these processors of its own runs the structure's: it has none, or marks where they go.
    public static boolean usesStructures(Optional<Holder<StructureProcessorList>> template) {
        return template.isEmpty() || template.get().value().list().stream().anyMatch(InlineFromStructureProcessor.class::isInstance);
    }

    @Override
    public StructureTemplate.StructureBlockInfo process(
            LevelReader level,
            BlockPos targetPosition,
            BlockPos referencePos,
            StructureTemplate.StructureBlockInfo original,
            StructureTemplate.StructureBlockInfo current,
            StructurePlaceSettings settings,
            @Nullable StructureTemplate template) {
        return current;
    }

    @Override
    public MapCodec<InlineFromStructureProcessor> codec() {
        return CODEC;
    }
}
