package com.ametrin.structures.structure;

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
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/// Settings shared by every kind of structure in a set
public abstract class StructureEntryBuilder<B extends StructureEntryBuilder<B>> {
    protected final String id;

    private Function<HolderGetter<Biome>, HolderSet<Biome>> biomes = lookup -> lookup.getOrThrow(BiomeTags.IS_OVERWORLD);
    private final Map<MobCategory, StructureSpawnOverride> spawnOverrides = new EnumMap<>(MobCategory.class);
    private GenerationStep.Decoration step = GenerationStep.Decoration.SURFACE_STRUCTURES;
    private TerrainAdjustment terrainAdaptation = TerrainAdjustment.NONE;
    private int weight = 1;

    protected StructureEntryBuilder(String id) {
        this.id = id;
    }

    protected abstract B self();

    /// defaults to `#minecraft:is_overworld`.
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

    /// defaults to [GenerationStep.Decoration#SURFACE_STRUCTURES].
    public B step(GenerationStep.Decoration step) {
        this.step = step;
        return self();
    }

    public B terrainAdaptation(TerrainAdjustment terrainAdaptation) {
        this.terrainAdaptation = terrainAdaptation;
        return self();
    }

    /// Relative chance to be tried first among the set's structures. Defaults to 1.
    public B weight(int weight) {
        this.weight = weight;
        return self();
    }

    int weight() {
        return weight;
    }

    protected void validate() {
        if (weight < 1) {
            throw fail("weight " + weight + " must be at least 1");
        }
    }

    protected abstract Structure create(Structure.StructureSettings settings, BootstrapContext<Structure> context);

    protected IllegalStateException fail(String message) {
        return new IllegalStateException("structure " + id + ": " + message);
    }

    protected IllegalStateException fail(String message, Throwable cause) {
        return new IllegalStateException("structure " + id + ": " + message, cause);
    }

    Structure bootstrap(BootstrapContext<Structure> context) {
        var settings = new Structure.StructureSettings(biomes.apply(context.lookup(Registries.BIOME)), Map.copyOf(spawnOverrides), step, terrainAdaptation);
        return create(settings, context);
    }
}
