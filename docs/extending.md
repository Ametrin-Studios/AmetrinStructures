# Extending the library

Most parts of the library are registry based. Implement the interface, give it a `MapCodec`, and register a type for it in the library's registry with a normal `DeferredRegister`. It then works from the Java builders and from JSON. The registry keys are in `ASRegistries`.

## Placement filters

A filter decides if a modular structure, or any other `ExtendedStructure`, can generate at a spot.

```java
public record MinHeight(int y) implements PlacementFilter {
    public static final MapCodec<MinHeight> CODEC = Codec.INT.fieldOf("y").xmap(MinHeight::new, MinHeight::y);

    @Override
    public boolean test(Context context) {
        return context.origin().getY() >= y;
    }

    @Override
    public PlacementFilterType type() {
        return MIN_HEIGHT.get();
    }
}

static final DeferredRegister<PlacementFilterType> FILTERS = DeferredRegister.create(ASRegistries.PLACEMENT_FILTER_TYPE, MODID);
static final DeferredHolder<PlacementFilterType, PlacementFilterType> MIN_HEIGHT = FILTERS.register("min_height", () -> new PlacementFilterType(MinHeight.CODEC));
```

Add it with `.filter(new MinHeight(40))` on any structure builder. The context has the start position, the bounding box of all pieces and a `TerrainSampler`. Use the sampler instead of the chunk generator for terrain heights. It caches the lookups and shares them with the other filters.

The spread report names a failed filter by its type id, so your filters show up there without extra work.

Reference: [`PlacementFilters`](../src/main/java/com/ametrin/structures/structure/filter/PlacementFilters.java) and [`ASPlacementFilters`](../src/main/java/com/ametrin/structures/registry/ASPlacementFilters.java).

## Piece sources

A piece source decides which pieces a modular structure places. Register a `PieceSourceType` in `ASRegistries.PIECE_SOURCE_TYPE` and pass your source to `.pieces(...)`.

Create pieces relative to `context.origin()`, which is at Y 0. The structure moves them to the start height afterwards, measured under them.

`PieceSources.createPiece(entry, context)` creates the same piece the built-in sources use. The context holds the structure's settings: terrain adaptation, liquid settings, foundation and processors. `createPiece` applies them. Pieces you create yourself need to take them from the context, since the structure doesn't hand them over afterwards.

Pieces that depend on the terrain or on the other pieces, like a jigsaw layout's, can be added in a step registered with `context.afterPlacement(...)`. It runs once every source appended its pieces and they're at their start height, and returns false to reject the spot.

`templates()` lists every template entry the source can place, so `/ametrin structures check` can check them.

Reference: [`PieceSources`](../src/main/java/com/ametrin/structures/structure/modular/PieceSources.java) and [`ASPieceSources`](../src/main/java/com/ametrin/structures/registry/ASPieceSources.java).

## Structures of your own

A set can hold any structure, not just modular ones. `structure(...)` takes a factory that creates your structure from the shared settings. Biomes, step, terrain adaptation and spawn overrides are set on the builder as usual. It takes the same suffix and weight arguments as `modular(...)`, and both can be mixed in one set:

```java
STRUCTURES.registerSet("castle", set -> set
        .scatteredGridPlacement(40, 0.5F)
        .structure((settings, _, context) -> new CastleStructure(settings), castle -> castle.biomes(BiomeTags.IS_TAIGA)));
```

Register its structure type and piece types with your own `DeferredRegister`s.

Extend `ExtendedStructure` to get filters, the biome check and detailed reasons in the spread report. You only implement `layOut`:

