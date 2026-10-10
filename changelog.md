## 0.2.0-beta
- `EvenSpreadPlacement`, now the default placement
- `/ametrin structures spread placement` and `spread set <set> placement` to try placements without datagen
- `StructureBootstrap` replaces `DeferredStructureRegister`. It's datagen only and no longer registers structure or piece types
- filters on custom structures, through a factory that takes `ExtendedStructureSettings`
- `ExtendedTemplateStructurePiece` handles the terrain box and terrain adaptation
- `SimpleStructure.builder(id)` and getters on `SimpleStructure` and `ExtendedJigsawStructure`
- placement builders can be shared between sets
- update surrounding block shapes when executing a fixture
- `SpawnDataBuilder.spawnRules`

## 0.1.0-beta
- initial release
- foam
- fixtures
- spawner profiles
- simple structure
- extended jigsaw structure