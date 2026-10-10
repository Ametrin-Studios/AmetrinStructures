package com.ametrin.structures.structure.simple;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.ProcessorLists;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/// Terrain adaptation keeps the ground at the structure's start height, whatever a template's offset.
@ExtendWith(EphemeralTestServerProvider.class)
class SimpleStructurePieceTest {
    private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
    private static final Identifier TEMPLATE = Identifier.withDefaultNamespace("igloo/top");

    @Test
    void aSunkTemplateKeepsTheGroundAtTheStartHeight(MinecraftServer server) {
        var piece = createPiece(server, new BlockPos(0, -4, 0));
        assertEquals(4, piece.getGroundLevelDelta());
        assertEquals(ORIGIN.getY(), piece.getBeardifierBox().minY() + piece.getGroundLevelDelta());
    }

    @Test
    void aRaisedTemplateStandsAboveTheGround(MinecraftServer server) {
        var piece = createPiece(server, new BlockPos(0, 3, 0));
        assertEquals(ORIGIN.getY(), piece.getBeardifierBox().minY() + piece.getGroundLevelDelta());
    }

    @Test
    void groundLevelAndTerrainAdaptationAreSavedWithThePiece(MinecraftServer server) {
        var piece = createPiece(server, new BlockPos(0, -4, 0));
        var context = new StructurePieceSerializationContext(server.getResourceManager(), server.registryAccess(), server.getStructureManager());
        var reloaded = new SimpleStructurePiece(context, piece.createTag(context));
        assertEquals(4, reloaded.getGroundLevelDelta());
        assertEquals(TerrainAdjustment.BEARD_THIN, reloaded.getTerrainAdjustment());
    }

    @Test
    void aLocalTerrainBoxIsPlacedLikeTheTemplate(MinecraftServer server) {
        var piece = createPiece(server, new BlockPos(0, -4, 0), Rotation.NONE, TerrainBox.of(1, 1, 1, 2, 2, 2));
        assertEquals(new BoundingBox(1, 61, 1, 2, 62, 2), piece.getBeardifierBox());
        assertEquals(0, piece.getGroundLevelDelta());
    }

    @Test
    void aLocalTerrainBoxTurnsWithTheTemplate(MinecraftServer server) {
        for (var rotation : Rotation.values()) {
            var piece = createPiece(server, BlockPos.ZERO, rotation, TerrainBox.of(0, 0, 0, 0, 0, 0));
            var corner = StructureTemplate.calculateRelativePosition(piece.placeSettings(), BlockPos.ZERO).offset(piece.templatePosition());
            assertEquals(new BoundingBox(corner), piece.getBeardifierBox(), rotation.toString());
        }
    }

    @Test
    void theFootprintSpansTheTemplateHeight(MinecraftServer server) {
        var piece = createPiece(server, new BlockPos(0, -4, 0), Rotation.CLOCKWISE_90, TerrainBox.footprint());
        var footprint = piece.getBeardifierBox();
        var bounds = piece.getBoundingBox();
        assertTrue(bounds.minX() <= footprint.minX() && footprint.maxX() <= bounds.maxX());
        assertTrue(bounds.minZ() <= footprint.minZ() && footprint.maxZ() <= bounds.maxZ());
        assertEquals(bounds.minY(), footprint.minY());
        assertEquals(bounds.maxY(), footprint.maxY());
        assertEquals(ORIGIN.getY(), footprint.minY() + piece.getGroundLevelDelta());
    }

    @Test
    void theTerrainBoxIsSavedWithThePiece(MinecraftServer server) {
        var piece = createPiece(server, new BlockPos(0, -4, 0), Rotation.CLOCKWISE_180, TerrainBox.of(1, 2, 1, 2, 3, 2));
        var context = new StructurePieceSerializationContext(server.getResourceManager(), server.registryAccess(), server.getStructureManager());
        var reloaded = new SimpleStructurePiece(context, piece.createTag(context));
        assertEquals(piece.getBeardifierBox(), reloaded.getBeardifierBox());
        assertEquals(piece.getGroundLevelDelta(), reloaded.getGroundLevelDelta());
    }

