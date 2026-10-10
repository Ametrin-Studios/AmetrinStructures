package com.ametrin.structures.structure.simple;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/// A template placed at `offset` from the structure origin, optionally with processors of its own.
/// Terrain adaptation fits the terrain to the [TerrainBox]. With the default box, the whole template, a negative Y offset buries the template's lower part.
public record TemplateEntry(Identifier template, BlockPos offset, Optional<Holder<StructureProcessorList>> processors,
                            TerrainBox terrainBox) {

    public static final MapCodec<TemplateEntry> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Identifier.CODEC.fieldOf("template").forGetter(TemplateEntry::template),
                    BlockPos.CODEC.optionalFieldOf("offset", BlockPos.ZERO).forGetter(TemplateEntry::offset),
                    StructureProcessorType.LIST_CODEC
                            .optionalFieldOf("processors")
                            .forGetter(TemplateEntry::processors),
                    TerrainBox.CODEC.optionalFieldOf("terrain_box", TerrainBox.template()).forGetter(TemplateEntry::terrainBox))
            .apply(instance, TemplateEntry::new));

    public static final Codec<TemplateEntry> CODEC = MAP_CODEC.codec();

    public static TemplateEntry of(Identifier template) {
        return new TemplateEntry(template, BlockPos.ZERO, Optional.empty(), TerrainBox.template());
    }

    public static class Builder {
        private final String defaultNamespace;
        private final HolderGetter<StructureProcessorList> processorsGetter;
        @Nullable
        private Identifier template;
        private BlockPos offset = BlockPos.ZERO;
        @Nullable
        private Holder<StructureProcessorList> processors;
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

        /// Defaults to [TerrainBox#template()].
        public Builder terrainBox(TerrainBox terrainBox) {
            this.terrainBox = terrainBox;
            return this;
        }

        public TemplateEntry build() {
            if (template == null) {
                throw new IllegalStateException("template has not been set");
            }
            return new TemplateEntry(template, offset, Optional.ofNullable(processors), terrainBox);
        }
    }
}
