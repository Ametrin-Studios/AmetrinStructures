package com.ametrin.structures.structure.jigsaw;

import com.ametrin.structures.registry.ASPoolElements;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.List;
import java.util.Optional;

/// Limits how often `element` appears in one structure. Only [JigsawSource] reads the counts. Other jigsaw structures place it like `element`.
///
/// The layout tries to place `soft_min_count` of the element once it reaches a jigsaw that could attach it, and generates with fewer where it can't fit them.
/// A spot where it can't fit `min_count` gets no structure, so a `min_count` above 0 makes the structure generate less often.
public class CountedPoolElement extends StructurePoolElement {
    private record Counts(int min, Optional<Integer> softMin, Optional<Integer> max) {}

    private static final MapCodec<Counts> COUNTS_CODEC = RecordCodecBuilder.<Counts>mapCodec(instance -> instance.group(
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("min_count", 0).forGetter(Counts::min),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("soft_min_count").forGetter(Counts::softMin),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("max_count").forGetter(Counts::max))
            .apply(instance, Counts::new))
            .validate(counts -> problem(counts.min(), counts.softMin().orElse(counts.min()), counts.max())
                    .map(problem -> DataResult.<Counts>error(() -> problem))
                    .orElseGet(() -> DataResult.success(counts)));
    public static final MapCodec<CountedPoolElement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    StructurePoolElement.CODEC.fieldOf("element").forGetter(CountedPoolElement::element),
                    COUNTS_CODEC.forGetter(counted -> new Counts(counted.minCount, Optional.of(counted.softMinCount).filter(softMin -> softMin != counted.minCount), counted.maxCount)))
            .apply(instance, (element, counts) -> new CountedPoolElement(element, counts.min(), counts.softMin().orElse(counts.min()), counts.max())));

    private final StructurePoolElement element;
    private final int minCount;
    private final int softMinCount;
    private final Optional<Integer> maxCount;

    /// @param minCount     how many a structure needs, or it doesn't generate at the spot
    /// @param softMinCount how many the layout tries for at least, fewer where they don't fit. At least `minCount`.
    /// @param maxCount     how many a structure can have at most, empty for no limit
    public CountedPoolElement(StructurePoolElement element, int minCount, int softMinCount, Optional<Integer> maxCount) {
        super(element.getProjection());
        problem(minCount, softMinCount, maxCount).ifPresent(problem -> {
            throw new IllegalArgumentException(problem);
        });
        this.element = element;
        this.minCount = minCount;
        this.softMinCount = softMinCount;
        this.maxCount = maxCount;
    }

    private static Optional<String> problem(int minCount, int softMinCount, Optional<Integer> maxCount) {
        if (minCount < 0 || maxCount.filter(max -> max < 0).isPresent()) {
            return Optional.of("counts can't be negative");
        }
        if (softMinCount < minCount) {
            return Optional.of("soft_min_count must be at least min_count");
        }
        return maxCount.filter(max -> max < softMinCount).map(_ -> "max_count must be at least soft_min_count");
    }

    public StructurePoolElement element() {
        return element;
    }

    public int minCount() {
        return minCount;
    }

    public int softMinCount() {
        return softMinCount;
    }

    /// Empty for no limit.
    public Optional<Integer> maxCount() {
        return maxCount;
    }

    @Override
    public Vec3i getSize(StructureTemplateManager templates, Rotation rotation) {
        return element.getSize(templates, rotation);
    }

    @Override
    public List<StructureTemplate.JigsawBlockInfo> getShuffledJigsawBlocks(StructureTemplateManager templates, BlockPos position, Rotation rotation, RandomSource random) {
        return element.getShuffledJigsawBlocks(templates, position, rotation, random);
    }

    @Override
    public BoundingBox getBoundingBox(StructureTemplateManager templates, BlockPos position, Rotation rotation) {
        return element.getBoundingBox(templates, position, rotation);
    }

    @Override
    public boolean place(
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
            boolean keepJigsaws) {
        return element.place(templates, level, structureManager, generator, position, referencePos, rotation, chunkBox, random, liquidSettings, keepJigsaws);
    }

    @Override
    public void handleDataMarker(LevelAccessor level, StructureTemplate.StructureBlockInfo dataMarker, BlockPos position, Rotation rotation, RandomSource random, BoundingBox chunkBox) {
        element.handleDataMarker(level, dataMarker, position, rotation, random, chunkBox);
    }

    @Override
    public StructurePoolElement setProjection(StructureTemplatePool.Projection projection) {
        element.setProjection(projection);
        return this;
    }

    @Override
    public StructureTemplatePool.Projection getProjection() {
        return element.getProjection();
    }

    @Override
    public int getGroundLevelDelta() {
        return element.getGroundLevelDelta();
    }

    @Override
    public StructurePoolElementType<?> getType() {
        return ASPoolElements.COUNTED.get();
    }

    @Override
    public String toString() {
        return "Counted[" + element + ", " + minCount + "/" + softMinCount + ".." + maxCount.map(String::valueOf).orElse("") + "]";
    }
}
