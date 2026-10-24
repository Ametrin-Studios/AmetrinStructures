## 0.3.0-beta
- jigsaw size as an `IntProvider`
- per structure processors for jigsaw structures
- `RemoveUnsupportedProcessor`
- renamed `HeightMode` types
- added `HeightMode.HIGHEST_CORNER`
- `HeightMode` is now specified by the `HeightAnchor` and not the structure
- Foam blocks placed by players no longer replace replaceable blocks
- Check biomes in the origin column before sampling terrain height

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