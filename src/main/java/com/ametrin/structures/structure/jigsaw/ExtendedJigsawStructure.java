package com.ametrin.structures.structure.jigsaw;

import com.ametrin.structures.registry.ASStructureTypes;
import com.ametrin.structures.structure.ExtendedStructure;
import com.ametrin.structures.structure.ExtendedStructureBuilder;
import com.ametrin.structures.structure.ExtendedStructureSettings;
import com.ametrin.structures.structure.filter.PlacementFilter;
import com.ametrin.structures.structure.filter.TerrainSampler;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Vanilla's jigsaw structure with the library's [ExtendedStructureSettings].
///
/// Filters see the box around every piece, so a structure with filters assembles its pieces before it knows whether it fits;
/// one without them assembles only once it generates, as vanilla does.
public class ExtendedJigsawStructure extends ExtendedStructure {
    // Vanilla's final class is duplicated rather than reopened with an access transformer, which would cost more across version bumps.

    // The margin terrain adaptation needs around the structure.
    private static final int TERRAIN_ADAPTATION_MARGIN = 12;

    public static final MapCodec<ExtendedJigsawStructure> CODEC = RecordCodecBuilder.<ExtendedJigsawStructure>mapCodec(
                    instance -> instance.group(
                                    settingsCodec(instance),
                                    extendedSettingsCodec(instance),
                                    StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(ExtendedJigsawStructure::startPool),
                                    Identifier.CODEC
                                            .optionalFieldOf("start_jigsaw_name")
                                            .forGetter(ExtendedJigsawStructure::startJigsawName),
                                    Codec.intRange(JigsawStructure.MIN_DEPTH, JigsawStructure.MAX_DEPTH).fieldOf("size").forGetter(ExtendedJigsawStructure::size),
                                    HeightProvider.CODEC.fieldOf("start_height").forGetter(ExtendedJigsawStructure::startHeight),
                                    Codec.BOOL.fieldOf("use_expansion_hack").forGetter(ExtendedJigsawStructure::useExpansionHack),
                                    Heightmap.Types.CODEC
                                            .optionalFieldOf("project_start_to_heightmap")
                                            .forGetter(ExtendedJigsawStructure::projectStartToHeightmap),
                                    JigsawStructure.MaxDistance.CODEC
                                            .fieldOf("max_distance_from_center")
                                            .forGetter(ExtendedJigsawStructure::maxDistanceFromCenter),
                                    Codec.list(PoolAliasBinding.CODEC)
                                            .optionalFieldOf("pool_aliases", List.of())
                                            .forGetter(ExtendedJigsawStructure::poolAliases),
                                    DimensionPadding.CODEC
                                            .optionalFieldOf("dimension_padding", JigsawStructure.DEFAULT_DIMENSION_PADDING)
                                            .forGetter(ExtendedJigsawStructure::dimensionPadding),
                                    LiquidSettings.CODEC
                                            .optionalFieldOf("liquid_settings", LiquidSettings.IGNORE_WATERLOGGING)
                                            .forGetter(ExtendedJigsawStructure::liquidSettings))
                            .apply(instance, ExtendedJigsawStructure::new))
            .validate(ExtendedJigsawStructure::verifyRange);

    private final Holder<StructureTemplatePool> startPool;
    private final Optional<Identifier> startJigsawName;
    private final int maxDepth;
    private final HeightProvider startHeight;
    private final boolean useExpansionHack;
    private final Optional<Heightmap.Types> projectStartToHeightmap;
    private final JigsawStructure.MaxDistance maxDistanceFromCenter;
    private final List<PoolAliasBinding> poolAliases;
    private final DimensionPadding dimensionPadding;
    private final LiquidSettings liquidSettings;

