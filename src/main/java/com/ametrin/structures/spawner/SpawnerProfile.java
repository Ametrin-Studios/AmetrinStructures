package com.ametrin.structures.spawner;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.SpawnData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// A spawner placed with a profile applies it every time it loads, so changing the profile updates spawners already generated.
/// Without spawn potentials the spawner keeps the entity it has.
///
/// ```
/// context.register(CRYPT, SpawnerProfile.builder()
///         .spawnCount(2)
///         .add(EntityTypes.ZOMBIE, 3)
///         .add(new SpawnDataBuilder(EntityTypes.SPIDER).passenger(EntityTypes.SKELETON), 1)
///         .build());
/// ```
public record SpawnerProfile(
        int spawnDelay,
        int minSpawnDelay,
        int maxSpawnDelay,
        int spawnCount,
        int maxNearbyEntities,
        int requiredPlayerRange,
        int spawnRange,
        WeightedList<SpawnData> spawnPotentials) {
    /// Vanilla's values, keeping the spawner's entity.
    public static final SpawnerProfile DEFAULT = builder().build();

    private static final Codec<Integer> NON_NEGATIVE_SHORT = Codec.intRange(0, Short.MAX_VALUE);
    private static final Codec<Integer> POSITIVE_SHORT = Codec.intRange(1, Short.MAX_VALUE);

    public static final Codec<SpawnerProfile> CODEC = RecordCodecBuilder.<SpawnerProfile>create(instance -> instance.group(
                            NON_NEGATIVE_SHORT.optionalFieldOf("spawn_delay", DEFAULT.spawnDelay).forGetter(SpawnerProfile::spawnDelay),
                            NON_NEGATIVE_SHORT.optionalFieldOf("min_spawn_delay", DEFAULT.minSpawnDelay).forGetter(SpawnerProfile::minSpawnDelay),
                            NON_NEGATIVE_SHORT.optionalFieldOf("max_spawn_delay", DEFAULT.maxSpawnDelay).forGetter(SpawnerProfile::maxSpawnDelay),
                            POSITIVE_SHORT.optionalFieldOf("spawn_count", DEFAULT.spawnCount).forGetter(SpawnerProfile::spawnCount),
                            POSITIVE_SHORT.optionalFieldOf("max_nearby_entities", DEFAULT.maxNearbyEntities).forGetter(SpawnerProfile::maxNearbyEntities),
                            POSITIVE_SHORT.optionalFieldOf("required_player_range", DEFAULT.requiredPlayerRange).forGetter(SpawnerProfile::requiredPlayerRange),
                            POSITIVE_SHORT.optionalFieldOf("spawn_range", DEFAULT.spawnRange).forGetter(SpawnerProfile::spawnRange),
                            SpawnData.LIST_CODEC.optionalFieldOf("spawn_potentials", WeightedList.of()).forGetter(SpawnerProfile::spawnPotentials))
                    .apply(instance, SpawnerProfile::new))
            .validate(SpawnerProfile::validate);

    public static Builder builder() {
        return new Builder();
    }

    private static DataResult<SpawnerProfile> validate(SpawnerProfile profile) {
        return profile.minSpawnDelay > profile.maxSpawnDelay
                ? DataResult.error(() -> "min_spawn_delay " + profile.minSpawnDelay + " exceeds max_spawn_delay " + profile.maxSpawnDelay)
                : DataResult.success(profile);
    }

    public static final class Builder {
        private int spawnDelay = 20;
        private int minSpawnDelay = 200;
        private int maxSpawnDelay = 800;
        private int spawnCount = 4;
        private int maxNearbyEntities = 6;
        private int requiredPlayerRange = 16;
        private int spawnRange = 4;
        private final List<Weighted<SpawnData>> spawnPotentials = new ArrayList<>();

        private Builder() {}

        public Builder spawnDelay(int spawnDelay) {
            this.spawnDelay = spawnDelay;
            return this;
        }

        public Builder minSpawnDelay(int minSpawnDelay) {
            this.minSpawnDelay = minSpawnDelay;
            return this;
        }

        public Builder maxSpawnDelay(int maxSpawnDelay) {
            this.maxSpawnDelay = maxSpawnDelay;
            return this;
        }

        public Builder spawnCount(int spawnCount) {
            this.spawnCount = spawnCount;
            return this;
        }

        public Builder maxNearbyEntities(int maxNearbyEntities) {
            this.maxNearbyEntities = maxNearbyEntities;
            return this;
        }

        public Builder requiredPlayerRange(int requiredPlayerRange) {
            this.requiredPlayerRange = requiredPlayerRange;
            return this;
        }

        public Builder spawnRange(int spawnRange) {
            this.spawnRange = spawnRange;
            return this;
        }

        public Builder add(EntityType<?> type, int weight) {
            var tag = new CompoundTag();
            tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
            return add(new SpawnData(tag, Optional.empty(), Optional.empty()), weight);
        }

        public Builder add(SpawnDataBuilder data, int weight) {
            return add(data.build(), weight);
        }

        public Builder add(SpawnData data, int weight) {
            spawnPotentials.add(new Weighted<>(data, weight));
            return this;
        }

        public SpawnerProfile build() {
            return new SpawnerProfile(
                    spawnDelay, minSpawnDelay, maxSpawnDelay, spawnCount, maxNearbyEntities, requiredPlayerRange,
                    spawnRange, WeightedList.of(List.copyOf(spawnPotentials)));
        }
    }
}
