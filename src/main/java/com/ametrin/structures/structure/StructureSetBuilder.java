package com.ametrin.structures.structure;

import com.ametrin.structures.placement.EvenSpreadPlacement;
import com.ametrin.structures.placement.ScatteredGridPlacement;
import com.ametrin.structures.structure.jigsaw.ExtendedJigsawStructure;
import com.ametrin.structures.structure.simple.SimpleStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/// A structure set and its structures, for [StructureBootstrap#registerSet]. Each structure is named `<set>/<suffix>`, or `<set>` for the empty suffix.
public final class StructureSetBuilder {
    private final String namespace;
    private final String name;

    private final Map<String, StructureEntryBuilder<?>> structures = new LinkedHashMap<>();
    // Registered elsewhere, with their weights.
    private final Map<ResourceKey<Structure>, Integer> existing = new LinkedHashMap<>();
    // Takes the namespace and set name, for the default salt.
    private BiFunction<String, String, StructurePlacement> saltedPlacement = EvenSpreadPlacement.builder()::build;
    // Replaces the salted placement when set.
    private @Nullable Function<BootstrapContext<StructureSet>, StructurePlacement> placement;

    StructureSetBuilder(String namespace, String name) {
        this.namespace = namespace;
        this.name = name;
    }

    @FunctionalInterface
    public interface StructureFactory {
        Structure create(Structure.StructureSettings settings, BootstrapContext<Structure> context);
    }

    /// For an [ExtendedStructure], which takes the filters and processors set on the builder.
    @FunctionalInterface
    public interface ExtendedStructureFactory {
        ExtendedStructure create(Structure.StructureSettings settings, ExtendedStructureSettings extendedSettings, BootstrapContext<Structure> context);
    }

    public StructureSetBuilder simple(String suffix, Consumer<SimpleStructure.Builder> configure) {
        return add(suffix, simpleEntry(namespace, compose(suffix), configure));
    }

    public StructureSetBuilder simple(Consumer<SimpleStructure.Builder> configure) {
        return simple("", configure);
    }

    public StructureSetBuilder jigsaw(String suffix, ResourceKey<StructureTemplatePool> pool, Consumer<ExtendedJigsawStructure.Builder> factory, Consumer<CustomStructureBuilder> configure) {
        return add(suffix, jigsawEntry(compose(suffix), pool, factory, configure));
    }

    public StructureSetBuilder jigsaw(ResourceKey<StructureTemplatePool> pool, Consumer<ExtendedJigsawStructure.Builder> factory, Consumer<CustomStructureBuilder> configure) {
        return jigsaw("", pool, factory, configure);
    }

    /// For a structure without filters or processors; see [#structure(String, ExtendedStructureFactory, Consumer)].
    public StructureSetBuilder structure(String suffix, StructureFactory factory, Consumer<CustomStructureBuilder> configure) {
        return add(suffix, customEntry(compose(suffix), factory, configure));
    }

    public StructureSetBuilder structure(StructureFactory factory, Consumer<CustomStructureBuilder> configure) {
        return structure("", factory, configure);
    }

    public StructureSetBuilder structure(String suffix, ExtendedStructureFactory factory, Consumer<CustomStructureBuilder> configure) {
        return add(suffix, customEntry(compose(suffix), factory, configure));
    }

    public StructureSetBuilder structure(ExtendedStructureFactory factory, Consumer<CustomStructureBuilder> configure) {
        return structure("", factory, configure);
    }

    /// Adds a structure registered elsewhere, like a vanilla one or one from [StructureBootstrap#registerSimple].
    public StructureSetBuilder existing(ResourceKey<Structure> structure) {
        return existing(structure, 1);
    }

    /// @param weight relative chance to be tried first among the set's structures
    public StructureSetBuilder existing(ResourceKey<Structure> structure, int weight) {
        if (weight < 1) {
            throw fail("gives " + structure.identifier() + " weight " + weight + ", which must be at least 1");
        }
        if (existing.putIfAbsent(structure, weight) != null) {
            throw fail("adds " + structure.identifier() + " twice");
        }
        return this;
    }

    private StructureSetBuilder add(String suffix, StructureEntryBuilder<?> structure) {
        if (structures.putIfAbsent(suffix, structure) != null) {
            throw fail("declares the structure " + StructureSetKeys.describe(suffix) + " twice");
        }
        return this;
    }

    static SimpleStructure.Builder simpleEntry(String namespace, String id, Consumer<SimpleStructure.Builder> configure) {
        var structure = new SimpleStructure.Builder(namespace, id);
        try {
            configure.accept(structure);
        } catch (IllegalArgumentException exception) {
            throw structure.fail(exception.getMessage(), exception);
        }
        return structure;
    }

    static CustomStructureBuilder jigsawEntry(
            String id, ResourceKey<StructureTemplatePool> pool, Consumer<ExtendedJigsawStructure.Builder> factory, Consumer<CustomStructureBuilder> configure) {
        return customEntry(id, (settings, extendedSettings, context) -> {
            var builder = ExtendedJigsawStructure.builder(settings, extendedSettings, context.lookup(Registries.TEMPLATE_POOL).getOrThrow(pool));
            try {
                factory.accept(builder);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("structure " + id + ": " + exception.getMessage(), exception);
            }
            return builder.build();
        }, configure);
    }

