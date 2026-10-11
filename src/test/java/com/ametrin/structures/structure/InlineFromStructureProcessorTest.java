package com.ametrin.structures.structure;

import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Which processors a piece runs, given its own list and the structure's.
class InlineFromStructureProcessorTest {
    private static final StructureProcessor STRUCTURE_ONE = new BlockIgnoreProcessor(List.of(Blocks.STONE));
    private static final StructureProcessor STRUCTURE_TWO = new BlockIgnoreProcessor(List.of(Blocks.DIRT));
    private static final StructureProcessor TEMPLATE_OWN = new BlockIgnoreProcessor(List.of(Blocks.SAND));
    private static final Optional<Holder<StructureProcessorList>> STRUCTURE =
            Optional.of(Holder.direct(new StructureProcessorList(List.of(STRUCTURE_ONE, STRUCTURE_TWO))));

    @Test
    void aTemplateWithoutProcessorsUsesTheStructures() {
        assertEquals(List.of(STRUCTURE_ONE, STRUCTURE_TWO), InlineFromStructureProcessor.resolve(List.of(), STRUCTURE));
    }

    @Test
    void aTemplatesOwnListReplacesTheStructures() {
        assertEquals(List.of(TEMPLATE_OWN), InlineFromStructureProcessor.resolve(List.of(TEMPLATE_OWN), STRUCTURE));
    }

    @Test
    void theMarkerInlinesTheStructuresListWhereItStands() {
        assertEquals(
                List.of(TEMPLATE_OWN, STRUCTURE_ONE, STRUCTURE_TWO),
                InlineFromStructureProcessor.resolve(List.of(TEMPLATE_OWN, InlineFromStructureProcessor.INSTANCE), STRUCTURE));
        assertEquals(
                List.of(STRUCTURE_ONE, STRUCTURE_TWO, TEMPLATE_OWN),
                InlineFromStructureProcessor.resolve(List.of(InlineFromStructureProcessor.INSTANCE, TEMPLATE_OWN), STRUCTURE));
    }

    @Test
    void theMarkerStandsForNothingWhenTheStructureHasNoProcessors() {
        assertEquals(
                List.of(TEMPLATE_OWN),
                InlineFromStructureProcessor.resolve(List.of(InlineFromStructureProcessor.INSTANCE, TEMPLATE_OWN), Optional.empty()));
    }
}