```java
public class CastleStructure extends ExtendedStructure {
    public static final MapCodec<CastleStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    settingsCodec(instance),
                    extendedSettingsCodec(instance))
            .apply(instance, CastleStructure::new));

    public CastleStructure(StructureSettings settings, ExtendedStructureSettings extendedSettings) {
        super(settings, extendedSettings);
    }

    @Override
    protected Either<Candidate, Evaluation> layOut(GenerationContext context, Timer timer) {
        var origin = context.chunkPos().getMiddleBlockPosition(64);
        var pieces = new StructurePiecesBuilder();
        // add the pieces
        return Either.left(new Candidate(origin, new TerrainSampler(context, Heightmap.Types.WORLD_SURFACE_WG),
                pieces::getBoundingBox, () -> new GenerationStub(origin, Either.right(pieces))));
    }

    @Override
    public StructureType<?> type() {
        return ExampleStructures.CASTLE_TYPE.get();
    }
}
```

The biome is checked at the origin once `layOut` returns. If laying out is slow and you know the origin's X and Z before its Y, return `Evaluation.WrongBiome` early when `hasValidBiome(context, x, z, minY, maxY)` is false for the Ys the origin can have. Modular structures do this before measuring the terrain.

Pass the extended settings on, and filters, processors and `onlyWhenPickedFirst()` work like on a modular structure. On a structure that isn't an `ExtendedStructure` they fail in datagen:

```java
.structure((settings, extendedSettings, context) -> new CastleStructure(settings, extendedSettings),
        castle -> castle.biomes(BiomeTags.IS_TAIGA).filterFlatness(4))
```

`ModularStructure` can be subclassed too. It has getters for all its settings, so a subclass can write its own codec.

Reference: [`ExtendedStructure`](../src/main/java/com/ametrin/structures/structure/ExtendedStructure.java).

### Template pieces

