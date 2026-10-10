package com.ametrin.structures.structure;

import com.mojang.datafixers.util.Function3;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.level.levelgen.structure.Structure;

/// A structure of your own in a [StructureSetBuilder]; see [StructureSetBuilder#structure].
public final class CustomStructureBuilder extends StructureEntryBuilder<CustomStructureBuilder> {
    private final Function3<Structure.StructureSettings, ExtendedStructureSettings, BootstrapContext<Structure>, Structure> factory;
    private final boolean takesFilters;

    CustomStructureBuilder(
            String id, Function3<Structure.StructureSettings, ExtendedStructureSettings, BootstrapContext<Structure>, Structure> factory, boolean takesFilters) {
        super(id);
        this.factory = factory;
        this.takesFilters = takesFilters;
    }

    @Override
    protected CustomStructureBuilder self() {
        return this;
    }

    @Override
    protected void validate() {
        super.validate();
        if (hasFilters() && !takesFilters) {
            throw fail("filters need a factory that takes ExtendedStructureSettings");
        }
    }

    @Override
    protected Structure create(Structure.StructureSettings settings, ExtendedStructureSettings extendedSettings, BootstrapContext<Structure> context) {
        return factory.apply(settings, extendedSettings, context);
    }
}
