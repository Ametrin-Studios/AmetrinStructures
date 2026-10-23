package com.ametrin.structures.mixin;

import com.ametrin.structures.structure.jigsaw.JigsawStructureProcessors;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(PoolElementStructurePiece.class)
public class PoolElementStructurePieceMixin implements JigsawStructureProcessors.Piece {
    @Unique
    private static final String ametrin$STRUCTURE_PROCESSORS_KEY = "ametrin_structures:structure_processors";

    @Unique
    private Optional<Holder<StructureProcessorList>> ametrin$structureProcessors = Optional.empty();

    @Override
    public Optional<Holder<StructureProcessorList>> ametrin$structureProcessors() {
        return ametrin$structureProcessors;
    }

    @Override
    public void ametrin$setStructureProcessors(Optional<Holder<StructureProcessorList>> processors) {
        ametrin$structureProcessors = processors;
    }

    @Inject(method = "<init>(Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePieceSerializationContext;Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void ametrin$readStructureProcessors(StructurePieceSerializationContext context, CompoundTag tag, CallbackInfo callback) {
        ametrin$structureProcessors = tag.read(ametrin$STRUCTURE_PROCESSORS_KEY, StructureProcessorType.LIST_CODEC, context.registryAccess().createSerializationContext(NbtOps.INSTANCE));
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void ametrin$saveStructureProcessors(StructurePieceSerializationContext context, CompoundTag tag, CallbackInfo callback) {
        ametrin$structureProcessors.ifPresent(list -> tag.store(ametrin$STRUCTURE_PROCESSORS_KEY, StructureProcessorType.LIST_CODEC, context.registryAccess().createSerializationContext(NbtOps.INSTANCE), list));
    }

    @WrapOperation(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/pools/StructurePoolElement;place(Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Rotation;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;Z)Z"))
    private boolean ametrin$placeWithStructureProcessors(
            StructurePoolElement element,
            StructureTemplateManager templates,
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator generator,
            BlockPos position,
            BlockPos referencePos,
            Rotation rotation,
            BoundingBox chunkBox,
            RandomSource random,
            LiquidSettings liquidSettings,
            boolean keepJigsaws,
            Operation<Boolean> original) {
        return JigsawStructureProcessors.place(ametrin$structureProcessors, () ->
                original.call(element, templates, level, structureManager, generator, position, referencePos, rotation, chunkBox, random, liquidSettings, keepJigsaws));
    }
}
