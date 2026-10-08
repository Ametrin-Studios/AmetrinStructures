package com.ametrin.structures.spawner;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.registry.ASAttachments;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/// A spawner carrying a spawner profile takes its settings from the profile when it loads.
@ExtendWith(EphemeralTestServerProvider.class)
class SpawnerProfileTest {
    private static final BlockState SPAWNER = Blocks.SPAWNER.defaultBlockState();

    @Test
    void loadingAppliesTheResolvedType(MinecraftServer server) {
        SpawnerBlockEntity spawner = new SpawnerBlockEntity(BlockPos.ZERO, SPAWNER);
        spawner.setData(ASAttachments.SPAWNER_PROFILE, new SpawnerProfileAttachment(AmetrinStructures.locate("undead")));

        CompoundTag saved = spawner.saveWithFullMetadata(server.registryAccess());
        BlockEntity loaded = BlockEntity.loadStatic(BlockPos.ZERO, SPAWNER, saved, server.registryAccess());

        SpawnerBlockEntity reloaded = assertInstanceOf(SpawnerBlockEntity.class, loaded);
        List<String> potentials = reloaded.getSpawner().spawnPotentials.unwrap().stream()
                .map(SpawnerProfileTest::describe)
                .toList();
        assertEquals(List.of("minecraft:zombie x2", "minecraft:skeleton x1"), potentials);
    }

    @Test
    void loadingKeepsTheSavedCountdown(MinecraftServer server) {
        SpawnerBlockEntity spawner = new SpawnerBlockEntity(BlockPos.ZERO, SPAWNER);
        spawner.setData(ASAttachments.SPAWNER_PROFILE, new SpawnerProfileAttachment(AmetrinStructures.locate("undead")));
        spawner.getSpawner().spawnDelay = 150;

        CompoundTag saved = spawner.saveWithFullMetadata(server.registryAccess());
        var reloaded = (SpawnerBlockEntity) BlockEntity.loadStatic(BlockPos.ZERO, SPAWNER, saved, server.registryAccess());

        assertEquals(150, reloaded.getSpawner().spawnDelay);
    }

    @Test
    void clientsGetTheNextSpawnAfterALoad(MinecraftServer server) {
        var reloaded = reload(server, new SpawnerBlockEntity(BlockPos.ZERO, SPAWNER));
        var updateTag = reloaded.getUpdateTag(server.registryAccess());
        var next = updateTag.getCompound("SpawnData").flatMap(data -> data.getCompound("entity")).flatMap(entity -> entity.getString("id"));
        assertTrue(next.isPresent(), "update tag without the next spawn: " + updateTag);
        assertTrue(reloaded.getSpawner().spawnPotentials.contains(SpawnerAccess.nextSpawnData(reloaded.getSpawner())));
    }

    @Test
    void loadingKeepsTheNextSpawnTheProfileStillOffers(MinecraftServer server) {
        var first = reload(server, new SpawnerBlockEntity(BlockPos.ZERO, SPAWNER));
        var next = SpawnerAccess.nextSpawnData(first.getSpawner());
        for (int i = 0; i < 8; i++) {
            assertEquals(next, SpawnerAccess.nextSpawnData(reload(server, first).getSpawner()));
        }
    }

    // A spawner with the undead profile, saved and loaded again.
    private static SpawnerBlockEntity reload(MinecraftServer server, SpawnerBlockEntity spawner) {
        spawner.setData(ASAttachments.SPAWNER_PROFILE, new SpawnerProfileAttachment(AmetrinStructures.locate("undead")));
        CompoundTag saved = spawner.saveWithFullMetadata(server.registryAccess());
        return (SpawnerBlockEntity) BlockEntity.loadStatic(BlockPos.ZERO, SPAWNER, saved, server.registryAccess());
    }

    @Test
    void omittedFieldsKeepVanillasValues() {
        var profile = parse("{\"spawn_count\": 2}").getOrThrow();
        assertEquals(2, profile.spawnCount());
        assertEquals(SpawnerProfile.DEFAULT.maxNearbyEntities(), profile.maxNearbyEntities());
        assertTrue(profile.spawnPotentials().isEmpty());
    }

    @Test
    void valuesMustFitTheShortsASpawnerSaves() {
        assertTrue(parse("{\"max_spawn_delay\": 40000}").isError());
        assertTrue(parse("{\"max_spawn_delay\": 32767}").isSuccess());
    }

    @Test
    void theMinimumDelayCantExceedTheMaximum() {
        assertTrue(parse("{\"min_spawn_delay\": 900, \"max_spawn_delay\": 100}").isError());
    }

    private static DataResult<SpawnerProfile> parse(String json) {
        return SpawnerProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static String describe(Weighted<SpawnData> entry) {
        return entry.value().entityToSpawn().getStringOr("id", "?") + " x" + entry.weight();
    }
}
