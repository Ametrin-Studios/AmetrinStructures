package com.ametrin.structures.structure;

import com.ametrin.structures.structure.filter.PlacementFilter;
import com.ametrin.structures.structure.filter.TerrainSampler;
import com.ametrin.structures.structure.jigsaw.JigsawStructureProcessors;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// Base class for the library's structure types. It finds the generation point in steps, so it can say
/// why a structure doesn't fit.
///
/// The subclass lays out the structure, then this class checks the biome and runs the [PlacementFilter]s.
public abstract class ExtendedStructure extends Structure {
    private final ExtendedStructureSettings extendedSettings;
    private volatile @Nullable ContainingSets containingSets;

    protected ExtendedStructure(StructureSettings settings, ExtendedStructureSettings extendedSettings) {
        super(settings);
        this.extendedSettings = extendedSettings;
    }

    protected static <S extends ExtendedStructure> RecordCodecBuilder<S, ExtendedStructureSettings> extendedSettingsCodec(RecordCodecBuilder.Instance<S> instance) {
        return ExtendedStructureSettings.CODEC.forGetter(ExtendedStructure::extendedSettings);
    }

    public ExtendedStructureSettings extendedSettings() {
        return extendedSettings;
    }

    public List<PlacementFilter> filters() {
        return extendedSettings.filters();
    }

    /// For template pieces without processors of their own, see [InlineFromStructureProcessor].
    public Optional<Holder<StructureProcessorList>> processors() {
        return extendedSettings.processors();
    }

