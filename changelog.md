## 0.4.0-beta
- simple structures are now modular structures: `ModularStructure`, `modular(...)`, `registerModular(...)`, `ametrin_structures:modular` and the piece type `modular_element`.
- `extended_jigsaw` structures are removed, modular structures grow jigsaw layouts with the `ametrin_structures:jigsaw` piece source instead
- `ametrin_structures:conditional` pool elements limit how often (`min_count`, `soft_min_count`, `max_count`), how deep (`min_depth`, `max_depth`) and where (`filters`) an element appears in a jigsaw layout.
- `fill_container` fixtures need a `block` and no longer accept blocks that take a loot table, use `loot_container` for those
- jigsaw blocks show their pool above them, toggled by the `jigsaw_name_tags` client config
- `HeightMode.CENTER`
- `ModularStructurePiece.create(...)` to use them in custom structures

## 0.3.2-beta
- `ConditionalProcessor` (only applies if all `NeoForgeCondition`s are true)
- `archeology` fixtures only accept brushable blocks
- improved `ametrin loot_stats` command
- `ametrin structures pieces` command
- `ametrin structures gallery` command
- `extend` fixture replaces `extend_down` and can grow up or down

## 0.3.1-beta
- `excluded_dimenstion` structure set data map
- `ametrin loot_stats` command

## 0.3.0-beta
- simplify the various `StructureBuilder`s
- jigsaw size as an `IntProvider`
- per structure processors for jigsaw structures
- `RemoveUnsupportedProcessor`
- renamed `HeightMode` types
- added `HeightMode.HIGHEST_CORNER`
- `HeightMode` is now specified by the `HeightAnchor` and not the structure
- Foam blocks placed by players no longer replace replaceable blocks
- Check biomes in the origin column before sampling terrain height
- `onlyWhenPickedFirst()` keeps a structure from being tried as a fallback
- show pool completions in jigsaw blocks

## 0.2.0-beta
- `EvenSpreadPlacement`, now the default placement
- `/ametrin structures spread placement` and `spread set <set> placement` to try placements without datagen
- `StructureBootstrap` replaces `DeferredStructureRegister`. It's datagen only and no longer registers structure or piece types
- filters on custom structures, through a factory that takes `ExtendedStructureSettings`
- `ExtendedTemplateStructurePiece` handles the terrain box and terrain adaptation
- `SimpleStructure.builder(id)` and getters on `SimpleStructure` and `ExtendedJigsawStructure`
- placement builders can be shared between sets
- update surrounding block shapes when executing a fixture
- split `SpawnDataBuilder` into `EntityDataBuilder`
- `SpawnDataBuilder.spawnRules`

## 0.1.0-beta
- initial release
- foam
- fixtures
- spawner profiles
- simple structure
- extended jigsaw structure