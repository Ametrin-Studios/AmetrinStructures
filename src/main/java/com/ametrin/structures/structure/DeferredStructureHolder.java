package com.ametrin.structures.structure;

import com.ametrin.structures.placement.ScatteredGridPlacement;
import com.ametrin.structures.structure.jigsaw.ExtendedJigsawStructure;
import com.ametrin.structures.structure.simple.SimpleStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/// A registered structure set with its structures and piece types. Each is named `<set>/<suffix>`, or `<set>` for the empty suffix.
public class DeferredStructureHolder {
    private final String name;
    private final Map<String, DeferredHolder<StructurePieceType, StructurePieceType>> pieces;
    private final Map<String, ResourceKey<Structure>> structures;
    private final ResourceKey<StructureSet> structureSet;

    private DeferredStructureHolder(
            String name,
            Map<String, DeferredHolder<StructurePieceType, StructurePieceType>> pieces,
            Map<String, ResourceKey<Structure>> structures,
            ResourceKey<StructureSet> structureSet) {
        this.name = name;
        this.pieces = Map.copyOf(pieces);
        this.structures = Map.copyOf(structures);
        this.structureSet = structureSet;
    }

    public String name() {
        return name;
    }

    public DeferredHolder<StructurePieceType, StructurePieceType> pieceType(String suffix) {
        return require(pieces, suffix, "piece type");
    }

    public DeferredHolder<StructurePieceType, StructurePieceType> pieceType() {
        return single(pieces, "piece type");
    }

    public ResourceKey<Structure> structure(String suffix) {
        return require(structures, suffix, "structure");
    }

    public ResourceKey<Structure> structure() {
        return single(structures, "structure");
    }

    public Map<String, ResourceKey<Structure>> structures() {
        return structures;
    }

    public ResourceKey<StructureSet> structureSet() {
        return structureSet;
    }

    private <T> T require(Map<String, T> map, String suffix, String kind) {
        T value = map.get(suffix);
        if (value == null) {
            throw new IllegalArgumentException(
                    "structure set " + name + " has no " + kind + " named " + describe(suffix) + "; known: " + map.keySet());
        }
        return value;
    }

    private <T> T single(Map<String, T> map, String kind) {
        if (map.size() != 1) {
            throw new IllegalStateException("structure set " + name + " declares " + map.size() + " " + kind
                    + " entries, so one must be named explicitly; known: " + map.keySet());
        }
        return map.values().iterator().next();
    }

    private static String describe(String suffix) {
        return suffix.isEmpty() ? "(the bare name)" : suffix;
    }

    static String compose(String name, String suffix) {
        return suffix.isEmpty() ? name : name + "/" + suffix;
    }

    @FunctionalInterface
    public interface StructureFactory {
        Structure create(Structure.StructureSettings settings, BootstrapContext<Structure> context);
    }

    public static class Builder {
        private final DeferredStructureRegister register;
        private final String name;

        private final Map<String, Supplier<StructurePieceType>> pieces = new LinkedHashMap<>();
        private final Map<String, StructureEntryBuilder<?>> structures = new LinkedHashMap<>();
        private ScatteredGridPlacement.Builder grid = ScatteredGridPlacement.builder();
        // Replaces the grid when set.
        private @Nullable Function<BootstrapContext<StructureSet>, StructurePlacement> placement;

        Builder(DeferredStructureRegister register, String name) {
            this.register = register;
            this.name = name;
        }

        public Builder pieceType(String suffix, Supplier<StructurePieceType> type) {
            pieces.put(suffix, type);
            return this;
        }

        public Builder pieceType(Supplier<StructurePieceType> type) {
            return pieceType("", type);
        }

        public Builder simple(String suffix, Consumer<SimpleStructure.Builder> configure) {
            var structure = new SimpleStructure.Builder(register.modId, compose(name, suffix));
            try {
                configure.accept(structure);
            } catch (IllegalArgumentException exception) {
                throw structure.fail(exception.getMessage(), exception);
            }
            return add(suffix, structure);
        }

        public Builder simple(Consumer<SimpleStructure.Builder> configure) {
            return simple("", configure);
        }

        public Builder jigsaw(String suffix, ResourceKey<StructureTemplatePool> pool, Consumer<ExtendedJigsawStructure.Builder> factory, Consumer<CustomStructureBuilder> configure) {
            var id = compose(name, suffix);
            return structure(suffix, (settings, context) -> {
                var builder = ExtendedJigsawStructure.builder(settings, context.lookup(Registries.TEMPLATE_POOL).getOrThrow(pool));
                try {
                    factory.accept(builder);
                } catch (IllegalArgumentException exception) {
                    throw new IllegalStateException("structure " + id + ": " + exception.getMessage(), exception);
                }
                return builder.build();
            }, configure);
        }