    @Override
    public final Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return evaluateGenerationPoint(context) instanceof Evaluation.Generated(
                GenerationStub stub
        ) ? Optional.of(stub) : Optional.empty();
    }

    // The evaluation already checked the biome.
    @Override
    public final Optional<GenerationStub> findValidGenerationPoint(GenerationContext context) {
        return findGenerationPoint(context);
    }

    /// Where the structure would generate in the context's chunk, or why it wouldn't. Includes the biome check.
    public Evaluation evaluateGenerationPoint(GenerationContext context) {
        return evaluateGenerationPoint(context, Timer.NONE);
    }

    /// As [#evaluateGenerationPoint(GenerationContext)], telling `timer` how long each step took.
    public Evaluation evaluateGenerationPoint(GenerationContext context, Timer timer) {
        if (extendedSettings.onlyWhenPickedFirst() && !isPickedFirst(context)) {
            return new Evaluation.NotPickedFirst();
        }
        return layOut(context, timer).map(candidate -> evaluate(context, candidate, timer), rejection -> rejection);
    }

    /// Lays out the structure in the context's chunk without checking the biome, the filters or [StructureBuilder#onlyWhenPickedFirst], e.g. to test how it assembles.
    public Optional<GenerationStub> layOutUnchecked(GenerationContext context) {
        return layOut(context, Timer.NONE).left().map(candidate -> withProcessors(candidate.stub().get()));
    }

    // A structure outside any set counts as picked.
    private boolean isPickedFirst(GenerationContext context) {
        var sets = containingSets(context.registryAccess());
        return sets.isEmpty() || sets.stream().anyMatch(set -> StructureSetOrder.of(set, context.seed(), context.chunkPos()).getFirst().structure().value() == this);
    }

    private List<StructureSet> containingSets(RegistryAccess registries) {
        var cached = containingSets;
        if (cached == null || cached.registries() != registries) {
            var sets = registries.lookupOrThrow(Registries.STRUCTURE_SET).stream()
                    .filter(set -> set.structures().stream().anyMatch(entry -> entry.structure().value() == this))
                    .toList();
            cached = new ContainingSets(registries, sets);
            containingSets = cached;
        }
        return cached.sets();
    }

    private record ContainingSets(RegistryAccess registries, List<StructureSet> sets) {}

    private Evaluation evaluate(GenerationContext context, Candidate candidate, Timer timer) {
        var origin = candidate.origin();
        if (!isValidBiome(context, origin)) {
            return new Evaluation.WrongBiome(origin);
        }
        var filters = filters();
        if (!filters.isEmpty()) {
            var filterContext = new PlacementFilter.Context(context, origin, candidate.footprint().get(), candidate.terrain());
            for (var filter : filters) {
                long start = System.nanoTime();
                var passed = filter.test(filterContext);
                timer.filter(filter, System.nanoTime() - start);
                if (!passed) {
                    return new Evaluation.Filtered(filter, origin);
                }
            }
        }
        return new Evaluation.Generated(withProcessors(candidate.stub().get()));
    }

    // Hands the structure's processors to its template pieces, without assembling a lazy stub early.
    private GenerationStub withProcessors(GenerationStub stub) {
        if (processors().isEmpty()) {
            return stub;
        }
        return new GenerationStub(stub.position(), stub.generator().<Consumer<StructurePiecesBuilder>, StructurePiecesBuilder>mapBoth(
                generator -> builder -> {
                    generator.accept(builder);
                    applyProcessors(builder);
                },
                builder -> {
                    applyProcessors(builder);
                    return builder;
                }));
    }

    private void applyProcessors(StructurePiecesBuilder builder) {
        for (var piece : builder.build().pieces()) {
            if (piece instanceof ExtendedTemplateStructurePiece template) {
                template.setStructureProcessors(processors());
            } else if (piece instanceof PoolElementStructurePiece jigsaw) {
                JigsawStructureProcessors.set(jigsaw, processors());
            }
        }
    }

    /// Lays out the structure and finds its start position, or returns [Evaluation.NoPieces] or [Evaluation.NoStartHeight].
    protected abstract Either<Candidate, Evaluation> layOut(GenerationContext context, Timer timer);

    /// @param footprint the box around all pieces at `origin`, only requested when there are filters
    /// @param stub      the structure to generate, only requested once all checks passed
    public record Candidate(BlockPos origin, TerrainSampler terrain, Supplier<BoundingBox> footprint,
                            Supplier<GenerationStub> stub) {}

    public sealed interface Evaluation {
        record Generated(GenerationStub stub) implements Evaluation {}

        record NoPieces() implements Evaluation {}

        record NoStartHeight() implements Evaluation {}

        record WrongBiome(BlockPos origin) implements Evaluation {}

        record Filtered(PlacementFilter filter, BlockPos origin) implements Evaluation {}

        /// See [StructureBuilder#onlyWhenPickedFirst].
        record NotPickedFirst() implements Evaluation {}
    }

    /// Receives how long each step of [#evaluateGenerationPoint(GenerationContext, Timer)] took, in nanoseconds.
    public interface Timer {
        Timer NONE = new Timer() {};

        /// Checking the biome before laying out, see [#hasValidBiome].
        default void biomePrecheck(long nanos) {}

        /// Creating the pieces, which loads their templates the first time.
        default void pieces(long nanos) {}

        default void startHeight(long nanos) {}

        default void filter(PlacementFilter filter, long nanos) {}
    }

    public static boolean isValidBiome(GenerationContext context, BlockPos start) {
        return isValidBiome(context, QuartPos.fromBlock(start.getX()), QuartPos.fromBlock(start.getY()), QuartPos.fromBlock(start.getZ()));
    }

    /// Whether [#isValidBiome(GenerationContext, BlockPos)] passes for any Y from `minY` to `maxY` at `x`, `z`.
    /// Rejects a chunk before an expensive layout when the start's X and Z are known first.
    public static boolean hasValidBiome(GenerationContext context, int x, int z, int minY, int maxY) {
        int quartX = QuartPos.fromBlock(x);
        int quartZ = QuartPos.fromBlock(z);
        int bottom = QuartPos.fromBlock(minY);
        int top = QuartPos.fromBlock(maxY);
        // Outward from sea level, where most structures start, so a valid biome is usually found with the first samples.
        int start = Mth.clamp(QuartPos.fromBlock(context.chunkGenerator().getSeaLevel()), bottom, top);
        for (int distance = 0; start - distance >= bottom || start + distance <= top; distance++) {
            if (start + distance <= top && isValidBiome(context, quartX, start + distance, quartZ)
                    || distance > 0 && start - distance >= bottom && isValidBiome(context, quartX, start - distance, quartZ)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidBiome(GenerationContext context, int quartX, int quartY, int quartZ) {
        // based on Structure#isValidBiome.
        return context.validBiome().test(context.biomeSource().getNoiseBiome(quartX, quartY, quartZ, context.randomState().sampler()));
    }
}
