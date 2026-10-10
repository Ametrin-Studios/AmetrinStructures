# Extending the library

Most building blocks are registry-backed: implement an interface, give it a `MapCodec`, and register
a type object in the library's registry with an ordinary `DeferredRegister`. Your additions are then
usable from Java builders and from JSON alike. The registry keys are in `ASRegistries`.

[Dungeons Enhanced](https://github.com/Ametrin-Studios/DungeonsEnhanced), a source-available mod using this library,
serves as a
real-world example.

## Placement filters

A filter decides whether a simple or jigsaw structure may generate at a spot.

```java
public record MinHeight(int y) implements PlacementFilter {
    public static final MapCodec<MinHeight> CODEC = Codec.INT.fieldOf("y").xmap(MinHeight::new, MinHeight::y);

    @Override
    public boolean test(Context context) {
        return context.origin().getY() >= y;
    }

    @Override
    public PlacementFilterType type() {
        return MIN_HEIGHT.get();
    }
}

static final DeferredRegister<PlacementFilterType> FILTERS = DeferredRegister.create(ASRegistries.PLACEMENT_FILTER_TYPE, MODID);
static final DeferredHolder<PlacementFilterType, PlacementFilterType> MIN_HEIGHT = FILTERS.register("min_height", () -> new PlacementFilterType(MinHeight.CODEC));
```

Use it with `.filter(new MinHeight(40))` on either builder. The context carries the start position,
the box around all pieces, and a `TerrainSampler`. Prefer the sampler over the chunk generator for
terrain heights: its lookups are cached and shared with the other filters. A jigsaw structure with
filters assembles its pieces to know that box, before it knows whether it fits.

Settings that every library structure takes live in `ExtendedStructureSettings`, which both
structure types extend through `ExtendedStructure`; their builders share `ExtendedStructureBuilder`.

`/ametrin structures spread` reports rejections by the filter's type id, so a registered filter
shows up in the report without further work.

Reference: [`PlacementFilters`](../src/main/java/com/ametrin/structures/structure/filter/PlacementFilters.java), [
`ExtendedStructure`](../src/main/java/com/ametrin/structures/structure/ExtendedStructure.java) and [
`ASPlacementFilters`](../src/main/java/com/ametrin/structures/registry/ASPlacementFilters.java).

## Piece sources

A piece source decides which pieces a simple structure places. Register a `PieceSourceType` in
`ASRegistries.PIECE_SOURCE_TYPE` and pass the source with `.pieces(...)`.

`PieceSources.createPiece(template, context)` creates a template piece that behaves like the built-in
ones. Pieces are created at Y 0; the structure moves them to their start height afterwards, so build
them relative to `context.origin()`.

`templates()` lists every template the source can place, so `/ametrin structures check` can look
into them.

Reference: [`PieceSources`](../src/main/java/com/ametrin/structures/structure/simple/PieceSources.java) and [
`ASPieceSources`](../src/main/java/com/ametrin/structures/registry/ASPieceSources.java).

## Structures of your own

A structure set accepts any structure next to simple ones. `structure(...)` takes a factory from the
shared settings to your structure, and keeps biomes, weight, step, terrain adaptation and spawn
overrides on the builder:

```java
STRUCTURES.registerSet("castle", set -> set
        .scatteredGridPlacement(40, 0.5F)
        .structure((settings, context) -> new CastleStructure(settings), castle -> castle.biomes(BiomeTags.IS_TAIGA)));
```

Register its structure type and piece types with your own `DeferredRegister`s, like any other registry entry.

Extend `ExtendedStructure` to get the library's filters, biome check and `/ametrin structures spread`
reasons. Lay the structure out in `layOut` and leave the rest to it:

```java
public class CastleStructure extends ExtendedStructure {
    public static final MapCodec<CastleStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    settingsCodec(instance),
                    extendedSettingsCodec(instance))
            .apply(instance, CastleStructure::new));

    public CastleStructure(StructureSettings settings, ExtendedStructureSettings extendedSettings) {
        super(settings, extendedSettings);
    }

    @Override
    protected Either<Candidate, Evaluation> layOut(GenerationContext context, Timer timer) {
        var origin = context.chunkPos().getMiddleBlockPosition(64);
        var pieces = new StructurePiecesBuilder();
        // add the pieces
        return Either.left(new Candidate(origin, new TerrainSampler(context, Heightmap.Types.WORLD_SURFACE_WG),
                pieces::getBoundingBox, () -> new GenerationStub(origin, Either.right(pieces))));
    }

    @Override
    public StructureType<?> type() {
        return ExampleStructures.CASTLE_TYPE.get();
    }
}
```

Give its factory the extended settings, and the builder takes filters like a simple structure's:

```java
.structure((settings, extendedSettings, context) -> new CastleStructure(settings, extendedSettings),
        castle -> castle.biomes(BiomeTags.IS_TAIGA).filterFlatness(4))
```

`SimpleStructure` and `ExtendedJigsawStructure` can be subclassed too: every setting has a getter for
the subclass's codec. Outside a set, `SimpleStructure.builder(id)...build(context)` and
`ExtendedJigsawStructure.builder(settings, pool)...build()` create them for your own registry bootstrap.

To place pieces from saved templates, extend `ExtendedTemplateStructurePiece`. Like the simple
structure's pieces, it runs fixtures, removes foam, extends down to a foundation, and fits the terrain
to a `TerrainBox`. It replaces the structure's terrain adaptation with its own, so pass it the
structure's `terrainAdaptation()`. Other template pieces place fixture blocks as they were saved.

Reference:
[`ExtendedStructure`](../src/main/java/com/ametrin/structures/structure/ExtendedStructure.java),
[`ExtendedTemplateStructurePiece`](../src/main/java/com/ametrin/structures/structure/ExtendedTemplateStructurePiece.java),
[`ExtendedJigsawStructure`](../src/main/java/com/ametrin/structures/structure/jigsaw/ExtendedJigsawStructure.java),
[`SimpleStructurePiece`](../src/main/java/com/ametrin/structures/structure/simple/SimpleStructurePiece.java),
[`ASStructureTypes`](../src/main/java/com/ametrin/structures/registry/ASStructureTypes.java) and
[`ASPieceTypes`](../src/main/java/com/ametrin/structures/registry/ASPieceTypes.java).

## Placements

`horizontalPlacement(...)` accepts any `StructurePlacement`, vanilla's or your own. The function overload receives the bootstrap context, for placements that reference registry entries.

The library has two built-in `StructurePlacement`s:

- `evenSpreadPlacement(...)`, spreads the attempts evenly with a guaranteed
  `min_distance` and no visible pattern. They average about 1.3 times `min_distance` apart.
- `scatteredGridPlacement(...)` puts one attempt in each grid cell. This allows you to interleave sets through a shared `grid_offset` and has a larger radius explorer maps can check. It's faster than `evenSpreadPlacement`.

`StructurePlacements.hasStructureChunkInRange(...)` and `LakeProof` answer "is there a structure
nearby" and "is this position inside a structure" for a structure, holder set or tag, which is handy
for placements and features that should keep their distance.

Reference: [`ScatteredGridPlacement`](../src/main/java/com/ametrin/structures/placement/ScatteredGridPlacement.java),
[`EvenSpreadPlacement`](../src/main/java/com/ametrin/structures/placement/EvenSpreadPlacement.java),
[`StructurePlacements`](../src/main/java/com/ametrin/structures/placement/StructurePlacements.java),
[`LakeProof`](../src/main/java/com/ametrin/structures/placement/LakeProof.java) and
[`ASPlacementTypes`](../src/main/java/com/ametrin/structures/registry/ASPlacementTypes.java).

## Processors

`ReplaceBlockProcessor` swaps random blocks, e.g. to weather a build. In a simple structure template's
own processor list, `InlineFromStructureProcessor.INSTANCE` marks where the structure's processors
run, so a template can add to them rather than replace them.

`RetainExistingProcessor.ALL` only places blocks where the world's block is replaceable, such
as air, plants or water, so ruins blend into the terrain instead of cutting through it.
`RetainExistingProcessor.REPLACEABLE_ONLY` (`"replaceable_only": true`) always places solid blocks
and only holds back the template's air, plants and water, so a building keeps its walls but its
air doesn't carve into the terrain around it. World water counts as replaceable, so template air
still clears it.

`RemoveFoamProcessor` is added to every piece automatically. Declare one yourself to fill foam with
something else: `RemoveFoamProcessor.WATER` floods the inside of a sunken build.

Reference: [`ReplaceBlockProcessor`](../src/main/java/com/ametrin/structures/processor/ReplaceBlockProcessor.java),
[`RetainExistingProcessor`](../src/main/java/com/ametrin/structures/processor/RetainExistingProcessor.java),
[
`InlineFromStructureProcessor`](../src/main/java/com/ametrin/structures/structure/simple/InlineFromStructureProcessor.java),
[`RemoveFoamProcessor`](../src/main/java/com/ametrin/structures/foam/RemoveFoamProcessor.java) and
[`ASProcessors`](../src/main/java/com/ametrin/structures/registry/ASProcessors.java).

## Fixtures

A fixture is a record implementing `Fixture`. Its fields are `FixtureField`s: the record's codec is
built from them, and the authoring screen builds its rows from them, with validation and completion
from each field's `FieldType`.

```java
public record Lantern(boolean hanging) implements Fixture {
    static final FixtureField<Boolean> HANGING = FixtureField.withDefault("hanging", FieldType.bool(), true);
    static final MapCodec<Lantern> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    HANGING.forGetter(Lantern::hanging))
            .apply(instance, Lantern::new));

    @Override
    public void apply(FixtureContext context) {
        context.placeBlock(Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, hanging));
    }

    @Override
    public FixtureType type() {
        return LANTERN.get();
    }
}

static final DeferredRegister<FixtureType> FIXTURES = DeferredRegister.create(ASRegistries.FIXTURE_TYPE, MODID);
static final DeferredHolder<FixtureType, FixtureType> LANTERN = FIXTURES.register("lantern", () -> new FixtureType(Lantern.CODEC, List.of(Lantern.HANGING)));
```

A field is `required`, `withDefault` or `optional`, the last one holding an `Optional`. `FieldType`
has the common kinds of value, such as numbers, registry keys, block states, items and NBT, and
`validated` narrows one down.

An alternative that doesn't decode, such as one naming a block from a mod that isn't installed, is
kept as it was stored and skipped when the structure generates, so a template never fails to load
because of a fixture.

Override `references()` to return the registry keys the fixture names, such as loot tables, so
`/ametrin structures check` reports the ones that don't exist.

`context.placeBlock(...)` places a block the way the built-in fixtures do, turned to face like the
marker; a block state field for it takes `BlockStateMerging.mergedBlockState()`, which leaves out what
the marker sets. `Fixtures` has the other helpers they share, such as `spawnEntity(...)` and
`placeSpawner(...)`.

Reference: the built-in fixtures, such as
[`LootContainerFixture`](../src/main/java/com/ametrin/structures/fixture/LootContainerFixture.java),
[`Fixtures`](../src/main/java/com/ametrin/structures/fixture/Fixtures.java) and
[`ASFixtures`](../src/main/java/com/ametrin/structures/registry/ASFixtures.java).

## Fixture presets

A `FixturePreset` is a weighted list of fixtures, like the one a fixture block holds, that many
fixtures can share. Change the preset and every structure using it follows, and datapacks can
override it.

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

In a fixture's screen, pick the `preset` type and enter the preset's id. It's one alternative like
any other:

- **The whole list:** make the preset the only alternative.
- **A single entry:** mix it with other alternatives, such as a weight of 5 for `empty` and 1 for a
  `rare_chest` preset.

When the preset alternative is drawn, the preset draws among its own alternatives, so its weights
mean the same everywhere. Presets can use other presets; generation chances along the way multiply.
The fixture block keeps its own settings: offset, gravity, `becomes`, facing and post-processing.

Presets live at `data/<namespace>/ametrin_structures/fixture_preset/<name>.json`. Each alternative
holds the fixture's fields next to its `type`, `weight` and `generation_chance`:

```json
{"fixtures": [
  {"weight": 3, "type": "ametrin_structures:loot_container", "loot_table": "minecraft:chests/simple_dungeon"},
  {"type": "ametrin_structures:empty"}
]}
```

Reference: [`FixturePreset`](../src/main/java/com/ametrin/structures/fixture/FixturePreset.java).

## Fixture conditions

Any alternative, on a fixture block or in a preset, can have `conditions`. They are tested where the
fixture acts, after the marker is gone; alternatives whose conditions don't all pass are left out
before one is drawn, so the others keep their weights relative to each other. When none pass, the
fixture does nothing.

```json
{"fixtures": [
  {"type": "ametrin_structures:loot_container", "loot_table": "example:chests/snowy",
   "conditions": [{"type": "ametrin_structures:biome", "biomes": "#minecraft:spawns_snow_foxes"}]},
  {"type": "ametrin_structures:loot_container", "loot_table": "example:chests/crate", "block": {"Name": "othermod:crate"},
   "conditions": [{"type": "neoforge:mod_loaded", "modid": "othermod"}]},
  {"type": "ametrin_structures:loot_container", "loot_table": "example:chests/default"}
]}
```

- `ametrin_structures:biome`: the biome is one of `biomes`, a biome, a list or a `#tag`.
- `ametrin_structures:dimension`: the dimension is one of `dimensions`.
- `ametrin_structures:height_range`: Y is from `min` to `max`; either may be left out.
- `ametrin_structures:not`, `any_of` and `all_of` combine others.
- Any NeoForge condition, such as `neoforge:mod_loaded` or `neoforge:tag_empty`, tested against the
  world's tags.

An alternative for a mod that isn't installed doesn't read, but keeps its conditions, so a
`neoforge:mod_loaded` condition rules it out instead of it taking its weight and placing nothing.

In Java, add conditions with `withConditions(...)`, and wrap NeoForge's in `FixtureConditions.NeoForge`:

```java
.add(new WeightedFixture(1, new LootContainerFixture(CRATE_LOOT))
        .withConditions(new FixtureConditions.NeoForge(new ModLoadedCondition("othermod"))))
```

Biome conditions take a `HolderSet`, from the bootstrap context's lookup. A husk spawner
in deserts and a zombie spawner elsewhere:

```java
var desert = new FixtureConditions.InBiome(context.lookup(Registries.BIOME).getOrThrow(Tags.Biomes.IS_DESERT));
context.register(CRYPT_SPAWNER, FixturePreset.builder()
        .add(new WeightedFixture(1, SpawnerFixture.of(EntityType.HUSK)).withConditions(desert))
        .add(new WeightedFixture(1, SpawnerFixture.of(EntityType.ZOMBIE)).withConditions(new FixtureConditions.Not(desert)))
        .build());
```

The fixture's screen edits them as SNBT. Register your own kinds as a `FixtureConditionType` in
`ASRegistries.FIXTURE_CONDITION_TYPE`, like placement filters.

Reference: [`FixtureConditions`](../src/main/java/com/ametrin/structures/fixture/FixtureConditions.java) and [
`ASFixtureConditions`](../src/main/java/com/ametrin/structures/registry/ASFixtureConditions.java).

## Spawner profiles

A `SpawnerProfile` configures a spawner: delays, counts, ranges and what it spawns. Place spawners
using it with the `spawner_profile` fixture. A spawner applies its profile every time it loads, so
changing a profile, in your mod or a datapack, also updates spawners that generated earlier.

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

Profiles live at `data/<namespace>/ametrin_structures/spawner_profile/<name>.json`. Every field is
optional and defaults to vanilla's value; without `spawn_potentials` the spawner keeps its entity.

`EntityDataBuilder` describes an entity for spawners, entity fixtures and riders alike: name, health
and other attributes, effects, gear per slot, death loot, left-handedness, babies, riders and more.
Setting any of it makes the entity skip its own spawn randomization, such as random armor, as vanilla
spawners do; entity fixtures also keep it with a death loot table.

`SpawnDataBuilder` adds what only spawners use: an equipment loot table and spawn rules, such as
`noLightLimit()` for spawning in daylight. Entity fixtures take their equipment loot table with `withEquipment(...)`.

Reference: [`SpawnerProfile`](../src/main/java/com/ametrin/structures/spawner/SpawnerProfile.java) and [
`ASSpawnerProfiles`](../src/main/java/com/ametrin/structures/registry/ASSpawnerProfiles.java).

## Foam

How foam spreads is data on the item stack, not the block. `FoamPresets.stack(spread, name)` makes a
foam item from any `FoamSpread`: a `FoamSpreadBehavior` choosing where each step reaches, plus
`FoamSpreadRestriction`s vetoing positions. Register your own behaviors and restrictions in
`ASRegistries.FOAM_SPREAD_BEHAVIOR_TYPE` and `ASRegistries.FOAM_SPREAD_RESTRICTION_TYPE`. Their tooltip
line is the translation of `foam_spread_behavior.<namespace>.<path>` or
`foam_spread_restriction.<namespace>.<path>`; override `description()` to pass arguments.

To fill some regions with something other than air, register a foam block of your own with a
replacement state, and a `FoamBlockItem` for it. Give it its own texture so the regions are easy to
tell apart:

```java
static final DeferredBlock<FoamBlock> WATER_FOAM = BLOCKS.registerBlock("water_foam",
        properties -> new FoamBlock(Optional.of(Blocks.WATER.defaultBlockState()), properties),
        properties -> properties.noCollision().noOcclusion().noLootTable().instabreak());
static final DeferredItem<FoamBlockItem> WATER_FOAM_ITEM = ITEMS.registerItem("water_foam",
        properties -> new FoamBlockItem(WATER_FOAM.get(), properties),
        properties -> properties.useBlockDescriptionPrefix().component(ASDataComponents.FOAM_SPREAD, FoamPresets.BASIC));
```

It spreads, dissolves and refills openings like the library's foam, and turns into its replacement
state when the structure generates, whatever the piece's `RemoveFoamProcessor` fills with.
`FoamPresets.stack(item, spread, name)` makes stacks of it with other spreads.

Reference: [`FoamSpreadBehaviors`](../src/main/java/com/ametrin/structures/foam/FoamSpreadBehaviors.java),
[`FoamSpreadRestrictions`](../src/main/java/com/ametrin/structures/foam/FoamSpreadRestrictions.java),
[`FoamPresets`](../src/main/java/com/ametrin/structures/foam/FoamPresets.java),
[`ASFoamSpreadBehaviors`](../src/main/java/com/ametrin/structures/registry/ASFoamSpreadBehaviors.java) and
[`ASFoamSpreadRestrictions`](../src/main/java/com/ametrin/structures/registry/ASFoamSpreadRestrictions.java).
