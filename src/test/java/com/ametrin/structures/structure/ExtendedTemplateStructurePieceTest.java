package com.ametrin.structures.structure;

import com.ametrin.structures.structure.modular.TerrainBox;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class ExtendedTemplateStructurePieceTest {
    @Test
    void itLeavesOutStructureBlocksAndSetsUpMobsLikeAPoolElement(MinecraftServer server) {
        var piece = new ExtendedTemplateStructurePiece(StructurePieceType.JIGSAW, 0, server.getStructureManager(), Identifier.withDefaultNamespace("igloo/top"),
                new StructurePlaceSettings(), BlockPos.ZERO, TerrainAdjustment.NONE, TerrainBox.template(), 0) {};
        // Also once the structure hands over its processors.
        piece.setStructureProcessors(Optional.empty());
        assertTrue(piece.placeSettings().getProcessors().contains(BlockIgnoreProcessor.STRUCTURE_BLOCK));
        assertTrue(piece.placeSettings().shouldFinalizeEntities());
    }
}
