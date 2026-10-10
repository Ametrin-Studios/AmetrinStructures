package com.ametrin.structures.fixture;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class VaultFixtureTest {
    @Test
    void theFixtureReadsWithDefaults() {
        var json = JsonParser.parseString("{\"type\":\"ametrin_structures:vault\",\"ominous\":true}");
        var fixture = assertInstanceOf(VaultFixture.class, Fixture.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals(new VaultFixture(true), fixture);
    }

    @Test
    void aNormalVaultTakesTheTrialChambersReward() {
        var config = new VaultFixture(false).config();
        assertEquals(BuiltInLootTables.TRIAL_CHAMBERS_REWARD, config.lootTable());
        assertTrue(config.keyItem().is(Items.TRIAL_KEY));
        assertEquals(Optional.empty(), config.overrideLootTableToDisplay());
    }

    @Test
    void anOminousVaultTakesTheOminousRewardAndKey() {
        var config = new VaultFixture(true).config();
        assertEquals(BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS, config.lootTable());
        assertTrue(config.keyItem().is(Items.OMINOUS_TRIAL_KEY));
    }

    @Test
    void anExplicitLootTableWins() {
        var config = new VaultFixture(true, BuiltInLootTables.SIMPLE_DUNGEON).config();
        assertEquals(BuiltInLootTables.SIMPLE_DUNGEON, config.lootTable());
        assertTrue(config.keyItem().is(Items.OMINOUS_TRIAL_KEY));
    }
}
