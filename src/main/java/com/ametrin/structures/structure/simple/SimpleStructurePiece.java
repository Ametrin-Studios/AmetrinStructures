package com.ametrin.structures.structure.simple;

import com.ametrin.structures.registry.ASPieceTypes;
import com.ametrin.structures.structure.ExtendedTemplateStructurePiece;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.neoforged.neoforge.common.world.PieceBeardifierModifier;
import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;

@ApiStatus.Internal
public class SimpleStructurePiece extends ExtendedTemplateStructurePiece implements PieceBeardifierModifier {
    private static final String ROTATION_KEY = "rotation";
    private static final String PROCESSORS_KEY = "processors";
    private static final String STRUCTURE_PROCESSORS_KEY = "structure_processors";
    private static final String GROUND_LEVEL_DELTA_KEY = "ground_level_delta";
    private static final String TERRAIN_ADAPTATION_KEY = "terrain_adaptation";
    private static final String TERRAIN_BOX_KEY = "terrain_box";

    private final Rotation rotation;
    // Both saved unexpanded, so processor lists from the registry are saved as their ids.
    private final Optional<Holder<StructureProcessorList>> processors;
    private final Optional<Holder<StructureProcessorList>> structureProcessors;
    private final int groundLevelDelta;
    private TerrainAdjustment terrainAdaptation = TerrainAdjustment.NONE;
    // In template coordinates; empty for the whole template.
    private final Optional<BoundingBox> terrainBox;

    /// @param structureProcessors see [InlineFromStructureProcessor#resolve]
    /// @param groundLevelDelta    how far above the bottom of the template the ground lies, the negated Y of the template's offset from the structure origin
    public SimpleStructurePiece(
            StructureTemplateManager manager,
            Identifier template,
            BlockPos position,
            Rotation rotation,
            Optional<Holder<StructureProcessorList>> processors,
            Optional<Holder<StructureProcessorList>> structureProcessors,
            int groundLevelDelta,
            TerrainBox terrainBox) {
        super(ASPieceTypes.SIMPLE.get(), 0, manager, template, settings(rotation, processors, structureProcessors), position);
        this.rotation = rotation;
        this.processors = processors;
        this.structureProcessors = InlineFromStructureProcessor.usesStructures(processors) ? structureProcessors : Optional.empty();
        this.terrainBox = terrainBox.resolve(this.template, groundLevelDelta);
        // Relative to the bottom of the terrain box, which only a local box moves; its bottom is the ground.
        this.groundLevelDelta = terrainBox instanceof TerrainBox.Local ? 0 : groundLevelDelta;
    }

    public SimpleStructurePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        this(context, tag, readRotation(tag), readProcessors(context, tag, PROCESSORS_KEY), readProcessors(context, tag, STRUCTURE_PROCESSORS_KEY));
    }

    private SimpleStructurePiece(
            StructurePieceSerializationContext context,
            CompoundTag tag,
            Rotation rotation,
            Optional<Holder<StructureProcessorList>> processors,
            Optional<Holder<StructureProcessorList>> structureProcessors) {
        super(ASPieceTypes.SIMPLE.get(), tag, context.structureTemplateManager(), id -> settings(rotation, processors, structureProcessors));
        this.rotation = rotation;
        this.processors = processors;
        this.structureProcessors = structureProcessors;
        this.groundLevelDelta = tag.getIntOr(GROUND_LEVEL_DELTA_KEY, 0);
        this.terrainAdaptation = tag.read(TERRAIN_ADAPTATION_KEY, TerrainAdjustment.CODEC).orElse(TerrainAdjustment.NONE);
        this.terrainBox = tag.read(TERRAIN_BOX_KEY, BoundingBox.CODEC);
    }

    private static StructurePlaceSettings settings(
            Rotation rotation, Optional<Holder<StructureProcessorList>> processors, Optional<Holder<StructureProcessorList>> structureProcessors) {
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setIgnoreEntities(false);
        InlineFromStructureProcessor.resolve(processors, structureProcessors).ifPresent(list -> list.value().list().forEach(settings::addProcessor));
        return settings;
    }

    private static DynamicOps<Tag> registryOps(StructurePieceSerializationContext context) {
        return context.registryAccess().createSerializationContext(NbtOps.INSTANCE);
    }

    private static Rotation readRotation(CompoundTag tag) {
        return tag.read(ROTATION_KEY, Rotation.CODEC).orElse(Rotation.NONE);
    }

    private static Optional<Holder<StructureProcessorList>> readProcessors(
            StructurePieceSerializationContext context, CompoundTag tag, String key) {
        return tag.read(key, StructureProcessorType.LIST_CODEC, registryOps(context));
    }

    public void setTerrainAdaptation(TerrainAdjustment terrainAdaptation) {
        this.terrainAdaptation = terrainAdaptation;
    }

    @Override
    public BoundingBox getBeardifierBox() {
        // Placed like the template itself, so it follows the rotation and any later move.
        return terrainBox.map(local -> BoundingBox.fromCorners(
                        place(new BlockPos(local.minX(), local.minY(), local.minZ())),
                        place(new BlockPos(local.maxX(), local.maxY(), local.maxZ()))))
                .orElse(boundingBox);
    }

    private BlockPos place(BlockPos local) {
        return StructureTemplate.calculateRelativePosition(placeSettings, local).offset(templatePosition);
    }

    @Override
    public TerrainAdjustment getTerrainAdjustment() {
        return terrainAdaptation;
    }

    @Override
    public int getGroundLevelDelta() {
        return groundLevelDelta;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.store(ROTATION_KEY, Rotation.CODEC, rotation);
        processors.ifPresent(list -> tag.store(PROCESSORS_KEY, StructureProcessorType.LIST_CODEC, registryOps(context), list));
        structureProcessors.ifPresent(list -> tag.store(STRUCTURE_PROCESSORS_KEY, StructureProcessorType.LIST_CODEC, registryOps(context), list));
        tag.putInt(GROUND_LEVEL_DELTA_KEY, groundLevelDelta);
        tag.store(TERRAIN_ADAPTATION_KEY, TerrainAdjustment.CODEC, terrainAdaptation);
        terrainBox.ifPresent(box -> tag.store(TERRAIN_BOX_KEY, BoundingBox.CODEC, box));
    }
}
