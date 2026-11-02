package com.ametrin.structures.structure.jigsaw;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/// Lists the jigsaws of pool elements for layouts, looking through [ConditionalPoolElement]s.
final class Jigsaws {
    // Subclasses that list their jigsaws differently go through their own method.
    private static final ClassValue<Boolean> LISTS_LIKE_SINGLE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("getShuffledJigsawBlocks", StructureTemplateManager.class, BlockPos.class, Rotation.class, RandomSource.class).getDeclaringClass() == SinglePoolElement.class;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }
    };

    private Jigsaws() {}

    static List<StructureTemplate.JigsawBlockInfo> shuffled(StructurePoolElement element, StructureTemplateManager templates, BlockPos position, Rotation rotation, RandomSource random) {
        if (element instanceof ConditionalPoolElement conditional) {
            return shuffled(conditional.element(), templates, position, rotation, random);
        }
        return element.getShuffledJigsawBlocks(templates, position, rotation, random);
    }

    /// The pools `element`'s jigsaws point to, in any of its palettes. Other elements list theirs with `random`.
    static Stream<ResourceKey<StructureTemplatePool>> pools(StructurePoolElement element, StructureTemplateManager templates, RandomSource random) {
        if (element instanceof ConditionalPoolElement conditional) {
            return pools(conditional.element(), templates, random);
        }
        if (!(element instanceof SinglePoolElement single) || !LISTS_LIKE_SINGLE.get(element.getClass())) {
            return element.getShuffledJigsawBlocks(templates, BlockPos.ZERO, Rotation.NONE, random).stream().map(StructureTemplate.JigsawBlockInfo::pool);
        }
        return template(single, templates).palettes.stream().flatMap(palette -> palette.jigsaws().stream()).map(StructureTemplate.JigsawBlockInfo::pool);
    }

    private static StructureTemplate template(SinglePoolElement element, StructureTemplateManager templates) {
        return element.template.map(templates::getOrCreate, Function.identity());
    }
}
