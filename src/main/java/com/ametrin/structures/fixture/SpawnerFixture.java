package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASFixtures;
import com.ametrin.structures.spawner.SpawnerAccess;
import com.ametrin.structures.spawner.SpawnerProfile;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.SpawnData;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/// A spawner block or a spawner minecart. Setting `max_block_light` or `max_sky_light` replaces the
/// entity's own spawn rules with light ranges from 0 to those values, so the spawner works where the
/// entity wouldn't spawn naturally. For settings shared by many spawners, or for several entities,
/// use a [SpawnerProfileFixture].
public record SpawnerFixture(
        Optional<ResourceKey<EntityType<?>>> entity,
        boolean minecart,
        int spawnCount,
        int maxNearbyEntities,
        int requiredPlayerRange,
        int spawnRange,
        int spawnDelay,
        int minSpawnDelay,
        int maxSpawnDelay,
        Optional<Integer> maxBlockLight,
        Optional<Integer> maxSkyLight) implements Fixture {
    private static final int SPAWN_DELAY_LIMIT = 72_000;

    public static final FixtureField<Optional<ResourceKey<EntityType<?>>>> ENTITY = FixtureField.optional("entity", FieldType.registryKey(Registries.ENTITY_TYPE));
    public static final FixtureField<Boolean> MINECART = FixtureField.withDefault("minecart", FieldType.bool(), false);
    public static final FixtureField<Integer> SPAWN_COUNT = FixtureField.withDefault("spawn_count", FieldType.integer(1, 64), SpawnerProfile.DEFAULT.spawnCount());
    public static final FixtureField<Integer> MAX_NEARBY_ENTITIES = FixtureField.withDefault("max_nearby_entities", FieldType.integer(1, 256), SpawnerProfile.DEFAULT.maxNearbyEntities());
    public static final FixtureField<Integer> REQUIRED_PLAYER_RANGE = FixtureField.withDefault("required_player_range", FieldType.integer(1, 128), SpawnerProfile.DEFAULT.requiredPlayerRange());
    public static final FixtureField<Integer> SPAWN_RANGE = FixtureField.withDefault("spawn_range", FieldType.integer(1, 32), SpawnerProfile.DEFAULT.spawnRange());
    public static final FixtureField<Integer> SPAWN_DELAY = FixtureField.withDefault("spawn_delay", FieldType.integer(0, SPAWN_DELAY_LIMIT), SpawnerProfile.DEFAULT.spawnDelay());
    public static final FixtureField<Integer> MIN_SPAWN_DELAY = FixtureField.withDefault("min_spawn_delay", FieldType.integer(0, SPAWN_DELAY_LIMIT), SpawnerProfile.DEFAULT.minSpawnDelay());
    public static final FixtureField<Integer> MAX_SPAWN_DELAY = FixtureField.withDefault("max_spawn_delay", FieldType.integer(0, SPAWN_DELAY_LIMIT), SpawnerProfile.DEFAULT.maxSpawnDelay());
    public static final FixtureField<Optional<Integer>> MAX_BLOCK_LIGHT = FixtureField.optional("max_block_light", FieldType.integer(0, 15));
    public static final FixtureField<Optional<Integer>> MAX_SKY_LIGHT = FixtureField.optional("max_sky_light", FieldType.integer(0, 15));
    public static final List<FixtureField<?>> FIELDS = List.of(
            ENTITY, MINECART, SPAWN_COUNT, MAX_NEARBY_ENTITIES, REQUIRED_PLAYER_RANGE, SPAWN_RANGE, SPAWN_DELAY,
            MIN_SPAWN_DELAY, MAX_SPAWN_DELAY, MAX_BLOCK_LIGHT, MAX_SKY_LIGHT);
    public static final MapCodec<SpawnerFixture> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ENTITY.forGetter(SpawnerFixture::entity),
                    MINECART.forGetter(SpawnerFixture::minecart),
                    SPAWN_COUNT.forGetter(SpawnerFixture::spawnCount),
                    MAX_NEARBY_ENTITIES.forGetter(SpawnerFixture::maxNearbyEntities),
                    REQUIRED_PLAYER_RANGE.forGetter(SpawnerFixture::requiredPlayerRange),
                    SPAWN_RANGE.forGetter(SpawnerFixture::spawnRange),
                    SPAWN_DELAY.forGetter(SpawnerFixture::spawnDelay),
                    MIN_SPAWN_DELAY.forGetter(SpawnerFixture::minSpawnDelay),
                    MAX_SPAWN_DELAY.forGetter(SpawnerFixture::maxSpawnDelay),
                    MAX_BLOCK_LIGHT.forGetter(SpawnerFixture::maxBlockLight),
                    MAX_SKY_LIGHT.forGetter(SpawnerFixture::maxSkyLight))
            .apply(instance, SpawnerFixture::new));

    /// A spawner block for `entity` with vanilla's settings.
    public static SpawnerFixture of(EntityType<?> entity) {
        var defaults = SpawnerProfile.DEFAULT;
        return new SpawnerFixture(Optional.of(BuiltInRegistries.ENTITY_TYPE.getResourceKey(entity).orElseThrow()), false,
                defaults.spawnCount(), defaults.maxNearbyEntities(), defaults.requiredPlayerRange(), defaults.spawnRange(),
                defaults.spawnDelay(), defaults.minSpawnDelay(), defaults.maxSpawnDelay(), Optional.empty(), Optional.empty());
    }

    @Override
    public void apply(FixtureContext context) {
        var spawned = entity.flatMap(Fixtures::entityType).orElse(null);
        var profile = SpawnerProfile.builder()
                .spawnCount(spawnCount)
                .maxNearbyEntities(maxNearbyEntities)
                .requiredPlayerRange(requiredPlayerRange)
                .spawnRange(spawnRange)
                .spawnDelay(spawnDelay)
                .minSpawnDelay(minSpawnDelay)
                // The spawner draws its delay between the two, so the maximum can't be lower.
                .maxSpawnDelay(Math.max(minSpawnDelay, maxSpawnDelay))
                .build();
        Fixtures.placeSpawner(context, minecart, (_, spawner) -> {
            SpawnerAccess.applyWithSpawnDelay(spawner, profile);
            if (spawned != null) {
                // No level here. The spawner would read the block back from the server level to notify clients.
                // On a worldgen thread that waits for the main thread, which is waiting for this chunk. Clients get
                // the spawner with its chunk anyway.
                spawner.setEntityId(spawned, null, context.random(), context.actionBlockPos());
            }
            var next = SpawnerAccess.nextSpawnData(spawner);
            if (next != null && (maxBlockLight.isPresent() || maxSkyLight.isPresent())) {
                var rules = new SpawnData.CustomSpawnRules(lightRange(maxBlockLight), lightRange(maxSkyLight));
                SpawnerAccess.setNextSpawnData(spawner, new SpawnData(next.entityToSpawn(), Optional.of(rules), next.equipment()));
            }
        });
    }

    private static InclusiveRange<Integer> lightRange(Optional<Integer> max) {
        return new InclusiveRange<>(0, max.orElse(15));
    }

    @Override
    public Stream<ResourceKey<?>> references() {
        return Fixtures.present(entity);
    }

    @Override
    public FixtureType type() {
        return ASFixtures.SPAWNER.get();
    }
}
