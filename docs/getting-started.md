# Getting started

This guide takes you from an empty mod to a structure generating in the world. It assumes you know how to build and save a structure with structure blocks and how NeoForge datagen works.

## 1. Add the dependency

Add the Ametrin Studios maven and the library to your `build.gradle`:

```gradle
repositories {
    maven {
        name "Ametrin Studios Maven"
        url "https://github.com/Ametrin-Studios/maven/raw/main/"
    }
}

dependencies {
    implementation "com.ametrin.structures:ametrin_structures-${minecraft_version}:${ametrin_structures_version}"
}
```

Declare the dependency in `neoforge.mods.toml`. Ametrin Structures has to load before your mod:

```toml
[[dependencies.examplemod]]
modId = "ametrin_structures"
type = "required"
versionRange = "[0.1.0,)"
ordering = "AFTER"
side = "BOTH"
```

## 2. Build the template

Air in a template is treated as structure void, so the structure blends into the terrain around it. Before you save, fill every space that should stay air, like rooms and hallways, with foam from the operator items tab. Foam turns into air when the structure generates.

Placing foam while sneaking fills the space around it. How it spreads depends on the foam item, and its tooltip explains it. Interior Foam is what you want most of the time. To remove a blob of foam, use an amethyst shard on it in creative mode.

## 3. Declare the structure

```java
public final class ExampleStructures {
    public static final StructureBootstrap STRUCTURES = new StructureBootstrap("examplemod");

    public static final StructureSetKeys RUINED_TOWER = STRUCTURES.registerSet("ruined_tower", set -> set
            .evenSpreadPlacement(18, 0.6F)
            .simple(tower -> tower
                    .surface()
                    .single("ruined_tower")
                    .filterFlatness(3)
                    .biomes(BiomeTags.IS_FOREST)));
}
```

- `registerSet(...)`: declares a structure set and returns the keys of the set and its structures.
- `evenSpreadPlacement(18, 0.6F)`: attempts are at least 18 chunks apart, about 24 on average, and 60% of them generate.
- `simple(...)`: adds a simple structure to the set.
- `surface()`: puts the structure's origin on the surface.
- `single("ruined_tower")`: places the template `examplemod:ruined_tower`.
- `filterFlatness(3)`: skips spots where the ground varies by more than 3 blocks. Use sparingly.
- `biomes(...)`: where it can generate. Defaults to all overworld biomes.

## 4. Add it to datagen

The structures are fully data-driven, so they only need to be added in datagen:

```java
modBus.addListener(GatherDataEvent.Client.class, event -> {
    var registries = new RegistrySetBuilder();
    ExampleStructures.STRUCTURES.addTo(registries);
    event.createDatapackRegistryObjects(registries);
});
```

Run datagen and start the game. `/locate structure examplemod:ruined_tower` should find it. If it doesn't generate where you expect, [`/ametrin structures spread`](debugging.md) helps you figure out why.

## Going further

The builders can do a lot more than this example. Your IDE's completion lists everything. Some highlights:

**Several templates.** `weighted(...)` picks one of several templates at random. `compound(...)` places several together. Each template can have its own offset, processors, foundation and liquid settings, like an element in a template pool. `element(...)` places any pool element instead of a template, like a feature or list element. In JSON, a template is `template` with optional `processors`, or `element` with a pool element.

**Height.** `surface()` places the structure on the ground, or on top of water. `oceanFloor()` places it on the ground under water. `between(HeightAnchor.aboveBottom(8), HeightAnchor.surface(-24))` picks a random height in a range, e.g. for something buried. A `HeightMode` on a heightmap anchor, e.g. `surface(0, HeightMode.LOWEST_CORNER)`, sets where the terrain height is measured: at the origin, as the average of the corners, or at the lowest corner.

**Foundations.** `foundation()` extends the bottom of the structure down to the ground, so it doesn't float on uneven terrain.

**Filters.** Besides flatness there are filters for the height range, water depth, being submerged and staying inside the correct biomes.

**Structure settings.** `terrainAdaptation(...)`, `step(...)` and `noSpawns(...)` work like on vanilla structures.

**Overhangs.** Terrain adaptation fits the terrain to the whole template, so a wide roof or a balcony gets a hill under it. Give the template a smaller box instead:

```java
tower.single(t -> t
        .template("tower")
        .terrainBox(TerrainBox.footprint()))
```

`TerrainBox.footprint()` only uses the blocks at and below ground level. `TerrainBox.of(minX, minY, minZ, maxX, maxY, maxZ)` takes a box in template coordinates, as the structure block shows them. The terrain meets the structure at the bottom of the box.

