# Extending the library

Most parts of the library are registry based. Implement the interface, give it a `MapCodec`, and register a type for it
in the library's registry with a normal `DeferredRegister`. It then works from the Java builders and from JSON. The
registry keys are in `ASRegistries`.

[Dungeons Enhanced](https://github.com/Ametrin-Studios/DungeonsEnhanced) is a source-available mod built on this
library, if you want a real example.

## Placement filters

A filter decides if a simple or jigsaw structure can generate at a spot.

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

Add it with `.filter(new MinHeight(40))` on any structure builder. The context has the start position, the bounding
box of all pieces and a `TerrainSampler`. Use the sampler instead of the chunk generator for terrain heights. It
caches the lookups and shares them with the other filters. A jigsaw structure with filters has to assemble its pieces
before the filters run, to know the box.

The spread report names a failed filter by its type id, so your filters show up there without extra work.

Reference: [`PlacementFilters`](../src/main/java/com/ametrin/structures/structure/filter/PlacementFilters.java) and
[`ASPlacementFilters`](../src/main/java/com/ametrin/structures/registry/ASPlacementFilters.java).

## Piece sources

A piece source decides which pieces a simple structure places. Register a `PieceSourceType` in
`ASRegistries.PIECE_SOURCE_TYPE` and pass your source to `.pieces(...)`.

`PieceSources.createPiece(template, context)` creates the same piece the built-in sources use. Create pieces relative
to `context.origin()`, which is at Y 0. The structure moves them to the start height afterwards.

`templates()` lists every template the source can place. `/ametrin structures check` uses it.

Reference: [`PieceSources`](../src/main/java/com/ametrin/structures/structure/simple/PieceSources.java) and
[`ASPieceSources`](../src/main/java/com/ametrin/structures/registry/ASPieceSources.java).

## Structures of your own

A set can hold any structure, not just simple ones. `structure(...)` takes a factory that creates your structure from
the shared settings. Biomes, weight, step, terrain adaptation and spawn overrides are set on the builder as usual:

```java
STRUCTURES.registerSet("castle", set -> set
        .scatteredGridPlacement(40, 0.5F)
        .structure((settings, context) -> new CastleStructure(settings), castle -> castle.biomes(BiomeTags.IS_TAIGA)));
```

Register its structure type and piece types with your own `DeferredRegister`s.

Extend `ExtendedStructure` to get filters, the biome check and detailed reasons in the spread report. You only
implement `layOut`:

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

Use the factory that also takes the extended settings, and the builder accepts filters like a simple structure's:

```java
.structure((settings, extendedSettings, context) -> new CastleStructure(settings, extendedSettings),
        castle -> castle.biomes(BiomeTags.IS_TAIGA).filterFlatness(4))
```

`SimpleStructure` and `ExtendedJigsawStructure` can be subclassed too. They have getters for all their settings, so a
subclass can write its own codec. To create them outside a set, e.g. in your own bootstrap, use
`SimpleStructure.builder(id)...build(context)` or `ExtendedJigsawStructure.builder(settings, pool)...build()`.

For pieces placed from templates, extend `ExtendedTemplateStructurePiece`. Like the simple structure's pieces, it runs
fixtures, removes foam, places foundations and fits the terrain to a `TerrainBox`. The piece decides its own terrain
adaptation, so pass it the structure's `terrainAdaptation()`. Other template pieces place fixture blocks as they are.

Reference:
[`ExtendedStructure`](../src/main/java/com/ametrin/structures/structure/ExtendedStructure.java),
[`ExtendedTemplateStructurePiece`](../src/main/java/com/ametrin/structures/structure/ExtendedTemplateStructurePiece.java),
[`ExtendedJigsawStructure`](../src/main/java/com/ametrin/structures/structure/jigsaw/ExtendedJigsawStructure.java) and
[`SimpleStructurePiece`](../src/main/java/com/ametrin/structures/structure/simple/SimpleStructurePiece.java).

## Placements

`horizontalPlacement(...)` takes any `StructurePlacement`, vanilla or your own. The overload with a function gets the
bootstrap context, for placements that need registry entries.

The library has two placements:

- `evenSpreadPlacement(...)` spreads attempts evenly without a visible pattern. They're always at least
  `min_distance` chunks apart, and about 1.3 times that on average.
- `scatteredGridPlacement(...)` puts one attempt in each cell of a grid. Sets with the same spacing can be interleaved
  with a shared `grid_offset`, and explorer maps can search further for it. It's also faster than even spread.

`StructurePlacements.hasStructureChunkInRange(...)` checks if a structure, holder set or tag has a spot nearby.
`LakeProof` checks if a position is inside a structure. Both are useful for placements and features that should keep
away from structures.

Reference: [`EvenSpreadPlacement`](../src/main/java/com/ametrin/structures/placement/EvenSpreadPlacement.java),
[`ScatteredGridPlacement`](../src/main/java/com/ametrin/structures/placement/ScatteredGridPlacement.java),
[`StructurePlacements`](../src/main/java/com/ametrin/structures/placement/StructurePlacements.java) and
[`LakeProof`](../src/main/java/com/ametrin/structures/placement/LakeProof.java).

## Processors

`ReplaceBlockProcessor` replaces random blocks, e.g. to make a build look weathered.

Processors on a template in a simple structure replace the structure's processors. Add
`InlineFromStructureProcessor.INSTANCE` to the template's list to run the structure's processors at that point.

`RetainExistingProcessor.ALL` only places blocks where the world has something replaceable, like air, plants or water.
Ruins blend into the terrain instead of cutting through it. `RetainExistingProcessor.REPLACEABLE_ONLY`
(`"replaceable_only": true`) always places solid blocks and only holds back the template's air, plants and water. A
building keeps its walls, but its air doesn't dig into the terrain around it. Water in the world counts as replaceable,
so template air still removes it.

Every piece gets a `RemoveFoamProcessor` automatically. Add one yourself to fill foam with something else, e.g.
`RemoveFoamProcessor.WATER` to flood a sunken build.

Reference: [`ReplaceBlockProcessor`](../src/main/java/com/ametrin/structures/processor/ReplaceBlockProcessor.java),
[`RetainExistingProcessor`](../src/main/java/com/ametrin/structures/processor/RetainExistingProcessor.java),
[`InlineFromStructureProcessor`](../src/main/java/com/ametrin/structures/structure/simple/InlineFromStructureProcessor.java)
and [`RemoveFoamProcessor`](../src/main/java/com/ametrin/structures/foam/RemoveFoamProcessor.java).

## Fixtures

A fixture is a record that implements `Fixture`. Its fields are `FixtureField`s. The codec is built from them, and so
is the fixture's screen, with validation and completion from each field's `FieldType`.

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

A field is `required`, `withDefault` or `optional` (wrapped in an `Optional`). `FieldType` covers the common value
types, like numbers, registry keys, block states, items and NBT. `validated` adds a check to one.

If an alternative can't be decoded, e.g. because it names a block from a mod that isn't installed, it's kept as it was
stored and skipped at generation. A broken fixture never stops a template from loading.

Override `references()` to return the registry keys the fixture uses, like loot tables. `/ametrin structures check`
then reports the missing ones.

`context.placeBlock(...)` places a block the way the built-in fixtures do, turned to match the marker. For a block
state field, use `BlockStateMerging.mergedBlockState()`. It leaves out the properties the marker sets. `Fixtures` has
more shared helpers, like `spawnEntity(...)` and `placeSpawner(...)`.

Reference: the built-in fixtures, like
[`LootContainerFixture`](../src/main/java/com/ametrin/structures/fixture/LootContainerFixture.java), and
[`Fixtures`](../src/main/java/com/ametrin/structures/fixture/Fixtures.java).

## Fixture presets

A `FixturePreset` is a shared weighted list of fixtures, the same as the list in a fixture block. When you change a
preset, every structure using it changes too, and datapacks can override it.

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

When the preset is picked, it picks from its own alternatives, so its weights work the same everywhere. Presets can use
other presets, and their generation chances multiply. Offset, gravity, `becomes`, facing and post-processing still come
from the fixture block.

Presets are stored at `data/<namespace>/ametrin_structures/fixture_preset/<name>.json`. Each alternative has the
fixture's fields next to its `type`, `weight` and `generation_chance`:

```json
{"fixtures": [
  {"weight": 3, "type": "ametrin_structures:loot_container", "loot_table": "minecraft:chests/simple_dungeon"},
  {"type": "ametrin_structures:empty"}
]}
```

Reference: [`FixturePreset`](../src/main/java/com/ametrin/structures/fixture/FixturePreset.java).

## Fixture conditions

Any alternative, in a fixture block or a preset, can have `conditions`. They're tested at the position the fixture
acts on, after the marker is removed. Alternatives whose conditions fail are dropped before picking, and the rest keep
their relative weights. If none pass, the fixture does nothing.

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

An alternative for a mod that isn't installed can't be decoded, but its conditions still work. So a
`neoforge:mod_loaded` condition removes it, instead of it being picked and placing nothing.

In Java, add conditions with `withConditions(...)`. Wrap NeoForge conditions in `FixtureConditions.NeoForge`:

```java
.add(new WeightedFixture(1, new LootContainerFixture(CRATE_LOOT))
        .withConditions(new FixtureConditions.NeoForge(new ModLoadedCondition("othermod"))))
```

Biome conditions take a `HolderSet`, which you get from the bootstrap context. For example, a husk spawner in deserts
and a zombie spawner everywhere else:

```java
var desert = new FixtureConditions.InBiome(context.lookup(Registries.BIOME).getOrThrow(Tags.Biomes.IS_DESERT));
context.register(CRYPT_SPAWNER, FixturePreset.builder()
        .add(new WeightedFixture(1, SpawnerFixture.of(EntityType.HUSK)).withConditions(desert))
        .add(new WeightedFixture(1, SpawnerFixture.of(EntityType.ZOMBIE)).withConditions(new FixtureConditions.Not(desert)))
        .build());
```

The fixture's screen edits conditions as SNBT. To add your own condition types, register a `FixtureConditionType` in
`ASRegistries.FIXTURE_CONDITION_TYPE`.

Reference: [`FixtureConditions`](../src/main/java/com/ametrin/structures/fixture/FixtureConditions.java).

## Spawner profiles

A `SpawnerProfile` configures a spawner: delays, counts, ranges and what it spawns. Use the `spawner_profile` fixture
to place spawners with it. A spawner applies its profile every time it loads, so changes to the profile, from your mod
or a datapack, also reach spawners that already generated.

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

Profiles are stored at `data/<namespace>/ametrin_structures/spawner_profile/<name>.json`. All fields are optional and
default to vanilla's values. Without `spawn_potentials` the spawner keeps its current entity.

`EntityDataBuilder` describes an entity for spawners, entity fixtures and passengers: name, health and other
attributes, effects, equipment per slot, death loot, left-handedness, baby, passengers and more. If you set any of it,
the entity skips its own spawn randomization, like random armor, the same as with vanilla spawners. Entity fixtures
also skip it when you set a death loot table.

`SpawnDataBuilder` adds settings only spawners use: an equipment loot table and spawn rules, like `noLightLimit()` to
spawn in daylight. For entity fixtures, set the equipment loot table with `withEquipment(...)`.

Reference: [`SpawnerProfile`](../src/main/java/com/ametrin/structures/spawner/SpawnerProfile.java).

## Foam

How foam spreads is stored on the item stack, not the block. `FoamPresets.stack(spread, name)` creates a foam item with
any `FoamSpread`: a `FoamSpreadBehavior` that picks where each step goes, plus `FoamSpreadRestriction`s that block
positions. Register your own in `ASRegistries.FOAM_SPREAD_BEHAVIOR_TYPE` and
`ASRegistries.FOAM_SPREAD_RESTRICTION_TYPE`. Their tooltip line is the translation of
`foam_spread_behavior.<namespace>.<path>` or `foam_spread_restriction.<namespace>.<path>`. Override `description()` to
pass arguments.

To fill some areas with something other than air, register your own foam block with a replacement state, plus a
`FoamBlockItem`. Give it its own texture so you can tell the areas apart:

```java
static final DeferredBlock<FoamBlock> WATER_FOAM = BLOCKS.registerBlock("water_foam",
        properties -> new FoamBlock(Optional.of(Blocks.WATER.defaultBlockState()), properties),
        properties -> properties.noCollision().noOcclusion().noLootTable().instabreak());
static final DeferredItem<FoamBlockItem> WATER_FOAM_ITEM = ITEMS.registerItem("water_foam",
        properties -> new FoamBlockItem(WATER_FOAM.get(), properties),
        properties -> properties.useBlockDescriptionPrefix().component(ASDataComponents.FOAM_SPREAD, FoamPresets.BASIC));
```

It spreads, dissolves and fills openings like the library's foam. When the structure generates it turns into its
replacement state, no matter what the piece's `RemoveFoamProcessor` uses. `FoamPresets.stack(item, spread, name)`
creates stacks of it with other spreads.

Reference: [`FoamSpreadBehaviors`](../src/main/java/com/ametrin/structures/foam/FoamSpreadBehaviors.java),
[`FoamSpreadRestrictions`](../src/main/java/com/ametrin/structures/foam/FoamSpreadRestrictions.java) and
[`FoamPresets`](../src/main/java/com/ametrin/structures/foam/FoamPresets.java).
