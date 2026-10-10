package com.ametrin.structures.structure;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/// The keys of a set from [StructureBootstrap#registerSet] and of its structures, by suffix. The empty suffix is the set's own name.
public final class StructureSetKeys {
    private final ResourceKey<StructureSet> key;
    private final Map<String, ResourceKey<Structure>> structures;

    StructureSetKeys(ResourceKey<StructureSet> key, Map<String, ResourceKey<Structure>> structures) {
        this.key = key;
        this.structures = Collections.unmodifiableMap(new LinkedHashMap<>(structures));
    }

    public ResourceKey<StructureSet> key() {
        return key;
    }

    /// @throws IllegalArgumentException when the set has no such structure
    public ResourceKey<Structure> structure(String suffix) {
        var structure = structures.get(suffix);
        if (structure == null) {
            throw new IllegalArgumentException("structure set " + key.identifier() + " has no structure " + describe(suffix) + "; known: " + structures.keySet());
        }
        return structure;
    }

    /// @throws IllegalStateException when the set has several structures
    public ResourceKey<Structure> structure() {
        if (structures.size() != 1) {
            throw new IllegalStateException("structure set " + key.identifier() + " has " + structures.size() + " structures, so one must be named; known: " + structures.keySet());
        }
        return structures.values().iterator().next();
    }

    /// In the order they were declared.
    public Map<String, ResourceKey<Structure>> structures() {
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
