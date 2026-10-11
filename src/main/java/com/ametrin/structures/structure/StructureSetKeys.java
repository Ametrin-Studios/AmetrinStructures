package com.ametrin.structures.structure;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/// The keys of a set from [StructureBootstrap#registerSet] and of its structures: the ones it declares, by suffix, and the ones added with [StructureSetBuilder#existing].
/// The empty suffix is the set's own name.
public final class StructureSetKeys {
    private final ResourceKey<StructureSet> key;
    private final Map<String, ResourceKey<Structure>> declared;
    private final List<ResourceKey<Structure>> existing;
    private final List<ResourceKey<Structure>> structures;

    StructureSetKeys(ResourceKey<StructureSet> key, Map<String, ResourceKey<Structure>> declared, List<ResourceKey<Structure>> existing) {
        this.key = key;
        this.declared = Collections.unmodifiableMap(new LinkedHashMap<>(declared));
        this.existing = List.copyOf(existing);
        this.structures = Stream.concat(declared.values().stream(), existing.stream()).toList();
    }

    public ResourceKey<StructureSet> key() {
        return key;
    }

    /// A structure the set declares itself.
    ///
    /// @throws IllegalArgumentException when the set declares no such structure
    public ResourceKey<Structure> structure(String suffix) {
        var structure = declared.get(suffix);
        if (structure == null) {
            throw new IllegalArgumentException("structure set " + key.identifier() + " has no structure " + describe(suffix) + "; known: " + declared.keySet());
        }
        return structure;
    }

    /// @throws IllegalStateException when the set has several structures
    public ResourceKey<Structure> structure() {
        if (structures.size() != 1) {
            throw new IllegalStateException("structure set " + key.identifier() + " has " + structures.size() + " structures, so one must be picked; known: " + structures);
        }
        return structures.getFirst();
    }

    /// In the order they were declared.
    public Map<String, ResourceKey<Structure>> declared() {
        return declared;
    }

    /// In the order they were added.
    public List<ResourceKey<Structure>> existing() {
        return existing;
    }

    public List<ResourceKey<Structure>> structures() {
        return structures;
    }

    public Holder.Reference<StructureSet> get(HolderGetter.Provider registries) {
        return registries.lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(key);
    }

    static String describe(String suffix) {
        return suffix.isEmpty() ? "(the bare name)" : suffix;
    }

    @Override
    public String toString() {
        return key.identifier().toString();
    }
}
