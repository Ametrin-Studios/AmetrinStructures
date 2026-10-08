package com.ametrin.structures.spawner;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.SpawnData;
import org.jspecify.annotations.Nullable;

public final class SpawnerAccess {
    /// Applies everything but the countdown to the next spawn, which the spawner saves and keeps across loads.
    public static void apply(BaseSpawner spawner, SpawnerProfile profile) {
        spawner.minSpawnDelay = profile.minSpawnDelay();
        spawner.maxSpawnDelay = profile.maxSpawnDelay();
        spawner.spawnCount = profile.spawnCount();
        spawner.maxNearbyEntities = profile.maxNearbyEntities();
        spawner.requiredPlayerRange = profile.requiredPlayerRange();
        spawner.spawnRange = profile.spawnRange();
        if (!profile.spawnPotentials().isEmpty()) {
            spawner.spawnPotentials = profile.spawnPotentials();
            // Clients only get the next spawn, not the potentials, so it must never be left empty or
            // the spawner shows no entity and no particles until it spawns. Keep it while the profile
            // still offers it, which also keeps it stable across loads.
            if (spawner.nextSpawnData == null || !profile.spawnPotentials().contains(spawner.nextSpawnData)) {
                spawner.nextSpawnData = profile.spawnPotentials().getRandomOrThrow(RandomSource.create());
            }
        }
    }

    public static void applyWithSpawnDelay(BaseSpawner spawner, SpawnerProfile profile) {
        apply(spawner, profile);
        spawner.spawnDelay = profile.spawnDelay();
    }

    public static @Nullable SpawnData nextSpawnData(BaseSpawner spawner) {
        return spawner.nextSpawnData;
    }

    public static void setNextSpawnData(BaseSpawner spawner, @Nullable SpawnData data) {
        spawner.nextSpawnData = data;
    }
}
