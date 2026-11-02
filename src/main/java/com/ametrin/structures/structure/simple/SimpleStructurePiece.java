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
        super(templates, element, position, terrainBox.groundLevelDelta(groundLevelDelta), rotation, element.getBoundingBox(templates, position, rotation), liquidSettings);
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
        return terrainBox.map(local -> TerrainBox.place(local, new StructurePlaceSettings().setRotation(getRotation()), getPosition())).orElse(boundingBox);
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
