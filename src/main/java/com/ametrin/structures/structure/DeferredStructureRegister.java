package com.ametrin.structures.structure;

import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.data.event.GatherDataRegistryEntriesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class DeferredStructureRegister {
    final DeferredRegister<StructureType<?>> structureTypes;
    final DeferredRegister<StructurePieceType> pieceTypes;
    public final String modId;
    final Map<ResourceKey<StructureSet>, Function<BootstrapContext<StructureSet>, StructureSet>> structureSets = new HashMap<>();
    final Map<ResourceKey<Structure>, Function<BootstrapContext<Structure>, Structure>> structures = new HashMap<>();

    public DeferredStructureRegister(String modId) {
        structureTypes = DeferredRegister.create(Registries.STRUCTURE_TYPE, modId);
        pieceTypes = DeferredRegister.create(Registries.STRUCTURE_PIECE, modId);
        this.modId = modId;
    }

    /// Declares a structure set `name`. [DeferredStructureHolder.Builder#build()] records it.
    public DeferredStructureHolder.Builder set(String name) {
        return new DeferredStructureHolder.Builder(this, name);
    }

    public <S extends Structure> DeferredHolder<StructureType<?>, StructureType<S>> structureType(String name, Supplier<StructureType<S>> type) {
        return structureTypes.register(name, type);
    }

    public ResourceKey<StructureSet> registerSet(String name, Function<BootstrapContext<StructureSet>, StructureSet> factory) {
        var key = ResourceKey.create(Registries.STRUCTURE_SET, Identifier.fromNamespaceAndPath(modId, name));
        structureSets.put(key, factory);
        return key;
    }

    public ResourceKey<Structure> registerStructure(String name, Function<BootstrapContext<Structure>, Structure> factory) {
        var key = ResourceKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath(modId, name));
        structures.put(key, factory);
        return key;
    }

    public Stream<ResourceKey<StructureSet>> getAllSets() {
        return structureSets.keySet().stream().sorted();
    }

    public Stream<ResourceKey<Structure>> getAllStructures() {
        return structures.keySet().stream().sorted();
    }

    private void bootstrapStructureSets(BootstrapContext<StructureSet> context) {
        structureSets.forEach((key, factory) -> context.register(key, factory.apply(context)));
    }

    private void bootstrapStructures(BootstrapContext<Structure> context) {
        structures.forEach((key, factory) -> context.register(key, factory.apply(context)));
    }

    /// use [#bootstrap(net.neoforged.neoforge.data.event.GatherDataRegistryEntriesEvent)] instead
    @Deprecated
    public void bootstrap(RegistrySetBuilder builder) {
        builder.add(Registries.STRUCTURE_SET, this::bootstrapStructureSets)
                .add(Registries.STRUCTURE, this::bootstrapStructures);
    }

    /// Adds every declared structure and structure set. Call in datagen.
    public void bootstrap(GatherDataRegistryEntriesEvent event) {
        event.add(Registries.STRUCTURE_SET, this::bootstrapStructureSets)
                .add(Registries.STRUCTURE, this::bootstrapStructures);
    }

    /// Registers the declared structure and piece types. Call from the mod constructor.
    public void register(IEventBus bus) {
        structureTypes.register(bus);
        pieceTypes.register(bus);
    }
}
