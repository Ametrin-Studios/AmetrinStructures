package com.ametrin.structures.structure;

import com.ametrin.structures.fixture.FixtureGeneration;
import com.ametrin.structures.foam.RemoveFoamProcessor;
import com.ametrin.structures.structure.modular.TerrainBox;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.neoforged.neoforge.common.world.PieceBeardifierModifier;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/// A template piece that runs fixtures, removes foam, extends down to a [Foundation], fits the terrain to its [TerrainBox],
/// and runs its structure's processors, see [InlineFromStructureProcessor]. Like a pool element, it leaves out structure blocks and sets up mobs with their equipment and variants.
///
/// The terrain adapts to the piece's settings instead of the structure's, so pass it the structure's [net.minecraft.world.level.levelgen.structure.Structure#terrainAdaptation()].
/// An [ExtendedStructure] hands over its processors itself.
public abstract class ExtendedTemplateStructurePiece extends TemplateStructurePiece implements PieceBeardifierModifier {
    private static final String LIQUID_SETTINGS_KEY = "liquid_settings";
    private static final String FOUNDATION_KEY = "foundation";
    private static final String GROUND_LEVEL_DELTA_KEY = "ground_level_delta";
    private static final String TERRAIN_ADAPTATION_KEY = "terrain_adaptation";
    private static final String TERRAIN_BOX_KEY = "terrain_box";
    private static final String STRUCTURE_PROCESSORS_KEY = "structure_processors";

    // From the settings the piece was created with.
    private final List<StructureProcessor> ownProcessors;
    // Empty unless the piece runs them.
    private Optional<Holder<StructureProcessorList>> structureProcessors = Optional.empty();
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
        var ownProcessors = List.copyOf(settings.getProcessors());
        super(type, genDepth, manager, template, template.toString(), settings, position);
        this.ownProcessors = ownProcessors;
        this.terrainAdaptation = terrainAdaptation;
        this.terrainBox = terrainBox.resolve(this.template, groundLevelDelta);
        this.groundLevelDelta = terrainBox.groundLevelDelta(groundLevelDelta);
        applySettings();
    }

    protected ExtendedTemplateStructurePiece(
            StructurePieceType type,
            CompoundTag tag,
            StructurePieceSerializationContext context,
            Function<Identifier, StructurePlaceSettings> settingsFactory) {
        super(type, tag, context.structureTemplateManager(), id -> readLiquidSettings(tag, settingsFactory.apply(id)));
        this.ownProcessors = List.copyOf(placeSettings.getProcessors());
        this.structureProcessors = tag.read(STRUCTURE_PROCESSORS_KEY, StructureProcessorType.LIST_CODEC, registryOps(context));
        this.foundation = tag.read(FOUNDATION_KEY, Foundation.CODEC);
        this.terrainAdaptation = tag.read(TERRAIN_ADAPTATION_KEY, TerrainAdjustment.CODEC).orElse(TerrainAdjustment.NONE);
        this.terrainBox = tag.read(TERRAIN_BOX_KEY, BoundingBox.CODEC);
        this.groundLevelDelta = tag.getIntOr(GROUND_LEVEL_DELTA_KEY, 0);
        applySettings();
    }

    private static StructurePlaceSettings readLiquidSettings(CompoundTag tag, StructurePlaceSettings settings) {
        tag.read(LIQUID_SETTINGS_KEY, LiquidSettings.CODEC).ifPresent(settings::setLiquidSettings);
        return settings;
    }

    /// For saving processor lists from the registry as ids.
    protected static DynamicOps<Tag> registryOps(StructurePieceSerializationContext context) {
        return context.registryAccess().createSerializationContext(NbtOps.INSTANCE);
    }

    // Runs for both the fresh and the reloaded piece, so a partially generated structure finishes the same way it started.
    private void applySettings() {
        placeSettings.clearProcessors();
        placeSettings.addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        InlineFromStructureProcessor.resolve(ownProcessors, structureProcessors).forEach(placeSettings::addProcessor);
        RemoveFoamProcessor.addDefaultIfAbsent(placeSettings);
        placeSettings.setFinalizeEntities(true);
    }

    /// The structure's processors, if the piece runs them.
    public Optional<Holder<StructureProcessorList>> structureProcessors() {
        return structureProcessors;
    }

    /// Ignored if the piece has processors of its own without an [InlineFromStructureProcessor] marker.
    public void setStructureProcessors(Optional<Holder<StructureProcessorList>> structureProcessors) {
        this.structureProcessors = InlineFromStructureProcessor.usesStructures(ownProcessors) ? structureProcessors : Optional.empty();
        applySettings();
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
        return terrainBox.map(local -> TerrainBox.place(local, placeSettings, templatePosition)).orElse(boundingBox);
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
        structureProcessors.ifPresent(list -> tag.store(STRUCTURE_PROCESSORS_KEY, StructureProcessorType.LIST_CODEC, registryOps(context), list));
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {}
}