**Several structures per set.** A set can hold more than one structure. They're tried in weighted order at each spot, and if one doesn't fit, the next one gets a chance. The weight goes after the suffix, like for `existing(...)`:

```java
STRUCTURES.registerSet("graves", set -> set
        .horizontalPlacement(new YourCustomPlacement(...)) // you can also keep using evenSpreadPlacement
        .simple("small", 3, grave -> grave
                .single("graves/small")
                .surface())
        .simple("large", 1, grave -> grave
                .single("graves/large")
                .surface()));
```

Structures in a set are named `<set>/<suffix>`, here `examplemod:graves/small` and `examplemod:graves/large`. `horizontalPlacement(...)` takes any placement, vanilla or your own.

`onlyWhenPickedFirst()` keeps a structure from being tried as a fallback when another one of the set didn't fit. It can be used to optimize variants that fail at the same spots: each spot is measured once, and locating one no longer stops at the others' spots.

**Custom structures.** For something the simple type can't do, use `structure(...)` instead of `simple(...)`. It takes the same settings and weight argument as `simple(...)`, and both can be mixed in one set.

**Jigsaw layouts.** `simple(...)` can grow a jigsaw layout with `.jigsaw(startPool, jigsaw -> jigsaw.size(6))`, or in JSON with pieces of type `ametrin_structures:jigsaw`. `JigsawPools` helps you declare template pools in datagen. Its elements run fixtures and remove foam like simple structures do, and `foundation()` on an element extends that piece down to the ground. If you write pools by hand, use `ametrin_structures:single_pool_element` to get the same. Vanilla's element places fixture blocks as they are.

The layout grows like vanilla's, but measures the start height under the start piece like other simple structures, and it keeps conditional elements within their counts. Set them with `softMinCount(n)`, `minCount(n)` and `maxCount(n)` on a `JigsawPools` element, or with `ametrin_structures:conditional` and its `soft_min_count`, `min_count` and `max_count` in a pool written by hand. The layout tries to place at least `soft_min_count` of the element: while it has fewer, jigsaws that can't lead to it wait until the rest of the layout is done, so other pieces don't take its space first, and when the layout still ends with too few, it finds the earliest jigsaw where the element fits once the pieces in its way are undone, attaches it there and grows the rest of the layout back around it. It doesn't take away pieces another element needs for its `soft_min_count`, except to reach a `min_count`, which may take others down to their `min_count`. If that doesn't work either, the structure generates with fewer. A spot where the layout can't fit `min_count` gets no structure, so a `min_count` above 0 makes the structure generate less often. `soft_min_count` defaults to `min_count`. The counts only apply once the layout reaches a pool that has the element. Vanilla's jigsaw structures ignore the counts.

An element can also be limited to where it attaches: `minDepth(n)` and `maxDepth(n)` set how many jigsaws deep, and `filter(...)` adds placement filters that its piece has to pass, in JSON `min_depth`, `max_depth` and `filters`. The filters see the piece's position and box. For example, a shaft segment with `height_range` and `"check": "box"` only fits while it stays above a Y or below the surface, so the shaft ends there and the pool's fallback attaches.

**Fixtures.** A fixture block in a template turns into something else when the structure generates. It can fill a container with loot, spawn an entity, place a spawner, run a feature and more. Place one from the operator items tab and right-click it to configure it. "Generate now" runs it in place so you can test it. If you use the same setup in many places, put it in a [preset](extending.md#fixture-presets).

**Lake proofing.** Lakes don't carve into structures in the `#ametrin_structures:lake_proof` structure tag.

**Updating templates.** Templates saved in an older Minecraft version still load, but the game upgrades them every time it loads them. `StructureTemplateUpdater` upgrades them in place during datagen. Pass your resources to the data run as an input:

```gradle
programArguments.addAll '--input', file('src/main/resources/').getAbsolutePath()
```

```java
// Datagen
event.addProvider(new StructureTemplateUpdater(event.getInputs()));
```

Then commit the updated files. Running it once after each Minecraft update is enough.

For a real example, see [Dungeons Enhanced](https://github.com/Ametrin-Studios/DungeonsEnhanced), a source-available mod built on this library. [extending.md](extending.md) covers adding your own building blocks, and [debugging.md](debugging.md) the tools for tuning structures.

If you have any questions, ask on the [Ametrin Studios discord](https://discord.gg/Ye6WxRV2Tt).
