# Structures

Structures and structure sets are declared on a `StructureBootstrap` and written to JSON by datagen, as in [getting started](getting-started.md). This page covers what the builders can do. Your IDE's completion lists every option.

## Structure sets

A set decides where its structures are attempted. `registerSet(...)` declares one and returns its keys.

### Placements

- `evenSpreadPlacement(...)` spreads attempts evenly without a visible pattern. They're always at least `minDistance` chunks apart, and about 1.3 times that on average.
- `scatteredGridPlacement(...)` puts one attempt at a random spot in each cell of a grid. Sets with the same spacing can be interleaved with a shared `gridOffset(...)`, and explorer maps can search further for it. It's also faster than even spread.

Both take a probability, and their builder overloads add an exclusion zone around a structure tag, a minimum distance from the world origin and a salt:

```java
set.evenSpreadPlacement(spread -> spread
        .minDistance(24)
        .probability(0.5F)
        .minChunksFromCenter(16)
        .exclusionZone(ExampleTags.CASTLES, 8))
```

`horizontalPlacement(...)` takes any other `StructurePlacement`, vanilla or your own. The overload with a function gets the bootstrap context, for placements that need registry entries.

### Several structures per set

A set can hold more than one structure. They're tried in weighted order at each spot and if one doesn't fit, the next one gets a chance. The weight goes after the suffix:

```java
STRUCTURES.registerSet("graves", set -> set
        .evenSpreadPlacement(20, 0.5F)
        .simple("small", 3, grave -> grave
                .single("graves/small")
                .surface())
        .simple("large", 1, grave -> grave
                .single("graves/large")
                .surface()));
```

Structures in a set are named `<set>/<suffix>`, here `examplemod:graves/small` and `examplemod:graves/large`. An empty suffix names the structure after the set.

`onlyWhenPickedFirst()` keeps a structure from being tried as a fallback when another one of the set didn't fit. It can be used to optimize variants that fail at the same spots: each spot is measured once, and locating one no longer stops at the others' spots. It makes the structure generate less often.

