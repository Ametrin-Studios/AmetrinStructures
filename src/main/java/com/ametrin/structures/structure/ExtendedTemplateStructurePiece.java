package com.ametrin.structures.structure;

import com.ametrin.structures.fixture.FixtureGeneration;
import com.ametrin.structures.foam.RemoveFoamProcessor;
import com.ametrin.structures.structure.simple.TerrainBox;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.neoforge.common.world.PieceBeardifierModifier;

import java.util.Optional;
import java.util.function.Function;

/// A template piece that runs fixtures, removes foam, extends down to a [Foundation], and fits the terrain to its [TerrainBox].
///
/// The terrain adapts to the piece's settings instead of the structure's, so pass it the structure's [net.minecraft.world.level.levelgen.structure.Structure#terrainAdaptation()].
public abstract class ExtendedTemplateStructurePiece extends TemplateStructurePiece implements PieceBeardifierModifier {
    private static final String LIQUID_SETTINGS_KEY = "liquid_settings";
    private static final String FOUNDATION_KEY = "foundation";
    private static final String GROUND_LEVEL_DELTA_KEY = "ground_level_delta";
    private static final String TERRAIN_ADAPTATION_KEY = "terrain_adaptation";
    private static final String TERRAIN_BOX_KEY = "terrain_box";

    private Optional<Foundation> foundation = Optional.empty();
    private final TerrainAdjustment terrainAdaptation;
    // In template coordinates; empty for the whole template.
    private final Optional<BoundingBox> terrainBox;
    private final int groundLevelDelta;

    /// @param groundLevelDelta how far above the bottom of the template the ground lies
    protected ExtendedTemplateStructurePiece(
            StructurePieceType type,
            int genDepth,
            StructureTemplateManager manager,
            Identifier template,
            StructurePlaceSettings settings,
            BlockPos position,
            TerrainAdjustment terrainAdaptation,
            TerrainBox terrainBox,
            int groundLevelDelta) {
        super(type, genDepth, manager, template, template.toString(), prepare(settings), position);
        this.terrainAdaptation = terrainAdaptation;
        this.terrainBox = terrainBox.resolve(this.template, groundLevelDelta);
        // Relative to the bottom of the terrain box. Only a local box moves it, and its bottom is the ground.
        this.groundLevelDelta = terrainBox instanceof TerrainBox.Local ? 0 : groundLevelDelta;
    }

    protected ExtendedTemplateStructurePiece(
            StructurePieceType type,
            CompoundTag tag,
            StructureTemplateManager manager,
            Function<Identifier, StructurePlaceSettings> settingsFactory) {
        super(type, tag, manager, id -> readLiquidSettings(tag, prepare(settingsFactory.apply(id))));
        this.foundation = tag.read(FOUNDATION_KEY, Foundation.CODEC);
        this.terrainAdaptation = tag.read(TERRAIN_ADAPTATION_KEY, TerrainAdjustment.CODEC).orElse(TerrainAdjustment.NONE);
        this.terrainBox = tag.read(TERRAIN_BOX_KEY, BoundingBox.CODEC);
        this.groundLevelDelta = tag.getIntOr(GROUND_LEVEL_DELTA_KEY, 0);
    }

    private static StructurePlaceSettings readLiquidSettings(CompoundTag tag, StructurePlaceSettings settings) {
        tag.read(LIQUID_SETTINGS_KEY, LiquidSettings.CODEC).ifPresent(settings::setLiquidSettings);
        return settings;
    }

    // Applied to both the fresh and the reloaded piece, so a partially generated structure finishes the same way it started.
    private static StructurePlaceSettings prepare(StructurePlaceSettings settings) {
        return RemoveFoamProcessor.addDefaultIfAbsent(settings);
    }

    public Identifier templateId() {
        return makeTemplateLocation();
    }

    /// Whether waterloggable blocks placed in water get waterlogged.
    public LiquidSettings liquidSettings() {
        return placeSettings.shouldApplyWaterlogging() ? LiquidSettings.APPLY_WATERLOGGING : LiquidSettings.IGNORE_WATERLOGGING;
    }

    public void setLiquidSettings(LiquidSettings liquidSettings) {
        placeSettings.setLiquidSettings(liquidSettings);
    }

    /// Extends the piece down to the ground, see [Foundation].
    public Optional<Foundation> foundation() {
        return foundation;
    }

    public void setFoundation(Optional<Foundation> foundation) {
        this.foundation = foundation;
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
    public void postProcess(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox chunkBox,
            ChunkPos chunkPos,
            BlockPos referencePos) {
        super.postProcess(level, structureManager, generator, random, chunkBox, chunkPos, referencePos);
        FixtureGeneration.process(this, template, templatePosition, placeSettings, chunkBox, random, level, generator);
        foundation.ifPresent(value -> value.placeUnder(level, boundingBox, chunkBox));
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.store(LIQUID_SETTINGS_KEY, LiquidSettings.CODEC, liquidSettings());
        foundation.ifPresent(value -> tag.store(FOUNDATION_KEY, Foundation.CODEC, value));
        tag.store(TERRAIN_ADAPTATION_KEY, TerrainAdjustment.CODEC, terrainAdaptation);
        terrainBox.ifPresent(box -> tag.store(TERRAIN_BOX_KEY, BoundingBox.CODEC, box));
        tag.putInt(GROUND_LEVEL_DELTA_KEY, groundLevelDelta);
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {}
}
