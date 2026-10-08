package com.ametrin.structures.structure;

import com.ametrin.structures.fixture.FixtureGeneration;
import com.ametrin.structures.foam.RemoveFoamProcessor;
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
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.Optional;
import java.util.function.Function;

public abstract class ExtendedTemplateStructurePiece extends TemplateStructurePiece {
    private static final String LIQUID_SETTINGS_KEY = "liquid_settings";
    private static final String FOUNDATION_KEY = "foundation";

    private Optional<Foundation> foundation = Optional.empty();

    protected ExtendedTemplateStructurePiece(
            StructurePieceType type,
            int genDepth,
            StructureTemplateManager manager,
            Identifier template,
            StructurePlaceSettings settings,
            BlockPos position) {
        super(type, genDepth, manager, template, template.toString(), prepare(settings), position);
    }

    protected ExtendedTemplateStructurePiece(
            StructurePieceType type,
            CompoundTag tag,
            StructureTemplateManager manager,
            Function<Identifier, StructurePlaceSettings> settingsFactory) {
        super(type, tag, manager, id -> readLiquidSettings(tag, prepare(settingsFactory.apply(id))));
        this.foundation = tag.read(FOUNDATION_KEY, Foundation.CODEC);
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

    /// Whether a waterloggable block placed where the world holds water takes it in.
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
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {}
}
