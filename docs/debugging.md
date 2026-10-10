# Debugging structures

## Spread report

```
/ametrin structures spread <structure|#tag> [radius] [color] [rejected [all]]
/ametrin structures spread set <structure_set> [radius] [color] [rejected [all]]
```

Reports where a structure would generate around you and why it doesn't generate in other spots. It doesn't generate anything. It works for any structure, not just this library's, and needs operator permissions.

- With a tag, it reports all structures in the tag, through every structure set that has one of them.
- With `set`, it reports all structures in that set, using only that set's placement. This is useful to check how a set spreads, e.g. one whose structures take each other's spots.
- For a tag or a set, the report also counts the spots per structure.
- `radius` is in chunks and defaults to 128.
- The report shows how many candidate chunks the placement picked and how many of them generate, the most common
  reasons for the rest, the start heights, the average and smallest distance between spots, and the nearest spot.
- It also times the slowest structures: the total time and the time per candidate chunk to find spots and build pieces. For this library's structures, the time is split into creating the pieces, finding the start height and each filter. The first report after starting the game also loads templates and warms up the JVM, so run it twice before you trust the numbers. Placing blocks isn't timed, because the report doesn't place anything.
- This library's simple and jigsaw structures give specific reasons: wrong biome, empty start height range, which filter failed, or another structure in the same set taking the spot. Other structures can only say they found no spot.

To tune filters, change a value, run datagen, reopen the world and run the report again. The report doesn't depend on which chunks exist, so you can keep using the same world.

### Trying placements

```
/ametrin structures spread set <structure_set> placement <placement> [radius] [color] [rejected [all]]
/ametrin structures spread placement <placement> [radius] [color]
```

Try a placement without running datagen or reopening the world. `<placement>` is the `placement` of a structure set
written as SNBT, e.g. `{type:"ametrin_structures:even_spread",salt:1,min_distance:20}`.

- `set … placement` reports the set like above, but with this placement. Press Tab to fill in the set's current
  placement and edit it.
- `placement` only reports which chunks the placement picks, no matter what would generate there: how many, their
  spacing and the nearest one. It's fast even for large radii. Its waypoints and visits are the picked chunks.
- Each run gets a number and keeps the waypoints of earlier runs, in a random color unless you pass `color`. That way
  you can compare placements on the map. `rejected` still replaces all waypoints, and `spread clear` resets the
  numbering.
- Concentric rings are computed when the world loads, so they can't be tried this way.

### Visiting spots

```
/ametrin structures visit next|previous|<number>
```

Teleports you through the spots of your last report, nearest first.

### Map waypoints

With [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap) installed, the spots also appear as waypoints, named after the structure at each spot. They use `color`, or a color based on the reported id. Reports for different structures, tags or sets stay on the map together, so you can compare them.

- `rejected` adds the failed spots inside the structure's biomes as gray waypoints, named after the reason.
  `rejected all` adds every failed spot.
- Both remove all earlier waypoints first, so the gray spots don't get mixed up with another report's.
- `/ametrin structures spread clear` removes all of them.

## Checking structures

```
/ametrin structures check [namespace]
```

Checks every structure, or the ones in a namespace, for mistakes that would otherwise just show up as missing parts:

- templates that don't exist
- jigsaws that name a pool that doesn't exist or is empty, or that no jigsaw in their pool can connect to, because none has the target name or all of them face the wrong way
- a start jigsaw name that no template in the start pool has
- loot tables that containers in templates use but that don't exist
- fixtures in templates, and the presets they use, that can't be read or that use something that doesn't exist, like  a loot table, preset, spawner profile or entity
- fixtures in templates with an offset of more than 16 blocks along an axis

It checks simple and jigsaw structures and tells you how many of other types it skipped. Chat shows the first 50 problems, and the log has all of them.

A jigsaw that other pieces attach to isn't checked, because the attached piece replaces it. This skips the many vanilla jigsaws that point back to where they came from.

## Fixtures

"Generate now" in a fixture's screen runs it in place like generation would, but ignores its generation chance. The fixture goes back into your inventory first, with all its settings, so you can place it again.

If a value can't be parsed or isn't accepted, the fixture uses its default instead. Turn on debug logging to see why.

Broken fixture presets are logged as warnings when the server starts: unknown fixture types, fields or presets, values
that can't be parsed, and presets that use each other in a loop.

## Structure blocks

The structure block's name box completes every template id the server can load, from mods, datapacks and the world's saved templates.

`/ametrin structures save [radius]` saves every structure block in save mode within `radius` blocks (64 by default), as if you pressed each one's save button.

### Exporting templates

```
/ametrin structures export_templates [namespace]
```

Copies the templates your structure blocks saved in this world (in `<world>/generated/`) into the project's resources at `data/<namespace>/structure/`, so they ship with the mod. It tells you how many were new, changed or unchanged.

- Only namespaces that already have a `data/<namespace>` folder in your resources are exported. Edited vanilla
  templates or other mods' templates stay out.
- The world keeps its saved copies, and the game loads those before the mod's own. Delete them from `generated/` once
  you're done, otherwise they hide later changes to your resources.
- By default the resources are `src/main/resources` next to the `run` folder. For a different layout, list the folders
  in the `ametrin_structures.template_sources` system property, separated like a classpath:

  ```groovy
  configureEach {
      systemProperty 'ametrin_structures.template_sources', file('src/main/resources').absolutePath
  }
  ```

The command only exists in a development environment, never on a real server or client, because it writes into the project.

## Debug logging

Some details are only logged at debug level, like loot that didn't fit into a container. The run configurations from NeoForge's mod template log at that level.
