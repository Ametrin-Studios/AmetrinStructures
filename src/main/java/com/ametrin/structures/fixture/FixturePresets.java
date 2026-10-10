package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASRegistries;
import com.ametrin.structures.util.ASLog;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@ApiStatus.Internal
public final class FixturePresets {
    /// Deeper nesting is treated as a cycle.
    public static final int MAX_DEPTH = 16;

    private FixturePresets() {}

    public static Function<Identifier, Optional<FixturePreset>> lookup(HolderLookup.Provider registries) {
        var presets = registries.lookup(ASRegistries.FIXTURE_PRESET);
        return id -> presets.flatMap(lookup -> lookup.get(ResourceKey.create(ASRegistries.FIXTURE_PRESET, id))).map(Holder::value);
    }

    /// Follows preset alternatives, each drawing among its own that `eligible` accepts, until one that isn't a preset.
    /// The result's generation chance is the product of the chances along the way.
    ///
    /// empty when a preset is missing or nested deeper than [#MAX_DEPTH];
    ///
    /// empty when a preset has no eligible alternatives.
    public static Optional<WeightedFixture> resolve(WeightedFixture drawn, Function<Identifier, Optional<FixturePreset>> presets, Predicate<WeightedFixture> eligible, RandomSource random) {
        var alternative = drawn;
        var chance = drawn.generationChance();
        for (int depth = 0; alternative.fixture() instanceof PresetFixture(var key); depth++) {
            if (depth == MAX_DEPTH) {
                ASLog.warn("fixture presets nest deeper than {}, probably in a cycle, starting at {}", MAX_DEPTH, drawn.fixture());
                return Optional.empty();
            }
            var preset = presets.apply(key.identifier());
            if (preset.isEmpty()) {
                ASLog.warn("unknown fixture preset {}", key.identifier());
                return Optional.empty();
            }
            var next = WeightedFixture.draw(preset.get().fixtures(), eligible, random);
            if (next.isEmpty()) {
                return Optional.empty();
            }
            alternative = next.get();
            chance *= alternative.generationChance();
        }
        return Optional.of(new WeightedFixture(alternative.weight(), chance, alternative.conditions(), alternative.fixture()));
    }

    // Logged when the server starts, since a preset is only read once a structure generates.
    public static void validateOnStart(ServerAboutToStartEvent event) {
        var presets = event.getServer().registryAccess().lookup(ASRegistries.FIXTURE_PRESET)
                .map(lookup -> lookup.listElements().collect(Collectors.toMap(holder -> holder.key().identifier(), Holder::value)))
                .orElseGet(Map::of);
        problems(presets).forEach(problem -> ASLog.warn("fixture preset {}", problem));
    }

    /// What's wrong with the presets: alternatives that don't decode, unknown presets and cycles.
    public static List<String> problems(Map<Identifier, FixturePreset> presets) {
        var problems = new ArrayList<String>();
        presets.forEach((id, preset) -> {
            for (var alternative : preset.fixtures()) {
                switch (alternative.fixture()) {
                    case Fixture.Unreadable unreadable -> problems.add(id + ": " + unreadable.error());
                    case PresetFixture(var key) when !presets.containsKey(key.identifier()) ->
                            problems.add(id + ": unknown fixture preset " + key.identifier());
                    default -> {}
                }
            }
        });
        var visited = new HashSet<Identifier>();
        for (var id : presets.keySet()) {
            findCycle(id, presets, visited, new ArrayList<>()).ifPresent(cycle -> problems.add("cycle " + cycle));
        }
        return problems;
    }

    private static Optional<String> findCycle(Identifier id, Map<Identifier, FixturePreset> presets, Set<Identifier> visited, List<Identifier> path) {
        int start = path.indexOf(id);
        if (start >= 0) {
            var cycle = new ArrayList<>(path.subList(start, path.size()));
            cycle.add(id);
            return Optional.of(cycle.stream().map(Identifier::toString).collect(Collectors.joining(" -> ")));
        }
        if (!visited.add(id)) {
            return Optional.empty();
        }
        path.add(id);
        for (var alternative : presets.getOrDefault(id, new FixturePreset(List.of())).fixtures()) {
            if (alternative.fixture() instanceof PresetFixture(var key)) {
                var cycle = findCycle(key.identifier(), presets, visited, path);
                if (cycle.isPresent()) {
                    return cycle;
                }
            }
        }
        path.removeLast();
        return Optional.empty();
    }
}
