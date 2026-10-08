package com.ametrin.structures.processor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

/// Places template blocks only where the world's block is replaceable, such as air, plants or water, so ruins blend into the surrounding terrain.
///
/// With `replaceable_only`, only the template's own replaceable blocks are held back: solid blocks
/// always go in, while the template's air, plants and water no longer carve into solid terrain.
public final class RetainExistingProcessor implements StructureProcessor {
    /// Holds back every template block.
    public static final RetainExistingProcessor ALL = new RetainExistingProcessor(false);
    /// Holds back only the template's replaceable blocks.
    public static final RetainExistingProcessor REPLACEABLE_ONLY = new RetainExistingProcessor(true);

    public static final MapCodec<RetainExistingProcessor> CODEC = Codec.BOOL
            .optionalFieldOf("replaceable_only", false)
            .xmap(replaceableOnly -> replaceableOnly ? REPLACEABLE_ONLY : ALL, p -> p.replaceableOnly);

    private final boolean replaceableOnly;

    private RetainExistingProcessor(boolean replaceableOnly) {
        this.replaceableOnly = replaceableOnly;
    }

    @Override
    public StructureTemplate.@Nullable StructureBlockInfo process(
            LevelReader level,
            BlockPos targetPosition,
            BlockPos referencePos,
            StructureTemplate.StructureBlockInfo original,
            StructureTemplate.StructureBlockInfo current,
            StructurePlaceSettings settings,
            @Nullable StructureTemplate template) {
        if (replaceableOnly && !current.state().canBeReplaced()) {
            return current;
        }
        return level.getBlockState(current.pos()).canBeReplaced() ? current : null;
    }

    @Override
    public MapCodec<RetainExistingProcessor> codec() {
        return CODEC;
    }
}
