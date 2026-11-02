package com.ametrin.structures.structure.simple;

import com.ametrin.structures.registry.ASPieceSources;
import com.ametrin.structures.structure.jigsaw.JigsawSource;
import com.ametrin.structures.structure.jigsaw.StructureElements;
import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class PieceSources {
    private PieceSources() {
    }

    /// One template or pool element. Anywhere a source is expected it can also be written as the bare template entry.
    public record SingleSource(TemplateEntry template) implements PieceSource {
        public static final MapCodec<SingleSource> CODEC = TemplateEntry.MAP_CODEC.xmap(SingleSource::new, SingleSource::template);

        @Override
        public void appendPieces(StructurePiecesBuilder builder, Context context) {
            builder.addPiece(createPiece(template, context));
        }

        @Override
        public Stream<TemplateEntry> templates() {
            return Stream.of(template);
        }

        @Override
        public PieceSourceType type() {
            return ASPieceSources.SINGLE.get();
        }
    }

    /// One of the sources, drawn by weight.
    public record WeightedSource(WeightedList<PieceSource> sources) implements PieceSource {
        public static final MapCodec<WeightedSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        WeightedList.nonEmptyCodec(PieceSource.CODEC).fieldOf("sources").forGetter(WeightedSource::sources))
                .apply(instance, WeightedSource::new));

        @Override
        public void appendPieces(StructurePiecesBuilder builder, Context context) {
            sources.getRandom(context.generation().random()).ifPresent(source -> source.appendPieces(builder, context));
        }

        @Override
        public Stream<TemplateEntry> templates() {
            return sources.unwrap().stream().flatMap(source -> source.value().templates());
        }

        @Override
        public PieceSourceType type() {
            return ASPieceSources.WEIGHTED.get();
        }
    }

    /// Every source, for a structure made of several pieces.
    public record CompoundSource(List<PieceSource> sources) implements PieceSource {
        public static final MapCodec<CompoundSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ExtraCodecs.nonEmptyList(PieceSource.CODEC.listOf()).fieldOf("sources").forGetter(CompoundSource::sources))
                .apply(instance, CompoundSource::new));

        public CompoundSource {
            sources = List.copyOf(sources);
        }

        @Override
        public void appendPieces(StructurePiecesBuilder builder, Context context) {
            for (var source : sources) {
                source.appendPieces(builder, context);
            }
        }

        @Override
        public Stream<TemplateEntry> templates() {
            return sources.stream().flatMap(PieceSource::templates);
        }

        @Override
        public PieceSourceType type() {
            return ASPieceSources.COMPOUND.get();
        }
    }

    /// A piece for `entry` with the structure's settings, see [StructureElements].
    public static StructurePiece createPiece(TemplateEntry entry, PieceSource.Context context) {
        var element = StructureElements.withStructureSettings(entry.element(), context.processors(), context.foundation());
        return new SimpleStructurePiece(context.generation().structureTemplateManager(), element, context.origin().offset(entry.offset().rotate(context.rotation())), context.rotation(),
                context.liquidSettings(), context.terrainAdaptation(), entry.terrainBox(), -entry.offset().getY());
    }

    public abstract static class NestingBuilder {
        protected final String defaultNamespace;
        protected final HolderGetter<StructureProcessorList> processorsGetter;
        protected final HolderGetter<StructureTemplatePool> poolsGetter;

        protected NestingBuilder(String defaultNamespace, HolderGetter<StructureProcessorList> processorsGetter, HolderGetter<StructureTemplatePool> poolsGetter) {
            this.defaultNamespace = defaultNamespace;
            this.processorsGetter = processorsGetter;
            this.poolsGetter = poolsGetter;
        }

        protected SingleSource makeSingle(Consumer<TemplateEntry.Builder> configure) {
            var entry = new TemplateEntry.Builder(defaultNamespace, processorsGetter);
            configure.accept(entry);
            return new SingleSource(entry.build());
        }

        protected WeightedSource makeWeighted(Consumer<WeightedBuilder> configure) {
            var weighted = new WeightedBuilder(defaultNamespace, processorsGetter, poolsGetter);
            configure.accept(weighted);
            return weighted.build();
        }

        protected CompoundSource makeCompound(Consumer<CompoundBuilder> configure) {
            var compound = new CompoundBuilder(defaultNamespace, processorsGetter, poolsGetter);
            configure.accept(compound);
            return compound.build();
        }

        protected JigsawSource makeJigsaw(ResourceKey<StructureTemplatePool> startPool, Consumer<JigsawSource.Builder> configure) {
            var jigsaw = new JigsawSource.Builder(poolsGetter.getOrThrow(startPool));
            configure.accept(jigsaw);
            return jigsaw.build();
        }

    }

    public static class WeightedBuilder extends NestingBuilder {
        private final WeightedList.Builder<PieceSource> builder = WeightedList.builder();

        public WeightedBuilder(String defaultNamespace, HolderGetter<StructureProcessorList> processorsGetter, HolderGetter<StructureTemplatePool> poolsGetter) {
            super(defaultNamespace, processorsGetter, poolsGetter);
        }

        public WeightedBuilder add(int weight, PieceSource source) {
            builder.add(source, weight);
            return this;
        }

        public WeightedBuilder single(int weight, Consumer<TemplateEntry.Builder> configure) {
            return add(weight, makeSingle(configure));
        }

        public WeightedBuilder single(int weight, String template) {
            return single(weight, b -> b.template(template));
        }

        public WeightedBuilder weighted(int weight, Consumer<WeightedBuilder> configure) {
            return add(weight, makeWeighted(configure));
        }

        public WeightedBuilder compound(int weight, Consumer<CompoundBuilder> configure) {
            return add(weight, makeCompound(configure));
        }

        /// A jigsaw layout grown from `startPool`, see [JigsawSource].
        public WeightedBuilder jigsaw(int weight, ResourceKey<StructureTemplatePool> startPool, Consumer<JigsawSource.Builder> configure) {
            return add(weight, makeJigsaw(startPool, configure));
        }

        public WeightedSource build() {
            var sources = builder.build();
            if (sources.isEmpty()) {
                throw new IllegalStateException("weighted piece source is empty");
            }
            return new WeightedSource(sources);
        }
    }

    public static class CompoundBuilder extends NestingBuilder {
        private final ImmutableList.Builder<PieceSource> builder = new ImmutableList.Builder<>();

        public CompoundBuilder(String defaultNamespace, HolderGetter<StructureProcessorList> processorsGetter, HolderGetter<StructureTemplatePool> poolsGetter) {
            super(defaultNamespace, processorsGetter, poolsGetter);
        }

        public CompoundBuilder add(PieceSource source) {
            builder.add(source);
            return this;
        }

        public CompoundBuilder single(Consumer<TemplateEntry.Builder> configure) {
            return add(makeSingle(configure));
        }

        public CompoundBuilder single(String template) {
            return single(b -> b.template(template));
        }

        public CompoundBuilder weighted(Consumer<WeightedBuilder> configure) {
            return add(makeWeighted(configure));
        }

        public CompoundBuilder compound(Consumer<CompoundBuilder> configure) {
            return add(makeCompound(configure));
        }

        /// A jigsaw layout grown from `startPool`, see [JigsawSource]. It grows around the pieces of the other sources.
        public CompoundBuilder jigsaw(ResourceKey<StructureTemplatePool> startPool, Consumer<JigsawSource.Builder> configure) {
            return add(makeJigsaw(startPool, configure));
        }

        public CompoundSource build() {
            var sources = builder.build();
            if (sources.isEmpty()) {
                throw new IllegalStateException("compound piece source is empty");
            }
            return new CompoundSource(sources);
        }
    }
}
