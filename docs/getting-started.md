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
versionRange = "[0.4.0,)"
ordering = "AFTER"
side = "BOTH"
```

## 2. Build the template

Air in a template is treated as structure void, so the structure blends into the terrain around it. Before you save, fill every space that should stay air, like rooms and hallways, with foam from the operator items tab. Foam turns into air when the structure generates.

Placing foam while sneaking fills the space around it. How it spreads depends on the foam item and its tooltip explains it. Interior Foam is what you want most of the time. To remove a blob of foam use an amethyst shard on it in creative mode.

To place loot, mobs or spawners, use [fixtures](fixtures.md).

### Saving and exporting

`/ametrin structures save [radius]` saves every structure block in save mode within `radius` blocks (64 by default), as if you pressed each one's save button.

`/ametrin structures export_templates [namespace]` copies the templates your structure blocks saved in this world (in `<world>/generated/`) into the project's resources at `data/<namespace>/structure/`, so they ship with the mod.

- Only namespaces that already have a `data/<namespace>` folder in your resources are exported. Edited vanilla templates or other mods' templates stay out.
- The world keeps its saved copies, and the game loads those before the mod's own. Delete them from `generated/` once you're done, otherwise they hide later changes to your resources.
- By default the resources are `src/main/resources` next to the `run` folder. For a different layout, list the folders in the `ametrin_structures.template_sources` system property, separated like a classpath:

  ```groovy
  configureEach {
      systemProperty 'ametrin_structures.template_sources', file('src/main/resources').absolutePath
  }
  ```

The command only exists in a development environment, because it writes into the project.

### Templates from older versions

Templates saved in an older Minecraft version still load, but the game upgrades them every time it loads them. `StructureTemplateUpdater` upgrades them in place during datagen. Pass your resources to the data run as an input:

```gradle
programArguments.addAll '--input', file('src/main/resources/').getAbsolutePath()
```

```java
// Datagen
event.addProvider(new StructureTemplateUpdater(event.getInputs()));
```

Then commit the updated files. Running it once after each Minecraft update is enough.

## 3. Declare the structure

```java
public final class ExampleStructures {
    public static final StructureBootstrap STRUCTURES = new StructureBootstrap("examplemod");

    public static final StructureSetKeys RUINED_TOWER = STRUCTURES.registerSet("ruined_tower", set -> set
            .evenSpreadPlacement(18, 0.6F)
            .modular(tower -> tower
                    .surface()
                    .single("ruined_tower")
                    .filterFlatness(3)
                    .biomes(BiomeTags.IS_FOREST)));
}
```

- `registerSet(...)`: declares a structure set and returns the keys of the set and its structures.
- `evenSpreadPlacement(18, 0.6F)`: attempts are at least 18 chunks apart, about 24 on average, and 60% of them generate.
- `modular(...)`: adds a modular structure to the set.
- `surface()`: puts the structure's origin on the surface.
- `single("ruined_tower")`: places the template `examplemod:ruined_tower`.
- `filterFlatness(3)`: skips spots where the ground varies by more than 3 blocks. Use sparingly.
- `biomes(...)`: where it can generate. Defaults to all overworld biomes.

## 4. Add it to datagen

Structures are fully data-driven, so they only need to be added in datagen:

```java
modBus.addListener(GatherDataEvent.Client.class, event -> {
    var registries = new RegistrySetBuilder();
    ExampleStructures.STRUCTURES.addTo(registries);
    event.createDatapackRegistryObjects(registries);
});
```

Run datagen and start the game. `/locate structure examplemod:ruined_tower` should find it. If it doesn't generate where you expect, the [spread report](debugging.md#spread-report) helps you figure out why.

## Next steps

- [Structures](structures.md) covers the rest of the builder: several templates, heights, foundations, terrain adaptation, filters, processors and sets with several structures.
- [Jigsaw layouts](jigsaw.md) covers structures grown from template pools.
- [Fixtures](fixtures.md) covers loot, mobs and spawners.
- [Debugging](debugging.md) covers the commands for tuning structures.
