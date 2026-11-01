package com.ametrin.structures.structure.simple;

import com.ametrin.structures.structure.Foundation;
import com.ametrin.structures.structure.jigsaw.ExtendedSinglePoolElement;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/// A pool element placed at `offset` from the structure origin and turned with the structure.
/// In JSON, `template` and optionally `processors` stand for a rigid [ExtendedSinglePoolElement], and `element` takes any pool element.
/// Terrain adaptation fits the terrain to the [TerrainBox]. With the default box, the whole template, a negative Y offset buries the template's lower part.
public record TemplateEntry(StructurePoolElement element, BlockPos offset, TerrainBox terrainBox) {
    private static final MapCodec<StructurePoolElement> ELEMENT_CODEC = Codec.mapEither(Template.CODEC, StructurePoolElement.CODEC.fieldOf("element"))
            .xmap(either -> either.map(Template::element, element -> element),
                    element -> Template.of(element).<Either<Template, StructurePoolElement>>map(Either::left).orElseGet(() -> Either.right(element)));

    public static final MapCodec<TemplateEntry> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ELEMENT_CODEC.forGetter(TemplateEntry::element),
                    BlockPos.CODEC.optionalFieldOf("offset", BlockPos.ZERO).forGetter(TemplateEntry::offset),
                    TerrainBox.CODEC.optionalFieldOf("terrain_box", TerrainBox.template()).forGetter(TemplateEntry::terrainBox))
            .apply(instance, TemplateEntry::new));

    public static final Codec<TemplateEntry> CODEC = MAP_CODEC.codec();

    public static TemplateEntry of(Identifier template) {
        return new TemplateEntry(new Template(template, Optional.empty()).element(), BlockPos.ZERO, TerrainBox.template());
    }

    private record Template(Identifier template, Optional<Holder<StructureProcessorList>> processors) {
        static final MapCodec<Template> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Identifier.CODEC.fieldOf("template").forGetter(Template::template),
                        StructureProcessorType.LIST_CODEC.optionalFieldOf("processors").forGetter(Template::processors))
                .apply(instance, Template::new));

        static Optional<Template> of(StructurePoolElement element) {
            if (element.getClass() != ExtendedSinglePoolElement.class) {
                return Optional.empty();
            }
            var single = (ExtendedSinglePoolElement) element;
            if (single.template.left().isEmpty() || single.getProjection() != StructureTemplatePool.Projection.RIGID || single.overrideLiquidSettings.isPresent() || !single.processFoam() || single.foundation().isPresent()) {
                return Optional.empty();
            }
            return Optional.of(new Template(single.template.left().get(), single.ownProcessors()));
        }

        StructurePoolElement element() {
            return new ExtendedSinglePoolElement(Either.left(template), processors.orElse(ExtendedSinglePoolElement.NO_PROCESSORS), StructureTemplatePool.Projection.RIGID, Optional.empty(), true, Optional.empty());
        }
    }

    public static class Builder {
        private final String defaultNamespace;
        private final HolderGetter<StructureProcessorList> processorsGetter;
        @Nullable
        private Identifier template;
        @Nullable
        private StructurePoolElement element;
        private BlockPos offset = BlockPos.ZERO;
        @Nullable
        private Holder<StructureProcessorList> processors;
        private Optional<LiquidSettings> liquidSettings = Optional.empty();
        private boolean processFoam = true;
        private Optional<Foundation> foundation = Optional.empty();
        private TerrainBox terrainBox = TerrainBox.template();

        public Builder(String defaultNamespace, HolderGetter<StructureProcessorList> processorsGetter) {
            this.defaultNamespace = defaultNamespace;
            this.processorsGetter = processorsGetter;
        }

        public Builder template(String template) {
            return template(Identifier.fromNamespaceAndPath(defaultNamespace, template));
        }

        public Builder template(Identifier template) {
            this.template = template;
            return this;
        }

        /// Any pool element instead of a template, such as a feature or list element. The template settings don't apply to it.
        public Builder element(StructurePoolElement element) {
            this.element = element;
            return this;
        }

        public Builder offset(int x, int y, int z) {
            return offset(new BlockPos(x, y, z));
        }

        public Builder offset(BlockPos offset) {
            this.offset = offset;
            return this;
        }

        public Builder yOffset(int offset) {
            return offset(0, offset, 0);
        }

        /// Replaces the structure's processors. Add [InlineFromStructureProcessor#INSTANCE] to keep them.
        /// Stored inline.
        public Builder processors(List<StructureProcessor> processors) {
            return processors(Holder.direct(new StructureProcessorList(processors)));
        }

        /// Replaces the structure's processors. Add [InlineFromStructureProcessor#INSTANCE] to keep them.
        public Builder processors(ResourceKey<StructureProcessorList> processors) {
            return processors(processorsGetter.getOrThrow(processors));
        }

        /// Replaces the structure's processors. Add [InlineFromStructureProcessor#INSTANCE] to keep them.
        public Builder processors(Holder<StructureProcessorList> processors) {
            this.processors = processors;
            return this;
        }

        /// Defaults to the structure's liquid settings.
        public Builder liquidSettings(LiquidSettings liquidSettings) {
            this.liquidSettings = Optional.of(liquidSettings);
            return this;
        }

        /// places the air the template like a vanilla element.
        public Builder noFoamProcessing() {
            this.processFoam = false;
            return this;
        }

        /// Extends the bottom of the piece down to the ground, repeating each bottom block. Replaces the structure's foundation.
        public Builder foundation() {
            return foundation(Foundation.repeating());
        }

        /// Extends the bottom of the piece down to the ground with `state`, at most `maxDepth` blocks. Replaces the structure's foundation.
        public Builder foundation(BlockState state, int maxDepth) {
            return foundation(Foundation.of(state, maxDepth));
        }

        public Builder foundation(Foundation foundation) {
            this.foundation = Optional.of(foundation);
            return this;
        }

        /// Defaults to [TerrainBox#template()].
        public Builder terrainBox(TerrainBox terrainBox) {
            this.terrainBox = terrainBox;
            return this;
        }

        public TemplateEntry build() {
            if (element != null) {
                if (template != null) {
                    throw new IllegalStateException("set either a template or an element");
                }
                return new TemplateEntry(element, offset, terrainBox);
            }
            if (template == null) {
                throw new IllegalStateException("template has not been set");
            }
            var processors = this.processors != null ? this.processors : ExtendedSinglePoolElement.NO_PROCESSORS;
            return new TemplateEntry(new ExtendedSinglePoolElement(Either.left(template), processors, StructureTemplatePool.Projection.RIGID, liquidSettings, processFoam, foundation), offset, terrainBox);
        }
    }
}