    static CustomStructureBuilder customEntry(String id, StructureFactory factory, Consumer<CustomStructureBuilder> configure) {
        var structure = new CustomStructureBuilder(id, (settings, _, context) -> factory.create(settings, context), false);
        configure.accept(structure);
        return structure;
    }

    static CustomStructureBuilder customEntry(String id, ExtendedStructureFactory factory, Consumer<CustomStructureBuilder> configure) {
        var structure = new CustomStructureBuilder(id, factory::create, true);
        configure.accept(structure);
        return structure;
    }

    public StructureSetBuilder scatteredGridPlacement(int spacing) {
        return scatteredGridPlacement(ScatteredGridPlacement.builder().spacing(spacing));
    }

    public StructureSetBuilder scatteredGridPlacement(int spacing, float probability) {
        return scatteredGridPlacement(ScatteredGridPlacement.builder(spacing, probability));
    }

    /// The salt defaults to one derived from the set's id.
    public StructureSetBuilder scatteredGridPlacement(UnaryOperator<ScatteredGridPlacement.Builder> configure) {
        return scatteredGridPlacement(configure.apply(ScatteredGridPlacement.builder()));
    }

    /// The salt defaults to one derived from the set's id.
    public StructureSetBuilder scatteredGridPlacement(ScatteredGridPlacement.Builder grid) {
        this.saltedPlacement = grid::build;
        this.placement = null;
        return this;
    }

    public StructureSetBuilder evenSpreadPlacement(int minDistance) {
        return evenSpreadPlacement(EvenSpreadPlacement.builder().minDistance(minDistance));
    }

    public StructureSetBuilder evenSpreadPlacement(int minDistance, float probability) {
        return evenSpreadPlacement(EvenSpreadPlacement.builder(minDistance, probability));
    }

    /// The salt defaults to one derived from the set's id.
    public StructureSetBuilder evenSpreadPlacement(UnaryOperator<EvenSpreadPlacement.Builder> configure) {
        return evenSpreadPlacement(configure.apply(EvenSpreadPlacement.builder()));
    }

    /// The salt defaults to one derived from the set's id.
    public StructureSetBuilder evenSpreadPlacement(EvenSpreadPlacement.Builder evenSpread) {
        this.saltedPlacement = evenSpread::build;
        this.placement = null;
        return this;
    }

    public StructureSetBuilder horizontalPlacement(StructurePlacement placement) {
        return horizontalPlacement(_ -> placement);
    }

    /// For placements that need registry entries, such as the preferred biomes of concentric rings.
    public StructureSetBuilder horizontalPlacement(Function<BootstrapContext<StructureSet>, StructurePlacement> placement) {
        this.placement = placement;
        return this;
    }

    record Declared(
            StructureSetKeys keys,
            Map<ResourceKey<Structure>, Function<BootstrapContext<Structure>, Structure>> structures,
            Function<BootstrapContext<StructureSet>, StructureSet> set) {}

    Declared declare() {
        if (structures.isEmpty() && existing.isEmpty()) {
            throw fail("has no structures");
        }
        structures.values().forEach(StructureEntryBuilder::validate);
        var resolvedPlacement = resolvePlacement();

        var keys = new LinkedHashMap<String, ResourceKey<Structure>>();
        var bootstraps = new LinkedHashMap<ResourceKey<Structure>, Function<BootstrapContext<Structure>, Structure>>();
        var weights = new LinkedHashMap<>(existing);
        structures.forEach((suffix, structure) -> {
            var key = ResourceKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath(namespace, compose(suffix)));
            if (existing.containsKey(key)) {
                throw fail("declares " + key.identifier() + " and also adds it as an existing structure");
            }
            keys.put(suffix, key);
            bootstraps.put(key, structure::bootstrap);
            weights.put(key, structure.weight());
        });

        Function<BootstrapContext<StructureSet>, StructureSet> set = context -> {
            var lookup = context.lookup(Registries.STRUCTURE);
            var selection = weights.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(entry -> new StructureSet.StructureSelectionEntry(lookup.getOrThrow(entry.getKey()), entry.getValue()))
                    .toList();
            return new StructureSet(selection, resolvedPlacement.apply(context));
        };
        var setKey = ResourceKey.create(Registries.STRUCTURE_SET, Identifier.fromNamespaceAndPath(namespace, name));
        return new Declared(new StructureSetKeys(setKey, keys, List.copyOf(existing.keySet())), bootstraps, set);
    }

    private Function<BootstrapContext<StructureSet>, StructurePlacement> resolvePlacement() {
        if (placement != null) {
            return placement;
        }
        try {
            var built = saltedPlacement.apply(namespace, name);
            return _ -> built;
        } catch (IllegalStateException exception) {
            throw fail(exception.getMessage(), exception);
        }
    }

    private String compose(String suffix) {
        return suffix.isEmpty() ? name : name + "/" + suffix;
    }

    private IllegalStateException fail(String message) {
        return new IllegalStateException("structure set " + name + " " + message);
    }

    private IllegalStateException fail(String message, Throwable cause) {
        return new IllegalStateException("structure set " + name + ": " + message, cause);
    }
}
