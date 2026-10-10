package com.ametrin.structures.structure.simple;

import com.ametrin.structures.registry.ASStructureTypes;
import com.ametrin.structures.structure.*;
import com.ametrin.structures.structure.filter.PlacementFilter;
import com.ametrin.structures.structure.filter.TerrainSampler;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/// A structure of one or more templates at a start height measured from the terrain. A built-in type, so it needs no structure type, piece type or codec of its own:
///
/// ```
/// REGISTER.set("tower")
///     .evenSpreadPlacement(18, 0.6F)
///     .simple(tower -> tower.single("tower").surface().biomes(BiomeTags.IS_FOREST))
///     .build();
/// ```
public class SimpleStructure extends ExtendedStructure {
    public static final StartHeight ON_SURFACE_START_HEIGHT = StartHeight.at(HeightAnchor.surface(0));

    public static final MapCodec<SimpleStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    settingsCodec(instance),
                    extendedSettingsCodec(instance),
                    PieceSource.CODEC.fieldOf("pieces").forGetter(s -> s.pieces),
                    StructureProcessorType.LIST_CODEC.optionalFieldOf("processors").forGetter(s -> s.processors),
                    StartHeight.CODEC.optionalFieldOf("start_height", ON_SURFACE_START_HEIGHT).forGetter(s -> s.startHeight),
                    HeightMode.CODEC.optionalFieldOf("height_mode", HeightMode.CORNER).forGetter(s -> s.heightMode),
                    LiquidSettings.CODEC.optionalFieldOf("liquid_settings", LiquidSettings.IGNORE_WATERLOGGING).forGetter(s -> s.liquidSettings),
                    Foundation.CODEC.optionalFieldOf("foundation").forGetter(s -> s.foundation),
                    Rotation.CODEC.optionalFieldOf("rotation").forGetter(s -> s.rotation))
            .apply(instance, SimpleStructure::new));

    private final PieceSource pieces;
    private final Optional<Holder<StructureProcessorList>> processors;
    private final StartHeight startHeight;
    private final HeightMode heightMode;
    private final LiquidSettings liquidSettings;
    private final Optional<Foundation> foundation;
    private final Optional<Rotation> rotation;

    public SimpleStructure(
            StructureSettings settings,
            ExtendedStructureSettings extendedSettings,
            PieceSource pieces,
            Optional<Holder<StructureProcessorList>> processors,
            StartHeight startHeight,
            HeightMode heightMode,
            LiquidSettings liquidSettings,
            Optional<Foundation> foundation,
            Optional<Rotation> rotation) {
        super(settings, extendedSettings);
        this.pieces = pieces;
        this.processors = processors;
        this.startHeight = startHeight;
        this.heightMode = heightMode;
        this.liquidSettings = liquidSettings;
        this.foundation = foundation;
        this.rotation = rotation;
    }

    @Override
    protected Either<Candidate, Evaluation> layOut(GenerationContext context, Timer timer) {
        // The pieces come first, at Y 0, so the terrain can be measured under their real footprint.
        long start = System.nanoTime();
        var chunkPos = context.chunkPos();
        var unplaced = new BlockPos(chunkPos.getMinBlockX(), 0, chunkPos.getMinBlockZ());
        var created = new ArrayList<StructurePiece>();
        pieces.appendPieces(created, new PieceSource.Context(context, unplaced, rotation.orElseGet(() -> Rotation.getRandom(context.random())), processors));
        var unplacedFootprint = BoundingBox.encapsulatingBoxes(created.stream().map(StructurePiece::getBoundingBox).toList());
        timer.pieces(System.nanoTime() - start);
        if (unplacedFootprint.isEmpty()) {
            return Either.right(new Evaluation.NoPieces());
        }

        start = System.nanoTime();
        var terrain = new TerrainSampler(context, startHeight.groundHeightmap().orElse(Heightmap.Types.WORLD_SURFACE_WG));
        var sampled = startHeight.sample(context.random(), new WorldGenerationContext(context.chunkGenerator(), context.heightAccessor()),
                heightmap -> terrainHeight(terrain, heightmap, unplaced, unplacedFootprint.get()));
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
            if (piece instanceof SimpleStructurePiece simple) {
                simple.setTerrainAdaptation(terrainAdaptation());
            }
            builder.addPiece(piece);
        }
        return new GenerationStub(origin, Either.right(builder));
    }

    private int terrainHeight(TerrainSampler terrain, Heightmap.Types type, BlockPos origin, BoundingBox footprint) {
        if (heightMode == HeightMode.CORNER) {
            return terrain.surfaceHeight(type, origin.getX(), origin.getZ());
        }
        int[] corners = {
                terrain.surfaceHeight(type, footprint.minX(), footprint.minZ()),
                terrain.surfaceHeight(type, footprint.minX(), footprint.maxZ()),
                terrain.surfaceHeight(type, footprint.maxX(), footprint.minZ()),
                terrain.surfaceHeight(type, footprint.maxX(), footprint.maxZ())
        };
        return heightMode == HeightMode.LOWEST
                ? Arrays.stream(corners).min().orElseThrow()
                : Arrays.stream(corners).sum() / corners.length;
    }

    public PieceSource pieces() {
        return pieces;
    }

    @Override
    public StructureType<?> type() {
        return ASStructureTypes.SIMPLE.get();
    }

    public static class Builder extends StructureEntryBuilder<Builder> implements ExtendedStructureBuilder<Builder> {
        private final String namespace;

        @Nullable
        private Function<HolderGetter<StructureProcessorList>, PieceSource> pieces;
        private Function<HolderGetter<StructureProcessorList>, Optional<Holder<StructureProcessorList>>> processors =
                _ -> Optional.empty();
        private StartHeight startHeight = ON_SURFACE_START_HEIGHT;
        private HeightMode heightMode = HeightMode.CORNER;
        private LiquidSettings liquidSettings = LiquidSettings.IGNORE_WATERLOGGING;
        private Optional<Foundation> foundation = Optional.empty();
        private final List<PlacementFilter> filters = new ArrayList<>();
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

        /// Processors for templates without their own.
        public Builder processors(ResourceKey<StructureProcessorList> processors) {
            this.processors = lookup -> Optional.of(lookup.getOrThrow(processors));
            return this;
        }

        /// Processors for templates without their own.
        public Builder processors(Holder<StructureProcessorList> processors) {
            this.processors = _ -> Optional.of(processors);
            return this;
        }

        /// Processors for templates without their own, stored inline.
        public Builder processors(List<StructureProcessor> processors) {
            return processors(Holder.direct(new StructureProcessorList(processors)));
        }

        /// Starts on the world surface, which over water is the water's surface.
        public Builder surface() {
            return surface(0);
        }

        /// Starts `offset` blocks above the world surface.
        public Builder surface(int offset) {
            return verticalPlacement(HeightAnchor.surface(offset));
        }

        /// Starts on the ground, under any water.
        public Builder oceanFloor() {
            return oceanFloor(0);
        }

        /// Starts `offset` blocks above the ground, under any water.
        public Builder oceanFloor(int offset) {
            return verticalPlacement(HeightAnchor.oceanFloor(offset));
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

        /// How heightmap anchors measure the terrain under the structure. Defaults to [HeightMode#CORNER].
        public Builder verticalPlacementMode(HeightMode heightMode) {
            this.heightMode = heightMode;
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

        @Override
        public Builder filter(PlacementFilter filter) {
            filters.add(filter);
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
        protected Structure create(StructureSettings settings, BootstrapContext<Structure> context) {
            var processorLists = context.lookup(Registries.PROCESSOR_LIST);
            PieceSource builtPieces;
            try {
                builtPieces = Objects.requireNonNull(pieces).apply(processorLists);
            } catch (IllegalStateException exception) {
                throw fail(exception.getMessage(), exception);
            }
            return new SimpleStructure(settings, new ExtendedStructureSettings(filters), builtPieces, processors.apply(processorLists),
                    startHeight, heightMode, liquidSettings, foundation, Optional.ofNullable(rotation));
        }
    }
}
