package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASRegistries;
import com.ametrin.structures.util.ASLog;
import com.ametrin.structures.util.RegistryConditionContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.common.conditions.ICondition;
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

    public static Function<ResourceLocation, Optional<FixturePreset>> lookup(HolderLookup.Provider registries) {
        var presets = registries.lookup(ASRegistries.FIXTURE_PRESET);
        return id -> presets.flatMap(lookup -> lookup.get(ResourceKey.create(ASRegistries.FIXTURE_PRESET, id))).map(Holder::value);
    }

    /// Follows presets, each drawing from its alternatives that `eligible` accepts, until it reaches one
    /// that isn't a preset. The result's generation chance is the product of all chances along the way.
    ///
    /// Empty when a preset is missing, has no eligible alternatives, or is nested deeper than [#MAX_DEPTH].
    public static Optional<WeightedFixture> resolve(WeightedFixture drawn, Function<ResourceLocation, Optional<FixturePreset>> presets, Predicate<WeightedFixture> eligible, RandomSource random) {
        var alternative = drawn;
        var chance = drawn.generationChance();
        for (int depth = 0; alternative.fixture() instanceof PresetFixture(var key); depth++) {
            if (depth == MAX_DEPTH) {
                ASLog.warn("fixture presets nest deeper than {}, probably in a cycle, starting at {}", MAX_DEPTH, drawn.fixture());
                return Optional.empty();
            }
            var preset = presets.apply(key.location());
            if (preset.isEmpty()) {
                ASLog.warn("unknown fixture preset {}", key.location());
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

    // Logged when the server starts, since presets are otherwise only read when a structure generates.
    public static void validateOnStart(ServerAboutToStartEvent event) {
        var presets = event.getServer().registryAccess().lookup(ASRegistries.FIXTURE_PRESET)
                .map(lookup -> lookup.listElements().collect(Collectors.toMap(holder -> holder.key().location(), Holder::value)))
                .orElseGet(Map::of);
        problems(presets, new RegistryConditionContext(event.getServer().registryAccess())).forEach(problem -> ASLog.warn("fixture preset {}", problem));
    }

    public static List<String> problems(Map<ResourceLocation, FixturePreset> presets, ICondition.IContext conditions) {
        var problems = new ArrayList<String>();
        presets.forEach((id, preset) -> {
            for (var alternative : preset.fixtures()) {
                if (alternative.neverPasses(conditions)) {
                    continue;
                }
                switch (alternative.fixture()) {
                    case Fixture.Unreadable unreadable -> problems.add(id + ": " + unreadable.error());
                    case PresetFixture(var key) when !presets.containsKey(key.location()) ->
                            problems.add(id + ": unknown fixture preset " + key.location());
                    default -> {}
                }
            }
        });
        var visited = new HashSet<ResourceLocation>();
        for (var id : presets.keySet()) {
            findCycle(id, presets, visited, new ArrayList<>()).ifPresent(cycle -> problems.add("cycle " + cycle));
        }
        return problems;
    }

    private static Optional<String> findCycle(ResourceLocation id, Map<ResourceLocation, FixturePreset> presets, Set<ResourceLocation> visited, List<ResourceLocation> path) {
        int start = path.indexOf(id);
        if (start >= 0) {
            var cycle = new ArrayList<>(path.subList(start, path.size()));
            cycle.add(id);
            return Optional.of(cycle.stream().map(ResourceLocation::toString).collect(Collectors.joining(" -> ")));
        }
        if (!visited.add(id)) {
            return Optional.empty();
        }
        path.add(id);
        for (var alternative : presets.getOrDefault(id, new FixturePreset(List.of())).fixtures()) {
            if (alternative.fixture() instanceof PresetFixture(var key)) {
                var cycle = findCycle(key.location(), presets, visited, path);
                if (cycle.isPresent()) {
                    return cycle;
                }
            }
        }
        path.removeLast();
        return Optional.empty();
    }
}
