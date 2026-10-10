package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASRegistries;
import com.ametrin.structures.spawner.EntityDataBuilder;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class FixturePresetsTest {
    private static final WeightedFixture CHEST = new WeightedFixture(1, LootContainerFixture.chest(BuiltInLootTables.SIMPLE_DUNGEON));

    @Test
    void aFixtureThatIsntAPresetPassesThrough() {
        assertEquals(Optional.of(CHEST), resolve(CHEST, Map.of()));
    }

    @Test
    void nestedPresetsEachDrawAmongTheirOwn() {
        var presets = Map.of(
                id("outer"), new FixturePreset(List.of(preset("inner"))),
                id("inner"), new FixturePreset(List.of(CHEST)));
        assertEquals(Optional.of(CHEST), resolve(preset("outer"), presets));
    }

    @Test
    void chancesAlongTheWayMultiply() {
        var presets = Map.of(id("inner"), new FixturePreset(List.of(new WeightedFixture(1, 0.5F, CHEST.fixture()))));
        var drawn = resolve(new WeightedFixture(1, 0.5F, new PresetFixture(key("inner"))), presets).orElseThrow();
        assertEquals(0.25F, drawn.generationChance());
    }

    @Test
    void anUnknownPresetResolvesToNothing() {
        assertEquals(Optional.empty(), resolve(preset("missing"), Map.of()));
    }

    @Test
    void aCycleResolvesToNothing() {
        var presets = Map.of(
                id("a"), new FixturePreset(List.of(preset("b"))),
                id("b"), new FixturePreset(List.of(preset("a"))));
        assertEquals(Optional.empty(), resolve(preset("a"), presets));
    }

    @Test
    void anAlternativeThatDoesntDecodeIsKeptUnchanged(MinecraftServer server) {
        var json = JsonParser.parseString("""
                {"fixtures": [
                  {"type": "ametrin_structures:loot_container", "loot_table": "minecraft:chests/buried_treasure"},
                  {"weight": 2, "type": "ametrin_structures:loot_container", "loot_table": "minecraft:chests/buried_treasure", "block": "minecraft:stone"},
                  {"type": "minecraft:nope"}
                ]}""");
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var preset = FixturePreset.CODEC.parse(ops, json).getOrThrow();
        assertInstanceOf(LootContainerFixture.class, preset.fixtures().get(0).fixture());
        assertInstanceOf(Fixture.Unreadable.class, preset.fixtures().get(1).fixture(), "stone doesn't take a loot table");
        assertEquals(2, preset.fixtures().get(1).weight());
        assertInstanceOf(Fixture.Unreadable.class, preset.fixtures().get(2).fixture());
        assertEquals(json, FixturePreset.CODEC.encodeStart(ops, preset).getOrThrow());
    }

    @Test
    void problemsNameWhatIsWrong(MinecraftServer server) {
        var broken = FixturePreset.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString("""
                {"fixtures": [
                  {"type": "minecraft:nope"},
                  {"type": "ametrin_structures:block_state"},
                  {"type": "ametrin_structures:loot_container", "loot_table": "minecraft:chests/simple_dungeon", "generation_chance": 2}
                ]}""")).getOrThrow();
        var presets = Map.of(
                id("broken"), broken,
                id("missing_reference"), new FixturePreset(List.of(preset("missing"))),
                id("a"), new FixturePreset(List.of(preset("b"))),
                id("b"), new FixturePreset(List.of(preset("a"))),
                id("fine"), new FixturePreset(List.of(CHEST, preset("fine_too"))),
                id("fine_too"), new FixturePreset(List.of(CHEST)));
        var problems = FixturePresets.problems(presets);
        assertEquals(5, problems.size(), problems.toString());
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("minecraft:nope")), problems.toString());
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("state")), problems.toString());
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("unknown fixture preset test:missing")));
        assertTrue(problems.stream().anyMatch(problem -> problem.startsWith("cycle ")));
    }

    @Test
    void anEntityTakesItsEntityData() {
        var alternative = FixturePreset.builder().entity(2, new EntityDataBuilder(EntityTypes.ZOMBIE).passenger(EntityTypes.CHICKEN))
                .build().fixtures().getFirst();
        assertEquals(2, alternative.weight());
        var entity = assertInstanceOf(EntityFixture.class, alternative.fixture());
        assertEquals(Identifier.withDefaultNamespace("zombie"), entity.entity().identifier());
        assertTrue(entity.nbt().orElseThrow().toString().contains("minecraft:chicken"));
        assertFalse(entity.nbt().orElseThrow().contains("id"));
    }

    @Test
    void equipmentKeepsTheDefaultDropChance() {
        var entity = EntityFixture.of(EntityTypes.ZOMBIE).withEquipment(BuiltInLootTables.SIMPLE_DUNGEON);
        assertEquals(Optional.of(BuiltInLootTables.SIMPLE_DUNGEON), entity.equipment());
        assertEquals(0.085F, entity.equipmentDropChance());
    }

    @Test
    void deathLootIsItsOwnFieldNotExtraData() {
        var entity = EntityFixture.of(new EntityDataBuilder(EntityTypes.SKELETON).deathLootTable(BuiltInLootTables.SIMPLE_DUNGEON));
        assertEquals(Optional.of(BuiltInLootTables.SIMPLE_DUNGEON), entity.deathLootTable());
        assertEquals(Optional.empty(), entity.nbt(), "extra data would turn off the entity's randomization");
    }

    @Test
    void theBuilderWritesWhatTheCodecReads(MinecraftServer server) {
        var preset = FixturePreset.builder()
                .add(3, LootContainerFixture.chest(BuiltInLootTables.SIMPLE_DUNGEON))
                .add(1, key("rare"))
                .build();
        var json = JsonParser.parseString("""
                {"fixtures": [
                  {"weight": 3, "type": "ametrin_structures:loot_container", "loot_table": "minecraft:chests/simple_dungeon"},
                  {"type": "ametrin_structures:preset", "preset": "test:rare"}
                ]}""");
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        assertEquals(json, FixturePreset.CODEC.encodeStart(ops, preset).getOrThrow());
        assertEquals(preset, FixturePreset.CODEC.parse(ops, json).getOrThrow());
    }

    @Test
    void aSpawnerFromTheBuilderWritesOnlyItsEntity(MinecraftServer server) {
        var preset = FixturePreset.builder().spawner(2, EntityTypes.HUSK).build();
        var json = JsonParser.parseString("""
                {"fixtures": [{"weight": 2, "type": "ametrin_structures:spawner", "entity": "minecraft:husk"}]}""");
        assertEquals(json, FixturePreset.CODEC.encodeStart(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), preset).getOrThrow());
    }

    @Test
    void aPresetOnlyDrawsAmongEligibleAlternatives() {
        var excluded = new WeightedFixture(1000, EmptyFixture.INSTANCE);
        var presets = Map.of(id("inner"), new FixturePreset(List.of(excluded, CHEST)));
        for (int seed = 0; seed < 20; seed++) {
            var drawn = FixturePresets.resolve(preset("inner"), id -> Optional.ofNullable(presets.get(id)), alternative -> alternative != excluded, RandomSource.create(seed));
            assertEquals(Optional.of(CHEST), drawn);
        }
    }

    private static Optional<WeightedFixture> resolve(WeightedFixture fixture, Map<Identifier, FixturePreset> presets) {
        return FixturePresets.resolve(fixture, id -> Optional.ofNullable(presets.get(id)), _ -> true, RandomSource.create(0));
    }

    private static WeightedFixture preset(String path) {
        return new WeightedFixture(1, new PresetFixture(key(path)));
    }

    private static ResourceKey<FixturePreset> key(String path) {
        return ResourceKey.create(ASRegistries.FIXTURE_PRESET, id(path));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("test", path);
    }
}
