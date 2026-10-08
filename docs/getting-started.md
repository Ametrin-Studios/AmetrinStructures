Getting started
===============

This guide takes you from an empty mod to a structure generating in the world. It assumes you know how to build a
structure with structure blocks and how NeoForge datagen works.

1. Depend on the library

------------------------

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

Declare the dependency in `neoforge.mods.toml`, Ametrin Structures needs to load before your mod:

```toml
[[dependencies.examplemod]]
modId = "ametrin_structures"
type = "required"
versionRange = "[0.1.0,)"
ordering = "AFTER"
side = "BOTH"
```

2. Build the template

------------------------------

Before saving, fill every space that should keep its air, such as rooms and hallways, with foam
from the operator items tab. Foam turns into air when the structure generates. Air blocks get treated as structure void
so the structure blends into exising terrain.

Placing a foam item while crouching fills the space based on rules defined by the foam item. Their tooltips explain the
spreading. Interior Foam is probably what you need most of the time. To remove a blob of foam, use an amethyst shard on
it in creative mode.

3. Declare the structure

------------------------

```java
public final class ExampleStructures {
    public static final DeferredStructureRegister REGISTER = new DeferredStructureRegister("examplemod");

    public static final DeferredStructureHolder RUINED_TOWER = REGISTER.set("ruined_tower") // create the structure set
            .scatteredGridPlacement(24, 0.6F)
            .simple(tower -> tower // a single simple structure in the set
                    .surface() // place on the worlds surface
                    .single("ruined_tower") // a single template is placed
                    .filterFlatness(3)
                    .biomes(BiomeTags.IS_FOREST))
            .build();
}
```

- `scatteredGridPlacement(24, 0.6F)`: one attempt per 24 by 24 chunk cell, 60% of which go ahead.
- `single("ruined_tower")`: places the template `examplemod:ruined_tower`.
- `surface()`: the structure's origin sits on the terrain.
- `filterFlatness(3)`: skips spots where the terrain under it varies by more than 3 blocks.
- `biomes(...)`: where it may generate. Defaults to every overworld biome.

`build()` checks everything right away, so a mistake shows up when datagen runs, not while you
explore a world.

4. Wire it up

-------------

Two calls connect the register to the game:

```java
// Mod constructor
ExampleStructures.REGISTER.register(modBus);

// Datagen
modBus.

addListener(GatherDataEvent.Client .class, event ->{
var registries = new RegistrySetBuilder();
    ExampleStructures.REGISTER.

bootstrap(registries);
    event.

createDatapackRegistryObjects(registries);
});
```

Run datagen, then start the game. `/locate structure examplemod:ruined_tower` finds your structure.
If it doesn't show up where you expect, [`/ametrin structures spread`](debugging.md) tells you why.

Going further
-------------

The builder has more to offer than the example shows; your IDE's completion lists it all. Some
highlights:

**Several templates.** `weighted(...)` picks one of several templates at random, `compound(...)`
places several together. Each template can have its own offset and processors.

**Vertical placement.** `surface()` sits on the ground, or on the water where there is any;
`oceanFloor()` sits on the ground below the water. `between(HeightAnchor.aboveBottom(8),
HeightAnchor.surface(-24))` picks a random height, e.g. for something buried.
`verticalPlacementMode(...)` sets where the terrain is measured: at the origin corner, averaged
over the corners, or at the lowest corner.

**Foundations.** `foundation()` extends the bottom of the structure down to the ground, so it doesn't
float on uneven terrain.

**Filters.** Besides flatness, filters check height ranges, the ground block, water depth, being
submerged, or that the whole structure stays inside its biomes.

**Structure settings.** `terrainAdaptation(...)`, `step(...)` and `noSpawns(...)` work like on any
vanilla structure.

**Overhangs.** Terrain adaptation fits the terrain to the whole template, so a wide roof or a
balcony gets a hill raised under it. Give the template a smaller box to fit to:

```java
tower.single(t ->t.

template("tower").

terrainBox(TerrainBox.footprint()))
```

`TerrainBox.footprint()` uses the blocks at and below ground level.
`TerrainBox.of(minX, minY, minZ, maxX, maxY, maxZ)` takes a box in the template's own coordinates,
as the structure block shows them; its bottom is where the terrain meets the structure.

**Several structures per set.** A set can hold several structures. Each spot tries them in weighted
order, and falls back to the next when one doesn't fit:

```java
REGISTER.set("graves")
        .

horizontalPlacement(new RandomSpreadStructurePlacement(20, 8,RandomSpreadType.LINEAR, 482_193))
        .

simple("small",grave ->grave.

single("graves/small").

surface().

weight(3))
        .

simple("large",grave ->grave.

single("graves/large").

surface())
        .

build();
```

Structures in a set are named `<set>_<suffix>`, here `examplemod:graves_small` and
`examplemod:graves_large`. `horizontalPlacement(...)` accepts any vanilla placement.

**Jigsaw and custom structures.** When a structure outgrows the simple type, replace its `simple(...)`
with `structure(...)` and keep the rest. `ExtendedJigsawStructure.builder(...)` creates a jigsaw
structure, and `JigsawPools` declares its template pools in datagen with far less boilerplate. Its
elements run fixtures and remove foam like simple structures do, and `foundation()` on an element
extends that piece to the ground. Pools written by hand need `ametrin_structures:single_pool_element`
for that; vanilla's element places fixture blocks as they were saved.
Simple, jigsaw and custom structures can share one set.

**Fixtures.** A fixture block inside a template fills a container with loot, spawns an entity, places a
spawner or runs a feature when the structure generates, then disappears. Place one from the operator items tab and
right-click it to configure it. "Generate now" tries it out in place. Fixtures you use in many
places can share a [preset](extending.md#fixture-presets).

**Lake proofing.** Add a structure to the `#ametrin_structures:lake_proof` structure tag and lakes
won't carve into it.

**Datapacks.** Everything the builder sets is plain JSON after datagen, so datapacks can declare and
override structures the same way.

**Updating templates.** Templates saved in an older Minecraft version still load, but every world
fixes them again each time. `StructureTemplateUpdater` rewrites outdated templates in place during datagen.
Give the data run your resources as an input:

```gradle
programArguments.addAll '--input', file('src/main/resources/').getAbsolutePath()
```

```java
event.addProvider(new StructureTemplateUpdater(event.getInputs()));
```

Commit the rewritten files like any other change.

For a real-world example, see [Dungeons Enhanced](https://github.com/Ametrin-Studios/DungeonsEnhanced), a
source-available mod using
this library.

See [extending.md](extending.md) to add your own building blocks, and [debugging.md](debugging.md)
for the tools that help tune a structure.
