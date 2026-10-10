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
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;

@ApiStatus.Internal
public class SimpleStructurePiece extends ExtendedTemplateStructurePiece {
    private static final String ROTATION_KEY = "rotation";
    private static final String PROCESSORS_KEY = "processors";
    private static final String STRUCTURE_PROCESSORS_KEY = "structure_processors";

    private final Rotation rotation;
    // Both saved unexpanded, so processor lists from the registry are saved as their ids.
    private final Optional<Holder<StructureProcessorList>> processors;
    private final Optional<Holder<StructureProcessorList>> structureProcessors;

    /// @param structureProcessors see [InlineFromStructureProcessor#resolve]
    /// @param groundLevelDelta    how far above the bottom of the template the ground lies, the negated Y of the template's offset from the structure origin
    public SimpleStructurePiece(
            StructureTemplateManager manager,
            Identifier template,
            BlockPos position,
            Rotation rotation,
            Optional<Holder<StructureProcessorList>> processors,
            Optional<Holder<StructureProcessorList>> structureProcessors,
            TerrainAdjustment terrainAdaptation,
            TerrainBox terrainBox,
            int groundLevelDelta) {
        super(ASPieceTypes.SIMPLE.get(), 0, manager, template, settings(rotation, processors, structureProcessors), position,
                terrainAdaptation, terrainBox, groundLevelDelta);
        this.rotation = rotation;
        this.processors = processors;
        this.structureProcessors = InlineFromStructureProcessor.usesStructures(processors) ? structureProcessors : Optional.empty();
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

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.store(ROTATION_KEY, Rotation.CODEC, rotation);
        processors.ifPresent(list -> tag.store(PROCESSORS_KEY, StructureProcessorType.LIST_CODEC, registryOps(context), list));
        structureProcessors.ifPresent(list -> tag.store(STRUCTURE_PROCESSORS_KEY, StructureProcessorType.LIST_CODEC, registryOps(context), list));
    }
}
