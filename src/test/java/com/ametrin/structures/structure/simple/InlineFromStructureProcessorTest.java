package com.ametrin.structures.structure.simple;

import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/// Which processors a simple structure template runs, given its own list and the structure's.
class InlineFromStructureProcessorTest {
    private static final StructureProcessor STRUCTURE_ONE = new BlockIgnoreProcessor(List.of(Blocks.STONE));
    private static final StructureProcessor STRUCTURE_TWO = new BlockIgnoreProcessor(List.of(Blocks.DIRT));
    private static final StructureProcessor TEMPLATE_OWN = new BlockIgnoreProcessor(List.of(Blocks.SAND));
    private static final Optional<Holder<StructureProcessorList>> STRUCTURE = list(STRUCTURE_ONE, STRUCTURE_TWO);

    @Test
    void aTemplateWithoutProcessorsUsesTheStructures() {
        assertSame(STRUCTURE, InlineFromStructureProcessor.resolve(Optional.empty(), STRUCTURE));
    }

    @Test
    void aTemplatesOwnListReplacesTheStructures() {
        var own = list(TEMPLATE_OWN);
        assertSame(own, InlineFromStructureProcessor.resolve(own, STRUCTURE));
    }

    @Test
    void theMarkerInlinesTheStructuresListWhereItStands() {
        assertEquals(
                List.of(TEMPLATE_OWN, STRUCTURE_ONE, STRUCTURE_TWO),
                processors(InlineFromStructureProcessor.resolve(list(TEMPLATE_OWN, InlineFromStructureProcessor.INSTANCE), STRUCTURE)));
        assertEquals(
                List.of(STRUCTURE_ONE, STRUCTURE_TWO, TEMPLATE_OWN),
                processors(InlineFromStructureProcessor.resolve(list(InlineFromStructureProcessor.INSTANCE, TEMPLATE_OWN), STRUCTURE)));
    }

    @Test
    void theMarkerStandsForNothingWhenTheStructureHasNoProcessors() {
        assertEquals(
                List.of(TEMPLATE_OWN),
                processors(InlineFromStructureProcessor.resolve(list(InlineFromStructureProcessor.INSTANCE, TEMPLATE_OWN), Optional.empty())));
    }

    private static Optional<Holder<StructureProcessorList>> list(StructureProcessor... processors) {
        return Optional.of(Holder.direct(new StructureProcessorList(List.of(processors))));
    }

    private static List<StructureProcessor> processors(Optional<Holder<StructureProcessorList>> list) {
        return list.orElseThrow().value().list();
    }
}
