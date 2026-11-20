package com.ametrin.structures.structure.jigsaw;

import com.ametrin.structures.structure.filter.PlacementFilter;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

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
            return add(element.weight, element.build(template, UnaryOperator.identity()));
        }

        /// The template `name` placed as a row of copies, see [Repeat].
        public Pool repeated(String name, Repeat repeat) {
            return repeated(name, repeat, UnaryOperator.identity());
        }

        /// The template `name` placed as a row of copies, see [Repeat]. The element settings apply to the row, and its template settings to each copy.
        public Pool repeated(String name, Repeat repeat, UnaryOperator<Element> configure) {
            return repeated(id(name), repeat, configure);
        }

        /// `template` placed as a row of copies, see [Repeat]. The element settings apply to the row, and its template settings to each copy.
        public Pool repeated(Identifier template, Repeat repeat, UnaryOperator<Element> configure) {
            var element = configure.apply(defaultElementSettings.apply(new Element()));
            return add(element.weight, element.build(template, repeat::wrap));
        }

        public Pool elements(List<String> names) {
            names.forEach(this::element);
            return this;
        }

        public Pool elements(List<String> names, UnaryOperator<Element> configure) {
            names.forEach(name -> element(name, configure));
            return this;
        }

        public Pool elementIdentifiers(List<Identifier> templates, UnaryOperator<Element> configure) {
            templates.forEach(template -> element(template, configure));
            return this;
        }

        public Pool empty(int weight) {
            return add(weight, StructurePoolElement.empty());
        }

        /// Any other kind of element, such as a feature or list element.
        public Pool add(int weight, Function<StructureTemplatePool.Projection, ? extends StructurePoolElement> element) {
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

    public final class Element extends TemplateElementBuilder<Element> {
        private int weight = 1;
        private int minCount = 0;
        private Optional<Integer> softMinCount = Optional.empty();
        private Optional<Integer> maxCount = Optional.empty();
        private int minDepth = 0;
        private Optional<Integer> maxDepth = Optional.empty();
        private final List<PlacementFilter> filters = new ArrayList<>();

        private Element() {
            super(processorLists);
        }

        public Element weight(int weight) {
            this.weight = weight;
            return this;
        }

        /// At most `max` of this element per structure, see [ConditionalPoolElement].
        public Element maxCount(int max) {
            this.maxCount = Optional.of(max);
            return this;
        }

        /// At least `min` of this element per structure. A spot where the layout can't fit them gets no structure, so the structure generates less often. See [ConditionalPoolElement].
        public Element minCount(int min) {
            this.minCount = min;
            return this;
        }

        /// At least `min` of this element per structure where the layout can fit them, fewer where it can't. Defaults to [#minCount(int)]. See [ConditionalPoolElement].
        public Element softMinCount(int min) {
            this.softMinCount = Optional.of(min);
            return this;
        }

        /// Only attaches at least `min` jigsaws deep, see [ConditionalPoolElement].
        public Element minDepth(int min) {
            this.minDepth = min;
            return this;
        }

        /// Only attaches at most `max` jigsaws deep, see [ConditionalPoolElement].
        public Element maxDepth(int max) {
            this.maxDepth = Optional.of(max);
            return this;
        }

        /// Only attaches where the piece passes `filter`, see [ConditionalPoolElement].
        public Element filter(PlacementFilter filter) {
            this.filters.add(filter);
            return this;
        }

        private Function<StructureTemplatePool.Projection, ? extends StructurePoolElement> build(Identifier template, Function<StructurePoolElement, StructurePoolElement> around) {
            var single = element(template);
            Function<StructureTemplatePool.Projection, StructurePoolElement> element = projection -> around.apply(single.apply(projection));
            var min = minCount;
            var softMin = softMinCount.orElse(minCount);
            var max = maxCount;
            var fromDepth = minDepth;
            var toDepth = maxDepth;
            var pieceFilters = List.copyOf(filters);
            if (softMin == 0 && max.isEmpty() && fromDepth == 0 && toDepth.isEmpty() && pieceFilters.isEmpty()) {
                return element;
            }
            return projection -> new ConditionalPoolElement(element.apply(projection), min, softMin, max, fromDepth, toDepth, pieceFilters);
        }
    }
}
