package com.ametrin.structures.registry;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.structure.filter.PlacementFilterType;
import com.ametrin.structures.structure.filter.PlacementFilters;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ASPlacementFilters {
    public static final DeferredRegister<PlacementFilterType> REGISTER = DeferredRegister.create(ASRegistries.PLACEMENT_FILTER_TYPE, AmetrinStructures.MOD_ID);

    public static final DeferredHolder<PlacementFilterType, PlacementFilterType> HEIGHT_RANGE = REGISTER.register("height_range", () -> new PlacementFilterType(PlacementFilters.HeightRange.CODEC));
    public static final DeferredHolder<PlacementFilterType, PlacementFilterType> GROUND_CHECK = REGISTER.register("ground_check", () -> new PlacementFilterType(PlacementFilters.GroundCheck.CODEC));
    public static final DeferredHolder<PlacementFilterType, PlacementFilterType> FLATNESS = REGISTER.register("flatness", () -> new PlacementFilterType(PlacementFilters.Flatness.CODEC));
    public static final DeferredHolder<PlacementFilterType, PlacementFilterType> SUBMERGED = REGISTER.register("submerged", () -> new PlacementFilterType(PlacementFilters.Submerged.CODEC));
    public static final DeferredHolder<PlacementFilterType, PlacementFilterType> WATER_DEPTH = REGISTER.register("water_depth", () -> new PlacementFilterType(PlacementFilters.WaterDepth.CODEC));
    public static final DeferredHolder<PlacementFilterType, PlacementFilterType> WITHIN_BIOME = REGISTER.register("within_biome", () -> new PlacementFilterType(PlacementFilters.WithinBiome.CODEC));
}
