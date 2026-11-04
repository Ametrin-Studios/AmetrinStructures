package com.ametrin.structures.structure.jigsaw;

import com.ametrin.structures.registry.ASPoolElements;
import com.ametrin.structures.structure.Foundation;
import com.ametrin.structures.structure.filter.PlacementFilter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/// Places copies of `element` in a row, each moved by the element's size in `direction`, which turns with the piece.
///
/// In a [JigsawSource], it attaches by the jigsaws of its first copy and gets as many copies as fit and pass `filters`, up to a number drawn from `count`.
/// With fewer than `min_count` it doesn't fit there. The piece stores the copies it got. Elsewhere it places `min_count` copies.
///
/// Jigsaws that face a neighboring copy are left out. The others stay open on every copy.
public class RepeatedPoolElement extends StructurePoolElement implements StructureSettingsElement {
    public static final int MAX_COUNT = 256;
    private static final Comparator<StructureTemplate.JigsawBlockInfo> HIGHEST_SELECTION_PRIORITY_FIRST = Comparator.comparingInt(StructureTemplate.JigsawBlockInfo::selectionPriority).reversed();

    public static final MapCodec<RepeatedPoolElement> CODEC = RecordCodecBuilder.<RepeatedPoolElement>mapCodec(instance -> instance.group(
                    StructurePoolElement.CODEC.fieldOf("element").forGetter(RepeatedPoolElement::element),
                    Direction.CODEC.fieldOf("direction").forGetter(RepeatedPoolElement::direction),
                    IntProviders.codec(1, MAX_COUNT).fieldOf("count").forGetter(RepeatedPoolElement::count),
                    Codec.intRange(1, MAX_COUNT).optionalFieldOf("min_count", 1).forGetter(RepeatedPoolElement::minCount),
                    PlacementFilter.CODEC.listOf().optionalFieldOf("filters", List.of()).forGetter(RepeatedPoolElement::filters))
            .apply(instance, RepeatedPoolElement::new))
            .validate(repeated -> repeated.minCount > repeated.count.maxInclusive()
                    ? DataResult.error(() -> "min_count must be at most the count")
                    : DataResult.success(repeated));

    private final StructurePoolElement element;
    private final Direction direction;
    private final IntProvider count;
    private final int minCount;
    private final List<PlacementFilter> filters;

    /// @param count    how many copies it tries for
    /// @param minCount how many copies it needs at least
    /// @param filters  what each copy's spot has to pass
    public RepeatedPoolElement(StructurePoolElement element, Direction direction, IntProvider count, int minCount, List<PlacementFilter> filters) {
        super(element.getProjection());
        this.element = element;
        this.direction = direction;
        this.count = count;
        this.minCount = minCount;
        this.filters = List.copyOf(filters);
    }

    /// The repeated element `element` stands for, if it's one or one inside a [ConditionalPoolElement].
    public static @Nullable RepeatedPoolElement unwrap(StructurePoolElement element) {
        return switch (element) {
            case RepeatedPoolElement repeated -> repeated;
            case ConditionalPoolElement conditional -> unwrap(conditional.element());
            default -> null;
        };
    }

    public StructurePoolElement element() {
        return element;
    }

    public Direction direction() {
        return direction;
    }

    public IntProvider count() {
        return count;
    }

    public int minCount() {
        return minCount;
    }

    public List<PlacementFilter> filters() {
        return filters;
    }

    /// This element with exactly `copies` copies.
    public RepeatedPoolElement withCopies(int copies) {
        return new RepeatedPoolElement(element, direction, ConstantInt.of(copies), copies, filters);
    }

    /// Where the copy `index` goes, for the element at `position`.
    public BlockPos copyPosition(StructureTemplateManager templates, BlockPos position, Rotation rotation, int index) {
        var size = element.getSize(templates, Rotation.NONE);
        var step = direction.getUnitVec3i().multiply(direction.getAxis().choose(size.getX(), size.getY(), size.getZ()));
        return position.offset(new BlockPos(step.multiply(index)).rotate(rotation));
    }

