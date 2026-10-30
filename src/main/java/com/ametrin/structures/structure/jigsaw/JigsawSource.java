package com.ametrin.structures.structure.jigsaw;

import com.ametrin.structures.registry.ASPieceSources;
import com.ametrin.structures.structure.simple.PieceSource;
import com.ametrin.structures.structure.simple.PieceSourceType;
import com.ametrin.structures.structure.simple.TemplateEntry;
import com.ametrin.structures.util.ASLog;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/// Grows a jigsaw layout from `start_pool`, like vanilla's jigsaw structure, and keeps [CountedPoolElement]s within their counts.
///
/// The start piece's ground level is at the start height, measured under the start piece. With `start_jigsaw_name`, that jigsaw is at the origin instead, one block lower.
/// The rest of the layout grows once the start piece is in place, since terrain matching pieces follow the terrain.
///
/// @param size          how many jigsaws deep the layout grows, from {@value JigsawStructure#MIN_DEPTH} to {@value JigsawStructure#MAX_DEPTH}. At the last depth only fallback pools attach.
/// @param maxBacktracks how often the layout may go back to place an element below its minimum before the structure gives up on the spot
public record JigsawSource(
        Holder<StructureTemplatePool> startPool,
        Optional<Identifier> startJigsawName,
        IntProvider size,
        boolean useExpansionHack,
        JigsawStructure.MaxDistance maxDistanceFromCenter,
        List<PoolAliasBinding> poolAliases,
        DimensionPadding dimensionPadding,
        int maxBacktracks) implements PieceSource {
    public static final int DEFAULT_MAX_BACKTRACKS = 16;

    public static final MapCodec<JigsawSource> CODEC = RecordCodecBuilder.<JigsawSource>mapCodec(instance -> instance.group(
                    StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(JigsawSource::startPool),
                    Identifier.CODEC.optionalFieldOf("start_jigsaw_name").forGetter(JigsawSource::startJigsawName),
                    IntProviders.codec(JigsawStructure.MIN_DEPTH, JigsawStructure.MAX_DEPTH).optionalFieldOf("size", ConstantInt.of(7)).forGetter(JigsawSource::size),
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
        return source.maxDistanceFromCenter.horizontal() + ExtendedJigsawStructure.TERRAIN_ADAPTATION_MARGIN > JigsawStructure.MAX_TOTAL_STRUCTURE_RANGE
                ? DataResult.error(() -> "max_distance_from_center must be at most " + (JigsawStructure.MAX_TOTAL_STRUCTURE_RANGE - ExtendedJigsawStructure.TERRAIN_ADAPTATION_MARGIN))
                : DataResult.success(source);
    }

    @Override
    public Growth appendPieces(List<StructurePiece> builder, Context context) {
        var generation = context.generation();
        var random = generation.random();
        var templates = generation.structureTemplateManager();
        var pools = generation.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        var origin = context.origin();
        var rotation = context.rotation();
        var aliases = PoolAliasLookup.create(poolAliases, origin, generation.seed());
        var pool = startPool.unwrapKey().flatMap(key -> pools.getOptional(aliases.lookup(key))).orElse(startPool.value());
        var element = pool.getRandomTemplate(random);
        if (element == EmptyPoolElement.INSTANCE) {
            return Growth.NONE;
        }

        var position = origin;
        if (startJigsawName.isPresent()) {
            var name = startJigsawName.get();
            var anchor = element.getShuffledJigsawBlocks(templates, origin, rotation, random).stream().filter(jigsaw -> name.equals(jigsaw.name())).findFirst();
            if (anchor.isEmpty()) {
                ASLog.error("No starting jigsaw {} found in start pool {}", name, startPool.unwrapKey().map(key -> key.identifier().toString()).orElse("<unregistered>"));
                return Growth.NONE;
            }
            position = origin.subtract(anchor.get().info().pos().subtract(origin));
        }
        var start = new PoolElementStructurePiece(templates, element, position, element.getGroundLevelDelta(), rotation, element.getBoundingBox(templates, position, rotation), context.liquidSettings());
        start.move(0, position.getY() - (start.getBoundingBox().minY() + start.getGroundLevelDelta()), 0);
        builder.add(start);

        int depth = size.sample(random);
        return (pieces, placedOrigin) -> {
            var box = start.getBoundingBox();
            if (dimensionPadding != DimensionPadding.ZERO && (box.minY() < generation.heightAccessor().getMinY() + dimensionPadding.bottom() || box.maxY() > generation.heightAccessor().getMaxY() - dimensionPadding.top())) {
                return false;
            }
            var free = JigsawAssembler.freeSpace(box, placedOrigin.getY(), maxDistanceFromCenter, generation.heightAccessor(), dimensionPadding);
            var assembler = new JigsawAssembler(generation, pools::get, depth, useExpansionHack, aliases, context.liquidSettings(), maxBacktracks);
            var assembled = assembler.assemble(start, free);
            assembled.ifPresent(all -> pieces.addAll(all.subList(1, all.size())));
            return assembled.isPresent();
        };
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
            return new JigsawSource(startPool, startJigsawName, size, useExpansionHack, maxDistanceFromCenter, poolAliases, dimensionPadding, maxBacktracks);
        }
    }
}
