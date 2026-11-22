package com.ametrin.structures.structure.modular;

import com.ametrin.structures.structure.InlineFromStructureProcessor;
import com.ametrin.structures.structure.TerrainBox;
import com.ametrin.structures.structure.jigsaw.ExtendedSinglePoolElement;
import com.mojang.datafixers.util.Either;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.ProcessorLists;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/// Terrain adaptation keeps the ground at the structure's start height, whatever a template's offset.
@ExtendWith(EphemeralTestServerProvider.class)
class ModularStructurePieceTest {
    private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
    private static final Identifier TEMPLATE = Identifier.withDefaultNamespace("igloo/top");

    @Test
    void theGroundStaysAtTheStartHeightWhetherTheTemplateIsSunkOrRaised(MinecraftServer server) {
        var sunk = createPiece(server, new BlockPos(0, -4, 0));
        assertEquals(4, sunk.getBeardifierGroundLevelDelta());
        assertEquals(ORIGIN.getY(), sunk.getBeardifierBox().minY() + sunk.getBeardifierGroundLevelDelta());
        var raised = createPiece(server, new BlockPos(0, 3, 0));
        assertEquals(ORIGIN.getY(), raised.getBeardifierBox().minY() + raised.getBeardifierGroundLevelDelta());
    }

    @Test
    void aLocalTerrainBoxIsPlacedLikeTheTemplate(MinecraftServer server) {
        var piece = createPiece(server, new BlockPos(0, -4, 0), Rotation.NONE, TerrainBox.of(1, 1, 1, 2, 2, 2));
        assertEquals(new BoundingBox(1, 61, 1, 2, 62, 2), piece.getBeardifierBox());
        assertEquals(0, piece.getBeardifierGroundLevelDelta());
        assertEquals(4, piece.getGroundLevelDelta());
    }

    @Test
    void withoutATerrainBoxThePieceLeavesTheTerrainAlone(MinecraftServer server) {
        var junction = new JigsawJunction(0, 64, 0, 0, StructureTemplatePool.Projection.RIGID);
        var boxed = createPiece(server, BlockPos.ZERO);
        boxed.addJunction(junction);
        assertEquals(List.of(junction), boxed.getBeardifierJunctions());
        var piece = createPiece(server, BlockPos.ZERO, Rotation.NONE, TerrainBox.none());
        piece.addJunction(junction);
        assertEquals(TerrainAdjustment.NONE, piece.getTerrainAdjustment());
        assertEquals(List.of(), piece.getBeardifierJunctions());
        var context = new StructurePieceSerializationContext(server.getResourceManager(), server.registryAccess(), server.getStructureTemplateManager());
        assertEquals(TerrainAdjustment.NONE, new ModularStructurePiece(context, piece.createTag(context)).getTerrainAdjustment());
    }

    @Test
    void aLocalTerrainBoxTurnsWithTheTemplate(MinecraftServer server) {
        for (var rotation : Rotation.values()) {
            var piece = createPiece(server, BlockPos.ZERO, rotation, TerrainBox.of(0, 0, 0, 0, 0, 0));
            var corner = StructureTemplate.calculateRelativePosition(new StructurePlaceSettings().setRotation(rotation), BlockPos.ZERO).offset(piece.getPosition());
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
        assertEquals(ORIGIN.getY(), footprint.minY() + piece.getBeardifierGroundLevelDelta());
    }

    @Test
    void theTerrainSettingsAndTheStructuresProcessorsAreSavedWithThePiece(MinecraftServer server) {
        var context = new StructurePieceSerializationContext(server.getResourceManager(), server.registryAccess(), server.getStructureTemplateManager());
        var sunk = createPiece(server, new BlockPos(0, -4, 0));
        var reloadedSunk = new ModularStructurePiece(context, sunk.createTag(context));
        assertEquals(4, reloadedSunk.getBeardifierGroundLevelDelta());
        assertEquals(TerrainAdjustment.BEARD_THIN, reloadedSunk.getTerrainAdjustment());
        var boxed = createPiece(server, new BlockPos(0, -4, 0), Rotation.CLOCKWISE_180, TerrainBox.of(1, 2, 1, 2, 3, 2));
        var reloadedBoxed = new ModularStructurePiece(context, boxed.createTag(context));
        assertEquals(boxed.getBeardifierBox(), reloadedBoxed.getBeardifierBox());
        assertEquals(boxed.getBeardifierGroundLevelDelta(), reloadedBoxed.getBeardifierGroundLevelDelta());

        // By id, not inline.
        var mossify = server.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST).getOrThrow(ProcessorLists.MOSSIFY_10_PERCENT);
        var piece = createPiece(server, Optional.empty(), Optional.of(mossify));
        var tag = piece.createTag(context);
        assertEquals(Optional.of(ProcessorLists.MOSSIFY_10_PERCENT.identifier().toString()), tag.getCompoundOrEmpty("pool_element").getString("processors"));
        var reloaded = new ModularStructurePiece(context, tag);
        assertEquals(Optional.of(ProcessorLists.MOSSIFY_10_PERCENT), assertInstanceOf(SinglePoolElement.class, reloaded.getElement()).processors.unwrapKey());
    }

