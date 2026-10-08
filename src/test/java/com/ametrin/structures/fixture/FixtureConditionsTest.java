package com.ametrin.structures.fixture;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class FixtureConditionsTest {
    // Neither checks the level.
    private static final FixtureCondition.Context AT_64 = new FixtureCondition.Context(null, new BlockPos(0, 64, 0));

    @Test
    void conditionsTakeTagsAndNeoForgesOwnAndWriteThemBack(MinecraftServer server) {
        var json = JsonParser.parseString("""
                [
                  {"type": "ametrin_structures:biome", "biomes": "#minecraft:is_forest"},
                  {"type": "ametrin_structures:not", "condition": {"type": "ametrin_structures:dimension", "dimensions": ["minecraft:the_nether"]}},
                  {"type": "ametrin_structures:any_of", "conditions": [{"type": "ametrin_structures:height_range", "min": 0}, {"type": "neoforge:mod_loaded", "modid": "minecraft"}]},
                  {"type": "neoforge:tag_empty", "tag": "c:ingots/silver"}
                ]""");
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var conditions = FixtureCondition.LIST_CODEC.parse(ops, json).getOrThrow();
        assertInstanceOf(FixtureConditions.InBiome.class, conditions.get(0));
        assertInstanceOf(FixtureConditions.NeoForge.class, conditions.get(3));
        assertEquals(json, FixtureCondition.LIST_CODEC.encodeStart(ops, conditions).getOrThrow());
    }

    @Test
    void aHeightRangeIncludesItsEnds() {
        assertTrue(new FixtureConditions.HeightRange(Optional.of(64), Optional.of(64)).test(AT_64));
        assertFalse(new FixtureConditions.HeightRange(Optional.of(65), Optional.empty()).test(AT_64));
        assertTrue(new FixtureConditions.HeightRange(Optional.empty(), Optional.of(64)).test(AT_64));
    }

    @Test
    void anAlternativeDrawsOnlyWhenAllItsConditionsPass() {
        var missingMod = new FixtureConditions.NeoForge(new ModLoadedCondition("not_installed"));
        var low = new FixtureConditions.HeightRange(Optional.empty(), Optional.of(10));
        var alternative = new WeightedFixture(1, Fixtures.Empty.INSTANCE);
        assertTrue(alternative.conditionsPass(AT_64));
        assertFalse(alternative.withConditions(missingMod).conditionsPass(AT_64));
        assertFalse(alternative.withConditions(new FixtureConditions.Not(low), missingMod).conditionsPass(AT_64));
        assertTrue(alternative.withConditions(new FixtureConditions.AnyOf(List.of(missingMod, new FixtureConditions.Not(low)))).conditionsPass(AT_64));
    }

    @Test
    void anAlternativeForAMissingModKeepsItsConditionsSoItIsNeverDrawn(MinecraftServer server) throws Exception {
        var stored = TagParser.parseCompoundFully("""
                {type: "not_installed:crate", weight: 100, conditions: [{type: "neoforge:mod_loaded", modid: "not_installed"}]}""");
        var unreadable = WeightedFixture.decodeLeniently(server.registryAccess().createSerializationContext(NbtOps.INSTANCE), stored);
        assertInstanceOf(Fixture.Unreadable.class, unreadable.fixture());
        assertEquals(1, unreadable.conditions().size());

        var chest = new WeightedFixture(1, Fixtures.Empty.INSTANCE);
        for (int seed = 0; seed < 20; seed++) {
            assertEquals(Optional.of(chest), WeightedFixture.draw(List.of(unreadable, chest), alternative -> alternative.conditionsPass(AT_64), RandomSource.create(seed)));
        }
    }

    @Test
    void anUpsideDownHeightRangeDoesntRead() {
        var json = JsonParser.parseString("""
                {"type": "ametrin_structures:height_range", "min": 10, "max": 0}""");
        assertTrue(FixtureCondition.CODEC.parse(JsonOps.INSTANCE, json).isError());
    }
}