    public ExtendedJigsawStructure(
            StructureSettings settings,
            ExtendedStructureSettings extendedSettings,
            Holder<StructureTemplatePool> startPool,
            Optional<Identifier> startJigsawName,
            int maxDepth,
            HeightProvider startHeight,
            boolean useExpansionHack,
            Optional<Heightmap.Types> projectStartToHeightmap,
            JigsawStructure.MaxDistance maxDistanceFromCenter,
            List<PoolAliasBinding> poolAliases,
            DimensionPadding dimensionPadding,
            LiquidSettings liquidSettings) {
        super(settings, extendedSettings);
        this.startPool = startPool;
        this.startJigsawName = startJigsawName;
        this.maxDepth = maxDepth;
        this.startHeight = startHeight;
        this.useExpansionHack = useExpansionHack;
        this.projectStartToHeightmap = projectStartToHeightmap;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
        this.poolAliases = List.copyOf(poolAliases);
        this.dimensionPadding = dimensionPadding;
        this.liquidSettings = liquidSettings;
    }

    /// Rejects a max distance that, with terrain adaptation's margin, exceeds vanilla's limit.
    protected static <S extends ExtendedJigsawStructure> DataResult<S> verifyRange(S structure) {
        int margin = structure.terrainAdaptation() == TerrainAdjustment.NONE ? 0 : TERRAIN_ADAPTATION_MARGIN;
        return structure.maxDistanceFromCenter().horizontal() + margin > JigsawStructure.MAX_TOTAL_STRUCTURE_RANGE
                ? DataResult.error(() -> "max_distance_from_center plus terrain adaptation margin must be at most "
                + JigsawStructure.MAX_TOTAL_STRUCTURE_RANGE)
                : DataResult.success(structure);
    }

    // Only the start piece is placed here; the rest is assembled when the filters or the structure need it.
    @Override
    protected Either<Candidate, Evaluation> layOut(GenerationContext context, Timer timer) {
        long start = System.nanoTime();
        ChunkPos chunkPos = context.chunkPos();
        int y = startHeight.sample(context.random(), new WorldGenerationContext(context.chunkGenerator(), context.heightAccessor()));
        BlockPos origin = new BlockPos(chunkPos.getMinBlockX(), y, chunkPos.getMinBlockZ());
        var stub = JigsawPlacement.addPieces(
                context,
                startPool,
                startJigsawName,
                maxDepth,
                origin,
                useExpansionHack,
                projectStartToHeightmap,
                maxDistanceFromCenter,
                PoolAliasLookup.create(poolAliases, origin, context.seed()),
                dimensionPadding,
                liquidSettings);
        timer.pieces(System.nanoTime() - start);
        if (stub.isEmpty()) {
            return Either.right(new Evaluation.NoPieces());
        }
        var assembly = new Assembly(stub.get());
        var terrain = new TerrainSampler(context, projectStartToHeightmap.orElse(Heightmap.Types.WORLD_SURFACE_WG));
        return Either.left(new Candidate(stub.get().position(), terrain, assembly::footprint, assembly::stub));
    }

    // Assembles the pieces at most once, whether the filters ask for them first or the structure does.
    private static final class Assembly {
        private final GenerationStub stub;
        private @Nullable StructurePiecesBuilder pieces;

        Assembly(GenerationStub stub) {
            this.stub = stub;
        }

        BoundingBox footprint() {
            if (pieces == null) {
                pieces = stub.getPiecesBuilder();
            }
            return pieces.getBoundingBox();
        }

        GenerationStub stub() {
            return pieces == null ? stub : new GenerationStub(stub.position(), Either.right(pieces));
        }
    }

    @Override
    public StructureType<?> type() {
        return ASStructureTypes.EXTENDED_JIGSAW.get();
    }

    public Holder<StructureTemplatePool> startPool() {
        return startPool;
    }

    public Optional<Identifier> startJigsawName() {
        return startJigsawName;
    }

    public int size() {
        return maxDepth;
    }

    public HeightProvider startHeight() {
        return startHeight;
    }

    public boolean useExpansionHack() {
        return useExpansionHack;
    }

    public Optional<Heightmap.Types> projectStartToHeightmap() {
        return projectStartToHeightmap;
    }

    public JigsawStructure.MaxDistance maxDistanceFromCenter() {
        return maxDistanceFromCenter;
    }

    public List<PoolAliasBinding> poolAliases() {
        return poolAliases;
    }