`structure(...)` adds a structure of another type, see [structures of your own](extending.md#structures-of-your-own).

### Existing structures

`existing(...)` adds a structure registered elsewhere to a set, with an optional weight. It can be one of yours or one from vanilla or another mod. `registerSimple(...)` and `registerStructure(...)` declare a structure without a set and return its key:

```java
public static final ResourceKey<Structure> WELL = STRUCTURES.registerSimple("well", well -> well.single("well").surface());

public static final StructureSetKeys VILLAGE_EXTRAS = STRUCTURES.registerSet("village_extras", set -> set
        .scatteredGridPlacement(24, 0.5F)
        .existing(WELL, 3)
        .existing(BuiltinStructures.IGLOO));
```

The set's keys list these under `existing()` and its own under `declared()`. `structures()` returns both.

To create a simple structure outside a bootstrap, use `SimpleStructure.builder(id)...build(context)`.

### Excluding dimensions

Biomes keep most structures in the right dimension. When a dimension reuses vanilla's biomes, the `ametrin_structures:excluded_dimensions` data map takes structure sets out of it. It works for any set, vanilla and modded, e.g. in `data/<namespace>/data_maps/worldgen/structure_set/excluded_dimensions.json`:

```json
{
  "values": {
    "minecraft:villages": ["examplemod:example_dimension"],
    "minecraft:igloos": ["examplemod:example_dimension"]
  }
}
```

Or with a `DataMapProvider`:

```java
static final ResourceKey<Level> EXAMPLE_DIMENSION = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("examplemod", "example_dimension"));

@Override
protected void gather(HolderLookup.Provider provider) {
    builder(ASDataMaps.EXCLUDED_DIMENSIONS)
            .add(BuiltinStructureSets.VILLAGES, List.of(EXAMPLE_DIMENSION), false)
            .add(BuiltinStructureSets.IGLOOS, List.of(EXAMPLE_DIMENSION), false);
}
```

The set's structures don't generate there, and `/locate`, explorer maps and the spread report skip them. Lists from several packs are combined. Changes need a server restart.

## Pieces

A simple structure places one or more templates:

- `single("tower")` places one template.
- `weighted(...)` picks one of several at random.
- `compound(...)` places several together.
- `jigsaw(...)` grows a [jigsaw layout](jigsaw.md).
- `pieces(...)` takes any piece source, including [your own](extending.md#piece-sources).

Weighted and compound sources can hold each other:

```java
crypt.weighted(weighted -> weighted
        .single(3, "crypt/small")
        .compound(1, compound -> compound
                .single("crypt/large")
                .single(template -> template.template("crypt/large_cellar").offset(4, -6, 4))))
```

Each template can be configured like an element in a template pool:

- `offset(...)` and `yOffset(...)` move it from the structure's origin.
- `processors(...)`, `foundation(...)` and `liquidSettings(...)` replace the structure's.
- `noFoamProcessing()` leaves foam in place and places the air the template saved, like a vanilla element.
- `terrainBox(...)` sets what terrain adaptation fits the terrain to, see [overhangs](#overhangs).
- `element(...)` places any pool element instead of a template, like a feature or list element. The template settings don't apply to it.

The structure has a random rotation unless you set `fixedRotation(...)`. A compound structure rotates as one.

## Height

- `surface()` places the structure on the world surface, which over water is the water's surface.
- `oceanFloor()` places it on the ground under any water.
- `between(HeightAnchor.aboveBottom(8), HeightAnchor.surface(-24))` picks a random height in a range, e.g. for something buried.
- `verticalPlacement(...)` takes a single `HeightAnchor` or a vanilla height provider.

`surface(...)` and `oceanFloor(...)` take an offset. A `HeightMode` sets where a heightmap anchor measures the terrain under the structure:

- `ORIGIN`: at the structure's origin. The cheapest and the default.
- `CENTER`: at the center of the footprint.
- `CORNERS_MEAN`: the average of the four footprint corners.
- `LOWEST_CORNER`: the lowest corner, so no corner floats.
- `HIGHEST_CORNER`: the highest corner.

## Foundations

`foundation()` extends the bottom of every piece down to the ground, so the structure doesn't float on uneven terrain. Each column repeats the block above it. `foundation(state, maxDepth)` fills with one block instead. A template's own foundation replaces the structure's.

In jigsaw layouts it applies to the rigid elements that [`JigsawPools`](jigsaw.md#declaring-pools) declares.

## Terrain adaptation

`terrainAdaptation(...)` works like on vanilla structures.

### Overhangs

Terrain adaptation fits the terrain to the whole template, so a wide roof or a balcony gets a hill under it. Give the template a smaller box instead:

```java
tower.single(template -> template
        .template("tower")
        .terrainBox(TerrainBox.footprint()))
```

`TerrainBox.footprint()` only uses the blocks at and below ground level. `TerrainBox.of(minX, minY, minZ, maxX, maxY, maxZ)` takes a box in template coordinates, as the structure block shows them. The terrain meets the structure at the bottom of the box. With the default box, a negative Y offset buries the template's lower part.

## Filters

Filters run in order once the structure has a start height, and the structure only generates where all of them pass. The spread report says which one failed.

- `filterFlatness(maxVariance)` skips spots where the ground under the footprint varies by more than `maxVariance` blocks. An optional margin widens the measured area, or narrows it when negative. Use sparingly.
- `filterHeightRange(min, max)` requires a start Y between two absolute Ys. For heights relative to the terrain, add a `PlacementFilters.HeightRange` with height anchors through `filter(...)`. With `Check.BOX` it compares the footprint's lowest block with the minimum and its highest with the maximum.
- `filterGroundCheck(tag)` requires the block below the origin to be in the tag.
- `filterSubmerged(minDepth)` requires at least `minDepth` blocks of fluid above the structure.
- `filterWaterDepth(min, max)`, `filterMinWaterDepth(...)` and `filterMaxWaterDepth(...)` limit how deep the water under the structure is. Dry land is depth 0.
- `filterWithinBiome()` requires the whole structure to be in its biomes, not just the origin. An optional margin widens the checked area.

`filter(...)` also takes [filters of your own](extending.md#placement-filters).

## Processors

`processors(...)` on a structure runs on every template piece that has no processors of its own, in jigsaw layouts too. A piece's own processors replace the structure's. Add `InlineFromStructureProcessor.INSTANCE` to its list to run the structure's processors at that point.

Every piece gets a `RemoveFoamProcessor` automatically. Add one yourself to fill foam with something else, e.g. `RemoveFoamProcessor.WATER` to flood a sunken build.

The library adds these processors:

- `ReplaceBlockProcessor` replaces random blocks, e.g. to make a build look weathered.
- `RetainExistingProcessor.ALL` only places blocks where the world has something replaceable, like air, plants or water. Ruins blend into the terrain instead of cutting through it. `RetainExistingProcessor.REPLACEABLE_ONLY` always places solid blocks and only holds back the template's air, plants and water. A building keeps its walls, but its air doesn't dig into the terrain around it. Water in the world counts as replaceable, so template air still removes it.
- `RemoveUnsupportedProcessor.INSTANCE` removes the sides of glow lichen, resin clumps, sculk veins and similar blocks that touch nothing solid, and places air when no side is left, so they don't break and drop as items. Put it after a `ReplaceBlockProcessor` that turns vines into resin clumps with `preserve_state`.
- `ConditionalProcessor` runs its processors only when NeoForge conditions pass, e.g. when another mod is installed. Its processors are only read then, so they can use the other mod's blocks without breaking anything when it's missing. One processor list can hold the swaps for several mods side by side:

```json
{
  "processor_type": "ametrin_structures:conditional",
  "conditions": [{ "type": "neoforge:mod_loaded", "modid": "othermod" }],
  "processors": [
    { "processor_type": "ametrin_structures:replace_block", "condition": "minecraft:stone_bricks", "chance": 0.2, "change_to": { "Name": "othermod:cracked_stone_tiles" } }
  ]
}
```

`processors` can also be a processor list id.

## Other settings

- `biomes(...)` sets where the structure can generate. Defaults to `#minecraft:is_overworld`.
- `step(...)` sets the generation step. Defaults to surface structures.
- `spawnOverride(...)` replaces the mob spawns inside the structure, and `noSpawns(...)` turns them off.

## Lake proofing

Lakes don't carve into structures in the `#ametrin_structures:lake_proof` structure tag. This works for any structure.

## JSON

Datagen writes simple structures as `ametrin_structures:simple`. Pack authors can edit that JSON or write their own:

```json
{
  "type": "ametrin_structures:simple",
  "biomes": "#minecraft:is_forest",
  "spawn_overrides": {},
  "terrain_adaptation": "beard_thin",
  "pieces": {
    "template": "examplemod:tower",
    "offset": [0, -2, 0],
    "terrain_box": "footprint"
  },
  "foundation": {},
  "filters": [
    { "type": "ametrin_structures:flatness", "max_variance": 3 }
  ]
}
```

| Field | Java | Value |
|---|---|---|
| `pieces` | `single`, `weighted`, `compound`, `jigsaw` | a piece source, see below |
| `start_height` | `surface`, `oceanFloor`, `between`, `verticalPlacement` | a height anchor, `{"min": anchor, "max": anchor}` or a vanilla height provider. Defaults to the surface |
| `foundation` | `foundation` | `{}` repeats each bottom block. `state` fills with one block, `max_depth` defaults to 64 |
| `liquid_settings` | `liquidSettings` | `ignore_waterlogging` (default) or `apply_waterlogging` |
| `rotation` | `fixedRotation` | a rotation, random when left out |
| `filters` | `filter...` | a list of filters, see below |
| `processors` | `processors` | a processor list id or a list of processors |
| `only_when_picked_first` | `onlyWhenPickedFirst` | `true` or `false` (default) |

The rest, like `biomes`, `step` and `terrain_adaptation`, are vanilla's structure fields.

A height anchor is a vanilla vertical anchor, like `{"absolute": 64}` or `{"above_bottom": 8}`, or a heightmap with an optional offset and mode, like `{"heightmap": "WORLD_SURFACE_WG", "offset": -24, "mode": "lowest_corner"}`. The modes are `origin`, `center`, `corners_mean`, `lowest_corner` and `highest_corner`.

A template entry places a template, with optional `processors`, or any pool element under `element`. Both take an optional `offset` and `terrain_box`, which is `template` (default), `footprint` or `[minX, minY, minZ, maxX, maxY, maxZ]`. A template entry is also a piece source on its own. The other sources have a type:

```json
{
  "type": "ametrin_structures:weighted",
  "sources": [
    { "weight": 3, "data": { "template": "examplemod:crypt/small" } },
    { "weight": 1, "data": {
        "type": "ametrin_structures:compound",
        "sources": [
          { "template": "examplemod:crypt/large" },
          { "template": "examplemod:crypt/large_cellar", "offset": [4, -6, 4] }
        ]
    } }
  ]
}
```

`ametrin_structures:jigsaw` is covered in [jigsaw layouts](jigsaw.md#json).

| Filter | Fields |
|---|---|
| `ametrin_structures:flatness` | `max_variance`, `margin` |
| `ametrin_structures:height_range` | `min` and `max`, each a Y or a height anchor, `check` is `origin` (default) or `box` |
| `ametrin_structures:ground_check` | `blocks`, a block tag |
| `ametrin_structures:submerged` | `min_depth` |
| `ametrin_structures:water_depth` | `min`, `max` |
| `ametrin_structures:within_biome` | `biomes`, defaults to the structure's, `margin` |

The placements are `ametrin_structures:even_spread` with `min_distance` and `ametrin_structures:scattered_grid` with `spacing`, `random_offset` and `grid_offset`. Both take `probability`, `salt`, `min_chunks_from_center`, `locate_offset` and `exclusion_zone` with `structures` and `chunk_count`:

```json
{
  "type": "ametrin_structures:even_spread",
  "min_distance": 18,
  "probability": 0.6,
  "salt": 1849263
}
```

Reference: [`SimpleStructure`](../src/main/java/com/ametrin/structures/structure/simple/SimpleStructure.java), [`PieceSources`](../src/main/java/com/ametrin/structures/structure/simple/PieceSources.java), [`TemplateEntry`](../src/main/java/com/ametrin/structures/structure/simple/TemplateEntry.java), [`PlacementFilters`](../src/main/java/com/ametrin/structures/structure/filter/PlacementFilters.java), [`EvenSpreadPlacement`](../src/main/java/com/ametrin/structures/placement/EvenSpreadPlacement.java) and [`ScatteredGridPlacement`](../src/main/java/com/ametrin/structures/placement/ScatteredGridPlacement.java).