    @Test
    void ownProcessorsReplaceTheStructuresUnlessTheyInlineThem(MinecraftServer server) {
        var mossify = server.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST).getOrThrow(ProcessorLists.MOSSIFY_10_PERCENT);
        var own = processors(new BlockIgnoreProcessor(List.of(Blocks.STONE)));
        assertSame(own.orElseThrow(), assertInstanceOf(SinglePoolElement.class, createPiece(server, own, Optional.of(mossify)).getElement()).processors);
        var inlining = assertInstanceOf(SinglePoolElement.class, createPiece(server, processors(InlineFromStructureProcessor.INSTANCE), Optional.of(mossify)).getElement());
        assertEquals(mossify.value().list(), inlining.processors.value().list());
    }

    @Test
    void anEntryIsATemplateOrAnElement(MinecraftServer server) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var templateJson = JsonParser.parseString("""
                {"template": "minecraft:igloo/top", "offset": [0, -2, 0], "terrain_box": "footprint"}
                """);
        var template = assertInstanceOf(ExtendedSinglePoolElement.class, TemplateEntry.CODEC.parse(ops, templateJson).getOrThrow().element());
        assertEquals(TEMPLATE, template.getTemplateLocation());
        assertEquals(TerrainBox.footprint(), template.terrainBox());
        assertEquals(templateJson, TemplateEntry.CODEC.encodeStart(ops, new TemplateEntry(template, new BlockPos(0, -2, 0))).getOrThrow());

        var elementJson = JsonParser.parseString("""
                {"element": {"element_type": "minecraft:empty_pool_element"}}
                """);
        var element = TemplateEntry.CODEC.parse(ops, elementJson).getOrThrow();
        assertSame(EmptyPoolElement.INSTANCE, element.element());
        assertEquals(elementJson, TemplateEntry.CODEC.encodeStart(ops, element).getOrThrow());

        // A terrain box is a name or a box.
        assertEquals(TerrainBox.footprint(), parse("\"footprint\""));
        assertEquals(TerrainBox.none(), parse("\"none\""));
        assertEquals(TerrainBox.of(0, 1, 2, 3, 4, 5), parse("[0, 1, 2, 3, 4, 5]"));
    }

    private static TerrainBox parse(String json) {
        return TerrainBox.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static ModularStructurePiece createPiece(MinecraftServer server, BlockPos offset) {
        return createPiece(server, offset, Rotation.NONE, TerrainBox.template());
    }

    private static ModularStructurePiece createPiece(
            MinecraftServer server, Optional<Holder<StructureProcessorList>> processors, Optional<Holder<StructureProcessorList>> structureProcessors) {
        return createPiece(server, BlockPos.ZERO, Rotation.NONE, TerrainBox.template(), processors, structureProcessors);
    }

    private static ModularStructurePiece createPiece(MinecraftServer server, BlockPos offset, Rotation rotation, TerrainBox terrainBox) {
        return createPiece(server, offset, rotation, terrainBox, Optional.empty(), Optional.empty());
    }

    private static ModularStructurePiece createPiece(
            MinecraftServer server, BlockPos offset, Rotation rotation, TerrainBox terrainBox,
            Optional<Holder<StructureProcessorList>> processors, Optional<Holder<StructureProcessorList>> structureProcessors) {
        var element = new ExtendedSinglePoolElement(Either.left(TEMPLATE), processors.orElse(ExtendedSinglePoolElement.NO_PROCESSORS), StructureTemplatePool.Projection.RIGID, Optional.empty(), true, Optional.empty(), terrainBox);
        return ModularStructurePiece.create(server.getStructureTemplateManager(), new TemplateEntry(element, offset), ORIGIN, rotation,
                TerrainAdjustment.BEARD_THIN, LiquidSettings.IGNORE_WATERLOGGING, structureProcessors, Optional.empty());
    }

    private static Optional<Holder<StructureProcessorList>> processors(StructureProcessor... processors) {
        return Optional.of(Holder.direct(new StructureProcessorList(List.of(processors))));
    }
}
