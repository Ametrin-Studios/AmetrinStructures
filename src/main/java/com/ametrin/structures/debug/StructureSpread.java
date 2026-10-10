package com.ametrin.structures.debug;

import com.ametrin.structures.registry.ASRegistries;
import com.ametrin.structures.structure.ExtendedStructure;
import com.ametrin.structures.structure.filter.PlacementFilter;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

/// Works out where structures would generate around a chunk, and why the other candidate chunks fail,
/// without generating anything. It follows `ChunkGenerator#createStructures`: each set's placement picks
/// the chunks, a set with several structures tries them by weight until one fits, and each structure
/// checks the biome at its start position. The library's structures report which step failed. For
/// other structures every failure is reported as no generation point.
///
/// Only evaluates terrain noise, so it is safe to run off the server thread.
final class StructureSpread {
    private StructureSpread() {}

    /// Why a candidate chunk did not generate the structure. `argument` names a filter or structure, or is empty.
    public record Reason(String key, String argument) {
        static final Reason BIOME = new Reason("biome", "");
        static final Reason NO_PIECES = new Reason("no_pieces", "");
        static final Reason NO_START_HEIGHT = new Reason("no_start_height", "");
        static final Reason NO_GENERATION_POINT = new Reason("no_generation_point", "");

        static final String FILTER = "filter";

        static Reason filtered(String filter) {
            return new Reason(FILTER, filter);
        }

        static Reason takenBy(String structure) {
            return new Reason("taken", structure);
        }
    }

    public record Found(Holder<Structure> structure, BlockPos origin, BoundingBox box, BlockPos visit) {
        public Identifier id() {
            return structure.unwrapKey().orElseThrow().identifier();
        }
    }

    public record RejectedSpot(BlockPos position, Reason reason, boolean inBiome) {}

    public record Timing(Holder<Structure> structure, long nanos, int attempts, Map<Step, Long> steps) {
        public Identifier id() {
            return structure.unwrapKey().orElseThrow().identifier();
        }
    }

    /// @param argument names a filter or is empty
    public record Step(String key, String argument) {
        static final Step PIECES = new Step("pieces", "");
        static final Step START_HEIGHT = new Step("start_height", "");

        static Step filter(PlacementFilter filter) {
            return new Step("filter", filterName(filter));
        }
    }

    /// @param candidateCount chunks the sets' placement picked
    /// @param rejectedSpots  candidates that failed, nearest first; only when asked for
    /// @param spacing        each spot's horizontal distance to the closest other one (in blocks); empty with fewer than 2 or more than [StructureSpread#MAX_SPACING_SPOTS] spots
    public record Report(int structureSetCount, int candidateCount, Map<Reason, Integer> rejections, List<Found> found,
                         List<RejectedSpot> rejectedSpots, Optional<DoubleSummaryStatistics> spacing, List<Timing> timings) {
        public IntSummaryStatistics startHeights() {
            return found.stream().mapToInt(spot -> spot.origin().getY()).summaryStatistics();
        }
    }

    /// @param spots   the picked chunks' centers on the surface, nearest first
    /// @param spacing as in [Report]
    public record Candidates(List<BlockPos> spots, Optional<DoubleSummaryStatistics> spacing) {}

    /// Spacing compares every pair, so it is skipped for very dense structures.
    public static final int MAX_SPACING_SPOTS = 4096;

    private static Optional<DoubleSummaryStatistics> spacing(List<BlockPos> spots) {
        if (spots.size() < 2 || spots.size() > MAX_SPACING_SPOTS) {
            return Optional.empty();
        }
        return Optional.of(spots.stream()
                .mapToDouble(spot -> spots.stream()
                        .filter(other -> other != spot)
                        .mapToDouble(other -> horizontalDistance(spot, other))
                        .min()
                        .orElseThrow())
                .summaryStatistics());
    }

    private static final class Stopwatch implements ExtendedStructure.Timer {
        private long nanos;
        private int attempts;
        private final Map<Step, Long> steps = new LinkedHashMap<>();

        void attempted(long nanos) {
            this.nanos += nanos;
            attempts++;
        }

        @Override
        public void pieces(long nanos) {
            steps.merge(Step.PIECES, nanos, Long::sum);
        }

        @Override
        public void startHeight(long nanos) {
            steps.merge(Step.START_HEIGHT, nanos, Long::sum);
        }

        @Override
        public void filter(PlacementFilter filter, long nanos) {
            steps.merge(Step.filter(filter), nanos, Long::sum);
        }

        Timing timing(Holder<Structure> structure) {
            return new Timing(structure, nanos, attempts, Collections.unmodifiableMap(new LinkedHashMap<>(steps)));
        }
    }

    private sealed interface Outcome {
        record Generated(Found found) implements Outcome {}

        record Rejected(Reason reason, Optional<BlockPos> position) implements Outcome {
            Rejected(Reason reason) {
                this(reason, Optional.empty());
            }
        }
    }

