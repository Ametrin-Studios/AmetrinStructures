package com.ametrin.structures.structure;

import com.ametrin.structures.structure.filter.PlacementFilter;
import com.ametrin.structures.structure.filter.PlacementFilters;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/// Settings shared by every kind of structure
public abstract class StructureBuilder<B extends StructureBuilder<B>> {
    protected final String id;

    private Function<HolderGetter<Biome>, HolderSet<Biome>> biomes = lookup -> lookup.getOrThrow(BiomeTags.IS_OVERWORLD);
    private final Map<MobCategory, StructureSpawnOverride> spawnOverrides = new EnumMap<>(MobCategory.class);
    private GenerationStep.Decoration step = GenerationStep.Decoration.SURFACE_STRUCTURES;
    private TerrainAdjustment terrainAdaptation = TerrainAdjustment.NONE;
    private final List<PlacementFilter> filters = new ArrayList<>();
    private @Nullable Function<HolderGetter<StructureProcessorList>, Holder<StructureProcessorList>> processors;
    private boolean onlyWhenPickedFirst;

    protected StructureBuilder(String id) {
        this.id = id;
    }

    protected abstract B self();

    /// Defaults to `#minecraft:is_overworld`.
    public B biomes(TagKey<Biome> tag) {
        return biomes(lookup -> lookup.getOrThrow(tag));
    }

    @SafeVarargs
    public final B biomes(ResourceKey<Biome>... keys) {
        return biomes(lookup -> HolderSet.direct(Arrays.stream(keys).map(lookup::getOrThrow).toList()));
    }

    public B biomes(Function<HolderGetter<Biome>, HolderSet<Biome>> biomes) {
        this.biomes = biomes;
        return self();
    }

    public B spawnOverride(MobCategory category, StructureSpawnOverride override) {
        spawnOverrides.put(category, override);
        return self();
    }

    /// @param categories leave empty for every category
    public B noSpawns(StructureSpawnOverride.BoundingBoxType boundingBox, MobCategory... categories) {
        for (var category : categories.length == 0 ? MobCategory.values() : categories) {
            spawnOverride(category, new StructureSpawnOverride(boundingBox, WeightedList.of()));
        }
        return self();
    }

    /// Defaults to [GenerationStep.Decoration#SURFACE_STRUCTURES].
    public B step(GenerationStep.Decoration step) {
        this.step = step;
        return self();
    }

    public B terrainAdaptation(TerrainAdjustment terrainAdaptation) {
        this.terrainAdaptation = terrainAdaptation;
        return self();
    }

    /// Checked in order once the structure has a start position. Only an [ExtendedStructure] runs them.
    public B filter(PlacementFilter filter) {
        filters.add(filter);
        return self();
    }

    public B filterHeightRange(int min, int max) {
        var filter = new PlacementFilters.HeightRange(min, max);
        if (filter.validate().isError()) {
            throw new IllegalArgumentException("height range " + min + " to " + max + " is not a valid range");
        }
        return filter(filter);
    }

    public B filterGroundCheck(TagKey<Block> blocks) {
        return filter(new PlacementFilters.GroundCheck(blocks));
    }

    public B filterFlatness(int maxVariance) {
        return filterFlatness(maxVariance, 0);
    }

    /// @param margin negative values allowed
    public B filterFlatness(int maxVariance, int margin) {
        if (maxVariance < 0 || maxVariance > PlacementFilters.Flatness.MAX_VARIANCE) {
            throw new IllegalArgumentException("flatness variance " + maxVariance + " is outside 0 to " + PlacementFilters.Flatness.MAX_VARIANCE);
        }
        if (Math.abs(margin) > PlacementFilters.Flatness.MAX_MARGIN) {
            throw new IllegalArgumentException("flatness margin " + margin + " is outside -" + PlacementFilters.Flatness.MAX_MARGIN + " to " + PlacementFilters.Flatness.MAX_MARGIN);
        }
        return filter(new PlacementFilters.Flatness(maxVariance, margin));
    }