    @Test
    void theStructuresProcessorsAreSavedByIdAndExpandedOnLoad(MinecraftServer server) {
        var mossify = server.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST).getOrThrow(ProcessorLists.MOSSIFY_10_PERCENT);
        var piece = createPiece(server, processors(InlineFromStructureProcessor.INSTANCE), Optional.of(mossify));
        var context = new StructurePieceSerializationContext(server.getResourceManager(), server.registryAccess(), server.getStructureManager());
        var tag = piece.createTag(context);
        assertEquals(Optional.of(ProcessorLists.MOSSIFY_10_PERCENT.identifier().toString()), tag.getString("structure_processors"));
        var reloaded = new SimpleStructurePiece(context, tag);
        assertEquals(piece.placeSettings().getProcessors(), reloaded.placeSettings().getProcessors());
    }

    @Test
    void unusedStructureProcessorsAreNotSaved(MinecraftServer server) {
        var mossify = server.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST).getOrThrow(ProcessorLists.MOSSIFY_10_PERCENT);
        var piece = createPiece(server, processors(new BlockIgnoreProcessor(List.of(Blocks.STONE))), Optional.of(mossify));
        var context = new StructurePieceSerializationContext(server.getResourceManager(), server.registryAccess(), server.getStructureManager());
        assertFalse(piece.createTag(context).contains("structure_processors"));
    }

    @Test
    void aTerrainBoxIsANameOrABox() {
        assertEquals(TerrainBox.footprint(), parse("\"footprint\""));
        assertEquals(TerrainBox.of(0, 1, 2, 3, 4, 5), parse("[0, 1, 2, 3, 4, 5]"));
    }

    private static TerrainBox parse(String json) {
        return TerrainBox.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static SimpleStructurePiece createPiece(MinecraftServer server, BlockPos offset) {
        return createPiece(server, offset, Rotation.NONE, TerrainBox.template());
    }

    private static SimpleStructurePiece createPiece(
            MinecraftServer server, Optional<Holder<StructureProcessorList>> processors, Optional<Holder<StructureProcessorList>> structureProcessors) {
        return createPiece(server, BlockPos.ZERO, Rotation.NONE, TerrainBox.template(), processors, structureProcessors);
    }

    private static SimpleStructurePiece createPiece(MinecraftServer server, BlockPos offset, Rotation rotation, TerrainBox terrainBox) {
        return createPiece(server, offset, rotation, terrainBox, Optional.empty(), Optional.empty());
    }

    private static SimpleStructurePiece createPiece(
            MinecraftServer server, BlockPos offset, Rotation rotation, TerrainBox terrainBox,
            Optional<Holder<StructureProcessorList>> processors, Optional<Holder<StructureProcessorList>> structureProcessors) {
        var plains = server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        var generation = new Structure.GenerationContext(
                server.registryAccess(),
                new DebugLevelSource(plains),
                new FixedBiomeSource(plains),
                RandomState.create(server.registryAccess(), NoiseGeneratorSettings.OVERWORLD, 0),
                server.getStructureManager(),
                new WorldgenRandom(new LegacyRandomSource(0)),
                0,
                new ChunkPos(0, 0),
                LevelHeightAccessor.create(-64, 384),
                HolderSet.direct(plains)::contains);
        var entry = new TemplateEntry(TEMPLATE, offset, processors, terrainBox);
        var context = new PieceSource.Context(generation, ORIGIN, rotation, structureProcessors, TerrainAdjustment.BEARD_THIN);
        return (SimpleStructurePiece) PieceSources.createPiece(entry, context);
    }

    private static Optional<Holder<StructureProcessorList>> processors(StructureProcessor... processors) {
        return Optional.of(Holder.direct(new StructureProcessorList(List.of(processors))));
    }
}
