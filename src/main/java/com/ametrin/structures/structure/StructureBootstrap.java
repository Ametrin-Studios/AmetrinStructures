package com.ametrin.structures.structure;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.neoforged.neoforge.data.event.DatapackRegistryGatherer;
import net.neoforged.neoforge.data.event.GatherDataRegistryEntriesEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/// Declares a mod's structure sets for datagen.
///
/// ```
/// public static final StructureBootstrap STRUCTURES = new StructureBootstrap(MODID);
///
/// public static final StructureSetKeys TOWER = STRUCTURES.registerSet("tower", set -> set
///         .evenSpreadPlacement(18, 0.6F)
///         .simple(tower -> tower.single("tower").surface()));
/// ```
public final class StructureBootstrap {
    private final String namespace;
    private final List<StructureSetKeys> sets = new ArrayList<>();
    private final Map<ResourceKey<Structure>, Function<BootstrapContext<Structure>, Structure>> structures = new LinkedHashMap<>();
    private final Map<ResourceKey<StructureSet>, Function<BootstrapContext<StructureSet>, StructureSet>> structureSets = new LinkedHashMap<>();

    public StructureBootstrap(String namespace) {
        this.namespace = namespace;
    }

    /// Declares the set `name` and its structures, named `<name>/<suffix>`. Validates the builder immediately.
    public StructureSetKeys registerSet(String name, Consumer<StructureSetBuilder> configure) {
        var builder = new StructureSetBuilder(namespace, name);
        configure.accept(builder);
        var declared = builder.declare();
        if (structureSets.containsKey(declared.keys().key())) {
            throw new IllegalStateException("structure set " + name + " is registered twice");
        }
        structures.putAll(declared.structures());
        structureSets.put(declared.keys().key(), declared.set());
        sets.add(declared.keys());
        return declared.keys();
    }

    public String getNamespace() {
        return namespace;
    }

    /// In the order they were registered.
    public List<StructureSetKeys> getSets() {
        return Collections.unmodifiableList(sets);
    }

    /// Adds every structure and structure set. Listen for [GatherDataRegistryEntriesEvent] with it:
    ///
    /// ```
    /// modBus.addListener(GatherDataRegistryEntriesEvent.class, STRUCTURES::addTo);
    /// ```
    public void addTo(DatapackRegistryGatherer registries) {
        registries.add(Registries.STRUCTURE_SET, context -> structureSets.forEach((key, factory) -> context.register(key, factory.apply(context))))
                .add(Registries.STRUCTURE, context -> structures.forEach((key, factory) -> context.register(key, factory.apply(context))));
    }
}
