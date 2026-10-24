package com.ametrin.structures.structure;

import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.level.levelgen.structure.Structure;

/// A structure of your own, see [StructureSetBuilder#structure].
public final class CustomStructureBuilder extends StructureBuilder<CustomStructureBuilder> {
    private final Factory factory;

    CustomStructureBuilder(String id, Factory factory) {
        super(id);
        this.factory = factory;
    }

    /// A structure that isn't an [ExtendedStructure] can ignore `extendedSettings`, as long as none were set on the builder.
    @FunctionalInterface
    public interface Factory {
        Structure create(Structure.StructureSettings settings, ExtendedStructureSettings extendedSettings, BootstrapContext<Structure> context);
    }

    @Override
    protected CustomStructureBuilder self() {
        return this;
    }

    @Override
    protected Structure create(Structure.StructureSettings settings, ExtendedStructureSettings extendedSettings, BootstrapContext<Structure> context) {
        var structure = factory.create(settings, extendedSettings, context);
        if (hasExtendedSettings() && !(structure instanceof ExtendedStructure)) {
            throw fail("filters, processors and onlyWhenPickedFirst need an ExtendedStructure");
        }
        return structure;
    }
}
