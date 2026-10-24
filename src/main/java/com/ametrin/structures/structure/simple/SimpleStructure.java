package com.ametrin.structures.structure.simple;

import com.ametrin.structures.registry.ASStructureTypes;
import com.ametrin.structures.structure.*;
import com.ametrin.structures.structure.filter.TerrainSampler;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/// A structure made of one or more templates, at a start height measured from the terrain. It's a built-in type, so you don't need your own structure type, piece type or codec:
///
/// ```
/// STRUCTURES.registerSet("tower", set -> set
///     .evenSpreadPlacement(18, 0.6F)
///     .simple(tower -> tower.single("tower").surface().biomes(BiomeTags.IS_FOREST)));
/// ```
public class SimpleStructure extends ExtendedStructure {
    public static final StartHeight ON_SURFACE_START_HEIGHT = StartHeight.at(HeightAnchor.surface(0));

    public static final MapCodec<SimpleStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    settingsCodec(instance),
                    extendedSettingsCodec(instance),
                    PieceSource.CODEC.fieldOf("pieces").forGetter(SimpleStructure::pieces),
                    StartHeight.CODEC.optionalFieldOf("start_height", ON_SURFACE_START_HEIGHT).forGetter(SimpleStructure::startHeight),
                    LiquidSettings.CODEC.optionalFieldOf("liquid_settings", LiquidSettings.IGNORE_WATERLOGGING).forGetter(SimpleStructure::liquidSettings),
                    Foundation.CODEC.optionalFieldOf("foundation").forGetter(SimpleStructure::foundation),
                    Rotation.CODEC.optionalFieldOf("rotation").forGetter(SimpleStructure::rotation))
            .apply(instance, SimpleStructure::new));

    private final PieceSource pieces;
    private final StartHeight startHeight;
    private final LiquidSettings liquidSettings;
    private final Optional<Foundation> foundation;
    private final Optional<Rotation> rotation;

    public SimpleStructure(
            StructureSettings settings,
            ExtendedStructureSettings extendedSettings,
            PieceSource pieces,
            StartHeight startHeight,
            LiquidSettings liquidSettings,
            Optional<Foundation> foundation,
            Optional<Rotation> rotation) {
        super(settings, extendedSettings);
        this.pieces = pieces;
        this.startHeight = startHeight;
        this.liquidSettings = liquidSettings;
        this.foundation = foundation;
        this.rotation = rotation;
    }

    @Override
    protected Either<Candidate, Evaluation> layOut(GenerationContext context, Timer timer) {
        var chunkPos = context.chunkPos();
        var unplaced = new BlockPos(chunkPos.getMinBlockX(), 0, chunkPos.getMinBlockZ());
        var world = new WorldGenerationContext(context.chunkGenerator(), context.heightAccessor());

        // Measuring the terrain is by far the slowest step, so first rule out chunks where no possible start height is in a valid biome.
        // Without terrain, the check at the origin is cheaper.
        if (startHeight.groundHeightmap().isPresent()) {
            long precheckStart = System.nanoTime();
            var bounds = startHeight.bounds(world);
            var mayFit = bounds.isEmpty() || hasValidBiome(context, unplaced.getX(), unplaced.getZ(), bounds.get().minInclusive(), bounds.get().maxInclusive());
            timer.biomePrecheck(System.nanoTime() - precheckStart);
            if (!mayFit) {
                var y = Mth.clamp(context.chunkGenerator().getSeaLevel(), bounds.get().minInclusive(), bounds.get().maxInclusive());
                return Either.right(new Evaluation.WrongBiome(unplaced.atY(y)));
            }
        }

        // Create the pieces first, at Y 0, so the terrain can be measured under their actual footprint.
        long start = System.nanoTime();
        var created = new ArrayList<StructurePiece>();
        pieces.appendPieces(created, new PieceSource.Context(
                context, unplaced, rotation.orElseGet(() -> Rotation.getRandom(context.random())), terrainAdaptation()));
        var unplacedFootprint = BoundingBox.encapsulatingBoxes(created.stream().map(StructurePiece::getBoundingBox).toList());
        timer.pieces(System.nanoTime() - start);
        if (unplacedFootprint.isEmpty()) {
            return Either.right(new Evaluation.NoPieces());
        }

        start = System.nanoTime();
        var terrain = new TerrainSampler(context, startHeight.groundHeightmap().orElse(Heightmap.Types.WORLD_SURFACE_WG));
        var sampled = startHeight.sample(context.random(), world, (heightmap, mode) -> mode.measure(terrain, heightmap, unplaced, unplacedFootprint.get()));
        timer.startHeight(System.nanoTime() - start);
        if (sampled.isEmpty()) {
            return Either.right(new Evaluation.NoStartHeight());
        }

        var origin = unplaced.atY(sampled.getAsInt());
        var footprint = unplacedFootprint.get().moved(0, origin.getY(), 0);
        return Either.left(new Candidate(origin, terrain, () -> footprint, () -> place(created, origin)));
    }

    // Moves the pieces up to the start height and hands them the structure's settings.
    private GenerationStub place(List<StructurePiece> created, BlockPos origin) {
        var builder = new StructurePiecesBuilder();
        for (var piece : created) {
            piece.move(0, origin.getY(), 0);
            if (piece instanceof ExtendedTemplateStructurePiece template) {
                template.setLiquidSettings(liquidSettings);
                template.setFoundation(foundation);
            }
            builder.addPiece(piece);
        }
        return new GenerationStub(origin, Either.right(builder));
    }

    public PieceSource pieces() {
        return pieces;
    }

    public StartHeight startHeight() {
        return startHeight;
    }

    public LiquidSettings liquidSettings() {
        return liquidSettings;
    }

    public Optional<Foundation> foundation() {
        return foundation;
    }

    /// Empty for a random rotation.
    public Optional<Rotation> rotation() {
        return rotation;
    }

    @Override
    public StructureType<?> type() {
        return ASStructureTypes.SIMPLE.get();
    }

    /// For a structure outside a [com.ametrin.structures.structure.StructureBootstrap]. Templates default to `id`'s namespace.
    /// Create it with [Builder#build(BootstrapContext)].
    public static Builder builder(Identifier id) {
        return new Builder(id.getNamespace(), id.getPath());
    }

    public static class Builder extends StructureEntryBuilder<Builder> {
        private final String namespace;

        @Nullable
        private Function<HolderGetter<StructureProcessorList>, PieceSource> pieces;
        private StartHeight startHeight = ON_SURFACE_START_HEIGHT;
        private LiquidSettings liquidSettings = LiquidSettings.IGNORE_WATERLOGGING;
        private Optional<Foundation> foundation = Optional.empty();
        private @Nullable Rotation rotation;

        @ApiStatus.Internal
        public Builder(String namespace, String id) {
            super(id);
            this.namespace = namespace;
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Builder single(String template) {
            return pieces(_ -> new PieceSources.SingleSource(TemplateEntry.of(Identifier.fromNamespaceAndPath(namespace, template))));
        }

        public Builder single(Consumer<TemplateEntry.Builder> configure) {
            return pieces(g -> {
                var template = new TemplateEntry.Builder(namespace, g);
                configure.accept(template);
                return new PieceSources.SingleSource(template.build());
            });
        }

        /// A source drawn by weight.
        public Builder weighted(Consumer<PieceSources.WeightedBuilder> configure) {
            return pieces(g -> {
                var weighted = new PieceSources.WeightedBuilder(namespace, g);
                configure.accept(weighted);
                return weighted.build();
            });
        }

        /// Several pieces placed together.
        public Builder compound(Consumer<PieceSources.CompoundBuilder> configure) {
            return pieces(g -> {
                var builder = new PieceSources.CompoundBuilder(namespace, g);
                configure.accept(builder);
                return builder.build();
            });
        }

        public Builder pieces(Function<HolderGetter<StructureProcessorList>, PieceSource> pieces) {
            this.pieces = pieces;
            return this;
        }

        /// Starts on the world surface, which over water is the water's surface.
        public Builder surface() {
            return surface(0);
        }

        /// Starts `offset` blocks above the world surface.
        public Builder surface(int offset) {
            return verticalPlacement(HeightAnchor.surface(offset));
        }

        public Builder surface(int offset, HeightMode mode) {
            return verticalPlacement(HeightAnchor.surface(offset, mode));
        }

        /// Starts on the ground, under any water.
        public Builder oceanFloor() {
            return oceanFloor(0);
        }

        /// Starts `offset` blocks above the ground, under any water.
        public Builder oceanFloor(int offset) {
            return verticalPlacement(HeightAnchor.oceanFloor(offset));
        }

        public Builder oceanFloor(int offset, HeightMode mode) {
            return verticalPlacement(HeightAnchor.oceanFloor(offset, mode));
        }

        public Builder between(HeightAnchor min, HeightAnchor max) {
            return verticalPlacement(StartHeight.between(min, max));
        }

        public Builder verticalPlacement(HeightAnchor anchor) {
            return verticalPlacement(StartHeight.at(anchor));
        }

        public Builder verticalPlacement(HeightProvider provider) {
            return verticalPlacement(StartHeight.of(provider));
        }

        public Builder verticalPlacement(StartHeight startHeight) {
            this.startHeight = startHeight;
            return this;
        }

        /// Whether waterloggable blocks placed in water get waterlogged. Defaults to [LiquidSettings#IGNORE_WATERLOGGING].
        ///
        /// [com.ametrin.structures.foam.RemoveFoamProcessor#WATER] always waterlogs.
        public Builder liquidSettings(LiquidSettings liquidSettings) {
            this.liquidSettings = liquidSettings;
            return this;
        }

        /// Extends the bottom of every piece down to the ground, repeating each bottom block.
        public Builder foundation() {
            return foundation(Foundation.repeating());
        }

        /// Extends the bottom of every piece down to the ground with `state`, at most `maxDepth` blocks.
        public Builder foundation(BlockState state, int maxDepth) {
            return foundation(Foundation.of(state, maxDepth));
        }

        public Builder foundation(Foundation foundation) {
            this.foundation = Optional.of(foundation);
            return this;
        }

        /// Defaults to a random rotation. Compound structures rotate as one.
        public Builder fixedRotation(Rotation rotation) {
            this.rotation = rotation;
            return this;
        }

        @Override
        protected void validate() {
            super.validate();
            if (pieces == null) {
                throw fail("no piece source was set - call single(), weighted() or compound()");
            }
        }

        @Override
        public SimpleStructure build(BootstrapContext<Structure> context) {
            return (SimpleStructure) super.build(context);
        }

        @Override
        protected SimpleStructure create(StructureSettings settings, ExtendedStructureSettings extendedSettings, BootstrapContext<Structure> context) {
            var processorLists = context.lookup(Registries.PROCESSOR_LIST);
            PieceSource builtPieces;
            try {
                builtPieces = Objects.requireNonNull(pieces).apply(processorLists);
            } catch (IllegalStateException exception) {
                throw fail(exception.getMessage(), exception);
            }
            return new SimpleStructure(settings, extendedSettings, builtPieces, startHeight, liquidSettings, foundation, Optional.ofNullable(rotation));
        }
    }
}
