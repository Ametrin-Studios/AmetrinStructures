# Jigsaw layouts

A simple structure can grow a jigsaw layout from a template pool, from the same pools and jigsaw blocks as vanilla's jigsaw structures:

```java
STRUCTURES.registerSet("castle", set -> set
        .scatteredGridPlacement(40, 0.5F)
        .simple(castle -> castle
                .jigsaw(CastlePools.START, jigsaw -> jigsaw.size(8))
                .biomes(BiomeTags.IS_TAIGA)
                .terrainAdaptation(TerrainAdjustment.BEARD_THIN)));
```

The layout grows like vanilla's, with a few differences:

- The start height is measured under the start piece, like for other simple structures, and the start piece's ground level is at that height. With `startJigsawName(...)`, that jigsaw is at the origin instead, one block lower.
- The structure's [processors](structures.md#processors), [foundation](structures.md#foundations) and liquid settings apply to its pieces.
- It can be part of a compound structure, and grows around the other pieces.
- Pool elements can be [limited](#limiting-elements) in how often, how deep and where they appear.

The other settings work like on vanilla's jigsaw structures:

- `size(n)` sets how many jigsaws deep the layout grows, up to 128. At the last depth only fallback pools attach. `size(min, max)` picks a random depth per structure. Defaults to 7.
- `maxDistanceFromCenter(...)` keeps pieces within a distance of the start, 80 blocks by default.
- `useExpansionHack(true)` lets flat pieces like village streets hold taller buildings inside their footprint.
- `poolAliases(...)` and `dimensionPadding(...)`.

`maxBacktracks(n)` sets how hard the layout tries to reach the elements' minimum counts before it settles for fewer or gives up on the spot. Higher values reach them more often but take longer. Defaults to 16.

## Declaring pools

`JigsawPools` declares template pools in datagen, named `<modId>:<prefix><name>`:

```java
static final class CastlePools {
    static final ResourceKey<StructureTemplatePool> START = JigsawPools.key(MODID, "castle/start");

    static void bootstrap(BootstrapContext<StructureTemplatePool> context) {
        var pools = new JigsawPools(context, MODID, "castle/");

        pools.pool("start", pool -> pool
                .element("keep"));

        pools.pool("walls", pool -> pool
                .fallback("wall_ends")
                .element("wall_straight")
                .element("wall_corner")
                .element("wall_ruined", element -> element.weight(2).processors(ProcessorLists.MOSSIFY_10_PERCENT)));

        pools.pool("wall_ends", pool -> pool
                .element("wall_end"));

        pools.pool("moat", pool -> pool
                .terrainMatching()
                .elements(List.of("moat_straight", "moat_corner"), element -> element
                        .processors(List.of(RemoveFoamProcessor.WATER)))
                .empty(1));
    }
}

// Datagen, before STRUCTURES.addTo(registries)
registries.add(Registries.TEMPLATE_POOL, CastlePools::bootstrap);
```

- `element(...)` and `elements(...)` add templates from the mod's namespace, prefixed like the pool.
- `fallback(...)` sets the pool used where nothing else fits. Defaults to `minecraft:empty`.
- `terrainMatching()` projects the elements onto the terrain, like village paths.
- `empty(weight)` adds an empty element, and `add(...)` any other element, like a feature or list element.
- `defaultElementSettings(...)` sets what every element starts with, on a pool for its elements or on `JigsawPools` for the pools declared after it.

An element takes a `weight(...)` and the same template settings as a simple structure's templates: `processors(...)`, `foundation(...)`, `liquidSettings(...)` and `noFoamProcessing()`.

The elements are `ametrin_structures:single_pool_element`s. They run fixtures and remove foam like simple structures do. If you write pools by hand, use that element type to get the same. Vanilla's `minecraft:single_pool_element` places fixture blocks as they are.

## Limiting elements

An element can be limited per structure:

- `maxCount(n)`: at most `n`.
- `softMinCount(n)`: the layout tries to place at least `n`, and generates with fewer where they don't fit.
- `minCount(n)`: at least `n`. A spot where the layout can't fit them gets no structure, so the structure generates less often. The soft minimum defaults to this.

The counts only apply once the layout reaches a pool that has the element. Making room for one element never takes away pieces another element needs for its soft minimum, except to reach a minimum, which can take others down to their minimum.

It can also be limited to where it attaches:

- `minDepth(n)` and `maxDepth(n)`: how many jigsaws deep, counted from the start piece.
- `filter(...)`: [placement filters](structures.md#filters) that the piece has to pass. They see the piece's position and box. For example, a shaft segment with a `HeightRange` filter that checks the box only fits while it stays above a Y, so the shaft ends there and the pool's fallback attaches.

Neither applies to a start piece.

```java
pools.pool("tents", pool -> pool
        .element("tent")
        .element("chief_tent", element -> element.softMinCount(1).maxCount(1).minDepth(2)));
```

Vanilla's jigsaw structures ignore these limits. [`/ametrin structures check`](debugging.md#checking-structures) reports limited elements in them.

## JSON

The structure's `pieces` are an `ametrin_structures:jigsaw` source:

```json
{
  "type": "ametrin_structures:simple",
  "biomes": "#minecraft:is_taiga",
  "spawn_overrides": {},
  "pieces": {
    "type": "ametrin_structures:jigsaw",
    "start_pool": "examplemod:camp/start",
    "size": 6
  }
}
```

| Field | Java | Value |
|---|---|---|
| `start_pool` | `jigsaw(startPool, ...)` | a template pool |
| `start_jigsaw_name` | `startJigsawName` | a jigsaw name |
| `size` | `size` | 0 to 128, or an int provider. Defaults to 7 |
| `max_distance_from_center` | `maxDistanceFromCenter` | like vanilla's. Defaults to 80 |
| `use_expansion_hack` | `useExpansionHack` | `true` or `false` (default) |
| `pool_aliases` | `poolAliases` | like vanilla's |
| `dimension_padding` | `dimensionPadding` | like vanilla's |
| `max_backtracks` | `maxBacktracks` | 0 to 1024. Defaults to 16 |

In a pool, a limited element is an `ametrin_structures:conditional` that wraps the element:

```json
{
  "fallback": "examplemod:camp/tent_ends",
  "elements": [
    {
      "weight": 1,
      "element": {
        "element_type": "ametrin_structures:single_pool_element",
        "location": "examplemod:camp/tent",
        "projection": "rigid"
      }
    },
    {
      "weight": 1,
      "element": {
        "element_type": "ametrin_structures:conditional",
        "element": {
          "element_type": "ametrin_structures:single_pool_element",
          "location": "examplemod:camp/chief_tent",
          "projection": "rigid",
          "foundation": {}
        },
        "soft_min_count": 1,
        "max_count": 1,
        "min_depth": 2
      }
    }
  ]
}
```

`ametrin_structures:single_pool_element` has vanilla's fields, with optional `processors`, plus `foundation` and `process_foam`, which defaults to `true`. `ametrin_structures:conditional` takes `min_count`, `soft_min_count`, `max_count`, `min_depth`, `max_depth` and `filters`, all optional.

Reference: [`JigsawSource`](../src/main/java/com/ametrin/structures/structure/jigsaw/JigsawSource.java), [`JigsawPools`](../src/main/java/com/ametrin/structures/structure/jigsaw/JigsawPools.java), [`ConditionalPoolElement`](../src/main/java/com/ametrin/structures/structure/jigsaw/ConditionalPoolElement.java) and [`ExtendedSinglePoolElement`](../src/main/java/com/ametrin/structures/structure/jigsaw/ExtendedSinglePoolElement.java).