    public DimensionPadding dimensionPadding() {
        return dimensionPadding;
    }

    public LiquidSettings liquidSettings() {
        return liquidSettings;
    }

    public static Builder builder(StructureSettings settings, Holder<StructureTemplatePool> startPool) {
        return new Builder(settings, startPool);
    }

    /// Starts with `extendedSettings`' filters.
    public static Builder builder(StructureSettings settings, ExtendedStructureSettings extendedSettings, Holder<StructureTemplatePool> startPool) {
        var builder = new Builder(settings, startPool);
        extendedSettings.filters().forEach(builder::filter);
        return builder;
    }

    public static class Builder implements ExtendedStructureBuilder<Builder> {
        private final StructureSettings settings;
        private final List<PlacementFilter> filters = new ArrayList<>();
        private final Holder<StructureTemplatePool> startPool;
        private Optional<Identifier> startJigsawName = Optional.empty();
        private int size = 7;
        private HeightProvider startHeight = ConstantHeight.of(VerticalAnchor.absolute(0));
        private boolean useExpansionHack = false;
        private Optional<Heightmap.Types> projectStartToHeightmap = Optional.empty();
        private JigsawStructure.MaxDistance maxDistanceFromCenter = new JigsawStructure.MaxDistance(80);
        private List<PoolAliasBinding> poolAliases = List.of();
        private DimensionPadding dimensionPadding = JigsawStructure.DEFAULT_DIMENSION_PADDING;
        private LiquidSettings liquidSettings = LiquidSettings.IGNORE_WATERLOGGING;

        public Builder(StructureSettings settings, Holder<StructureTemplatePool> startPool) {
            this.settings = settings;
            this.startPool = startPool;
        }

        public Builder startJigsawName(Identifier name) {
            this.startJigsawName = Optional.of(name);
            return this;
        }

        /// expansion depth, {@value JigsawStructure#MIN_DEPTH} to {@value JigsawStructure#MAX_DEPTH}.
        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public Builder startHeight(HeightProvider startHeight) {
            this.startHeight = startHeight;
            return this;
        }

        public Builder startHeight(int y) {
            return startHeight(ConstantHeight.of(VerticalAnchor.absolute(y)));
        }

        public Builder startHeight(int minY, int maxY) {
            return startHeight(UniformHeight.of(VerticalAnchor.absolute(minY), VerticalAnchor.absolute(maxY)));
        }

        /// Stretches the bounding box of flat pieces (≤16 tall) upward to fit the tallest child their inward-facing jigsaws can attach, so layouts like village streets can host taller buildings inside their own footprint.
        public Builder useExpansionHack(boolean useExpansionHack) {
            this.useExpansionHack = useExpansionHack;
            return this;
        }

        public Builder projectStartToHeightmap(Heightmap.Types heightmap) {
            this.projectStartToHeightmap = Optional.of(heightmap);
            return this;
        }

        public Builder onSurface() {
            return projectStartToHeightmap(Heightmap.Types.WORLD_SURFACE_WG);
        }

        public Builder onOceanFloor() {
            return projectStartToHeightmap(Heightmap.Types.OCEAN_FLOOR_WG);
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
            this.poolAliases = List.copyOf(poolAliases);
            return this;
        }

        public Builder dimensionPadding(DimensionPadding dimensionPadding) {
            this.dimensionPadding = dimensionPadding;
            return this;
        }

        public Builder liquidSettings(LiquidSettings liquidSettings) {
            this.liquidSettings = liquidSettings;
            return this;
        }

        @Override
        public Builder filter(PlacementFilter filter) {
            filters.add(filter);
            return this;
        }

        public ExtendedJigsawStructure build() {
            return new ExtendedJigsawStructure(
                    settings,
                    new ExtendedStructureSettings(filters),
                    startPool,
                    startJigsawName,
                    size,
                    startHeight,
                    useExpansionHack,
                    projectStartToHeightmap,
                    maxDistanceFromCenter,
                    poolAliases,
                    dimensionPadding,
                    liquidSettings);
        }
    }
}