    /// Where each of the `min_count` copies goes.
    public List<BlockPos> copyPositions(StructureTemplateManager templates, BlockPos position, Rotation rotation) {
        return IntStream.range(0, minCount).mapToObj(index -> copyPosition(templates, position, rotation, index)).toList();
    }

    /// The first copy's jigsaws that don't face a second one, which it can attach by.
    List<StructureTemplate.JigsawBlockInfo> attachingJigsaws(StructureTemplateManager templates, BlockPos position, Rotation rotation, RandomSource random) {
        var next = element.getBoundingBox(templates, copyPosition(templates, position, rotation, 1), rotation);
        return Jigsaws.shuffled(element, templates, position, rotation, random).stream().filter(jigsaw -> !next.isInside(front(jigsaw))).toList();
    }

    private static BlockPos front(StructureTemplate.JigsawBlockInfo jigsaw) {
        return jigsaw.info().pos().relative(JigsawBlock.getFrontFacing(jigsaw.info().state()));
    }

    @Override
    public Vec3i getSize(StructureTemplateManager templates, Rotation rotation) {
        var box = getBoundingBox(templates, BlockPos.ZERO, rotation);
        return new Vec3i(box.getXSpan(), box.getYSpan(), box.getZSpan());
    }

    @Override
    public List<StructureTemplate.JigsawBlockInfo> getShuffledJigsawBlocks(StructureTemplateManager templates, BlockPos position, Rotation rotation, RandomSource random) {
        var positions = copyPositions(templates, position, rotation);
        var boxes = positions.stream().map(copy -> element.getBoundingBox(templates, copy, rotation)).toList();
        var jigsaws = new ArrayList<StructureTemplate.JigsawBlockInfo>();
        for (int i = 0; i < positions.size(); i++) {
            for (var jigsaw : Jigsaws.shuffled(element, templates, positions.get(i), rotation, random)) {
                var front = front(jigsaw);
                if ((i == 0 || !boxes.get(i - 1).isInside(front)) && (i == boxes.size() - 1 || !boxes.get(i + 1).isInside(front))) {
                    jigsaws.add(jigsaw);
                }
            }
        }
        Util.shuffle(jigsaws, random);
        jigsaws.sort(HIGHEST_SELECTION_PRIORITY_FIRST);
        return jigsaws;
    }

    @Override
    public BoundingBox getBoundingBox(StructureTemplateManager templates, BlockPos position, Rotation rotation) {
        var first = element.getBoundingBox(templates, position, rotation);
        var last = element.getBoundingBox(templates, copyPosition(templates, position, rotation, minCount - 1), rotation);
        return BoundingBox.encapsulatingBoxes(List.of(first, last)).orElseThrow();
    }

    @Override
    public boolean place(StructureTemplateManager templates, WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, BlockPos position, BlockPos referencePos, Rotation rotation, BoundingBox chunkBox, RandomSource random, LiquidSettings liquidSettings, boolean keepJigsaws) {
        var placed = true;
        for (var copy : copyPositions(templates, position, rotation)) {
            placed &= element.place(templates, level, structureManager, generator, copy, referencePos, rotation, chunkBox, random, liquidSettings, keepJigsaws);
        }
        return placed;
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
    public StructurePoolElement withStructureSettings(Optional<Holder<StructureProcessorList>> processors, Optional<Foundation> foundation) {
        var placed = StructureElements.withStructureSettings(element, processors, foundation);
        return placed == element ? this : new RepeatedPoolElement(placed, direction, count, minCount, filters);
    }

    @Override
    public StructurePoolElementType<?> getType() {
        return ASPoolElements.REPEATED.get();
    }

    @Override
    public String toString() {
        return "Repeated[" + element + ", " + direction.getSerializedName() + " " + count + "]";
    }
}
