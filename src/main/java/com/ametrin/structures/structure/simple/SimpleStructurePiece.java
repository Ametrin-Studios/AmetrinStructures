package com.ametrin.structures.structure.simple;

import com.ametrin.structures.registry.ASPieceTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.neoforge.common.world.PieceBeardifierModifier;
import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;
import java.util.function.Function;

/// A pool element placed by a [TemplateEntry]. It fits the terrain to its [TerrainBox] with the structure's terrain adaptation.
@ApiStatus.Internal
public class SimpleStructurePiece extends PoolElementStructurePiece implements PieceBeardifierModifier {
    private static final String TERRAIN_ADAPTATION_KEY = "terrain_adaptation";
    private static final String TERRAIN_BOX_KEY = "terrain_box";

    private final TerrainAdjustment terrainAdaptation;
    // In the element's coordinates; empty for the whole piece.
    private final Optional<BoundingBox> terrainBox;

    /// @param groundLevelDelta how far above the bottom of the element the ground lies, the negated Y of the entry's offset
    public SimpleStructurePiece(StructureTemplateManager templates, StructurePoolElement element, BlockPos position, Rotation rotation, LiquidSettings liquidSettings, TerrainAdjustment terrainAdaptation, TerrainBox terrainBox, int groundLevelDelta) {
        // Relative to the bottom of the terrain box. Only a local box moves it, and its bottom is the ground.
        super(templates, element, position, terrainBox instanceof TerrainBox.Local ? 0 : groundLevelDelta, rotation, element.getBoundingBox(templates, position, rotation), liquidSettings);
        this.terrainAdaptation = terrainAdaptation;
        this.terrainBox = element instanceof SinglePoolElement single
                ? terrainBox.resolve(single.template.map(templates::getOrCreate, Function.identity()), groundLevelDelta)
                : terrainBox instanceof TerrainBox.Local(var local) ? Optional.of(local) : Optional.empty();
    }

    public SimpleStructurePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(context, tag);
        this.terrainAdaptation = tag.read(TERRAIN_ADAPTATION_KEY, TerrainAdjustment.CODEC).orElse(TerrainAdjustment.NONE);
        this.terrainBox = tag.read(TERRAIN_BOX_KEY, BoundingBox.CODEC);
    }

    @Override
    public StructurePieceType getType() {
        return ASPieceTypes.SIMPLE.get();
    }

    @Override
    public BoundingBox getBeardifierBox() {
        // Placed like the element itself, so it follows the rotation and any later move.
        var settings = new StructurePlaceSettings().setRotation(getRotation());
        return terrainBox.map(local -> BoundingBox.fromCorners(
                        StructureTemplate.calculateRelativePosition(settings, new BlockPos(local.minX(), local.minY(), local.minZ())).offset(getPosition()),
                        StructureTemplate.calculateRelativePosition(settings, new BlockPos(local.maxX(), local.maxY(), local.maxZ())).offset(getPosition())))
                .orElse(boundingBox);
    }

    @Override
    public TerrainAdjustment getTerrainAdjustment() {
        return terrainAdaptation;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.store(TERRAIN_ADAPTATION_KEY, TerrainAdjustment.CODEC, terrainAdaptation);
        terrainBox.ifPresent(box -> tag.store(TERRAIN_BOX_KEY, BoundingBox.CODEC, box));
    }
}
