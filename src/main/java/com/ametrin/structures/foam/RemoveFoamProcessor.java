package com.ametrin.structures.foam;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/// Replaces foam with its [FoamBlock#replacementState()] or `fill`, and treats air saved in the template as structure void.
///
/// This library's pieces and [com.ametrin.structures.structure.jigsaw.ExtendedSinglePoolElement]s add [#AIR] unless they already have one.
public class RemoveFoamProcessor implements StructureProcessor {
    public static final RemoveFoamProcessor AIR = new RemoveFoamProcessor(Blocks.AIR.defaultBlockState());
    public static final RemoveFoamProcessor WATER = new RemoveFoamProcessor(Fluids.WATER.defaultFluidState().createLegacyBlock());
    public static final MapCodec<RemoveFoamProcessor> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    BlockState.CODEC.optionalFieldOf("fill", Blocks.AIR.defaultBlockState()).forGetter(p -> p.fill))
            .apply(instance, RemoveFoamProcessor::new));

    private final BlockState fill;
    private final boolean waterFilled;

    /// With water as `fill`, every waterloggable block of the template is waterlogged too.
    public RemoveFoamProcessor(BlockState fill) {
        this.fill = fill;
        // Compared by type, since tags aren't bound yet when the constants are created.
        this.waterFilled = fill.getFluidState().getType().isSame(Fluids.WATER);
    }

    public BlockState fill() {
        return fill;
    }

    @Override
    public StructureTemplate.@Nullable StructureBlockInfo process(LevelReader level, BlockPos targetPosition, BlockPos referencePos, StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo current, StructurePlaceSettings settings, @Nullable StructureTemplate template) {
        var state = current.state();
        if (FoamBlock.isFoam(state)) {
            var replacement = state.getBlock() instanceof FoamBlock foam ? foam.replacementState().orElse(fill) : fill;
            return new StructureTemplate.StructureBlockInfo(current.pos(), replacement, null);
        }
        // Only air saved in the template is void. Air from an earlier processor (e.g. a jigsaw block or a second foam removal) is placed like any other block.
        if (state.isAir() && original.state().isAir()) {
            return null;
        }
        if (waterFilled && state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            return new StructureTemplate.StructureBlockInfo(
                    current.pos(), state.setValue(BlockStateProperties.WATERLOGGED, true), current.nbt());
        }
        return current;
    }

    /// Adds [#AIR] ahead of the other processors unless `settings` already removes foam.
    public static StructurePlaceSettings addDefaultIfAbsent(StructurePlaceSettings settings) {
        var processors = settings.getProcessors();
        if (processors.stream().noneMatch(RemoveFoamProcessor.class::isInstance)) {
            processors.addFirst(AIR);
        }
        return settings;
    }

    @Override
    public MapCodec<RemoveFoamProcessor> codec() {
        return CODEC;
    }
}
