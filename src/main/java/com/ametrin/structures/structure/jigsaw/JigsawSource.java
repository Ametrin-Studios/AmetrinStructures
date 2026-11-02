package com.ametrin.structures.structure.jigsaw;

import com.ametrin.structures.registry.ASPieceSources;
import com.ametrin.structures.structure.modular.PieceSource;
import com.ametrin.structures.structure.modular.PieceSourceType;
import com.ametrin.structures.structure.modular.TemplateEntry;
import com.ametrin.structures.util.ASLog;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/// Grows a jigsaw layout from `start_pool`, like vanilla's jigsaw structure, and keeps [ConditionalPoolElement]s within their counts.
///
/// The start piece's ground level is at the start height, measured under the start piece. With `start_jigsaw_name`, that jigsaw is at the origin instead, one block lower.
/// `offset` moves the start from the origin and turns with the structure, like a template entry's.
/// The rest of the layout grows once all pieces are at their start height, since terrain matching pieces follow the terrain. It keeps clear of the structure's other pieces.
///
/// @param size          how many jigsaws deep the layout grows, from 0 to {@value #MAX_SIZE}. At the last depth only fallback pools attach.
/// @param maxBacktracks how often the layout may go back to place an element below its soft minimum before it settles for fewer, or gives up on the spot below the minimum
public record JigsawSource(
        Holder<StructureTemplatePool> startPool,
        Optional<Identifier> startJigsawName,
        BlockPos offset,
        IntProvider size,
        boolean useExpansionHack,
        JigsawStructure.MaxDistance maxDistanceFromCenter,
        List<PoolAliasBinding> poolAliases,
        DimensionPadding dimensionPadding,
        int maxBacktracks) implements PieceSource {
    public static final int DEFAULT_MAX_BACKTRACKS = 16;
    // Vanilla stops at 20. Deeper layouts, like a shaft down to a fixed height, need more.
    public static final int MAX_SIZE = 128;
    // The margin terrain adaptation needs around the structure.
    private static final int TERRAIN_ADAPTATION_MARGIN = 12;

    public static final MapCodec<JigsawSource> CODEC = RecordCodecBuilder.<JigsawSource>mapCodec(instance -> instance.group(
                    StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(JigsawSource::startPool),
                    Identifier.CODEC.optionalFieldOf("start_jigsaw_name").forGetter(JigsawSource::startJigsawName),
                    BlockPos.CODEC.optionalFieldOf("offset", BlockPos.ZERO).forGetter(JigsawSource::offset),
                    IntProviders.codec(0, MAX_SIZE).optionalFieldOf("size", ConstantInt.of(7)).forGetter(JigsawSource::size),
                    Codec.BOOL.optionalFieldOf("use_expansion_hack", false).forGetter(JigsawSource::useExpansionHack),
                    JigsawStructure.MaxDistance.CODEC.optionalFieldOf("max_distance_from_center", new JigsawStructure.MaxDistance(80)).forGetter(JigsawSource::maxDistanceFromCenter),
                    Codec.list(PoolAliasBinding.CODEC).optionalFieldOf("pool_aliases", List.of()).forGetter(JigsawSource::poolAliases),
                    DimensionPadding.CODEC.optionalFieldOf("dimension_padding", JigsawStructure.DEFAULT_DIMENSION_PADDING).forGetter(JigsawSource::dimensionPadding),
                    Codec.intRange(0, 1024).optionalFieldOf("max_backtracks", DEFAULT_MAX_BACKTRACKS).forGetter(JigsawSource::maxBacktracks))
            .apply(instance, JigsawSource::new))
            .validate(JigsawSource::verifyRange);

    public JigsawSource {
        poolAliases = List.copyOf(poolAliases);
    }

    // The source doesn't know the structure's terrain adaptation, so it always leaves room for its margin.
    private static DataResult<JigsawSource> verifyRange(JigsawSource source) {
        int offset = Math.max(Math.abs(source.offset.getX()), Math.abs(source.offset.getZ()));
        return source.maxDistanceFromCenter.horizontal() + offset + TERRAIN_ADAPTATION_MARGIN > JigsawStructure.MAX_TOTAL_STRUCTURE_RANGE
                ? DataResult.error(() -> "max_distance_from_center plus the horizontal offset must be at most " + (JigsawStructure.MAX_TOTAL_STRUCTURE_RANGE - TERRAIN_ADAPTATION_MARGIN))
                : DataResult.success(source);
    }

    @Override
    public void appendPieces(StructurePiecesBuilder builder, Context context) {
        var generation = context.generation();
        var random = generation.random();
        var templates = generation.structureTemplateManager();
        var pools = generation.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        var rotation = context.rotation();
        var origin = context.origin().offset(offset.rotate(rotation));
        var aliases = PoolAliasLookup.create(poolAliases, origin, generation.seed());
        var pool = startPool.unwrapKey().flatMap(key -> pools.getOptional(aliases.lookup(key))).orElse(startPool.value());
        var element = pool.getRandomTemplate(random);
        if (element == EmptyPoolElement.INSTANCE) {
            return;
        }

        var position = origin;
        if (startJigsawName.isPresent()) {
            var name = startJigsawName.get();
            var anchor = Jigsaws.shuffled(element, templates, origin, rotation, random).stream().filter(jigsaw -> name.equals(jigsaw.name())).findFirst();
            if (anchor.isEmpty()) {
                ASLog.error("No starting jigsaw {} found in start pool {}", name, startPool.unwrapKey().map(key -> key.identifier().toString()).orElse("<unregistered>"));
                return;
            }
            position = origin.subtract(anchor.get().info().pos().subtract(origin));
        }
        var start = JigsawAssembler.createPiece(context, element, position, element.getGroundLevelDelta(), rotation, element.getBoundingBox(templates, position, rotation));
        start.move(0, position.getY() - (start.getBoundingBox().minY() + start.getGroundLevelDelta()), 0);
        builder.addPiece(start);

        int depth = size.sample(random);
        context.afterPlacement((pieces, placedOrigin) -> {
            var box = start.getBoundingBox();
            if (dimensionPadding != DimensionPadding.ZERO && (box.minY() < generation.heightAccessor().getMinY() + dimensionPadding.bottom() || box.maxY() > generation.heightAccessor().getMaxY() - dimensionPadding.top())) {
                return false;
            }
            var free = JigsawAssembler.freeSpace(box, placedOrigin.getY() + offset.getY(), maxDistanceFromCenter, generation.heightAccessor(), dimensionPadding);
            var others = pieces.build().pieces().stream().filter(piece -> piece != start).map(piece -> piece.getBoundingBox()).toList();
            var assembler = new JigsawAssembler(context, pools::get, depth, useExpansionHack, aliases, maxBacktracks);
            var assembled = assembler.assemble(start, element, free, others);
            assembled.ifPresent(all -> all.subList(1, all.size()).forEach(pieces::addPiece));
            return assembled.isPresent();
        });
    }

    @Override
    public PieceSourceType type() {
        return ASPieceSources.JIGSAW.get();
    }

    // Pool elements aren't template entries.
    @Override
    public Stream<TemplateEntry> templates() {
        return Stream.empty();
    }

    public static class Builder {
        private final Holder<StructureTemplatePool> startPool;
        private Optional<Identifier> startJigsawName = Optional.empty();
        private BlockPos offset = BlockPos.ZERO;
        private IntProvider size = ConstantInt.of(7);
        private boolean useExpansionHack = false;
        private JigsawStructure.MaxDistance maxDistanceFromCenter = new JigsawStructure.MaxDistance(80);
        private List<PoolAliasBinding> poolAliases = List.of();
        private DimensionPadding dimensionPadding = JigsawStructure.DEFAULT_DIMENSION_PADDING;
        private int maxBacktracks = DEFAULT_MAX_BACKTRACKS;

        public Builder(Holder<StructureTemplatePool> startPool) {
            this.startPool = startPool;
        }

        public Builder startJigsawName(Identifier name) {
            this.startJigsawName = Optional.of(name);
            return this;
        }

        public Builder offset(int x, int y, int z) {
            return offset(new BlockPos(x, y, z));
        }

        /// Moves the start from the structure's origin. Turns with the structure.
        public Builder offset(BlockPos offset) {
            this.offset = offset;
            return this;
        }

        public Builder size(int size) {
            return size(ConstantInt.of(size));
        }

        /// A random depth from `min` to `max`, picked per structure.
        public Builder size(int min, int max) {
            return size(UniformInt.of(min, max));
        }

        public Builder size(IntProvider size) {
            this.size = size;
            return this;
        }

        /// Expands the bounding box of flat pieces (16 blocks tall or less) upward to fit the tallest piece their inward-facing jigsaws can attach. This lets layouts like village streets have taller buildings inside their footprint.
        public Builder useExpansionHack(boolean useExpansionHack) {
            this.useExpansionHack = useExpansionHack;
            return this;
        }

        public Builder maxDistanceFromCenter(int maxDistanceFromCenter) {
            return maxDistanceFromCenter(new JigsawStructure.MaxDistance(maxDistanceFromCenter));
        }

        public Builder maxDistanceFromCenter(int horizontal, int vertical) {
            return maxDistanceFromCenter(new JigsawStructure.MaxDistance(horizontal, vertical));
        }

        public Builder maxDistanceFromCenter(JigsawStructure.MaxDistance maxDistanceFromCenter) {
            this.maxDistanceFromCenter = maxDistanceFromCenter;
            return this;
        }

        public Builder poolAliases(List<PoolAliasBinding> poolAliases) {
            this.poolAliases = poolAliases;
            return this;
        }

        public Builder dimensionPadding(DimensionPadding dimensionPadding) {
            this.dimensionPadding = dimensionPadding;
            return this;
        }

        public Builder maxBacktracks(int maxBacktracks) {
            this.maxBacktracks = maxBacktracks;
            return this;
        }

        public JigsawSource build() {
            return new JigsawSource(startPool, startJigsawName, offset, size, useExpansionHack, maxDistanceFromCenter, poolAliases, dimensionPadding, maxBacktracks);
        }
    }
}