    /// Checks every chunk within `radius` chunks of `center`, for the dimension's structure sets that
    /// `sets` accepts. Structures that `targets` accepts are reported, the others in those sets can only
    /// take their spots. With `locateRejected`, the report also lists where each rejected candidate would
    /// have been. `placement` replaces the sets' placements if given.
    public static Report analyze(
            ServerLevel level, Predicate<Holder<StructureSet>> sets, Predicate<Holder<Structure>> targets,
            ChunkPos center, int radius, boolean locateRejected, @Nullable StructurePlacement placement) {
        var state = level.getChunkSource().getGeneratorState();
        var checked = state.possibleStructureSets().stream().filter(sets).map(Holder::value).toList();

        int candidates = 0;
        var rejections = new HashMap<Reason, Integer>();
        var found = new ArrayList<Found>();
        var rejectedSpots = new ArrayList<RejectedSpot>();
        var stopwatches = new HashMap<Holder<Structure>, Stopwatch>();
        for (int x = center.x() - radius; x <= center.x() + radius; x++) {
            for (int z = center.z() - radius; z <= center.z() + radius; z++) {
                for (var set : checked) {
                    if (!(placement != null ? placement : set.placement()).isStructureChunk(state, x, z)) {
                        continue;
                    }
                    candidates++;
                    var chunk = new ChunkPos(x, z);
                    switch (tryChunk(level, state, set, targets, chunk, stopwatches)) {
                        case Outcome.Generated generated -> found.add(generated.found());
                        case Outcome.Rejected rejected -> {
                            rejections.merge(rejected.reason(), 1, Integer::sum);
                            if (locateRejected) {
                                var position = rejected.position().orElseGet(() -> surface(level, chunk.getMiddleBlockX(), chunk.getMiddleBlockZ()));
                                rejectedSpots.add(new RejectedSpot(position, rejected.reason(), isInBiome(level, state, set, targets, chunk, rejected.reason(), position)));
                            }
                        }
                    }
                }
            }
        }

        var centerBlock = new BlockPos(center.getMiddleBlockX(), 0, center.getMiddleBlockZ());
        found.sort(Comparator.comparingDouble(spot -> horizontalDistance(spot.origin(), centerBlock)));
        rejectedSpots.sort(Comparator.comparingDouble(spot -> horizontalDistance(spot.position(), centerBlock)));
        var timings = stopwatches.entrySet().stream()
                .filter(entry -> targets.test(entry.getKey()))
                .map(entry -> entry.getValue().timing(entry.getKey()))
                .sorted(Comparator.comparingLong(Timing::nanos).reversed())
                .toList();
        return new Report(checked.size(), candidates, rejections, List.copyOf(found), List.copyOf(rejectedSpots),
                spacing(found.stream().map(Found::origin).toList()), timings);
    }

    /// The chunks `placement` picks within `radius` chunks of `center`, whatever would generate there.
    public static Candidates candidates(ServerLevel level, StructurePlacement placement, ChunkPos center, int radius) {
        var state = level.getChunkSource().getGeneratorState();
        var spots = new ArrayList<BlockPos>();
        for (int x = center.x() - radius; x <= center.x() + radius; x++) {
            for (int z = center.z() - radius; z <= center.z() + radius; z++) {
                if (placement.isStructureChunk(state, x, z)) {
                    var chunk = new ChunkPos(x, z);
                    spots.add(surface(level, chunk.getMiddleBlockX(), chunk.getMiddleBlockZ()));
                }
            }
        }
        var centerBlock = new BlockPos(center.getMiddleBlockX(), 0, center.getMiddleBlockZ());
        spots.sort(Comparator.comparingDouble(spot -> horizontalDistance(spot, centerBlock)));
        return new Candidates(List.copyOf(spots), spacing(spots));
    }

    /// What the structure set generates in one chunk, decided like vanilla does: the structures are tried
    /// in a weighted order seeded by the chunk, and the first one that fits wins. If several targets fail,
    /// the reason comes from the first one that passed its biome check, since that's the most useful.
    private static Outcome tryChunk(
            ServerLevel level, ChunkGeneratorStructureState state, StructureSet set, Predicate<Holder<Structure>> targets, ChunkPos chunk,
            Map<Holder<Structure>, Stopwatch> stopwatches) {
        var options = new ArrayList<>(set.structures());
        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(state.getLevelSeed(), chunk.x(), chunk.z());
        int total = options.stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum();

        Outcome.@Nullable Rejected rejected = null;
        while (!options.isEmpty()) {
            int choice = random.nextInt(total);
            int index = 0;
            for (var option : options) {
                choice -= option.weight();
                if (choice < 0) {
                    break;
                }
                index++;
            }
            var selected = options.get(index);
            var outcome = attempt(level, state, selected.structure(), chunk, stopwatches.computeIfAbsent(selected.structure(), _ -> new Stopwatch()));
            if (targets.test(selected.structure())) {
                switch (outcome) {
                    case Outcome.Generated generated -> {
                        return generated;
                    }
                    case Outcome.Rejected failed -> {
                        if (rejected == null || rejected.reason().equals(Reason.BIOME)) {
                            rejected = failed;
                        }
                    }
                }
            } else if (outcome instanceof Outcome.Generated) {
                return rejected != null ? rejected : new Outcome.Rejected(Reason.takenBy(name(selected.structure())));
            }
            options.remove(index);
            total -= selected.weight();
        }
        return Objects.requireNonNull(rejected, "the set holds a target");
    }

