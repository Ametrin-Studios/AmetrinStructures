package com.ametrin.structures.structure;

import com.ametrin.structures.structure.jigsaw.ExtendedJigsawStructure;
import com.ametrin.structures.structure.simple.SimpleStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.neoforged.neoforge.data.event.DatapackRegistryGatherer;
import net.neoforged.neoforge.data.event.GatherDataRegistryEntriesEvent;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/// ```
/// public static final StructureBootstrap STRUCTURES = new StructureBootstrap(MOD_ID);
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
        declared.structures().keySet().forEach(this::checkUnregistered);
        structures.putAll(declared.structures());
        structureSets.put(declared.keys().key(), declared.set());
        sets.add(declared.keys());
        return declared.keys();
    }

    public ResourceKey<Structure> registerSimple(String name, Consumer<SimpleStructure.Builder> configure) {
        return register(name, StructureSetBuilder.simpleEntry(namespace, name, configure));
    }

    public ResourceKey<Structure> registerJigsaw(
            String name, ResourceKey<StructureTemplatePool> pool, Consumer<ExtendedJigsawStructure.Builder> factory, Consumer<CustomStructureBuilder> configure) {
        return register(name, StructureSetBuilder.jigsawEntry(name, pool, factory, configure));
    }

    public ResourceKey<Structure> registerStructure(String name, StructureSetBuilder.StructureFactory factory, Consumer<CustomStructureBuilder> configure) {
        return register(name, StructureSetBuilder.customEntry(name, factory, configure));
    }

    public ResourceKey<Structure> registerStructure(String name, StructureSetBuilder.ExtendedStructureFactory factory, Consumer<CustomStructureBuilder> configure) {
        return register(name, StructureSetBuilder.customEntry(name, factory, configure));
    }

    private ResourceKey<Structure> register(String name, StructureEntryBuilder<?> structure) {
        structure.validate();
        var key = ResourceKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath(namespace, name));
        checkUnregistered(key);
        structures.put(key, structure::bootstrap);
        return key;
    }

    private void checkUnregistered(ResourceKey<Structure> key) {
        if (structures.containsKey(key)) {
            throw new IllegalStateException("structure " + key.identifier() + " is registered twice");
        }
    }

    public String getNamespace() {
        return namespace;
    }

    /// In the order they were registered.
    public List<StructureSetKeys> getSets() {
        return Collections.unmodifiableList(sets);
    }

    public Stream<ResourceKey<Structure>> getStructures() {
        return structures.keySet().stream().sorted();
    }

    /// Adds every structure and structure set. Call with [GatherDataRegistryEntriesEvent].
    public void addTo(DatapackRegistryGatherer registries) {
        registries.add(Registries.STRUCTURE_SET, context -> structureSets.forEach((key, factory) -> context.register(key, factory.apply(context))))
                .add(Registries.STRUCTURE, context -> structures.forEach((key, factory) -> context.register(key, factory.apply(context))));
    }
}
