package com.ametrin.structures.structure;

import com.ametrin.structures.structure.filter.PlacementFilter;
import com.ametrin.structures.structure.filter.TerrainSampler;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/// The base of the library's structure types. It holds the [ExtendedStructureSettings] and finds the
/// generation point in steps that say why a structure doesn't fit.
///
/// The subclass lays the structure out, then this class checks the biome and runs the [PlacementFilter]s.
public abstract class ExtendedStructure extends Structure {
    private final ExtendedStructureSettings extendedSettings;

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
        return layOut(context, timer).map(candidate -> evaluate(context, candidate, timer), rejection -> rejection);
    }

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
        return new Evaluation.Generated(candidate.stub().get());
    }

    /// Lays the structure out up to its start position, or says why it can't, with [Evaluation.NoPieces] or [Evaluation.NoStartHeight].
    protected abstract Either<Candidate, Evaluation> layOut(GenerationContext context, Timer timer);

    /// @param footprint the box around every piece at `origin`, only asked for when there are filters
    /// @param stub      the structure as it generates, asked for once it passed
    public record Candidate(BlockPos origin, TerrainSampler terrain, Supplier<BoundingBox> footprint,
                            Supplier<GenerationStub> stub) {}

    public sealed interface Evaluation {
        record Generated(GenerationStub stub) implements Evaluation {}

        record NoPieces() implements Evaluation {}

        record NoStartHeight() implements Evaluation {}

        record WrongBiome(BlockPos origin) implements Evaluation {}

        record Filtered(PlacementFilter filter, BlockPos origin) implements Evaluation {}
    }

    /// Told how long each step of [#evaluateGenerationPoint(GenerationContext, Timer)] took, in nanoseconds.
    public interface Timer {
        Timer NONE = new Timer() {};

        /// Creating the pieces, which loads their templates the first time.
        default void pieces(long nanos) {}

        default void startHeight(long nanos) {}

        default void filter(PlacementFilter filter, long nanos) {}
    }

    public static boolean isValidBiome(GenerationContext context, BlockPos start) {
        // based on Structure#isValidBiome.
        return context.validBiome().test(context.biomeSource().getNoiseBiome(
                QuartPos.fromBlock(start.getX()), QuartPos.fromBlock(start.getY()), QuartPos.fromBlock(start.getZ()),
                context.randomState().sampler()));
    }
}
