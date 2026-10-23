package com.ametrin.structures.structure.simple;

import com.ametrin.structures.registry.ASPieceTypes;
import com.ametrin.structures.structure.ExtendedTemplateStructurePiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;

@ApiStatus.Internal
public class SimpleStructurePiece extends ExtendedTemplateStructurePiece {
    private static final String ROTATION_KEY = "rotation";
    private static final String PROCESSORS_KEY = "processors";

    private final Rotation rotation;
    // Kept as a holder, so processor lists from the registry are saved as ids.
    private final Optional<Holder<StructureProcessorList>> processors;

    /// @param groundLevelDelta how far above the bottom of the template the ground lies, the negated Y of the template's offset from the structure origin
    public SimpleStructurePiece(
            StructureTemplateManager manager,
            Identifier template,
            BlockPos position,
            Rotation rotation,
            Optional<Holder<StructureProcessorList>> processors,
            TerrainAdjustment terrainAdaptation,
            TerrainBox terrainBox,
            int groundLevelDelta) {
        super(ASPieceTypes.SIMPLE.get(), 0, manager, template, settings(rotation, processors), position,
                terrainAdaptation, terrainBox, groundLevelDelta);
        this.rotation = rotation;
        this.processors = processors;
    }

    public SimpleStructurePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        this(context, tag, readRotation(tag), tag.read(PROCESSORS_KEY, StructureProcessorType.LIST_CODEC, registryOps(context)));
    }

    private SimpleStructurePiece(
            StructurePieceSerializationContext context, CompoundTag tag, Rotation rotation, Optional<Holder<StructureProcessorList>> processors) {
        super(ASPieceTypes.SIMPLE.get(), tag, context, id -> settings(rotation, processors));
        this.rotation = rotation;
        this.processors = processors;
    }

    private static StructurePlaceSettings settings(Rotation rotation, Optional<Holder<StructureProcessorList>> processors) {
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setIgnoreEntities(false);
        processors.ifPresent(list -> list.value().list().forEach(settings::addProcessor));
        return settings;
    }

    private static Rotation readRotation(CompoundTag tag) {
        return tag.read(ROTATION_KEY, Rotation.CODEC).orElse(Rotation.NONE);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.store(ROTATION_KEY, Rotation.CODEC, rotation);
        processors.ifPresent(list -> tag.store(PROCESSORS_KEY, StructureProcessorType.LIST_CODEC, registryOps(context), list));
    }
}