        public Builder jigsaw(ResourceKey<StructureTemplatePool> pool, Consumer<ExtendedJigsawStructure.Builder> factory, Consumer<CustomStructureBuilder> configure) {
            return jigsaw("", pool, factory, configure);
        }

        public Builder structure(String suffix, StructureFactory factory, Consumer<CustomStructureBuilder> configure) {
            var structure = new CustomStructureBuilder(compose(name, suffix), factory);
            configure.accept(structure);
            return add(suffix, structure);
        }

        public Builder structure(StructureFactory factory, Consumer<CustomStructureBuilder> configure) {
            return structure("", factory, configure);
        }

        private Builder add(String suffix, StructureEntryBuilder<?> structure) {
            if (structures.putIfAbsent(suffix, structure) != null) {
                throw fail("declares the structure " + describe(suffix) + " twice");
            }
            return this;
        }

        public Builder scatteredGridPlacement(int spacing) {
            return scatteredGridPlacement(ScatteredGridPlacement.builder().spacing(spacing));
        }

        public Builder scatteredGridPlacement(int spacing, float probability) {
            return scatteredGridPlacement(ScatteredGridPlacement.builder(spacing, probability));
        }

        /// The salt defaults to one derived from the set's id.
        public Builder scatteredGridPlacement(UnaryOperator<ScatteredGridPlacement.Builder> configure) {
            return scatteredGridPlacement(configure.apply(ScatteredGridPlacement.builder()));
        }

        /// The salt defaults to one derived from the set's id.
        public Builder scatteredGridPlacement(ScatteredGridPlacement.Builder grid) {
            this.grid = grid;
            this.placement = null;
            return this;
        }

        public Builder horizontalPlacement(StructurePlacement placement) {
            return horizontalPlacement(_ -> placement);
        }

        /// For placements that need registry entries, such as the preferred biomes of concentric rings.
        public Builder horizontalPlacement(Function<BootstrapContext<StructureSet>, StructurePlacement> placement) {
            this.placement = placement;
            return this;
        }

        public DeferredStructureHolder build() {
            if (structures.isEmpty()) {
                throw fail("has no structures");
            }
            structures.values().forEach(StructureEntryBuilder::validate);
            var resolvedPlacement = resolvePlacement();

            var pieceHolders = new LinkedHashMap<String, DeferredHolder<StructurePieceType, StructurePieceType>>();
            pieces.forEach((suffix, type) -> pieceHolders.put(suffix, register.pieceTypes.register(compose(name, suffix), type)));

            var structureKeys = new LinkedHashMap<String, ResourceKey<Structure>>();
            var entries = Map.copyOf(structures);
            entries.forEach((suffix, structure) -> structureKeys.put(
                    suffix, register.registerStructure(compose(name, suffix), structure::bootstrap)));

            var setKey = register.registerSet(name, context -> {
                var lookup = context.lookup(Registries.STRUCTURE);
                var selection = structureKeys.entrySet().stream().sorted(Map.Entry.comparingByValue())
                        .map(entry -> new StructureSet.StructureSelectionEntry(
                                lookup.getOrThrow(entry.getValue()), entries.get(entry.getKey()).weight()))
                        .toList();
                return new StructureSet(selection, resolvedPlacement.apply(context));
            });

            return new DeferredStructureHolder(name, pieceHolders, structureKeys, setKey);
        }

        private Function<BootstrapContext<StructureSet>, StructurePlacement> resolvePlacement() {
            if (placement != null) {
                return placement;
            }
            try {
                var built = grid.saltIfUnset(register.modId, name).build();
                return _ -> built;
            } catch (IllegalStateException exception) {
                throw fail(exception.getMessage(), exception);
            }
        }

        private IllegalStateException fail(String message) {
            return new IllegalStateException("structure set " + name + " " + message);
        }

        private IllegalStateException fail(String message, Throwable cause) {
            return new IllegalStateException("structure set " + name + ": " + message, cause);
        }
    }

    public static final class CustomStructureBuilder extends StructureEntryBuilder<CustomStructureBuilder> {
        private final StructureFactory factory;

        private CustomStructureBuilder(String id, StructureFactory factory) {
            super(id);
            this.factory = factory;
        }

        @Override
        protected CustomStructureBuilder self() {
            return this;
        }

        @Override
        protected Structure create(Structure.StructureSettings settings, BootstrapContext<Structure> context) {
            return factory.create(settings, context);
        }
    }
}
