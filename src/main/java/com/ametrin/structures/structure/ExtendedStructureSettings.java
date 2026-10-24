package com.ametrin.structures.structure;

import com.ametrin.structures.structure.filter.PlacementFilter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;

import java.util.List;
import java.util.Optional;

/// @param filters    checked in order once the structure has a start position
/// @param processors for template pieces without processors of their own, see [InlineFromStructureProcessor]
/// @param onlyWhenPickedFirst see [ExtendedStructureBuilder#onlyWhenPickedFirst]
public record ExtendedStructureSettings(List<PlacementFilter> filters, Optional<Holder<StructureProcessorList>> processors, boolean onlyWhenPickedFirst) {
    public static final ExtendedStructureSettings DEFAULT = new ExtendedStructureSettings(List.of(), Optional.empty());

    public static final MapCodec<ExtendedStructureSettings> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.list(PlacementFilter.CODEC).optionalFieldOf("filters", List.of()).forGetter(ExtendedStructureSettings::filters),
                    StructureProcessorType.LIST_CODEC.optionalFieldOf("processors").forGetter(ExtendedStructureSettings::processors),
                    Codec.BOOL.optionalFieldOf("only_when_picked_first", false).forGetter(ExtendedStructureSettings::onlyWhenPickedFirst))
            .apply(instance, ExtendedStructureSettings::new));

    public ExtendedStructureSettings {
        filters = List.copyOf(filters);
    }

    public ExtendedStructureSettings(List<PlacementFilter> filters, Optional<Holder<StructureProcessorList>> processors) {
        this(filters, processors, false);
    }
}