    private static Outcome attempt(ServerLevel level, ChunkGeneratorStructureState state, Holder<Structure> holder, ChunkPos chunk, Stopwatch stopwatch) {
        var context = context(level, state, holder.value(), chunk);
        long start = System.nanoTime();
        var evaluated = evaluate(holder.value(), context, stopwatch);
        // Jigsaw structures only assemble their pieces when asked, so include that in the time.
        var pieces = evaluated.left().map(Structure.GenerationStub::getPiecesBuilder);
        stopwatch.attempted(System.nanoTime() - start);
        return evaluated.<Outcome>map(stub -> found(holder, context, stub.position(), pieces.orElseThrow()), rejected -> rejected);
    }

    /// Where the structure generates, or why it doesn't.
    private static Either<Structure.GenerationStub, Outcome.Rejected> evaluate(Structure structure, Structure.GenerationContext context, ExtendedStructure.Timer timer) {
        if (!(structure instanceof ExtendedStructure extended)) {
            // Only the library's structures can say why they failed. For others this includes the biome check.
            return structure.findValidGenerationPoint(context)
                    .<Either<Structure.GenerationStub, Outcome.Rejected>>map(Either::left)
                    .orElseGet(() -> Either.right(new Outcome.Rejected(Reason.NO_GENERATION_POINT)));
        }
        return switch (extended.evaluateGenerationPoint(context, timer)) {
            case ExtendedStructure.Evaluation.Generated generated -> Either.left(generated.stub());
            case ExtendedStructure.Evaluation.WrongBiome wrongBiome ->
                    Either.right(new Outcome.Rejected(Reason.BIOME, Optional.of(wrongBiome.origin())));
            case ExtendedStructure.Evaluation.NoPieces ignored -> Either.right(new Outcome.Rejected(Reason.NO_PIECES));
            case ExtendedStructure.Evaluation.NoStartHeight ignored ->
                    Either.right(new Outcome.Rejected(Reason.NO_START_HEIGHT));
            case ExtendedStructure.Evaluation.Filtered filtered -> Either.right(new Outcome.Rejected(
                    Reason.filtered(filterName(filtered.filter())), Optional.of(filtered.origin())));
        };
    }

    private static Outcome found(Holder<Structure> structure, Structure.GenerationContext context, BlockPos origin, StructurePiecesBuilder pieces) {
        if (pieces.isEmpty()) {
            return new Outcome.Rejected(Reason.NO_PIECES);
        }
        var box = pieces.getBoundingBox();
        var center = box.getCenter();
        int surface = context.chunkGenerator().getFirstFreeHeight(
                center.getX(), center.getZ(), Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        var visit = new BlockPos(center.getX(), Math.max(box.maxY() + 1, surface), center.getZ());
        return new Outcome.Generated(new Found(structure, origin, box, visit));
    }

    private static Structure.GenerationContext context(ServerLevel level, ChunkGeneratorStructureState state, Structure structure, ChunkPos chunk) {
        var generator = level.getChunkSource().getGenerator();
        return new Structure.GenerationContext(
                level.registryAccess(),
                generator,
                generator.getBiomeSource(),
                level.getChunkSource().randomState(),
                level.getServer().getStructureManager(),
                state.getLevelSeed(),
                chunk,
                level,
                structure.biomes()::contains);
    }

    private static boolean isInBiome(
            ServerLevel level, ChunkGeneratorStructureState state, StructureSet set, Predicate<Holder<Structure>> targets,
            ChunkPos chunk, Reason reason, BlockPos position) {
        // The library's structures check the biome before their filters.
        if (reason.key().equals(Reason.FILTER)) {
            return true;
        }
        return set.structures().stream()
                .map(StructureSet.StructureSelectionEntry::structure)
                .filter(targets)
                .anyMatch(structure -> ExtendedStructure.isValidBiome(context(level, state, structure.value(), chunk), position));
    }

    private static BlockPos surface(ServerLevel level, int x, int z) {
        var chunkSource = level.getChunkSource();
        return new BlockPos(x, chunkSource.getGenerator().getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, chunkSource.randomState()), z);
    }

    private static String filterName(PlacementFilter filter) {
        return String.valueOf(ASRegistries.PLACEMENT_FILTER_TYPES.getKey(filter.type()));
    }

    private static String name(Holder<Structure> structure) {
        return structure.unwrapKey().map(key -> key.identifier().toString()).orElse("?");
    }

    static double horizontalDistance(BlockPos a, BlockPos b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }
}
