# Debugging structures

## Spread report

```
/ametrin structures spread <structure|#tag> [radius] [color] [rejected [all]]
/ametrin structures spread set <structure_set> [radius] [color] [rejected [all]]
```

Reports where a structure would generate around you, and why it doesn't generate elsewhere, without
generating anything. Works for every structure, not just this library's, and needs operator
permissions.

- A tag reports all its structures at once, through every structure set that holds one of them.
- `set` reports all the structure set's structures, and only that set's placement. Use it to
  check how a set spreads, such as a set whose structures exclude each other's spots.
- For a tag or a set, the report also counts the spots of each structure.
- `radius`: how far to look, in chunks. Defaults to 128.
- The report lists how many candidate chunks the placement picked and how many of them generate,
  the most common reasons for the others, the start heights found, the average and smallest distance
  between spots and the nearest spot.
- It also times the slowest structures: how long each took to find its spots and build its pieces,
  in total and per candidate chunk. For the library's structures it splits the time into creating
  the pieces, finding the start height and each filter. The first report after starting the game also
  loads the templates and warms up Java, so run it twice before trusting the numbers. Placing the
  blocks isn't timed, since the report places nothing.
- Reasons for the library's simple and jigsaw structures are specific: wrong biome, empty start
  height range, which filter failed, or another structure of the same set taking the spot. Other
  structures only report that they found no spot.

Use it to tune filters: change a value, run datagen, reopen the world and run the report again. The
report doesn't depend on which chunks exist, so the same world works.

### Trying placements

```
/ametrin structures spread set <structure_set> placement <placement> [radius] [color] [rejected [all]]
/ametrin structures spread placement <placement> [radius] [color]
```

Try a placement without datagen or reopening the world. `<placement>` is written like the
`placement` of a structure set, in SNBT, such as
`{type:"ametrin_structures:even_spread",salt:1,min_distance:20}`.

- `set … placement` reports the set as above, with that placement instead of its own.
- `placement` reports only the chunks the placement picks, whatever would generate there: how many,
  their spacing and the nearest. It's fast even over large radii. Its waypoints and visits are the
  picked chunks.
- Concentric rings are worked out when the world loads, so they can't be tried this way.

### Visiting spots

```
/ametrin structures visit next|previous|<number>
```

Teleports you through the spots of your last report, nearest first.

### Map waypoints

With [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap) installed, the spots also appear as
waypoints, in `color` or a color picked from the reported id, named by the structure at each spot.
Reports for different structures, tags or sets stay side by side, so you can compare them.

- `rejected` adds the failed spots inside the structure's biomes as gray waypoints, each named by its
  reason. `rejected all` adds every failed spot.
- Both replace all earlier waypoints, so the gray spots are never mixed up with another report's.
- `/ametrin structures spread clear` removes all of them.

## Checking structures

```
/ametrin structures check [namespace]
```

Looks through every structure, or those of one namespace, for mistakes that would otherwise only show
as a structure that is missing parts:

- templates that don't exist
- jigsaws that name a pool that doesn't exist or is empty, or that no jigsaw in their pool can take,
  because none has the target name or all of those face the wrong way
- a start jigsaw name that no template in the start pool has
- loot tables that containers in templates name but that don't exist
- fixtures in templates, and the presets they use, that don't read or that name something that
  doesn't exist, such as a loot table, preset, spawner profile or entity
- fixtures in templates whose offset is more than 16 blocks along an axis

It looks into simple and jigsaw structures and says how many of other types it skipped. Chat shows
the first 50 problems; the log lists all of them.

A jigsaw that other pieces attach to isn't checked, since the attached piece takes its place. That
leaves out the many vanilla jigsaws that point back the way they came.

## Fixtures

"Generate now" in a fixture's screen runs it in place, the way generation would, ignoring its
generation chance. The fixture goes back into your inventory first, with all its settings, so you
can place it again.

A value the fixture can't parse or doesn't accept falls back to its default. Turn on debug logging to see
why.

Broken fixture presets are logged as warnings when the server starts: unknown fixture types,
parameters or presets, values that don't parse, and presets that use each other in a cycle.

## Structure blocks

The structure block's name box completes the ids of every template the server can load, from mods,
datapacks and the world's saved templates.

`/ametrin structures save [radius]` saves every structure block in save mode within
`radius` blocks, 64 by default, as if you had pressed each one's save button.

### Exporting templates

```
/ametrin structures export_templates [namespace]
```

Copies the templates this world's structure blocks saved, from `<world>/generated/`, into the
project's resources at `data/<namespace>/structure/`, so they ship with the mod. It reports how many
were new, changed or already there.

- Only namespaces the resources already have a `data/<namespace>` folder for are exported, so edited
  vanilla or other mods' templates stay out.
- The world keeps its saved copies. The game reads them before the mod's own, so the world already
  shows what the next build will. Delete them from `generated/` once you no longer need them, or they
  keep hiding later changes to the resources.
- The resources are `src/main/resources` next to the `run` folder. For another layout, list the
  folders in the `ametrin_structures.template_sources` system property, separated like a class path:

  ```groovy
  configureEach {
      systemProperty 'ametrin_structures.template_sources', file('src/main/resources').absolutePath
  }
  ```

The command only exists in a development environment, never on a real server or client, since it
writes into the project.

## Debug logging

Some details are only logged at debug level, such as loot that found no room in a filled container.
The run configurations of NeoForge's mod template log at that level.
