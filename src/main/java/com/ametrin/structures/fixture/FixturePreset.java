package com.ametrin.structures.fixture;

import com.ametrin.structures.spawner.EntityDataBuilder;
import com.ametrin.structures.spawner.SpawnerProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

/// A reusable, data driven weighted list of fixture alternatives
///
/// ```
/// context.register(DUNGEON_CHEST, FixturePreset.builder()
///         .add(3, Fixtures.LootContainer.chest(BuiltInLootTables.SIMPLE_DUNGEON))
///         .add(1, Fixtures.Empty.INSTANCE)
///         .build());
/// ```
public record FixturePreset(List<WeightedFixture> fixtures) {
    public static final Codec<FixturePreset> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    WeightedFixture.LIST_CODEC.fieldOf("fixtures").forGetter(FixturePreset::fixtures))
            .apply(instance, FixturePreset::new));

    public FixturePreset {
        fixtures = List.copyOf(fixtures);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<WeightedFixture> fixtures = new ArrayList<>();

        private Builder() {}

        public Builder add(int weight, Fixture fixture) {
            return add(new WeightedFixture(weight, fixture));
        }

        public Builder add(int weight, Fixture fixture, FixtureCondition... conditions) {
            return add(new WeightedFixture(weight, fixture).withConditions(conditions));
        }

        public Builder add(WeightedFixture fixture) {
            fixtures.add(fixture);
            return this;
        }

        /// Draws from another preset.
        public Builder add(int weight, ResourceKey<FixturePreset> preset) {
            return add(weight, new Fixtures.Preset(preset));
        }

        public Builder entity(int weight, EntityType<?> entity) {
            return add(weight, Fixtures.SpawnEntity.of(entity));
        }

        public Builder entity(int weight, EntityDataBuilder entity) {
            return add(weight, Fixtures.SpawnEntity.of(entity));
        }

        /// A spawner block for `entity` with vanilla's settings, see [Fixtures.Spawner#of(EntityType)].
        public Builder spawner(int weight, EntityType<?> entity) {
            return add(weight, Fixtures.Spawner.of(entity));
        }

        /// A spawner block that takes its settings from `profile`.
        public Builder spawner(int weight, ResourceKey<SpawnerProfile> profile) {
            return add(weight, new Fixtures.ProfileSpawner(profile, false));
        }

        public FixturePreset build() {
            return new FixturePreset(fixtures);
        }
    }
}
