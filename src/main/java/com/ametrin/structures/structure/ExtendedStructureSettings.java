package com.ametrin.structures.structure;

import com.ametrin.structures.structure.filter.PlacementFilter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/// @param filters checked in order once the structure has a start position
public record ExtendedStructureSettings(List<PlacementFilter> filters) {
    public static final ExtendedStructureSettings DEFAULT = new ExtendedStructureSettings(List.of());

    public static final MapCodec<ExtendedStructureSettings> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.list(PlacementFilter.CODEC).optionalFieldOf("filters", List.of()).forGetter(ExtendedStructureSettings::filters))
            .apply(instance, ExtendedStructureSettings::new));

    public ExtendedStructureSettings {
        filters = List.copyOf(filters);
    }
}