To place [template entries](structures.md#json) the way modular structures do, use `ModularStructurePiece.create(...)`. It takes the entry, the origin and rotation, and the structure's terrain adaptation, liquid settings, processors and foundation. Entries without their own processors or foundation take the structure's. A structure can keep its entries in its codec with `TemplateEntry.CODEC`:

```java
for (var entry : entries) {
    pieces.addPiece(ModularStructurePiece.create(context.structureTemplateManager(), entry, origin, rotation,
            terrainAdaptation(), LiquidSettings.IGNORE_WATERLOGGING, processors(), Optional.empty()));
}
```

For template pieces of your own, extend `ExtendedTemplateStructurePiece`. Like the modular structure's pieces, it runs fixtures, removes foam, places foundations, leaves out structure blocks, sets up mobs and fits the terrain to a `TerrainBox`. The piece decides its own terrain adaptation, so pass it the structure's `terrainAdaptation()`. An `ExtendedStructure` hands these pieces the structure's processors itself. Other template pieces place fixture blocks as they are.

Reference: [`ModularStructurePiece`](../src/main/java/com/ametrin/structures/structure/modular/ModularStructurePiece.java) and [`ExtendedTemplateStructurePiece`](../src/main/java/com/ametrin/structures/structure/ExtendedTemplateStructurePiece.java).

### Pool elements

A pool element type of your own can implement `StructureSettingsElement` to take the structure's processors and foundation in modular structures and their jigsaw layouts.

Reference: [`StructureSettingsElement`](../src/main/java/com/ametrin/structures/structure/jigsaw/StructureSettingsElement.java).

## Placements

`horizontalPlacement(...)` on a set takes any `StructurePlacement`, including your own.

`StructurePlacements.hasStructureChunkInRange(...)` checks if a structure, holder set or tag has a spot nearby. `StructureLookup` checks if a position or box is inside a structure. Both are useful for placements and features that should keep away from structures.

Reference: [`StructurePlacements`](../src/main/java/com/ametrin/structures/placement/StructurePlacements.java) and [`StructureLookup`](../src/main/java/com/ametrin/structures/placement/StructureLookup.java).

## Fixtures

A fixture is a record that implements `Fixture`. Its fields are `FixtureField`s. The codec is built from them, and so is the fixture's screen, with validation and completion from each field's `FieldType`.

```java
public record Lantern(boolean hanging) implements Fixture {
    static final FixtureField<Boolean> HANGING = FixtureField.withDefault("hanging", FieldType.bool(), true);
    static final MapCodec<Lantern> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    HANGING.forGetter(Lantern::hanging))
            .apply(instance, Lantern::new));

    @Override
    public void apply(FixtureContext context) {
        context.placeBlock(Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, hanging));
    }

    @Override
    public FixtureType type() {
        return LANTERN.get();
    }
}

static final DeferredRegister<FixtureType> FIXTURES = DeferredRegister.create(ASRegistries.FIXTURE_TYPE, MODID);
static final DeferredHolder<FixtureType, FixtureType> LANTERN = FIXTURES.register("lantern", () -> new FixtureType(Lantern.CODEC, List.of(Lantern.HANGING)));
```

A field is `required`, `withDefault` or `optional` (wrapped in an `Optional`). `FieldType` covers the common value types, like numbers, registry keys, block states, items and NBT. `validated` adds a check to one.

Override `references()` to return the registry keys the fixture uses, like loot tables. `/ametrin structures check` then reports the missing ones.

`context.placeBlock(...)` places a block the way the built-in fixtures do, turned to match the marker. For a block state field, use `BlockStateMerging.mergedBlockState()`. It leaves out the properties the marker sets. `Fixtures` has more shared helpers, like `spawnEntity(...)` and `placeSpawner(...)`.

To add your own [condition](fixtures.md#conditions) types, register a `FixtureConditionType` in `ASRegistries.FIXTURE_CONDITION_TYPE`.

Reference: the built-in fixtures, like [`LootContainerFixture`](../src/main/java/com/ametrin/structures/fixture/LootContainerFixture.java), and [`Fixtures`](../src/main/java/com/ametrin/structures/fixture/Fixtures.java).

## Foam

How foam spreads is stored on the item stack, not the block. `FoamPresets.stack(spread, name)` creates a foam item with any `FoamSpread`: a `FoamSpreadBehavior` that picks where each step goes, plus `FoamSpreadRestriction`s that block positions. Register your own in `ASRegistries.FOAM_SPREAD_BEHAVIOR_TYPE` and `ASRegistries.FOAM_SPREAD_RESTRICTION_TYPE`. Their tooltip line is the translation of `foam_spread_behavior.<namespace>.<path>` or `foam_spread_restriction.<namespace>.<path>`, which `FoamSpreadBehavior.descriptionKey(id)` and `FoamSpreadRestriction.descriptionKey(id)` give you, e.g. for a language provider. Override `description()` to pass arguments.

To fill some areas with something other than air, register your own foam block with a replacement state, plus a `FoamBlockItem`. Give it its own texture so you can tell the areas apart:

```java
static final DeferredBlock<FoamBlock> WATER_FOAM = BLOCKS.registerBlock("water_foam",
        properties -> new FoamBlock(Optional.of(Blocks.WATER.defaultBlockState()), properties),
        properties -> properties.noCollision().noOcclusion().noLootTable().instabreak());
static final DeferredItem<FoamBlockItem> WATER_FOAM_ITEM = ITEMS.registerItem("water_foam",
        properties -> new FoamBlockItem(WATER_FOAM.get(), properties),
        Item.Properties::useBlockDescriptionPrefix);
```

It dissolves and fills openings like the library's foam. When the structure generates it turns into its replacement state, no matter what the piece's `RemoveFoamProcessor` uses. Add a `ASDataComponents.FOAM_SPREAD` if you want it to spread by default. `FoamPresets.stack(item, spread, name)` creates stacks of it that do spread.

Reference: [`FoamSpreadBehaviors`](../src/main/java/com/ametrin/structures/foam/FoamSpreadBehaviors.java), [`FoamSpreadRestrictions`](../src/main/java/com/ametrin/structures/foam/FoamSpreadRestrictions.java) and [`FoamPresets`](../src/main/java/com/ametrin/structures/foam/FoamPresets.java).
