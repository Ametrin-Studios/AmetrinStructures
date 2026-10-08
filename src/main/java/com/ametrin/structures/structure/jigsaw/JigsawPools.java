package com.ametrin.structures.structure.jigsaw;

import com.ametrin.structures.structure.Foundation;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/// Declares template pools in datagen, named `<modId>:<prefix><name>`.
/// ```
/// var pools = new JigsawPools(context, MODID, "example_structure/");
/// pools.pool("walls", pool -> pool
///         .fallback("wall_ends")
///         .element("wall_straight")
///         .element("wall_ruined", element -> element.weight(2).processors(ProcessorLists.MOSSIFY_10_PERCENT)));
/// ```
public final class JigsawPools {
    private static final ResourceKey<StructureProcessorList> EMPTY_PROCESSORS = ResourceKey.create(Registries.PROCESSOR_LIST, Identifier.withDefaultNamespace("empty"));

    private final BootstrapContext<StructureTemplatePool> context;
    private final String modId;
    private final String prefix;
    private final HolderGetter<StructureProcessorList> processorLists;
    private final HolderGetter<StructureTemplatePool> pools;
    private UnaryOperator<Element> defaultElementSettings = UnaryOperator.identity();

    public JigsawPools(BootstrapContext<StructureTemplatePool> context, String modId, String prefix) {
        this.context = context;
        this.modId = modId;
        this.prefix = prefix;
        this.processorLists = context.lookup(Registries.PROCESSOR_LIST);
        this.pools = context.lookup(Registries.TEMPLATE_POOL);
    }

    public JigsawPools(BootstrapContext<StructureTemplatePool> context, String modId) {
        this(context, modId, "");
    }

    public static ResourceKey<StructureTemplatePool> key(String modId, String path) {
        return ResourceKey.create(Registries.TEMPLATE_POOL, Identifier.fromNamespaceAndPath(modId, path));
    }

    public Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(modId, prefix + name);
    }

    public ResourceKey<StructureTemplatePool> key(String name) {
        return ResourceKey.create(Registries.TEMPLATE_POOL, id(name));
    }

    public ResourceKey<StructureTemplatePool> pool(String name, Consumer<Pool> configure) {
        var key = key(name);
        var pool = new Pool(key);
        pool.defaultElementSettings(defaultElementSettings);
        configure.accept(pool);
        context.register(key, pool.build());
        return key;
    }

    public void defaultElementSettings(UnaryOperator<Element> configure) {
        this.defaultElementSettings = configure;
    }

    public final class Pool {
        private final ResourceKey<StructureTemplatePool> key;
        private final List<Pair<Function<StructureTemplatePool.Projection, ? extends StructurePoolElement>, Integer>> elements = new ArrayList<>();
        private ResourceKey<StructureTemplatePool> fallback = Pools.EMPTY;
        private StructureTemplatePool.Projection projection = StructureTemplatePool.Projection.RIGID;
        private UnaryOperator<Element> defaultElementSettings = UnaryOperator.identity();

        private Pool(ResourceKey<StructureTemplatePool> key) {
            this.key = key;
        }

        public Pool fallback(String name) {
            return fallback(key(name));
        }

        public Pool fallback(ResourceKey<StructureTemplatePool> fallback) {
            this.fallback = fallback;
            return this;
        }

        /// Projects the elements onto the terrain, like village paths.
        public Pool terrainMatching() {
            this.projection = StructureTemplatePool.Projection.TERRAIN_MATCHING;
            return this;
        }

        public Pool element(String name) {
            return element(name, UnaryOperator.identity());
        }

        public Pool element(String name, UnaryOperator<Element> configure) {
            return element(id(name), configure);
        }

        public Pool element(Identifier template, UnaryOperator<Element> configure) {
            var element = configure.apply(defaultElementSettings.apply(new Element()));
            return add(element.build(template), element.weight);
        }

        public Pool elements(List<String> names) {
            names.forEach(this::element);
            return this;
        }

        public Pool elements(List<String> names, UnaryOperator<Element> configure) {
            names.forEach(name -> element(name, configure));
            return this;
        }

        public Pool elementsI(List<Identifier> names, UnaryOperator<Element> configure) {
            names.forEach(name -> element(name, configure));
            return this;
        }

        public Pool empty(int weight) {
            return add(StructurePoolElement.empty(), weight);
        }

        /// Any other kind of element, such as a feature or list element.
        public Pool add(Function<StructureTemplatePool.Projection, ? extends StructurePoolElement> element, int weight) {
            elements.add(Pair.of(element, weight));
            return this;
        }

        public void defaultElementSettings(UnaryOperator<Element> configure) {
            this.defaultElementSettings = configure;
        }

        private StructureTemplatePool build() {
            if (elements.isEmpty()) {
                throw new IllegalStateException("pool " + key.identifier() + " has no elements");
            }
            return new StructureTemplatePool(pools.getOrThrow(fallback), elements, projection);
        }
    }

    public final class Element {
        private int weight = 1;
        @Nullable
        private Holder<StructureProcessorList> processors;
        private Optional<LiquidSettings> liquidSettings = Optional.empty();
        private boolean processFoam = true;
        private Optional<Foundation> foundation = Optional.empty();

        private Element() {
        }

        public Element weight(int weight) {
            this.weight = weight;
            return this;
        }

        public Element processors(ResourceKey<StructureProcessorList> processors) {
            return processors(processorLists.getOrThrow(processors));
        }

        public Element processors(Holder<StructureProcessorList> processors) {
            this.processors = processors;
            return this;
        }

        /// Stored inline in the pool.
        public Element processors(List<StructureProcessor> processors) {
            return processors(Holder.direct(new StructureProcessorList(processors)));
        }

        /// Defaults to the structure's liquid settings.
        public Element liquidSettings(LiquidSettings liquidSettings) {
            this.liquidSettings = Optional.of(liquidSettings);
            return this;
        }

        /// Leaves foam in place and places the air the template saved, like a vanilla element.
        public Element noFoamProcessing() {
            this.processFoam = false;
            return this;
        }

        /// Extends the bottom of the piece down to the ground, repeating each bottom block.
        public Element foundation() {
            return foundation(Foundation.repeating());
        }

        /// Extends the bottom of the piece down to the ground with `state`, at most `maxDepth` blocks.
        public Element foundation(BlockState state, int maxDepth) {
            return foundation(Foundation.of(state, maxDepth));
        }

        public Element foundation(Foundation foundation) {
            this.foundation = Optional.of(foundation);
            return this;
        }

        private Function<StructureTemplatePool.Projection, ? extends StructurePoolElement> build(Identifier template) {
            var resolved = processors != null ? processors : processorLists.getOrThrow(EMPTY_PROCESSORS);
            var liquid = liquidSettings;
            var foam = processFoam;
            var base = foundation;
            return projection -> new ExtendedSinglePoolElement(Either.left(template), resolved, projection, liquid, foam, base);
        }
    }
}