    /// Requires at least `minDepth` blocks of fluid above the structure.
    public B filterSubmerged(int minDepth) {
        if (minDepth < 0) {
            throw new IllegalArgumentException("submerged depth " + minDepth + " is negative");
        }
        return filter(new PlacementFilters.Submerged(minDepth));
    }

    /// Keeps the structure out of water deeper than `maxDepth`.
    public B filterMaxWaterDepth(int maxDepth) {
        return filterWaterDepth(0, maxDepth);
    }

    public B filterMinWaterDepth(int minDepth) {
        if (minDepth < 0) {
            throw new IllegalArgumentException("water depth " + minDepth + " is not a valid range");
        }
        return filter(new PlacementFilters.WaterDepth(minDepth, Optional.empty()));
    }

    public B filterWaterDepth(int minDepth, int maxDepth) {
        var filter = new PlacementFilters.WaterDepth(minDepth, Optional.of(maxDepth));
        if (minDepth < 0 || filter.validate().isError()) {
            throw new IllegalArgumentException("water depth " + minDepth + " to " + maxDepth + " is not a valid range");
        }
        return filter(filter);
    }

    public B filterWithinBiome() {
        return filterWithinBiome(0);
    }

    public B filterWithinBiome(int margin) {
        if (margin < 0 || margin > PlacementFilters.WithinBiome.MAX_MARGIN) {
            throw new IllegalArgumentException("biome margin " + margin + " is outside 0 to " + PlacementFilters.WithinBiome.MAX_MARGIN);
        }
        return filter(new PlacementFilters.WithinBiome(Optional.empty(), margin));
    }

    /// Only generates where its structure set tries it first, not as a fallback after another structure of the set didn't fit.
    /// Can be used to optimize variants that fail at the same spots. Each spot is then measured once and locating one skips the spots of the others without loading chunks.
    /// This option makes the structure generate less often.
    public B onlyWhenPickedFirst() {
        onlyWhenPickedFirst = true;
        return self();
    }

    /// For template pieces without processors of their own, see [InlineFromStructureProcessor].
    public B processors(ResourceKey<StructureProcessorList> processors) {
        this.processors = lookup -> lookup.getOrThrow(processors);
        return self();
    }

    /// For template pieces without processors of their own, see [InlineFromStructureProcessor].
    public B processors(Holder<StructureProcessorList> processors) {
        this.processors = _ -> processors;
        return self();
    }

    /// Stored inline, also in every piece that runs them.
    public B processors(List<StructureProcessor> processors) {
        return processors(Holder.direct(new StructureProcessorList(processors)));
    }

    /// Whether settings were set that only an [ExtendedStructure] takes.
    protected boolean hasExtendedSettings() {
        return !filters.isEmpty() || processors != null || onlyWhenPickedFirst;
    }

    protected void validate() {}

    /// Validates and creates the structure, for use outside a [StructureBootstrap].
    public Structure build(BootstrapContext<Structure> context) {
        validate();
        return bootstrap(context);
    }

    protected abstract Structure create(Structure.StructureSettings settings, ExtendedStructureSettings extendedSettings, BootstrapContext<Structure> context);

    protected IllegalStateException fail(String message) {
        return new IllegalStateException("structure " + id + ": " + message);
    }

    protected IllegalStateException fail(String message, Throwable cause) {
        return new IllegalStateException("structure " + id + ": " + message, cause);
    }

    // Names the structure when a setter rejects its argument.
    static <B extends StructureBuilder<B>> B configured(B builder, Consumer<? super B> configure) {
        try {
            configure.accept(builder);
        } catch (IllegalArgumentException exception) {
            throw builder.fail(exception.getMessage(), exception);
        }
        return builder;
    }

    Structure bootstrap(BootstrapContext<Structure> context) {
        var settings = new Structure.StructureSettings(biomes.apply(context.lookup(Registries.BIOME)), Map.copyOf(spawnOverrides), step, terrainAdaptation);
        var resolvedProcessors = processors == null
                ? Optional.<Holder<StructureProcessorList>>empty()
                : Optional.of(processors.apply(context.lookup(Registries.PROCESSOR_LIST)));
        return create(settings, new ExtendedStructureSettings(filters, resolvedProcessors, onlyWhenPickedFirst), context);
    }
}
