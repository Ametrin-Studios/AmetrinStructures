package com.ametrin.structures.structure.modular;

import com.ametrin.structures.registry.ASPieceTypes;
import com.ametrin.structures.structure.Foundation;
import com.ametrin.structures.structure.TerrainFit;
import com.ametrin.structures.structure.jigsaw.ExtendedSinglePoolElement;
import com.ametrin.structures.structure.jigsaw.StructureElements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.neoforge.common.world.PieceBeardifierModifier;

import java.util.Optional;
import java.util.function.Function;

/// A pool element placed by a [TemplateEntry] or a jigsaw layout. A rigid element fits the terrain to its [TerrainBox] with the structure's terrain adaptation.
/// Structures of your own can place template entries with it through [#create].
// TODO 26.4: remove the suppression, it's for the deprecated PieceBeardifierModifier#getGroundLevelDelta the piece inherits.
@SuppressWarnings("removal")
public class ModularStructurePiece extends PoolElementStructurePiece implements PieceBeardifierModifier {
    private final TerrainFit terrain;

    /// @param groundLevelDelta how far above the bottom of `box` the ground lies
    public ModularStructurePiece(StructureTemplateManager templates, StructurePoolElement element, BlockPos position, int groundLevelDelta, Rotation rotation, BoundingBox box, LiquidSettings liquidSettings, TerrainAdjustment terrainAdaptation) {
        super(templates, element, position, groundLevelDelta, rotation, box, liquidSettings);
        this.terrain = element instanceof ExtendedSinglePoolElement extended
                ? extended.terrainBox().fit(terrainAdaptation, extended.template.map(templates::getOrCreate, Function.identity()), groundLevelDelta)
                : new TerrainFit(terrainAdaptation, Optional.empty(), groundLevelDelta);
    }

    /// A piece for `entry` at its offset from `origin`, turned with `rotation`, like modular structures create them.
    /// Elements without processors or a foundation of their own take `processors` and `foundation`, see [com.ametrin.structures.structure.jigsaw.StructureSettingsElement].
    public static ModularStructurePiece create(StructureTemplateManager templates, TemplateEntry entry, BlockPos origin, Rotation rotation, TerrainAdjustment terrainAdaptation, LiquidSettings liquidSettings, Optional<Holder<StructureProcessorList>> processors, Optional<Foundation> foundation) {
        var element = StructureElements.withStructureSettings(entry.element(), processors, foundation);
        var position = origin.offset(entry.offset().rotate(rotation));
        return new ModularStructurePiece(templates, element, position, -entry.offset().getY(), rotation, element.getBoundingBox(templates, position, rotation), liquidSettings, terrainAdaptation);
    }

    public ModularStructurePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(context, tag);
        // Older pieces saved the terrain's delta as their ground level delta.
        this.terrain = TerrainFit.read(tag, getGroundLevelDelta());
    }

    @Override
    public StructurePieceType getType() {
        return ASPieceTypes.MODULAR.get();
    }

    @Override
    public BoundingBox getBeardifierBox() {
        return terrain.place(new StructurePlaceSettings().setRotation(getRotation()), getPosition(), boundingBox);
    }

    @Override
    public TerrainAdjustment getTerrainAdjustment() {
        return getElement().getProjection() == StructureTemplatePool.Projection.RIGID ? terrain.adaptation() : TerrainAdjustment.NONE;
    }

    @Override
    public int getBeardifierGroundLevelDelta() {
        return terrain.groundLevelDelta();
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        terrain.write(tag);
    }
}
