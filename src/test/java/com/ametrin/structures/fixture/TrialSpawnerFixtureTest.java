package com.ametrin.structures.fixture;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner.FullConfig;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerConfig;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@ExtendWith(EphemeralTestServerProvider.class)
class TrialSpawnerFixtureTest {
    private static final ResourceKey<TrialSpawnerConfig> NORMAL = config("trial_chamber/melee/zombie/normal");
    private static final ResourceKey<TrialSpawnerConfig> OMINOUS = config("trial_chamber/melee/zombie/ominous");

    @Test
    void theFixtureReadsLikeTheSpawnersOwnData() {
        var json = JsonParser.parseString("{\"type\":\"ametrin_structures:trial_spawner\",\"normal_config\":\"minecraft:trial_chamber/melee/zombie/normal\",\"required_player_range\":20}");
        var fixture = assertInstanceOf(Fixtures.TrialSpawner.class, Fixture.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals(new Fixtures.TrialSpawner(NORMAL, Optional.empty(), FullConfig.DEFAULT.targetCooldownLength(), 20), fixture);
    }

    @Test
    void theSpawnerTakesTheRegisteredConfigs(MinecraftServer server) {
        var configs = server.registryAccess().lookupOrThrow(Registries.TRIAL_SPAWNER_CONFIG);
        var spawner = new TrialSpawnerBlockEntity(BlockPos.ZERO, Blocks.TRIAL_SPAWNER.defaultBlockState());
        var config = new FullConfig(configs.getOrThrow(NORMAL), configs.getOrThrow(OMINOUS), 100, 20);
        Fixtures.TrialSpawner.configure(spawner, config, server.registryAccess());

        var trialSpawner = spawner.getTrialSpawner();
        assertEquals(configs.getOrThrow(NORMAL).value(), trialSpawner.normalConfig());
        assertEquals(configs.getOrThrow(OMINOUS).value(), trialSpawner.ominousConfig());
        assertEquals(20, trialSpawner.getRequiredPlayerRange());
        assertEquals(100, trialSpawner.getTargetCooldownLength());
    }

    private static ResourceKey<TrialSpawnerConfig> config(String path) {
        return ResourceKey.create(Registries.TRIAL_SPAWNER_CONFIG, Identifier.withDefaultNamespace(path));
    }
}
