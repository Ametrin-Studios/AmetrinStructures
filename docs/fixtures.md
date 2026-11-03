# Fixtures

A fixture block in a template turns into something else when the structure generates. It can fill a container with loot, spawn an entity, place a spawner, run a feature and more. Place one from the operator items tab and right-click it to configure it.

A fixture holds a weighted list of alternatives, and each alternative has a generation chance. When the structure generates, the fixture picks one of them.

"Generate now" in the fixture's screen runs it in place like generation would, but ignores its generation chance. The fixture goes back into your inventory first, with all its settings, so you can place it again.

If a value can't be parsed or isn't accepted, the fixture uses its default instead. Turn on [debug logging](debugging.md#debug-logging) to see why. If an alternative can't be decoded, e.g. because it names a block from a mod that isn't installed, it's kept as it was stored and skipped at generation. A broken fixture never stops a template from loading.

## Presets

A `FixturePreset` is a shared weighted list of fixtures, the same as the list in a fixture block. When you change a preset, every structure using it changes too, and datapacks can override it.

```java
static final ResourceKey<FixturePreset> DUNGEON_CHEST = ResourceKey.create(ASRegistries.FIXTURE_PRESET, id("dungeon_chest"));

static void bootstrap(BootstrapContext<FixturePreset> context) {
    context.register(DUNGEON_CHEST, FixturePreset.builder()
            .add(3, new LootContainerFixture(BuiltInLootTables.SIMPLE_DUNGEON))
            .add(new WeightedFixture(1, 0.5F, new LootContainerFixture(BuiltInLootTables.BURIED_TREASURE)))
            .build());
    context.register(GUARD, FixturePreset.builder()
            .add(1, EntityFixture.of(EntityType.SKELETON).withEquipment(GUARD_EQUIPMENT))
            .entity(1, new EntityDataBuilder(EntityType.SPIDER).passenger(EntityType.SKELETON))
            .build());
}

// Datagen
registries.add(ASRegistries.FIXTURE_PRESET, ExamplePresets::bootstrap);
```

In the fixture's screen, choose the `preset` type and enter the preset's id. It's an alternative like any other:

- To use the whole list, make the preset the only alternative.
- To use it as one option, mix it with others, e.g. weight 5 for `empty` and 1 for a `rare_chest` preset.

When the preset is picked, it picks from its own alternatives, so its weights work the same everywhere. Presets can use other presets, and their generation chances multiply. Offset, gravity, `becomes`, facing and post-processing still come from the fixture block.

Presets are stored at `data/<namespace>/ametrin_structures/fixture_preset/<name>.json`. Each alternative has the fixture's fields next to its `type`, `weight` and `generation_chance`:

```json
{"fixtures": [
  {"weight": 3, "type": "ametrin_structures:loot_container", "loot_table": "minecraft:chests/simple_dungeon"},
  {"type": "ametrin_structures:empty"}
]}
```

Broken presets are logged as warnings when the server starts: unknown fixture types, fields or presets, values that can't be parsed, and presets that use each other in a loop.

Reference: [`FixturePreset`](../src/main/java/com/ametrin/structures/fixture/FixturePreset.java).

## Conditions

Any alternative, in a fixture block or a preset, can have `conditions`. They're tested at the position the fixture acts on, after the marker is removed. Alternatives whose conditions fail are dropped before picking, and the rest keep their relative weights. If none pass, the fixture does nothing.

```json
{"fixtures": [
  {"type": "ametrin_structures:loot_container", "loot_table": "example:chests/snowy",
   "conditions": [{"type": "ametrin_structures:biome", "biomes": "#minecraft:spawns_snow_foxes"}]},
  {"type": "ametrin_structures:loot_container", "loot_table": "example:chests/crate", "block": {"Name": "othermod:crate"},
   "conditions": [{"type": "neoforge:mod_loaded", "modid": "othermod"}]},
  {"type": "ametrin_structures:loot_container", "loot_table": "example:chests/default"}
]}
```

- `ametrin_structures:biome`: the biome is in `biomes`, which is a biome, a list or a `#tag`.
- `ametrin_structures:dimension`: the dimension is in `dimensions`.
- `ametrin_structures:height_range`: Y is between `min` and `max`. Either can be left out.
- `ametrin_structures:not`, `any_of` and `all_of` combine other conditions.
- Any NeoForge condition, like `neoforge:mod_loaded` or `neoforge:tag_empty`. Tag conditions use the world's tags.

An alternative for a mod that isn't installed can't be decoded, but its conditions still work. So a `neoforge:mod_loaded` condition removes it, instead of it being picked and placing nothing.

The fixture's screen edits conditions as SNBT. In Java, add them with `withConditions(...)`. Wrap NeoForge conditions in `FixtureConditions.NeoForge`:

```java
.add(new WeightedFixture(1, new LootContainerFixture(CRATE_LOOT))
        .withConditions(new FixtureConditions.NeoForge(new ModLoadedCondition("othermod"))))
```

Biome conditions take a `HolderSet`, which you get from the bootstrap context. For example, a husk spawner in deserts and a zombie spawner everywhere else:

```java
var desert = new FixtureConditions.InBiome(context.lookup(Registries.BIOME).getOrThrow(Tags.Biomes.IS_DESERT));
context.register(CRYPT_SPAWNER, FixturePreset.builder()
        .add(new WeightedFixture(1, SpawnerFixture.of(EntityType.HUSK)).withConditions(desert))
        .add(new WeightedFixture(1, SpawnerFixture.of(EntityType.ZOMBIE)).withConditions(new FixtureConditions.Not(desert)))
        .build());
```

Reference: [`FixtureConditions`](../src/main/java/com/ametrin/structures/fixture/FixtureConditions.java).

## Entities

`EntityDataBuilder` describes an entity for entity fixtures, spawners and passengers: name, health and other attributes, effects, equipment per slot, death loot, left-handedness, baby, passengers and more. If you set any of it, the entity skips its own spawn randomization, like random armor, the same as with vanilla spawners. Entity fixtures keep it when you only set a death loot table or equipment, since they place those on the entity after it spawns.

For entity fixtures, set an equipment loot table with `withEquipment(...)`.

## Spawner profiles

A `SpawnerProfile` configures a spawner: delays, counts, ranges and what it spawns. Use the `spawner_profile` fixture to place spawners with it. A spawner applies its profile every time it loads, so changes to the profile, from your mod or a datapack, also reach spawners that already generated.

```java
static final ResourceKey<SpawnerProfile> CRYPT = ResourceKey.create(ASRegistries.SPAWNER_PROFILE, id("crypt"));

static void bootstrap(BootstrapContext<SpawnerProfile> context) {
    context.register(CRYPT, SpawnerProfile.builder()
            .spawnCount(2)
            .add(EntityType.ZOMBIE, 3)
            .add(new EntityDataBuilder(EntityType.SPIDER).passenger(EntityType.SKELETON), 1)
            .add(SpawnDataBuilder.of(EntityType.HUSK).noLightLimit(), 1)
            .build());
}

// Datagen
registries.add(ASRegistries.SPAWNER_PROFILE, ExampleSpawnerProfiles::bootstrap);
```

Profiles are stored at `data/<namespace>/ametrin_structures/spawner_profile/<name>.json`. All fields are optional and default to vanilla's values. Without `spawn_potentials` the spawner keeps its current entity.

`SpawnDataBuilder` adds settings only spawners use: an equipment loot table and spawn rules, like `noLightLimit()` to spawn in daylight.

Reference: [`SpawnerProfile`](../src/main/java/com/ametrin/structures/spawner/SpawnerProfile.java).
