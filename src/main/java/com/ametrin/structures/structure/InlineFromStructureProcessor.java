package com.ametrin.structures.structure;

import com.ametrin.structures.registry.ASProcessors;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// A piece's own processors replace the structure's processors.
/// In an [ExtendedTemplateStructurePiece] this marker pulls them back in. It does nothing anywhere else.
public final class InlineFromStructureProcessor extends StructureProcessor {
    public static final InlineFromStructureProcessor INSTANCE = new InlineFromStructureProcessor();
    public static final MapCodec<InlineFromStructureProcessor> CODEC = MapCodec.unit(INSTANCE);

    private InlineFromStructureProcessor() {}

    /// The processors a piece runs: the structure's if the piece has none, otherwise its own with each marker replaced by the structure's.
    public static List<StructureProcessor> resolve(List<StructureProcessor> own, Optional<Holder<StructureProcessorList>> structure) {
        var structureList = structure.map(list -> list.value().list()).orElse(List.of());
        if (own.isEmpty()) {
            return structureList;
        }
        if (!usesStructures(own)) {
            return own;
        }
        List<StructureProcessor> expanded = new ArrayList<>();
        for (var processor : own) {
            if (processor instanceof InlineFromStructureProcessor) {
                expanded.addAll(structureList);
            } else {
                expanded.add(processor);
            }
        }
        return expanded;
    }

    /// Whether a piece with these processors also runs the structure's: when it has none, or has this marker.
    public static boolean usesStructures(List<StructureProcessor> own) {
        return own.isEmpty() || own.stream().anyMatch(InlineFromStructureProcessor.class::isInstance);
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
    protected StructureProcessorType<?> getType() {
        return ASProcessors.INLINE_FROM_STRUCTURE.get();
    }
}
