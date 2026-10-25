package com.ametrin.structures.registry;

import com.ametrin.structures.AmetrinStructures;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.neoforged.neoforge.registries.datamaps.AdvancedDataMapType;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.DataMapValueMerger;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

import java.util.List;

public final class ASDataMaps {
    /// Dimensions the structure set is left out of, see `docs/extending.md`.
    public static final DataMapType<StructureSet, List<ResourceKey<Level>>> EXCLUDED_DIMENSIONS = AdvancedDataMapType
            .builder(AmetrinStructures.locate("excluded_dimensions"), Registries.STRUCTURE_SET, ResourceKey.codec(Registries.DIMENSION).listOf())
            .merger(DataMapValueMerger.listMerger())
            .build();

    private ASDataMaps() {}

    public static void register(RegisterDataMapTypesEvent event) {
        event.register(EXCLUDED_DIMENSIONS);
    }
}
