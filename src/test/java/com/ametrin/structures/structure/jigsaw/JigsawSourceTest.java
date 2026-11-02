package com.ametrin.structures.structure.jigsaw;

import com.ametrin.structures.structure.ExtendedStructure;
import com.ametrin.structures.structure.ExtendedStructureSettings;
import com.ametrin.structures.structure.Foundation;
import com.ametrin.structures.structure.InlineFromStructureProcessor;
import com.ametrin.structures.structure.filter.PlacementFilters;
import com.ametrin.structures.structure.modular.HeightAnchor;
import com.ametrin.structures.structure.modular.PieceSource;
import com.ametrin.structures.structure.modular.PieceSources;
import com.ametrin.structures.structure.modular.ModularStructure;
import com.ametrin.structures.structure.modular.StartHeight;
import com.ametrin.structures.structure.modular.TemplateEntry;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.ProcessorLists;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class JigsawSourceTest {
    private static final int SEEDS = 25;
    private static final int FAILED = -1;
    private static final int UNREACHED = -2;
    private static final ResourceKey<StructureTemplatePool> TOWN_CENTERS = pool("village/plains/town_centers");
    private static final ResourceKey<StructureTemplatePool> HOUSES = pool("village/plains/houses");
    private static final ResourceKey<StructureTemplatePool> STREETS = pool("village/plains/streets");
    private static final Identifier SMALL_HOUSE = Identifier.withDefaultNamespace("village/plains/houses/plains_small_house_1");
    private static final Identifier TEMPLE = Identifier.withDefaultNamespace("village/plains/houses/plains_temple_3");

    @Test
    void aVillageGrowsLikeVanilla(MinecraftServer server) {
        assertGrowsLikeVanilla(server, new JigsawSource.Builder(holder(server, TOWN_CENTERS)).size(6).useExpansionHack(true).build(), 64);
    }

    @Test
    void anAncientCityGrowsLikeVanilla(MinecraftServer server) {
        var source = new JigsawSource.Builder(holder(server, pool("ancient_city/city_center")))
                .startJigsawName(Identifier.withDefaultNamespace("city_anchor"))
                .size(7)
                .maxDistanceFromCenter(116)
                .build();
        assertGrowsLikeVanilla(server, source, -27);
    }

    @Test
    void aBastionGrowsLikeVanilla(MinecraftServer server) {
        assertGrowsLikeVanilla(server, new JigsawSource.Builder(holder(server, pool("bastion/starts"))).size(6).build(), 33);
    }

    @Test
    void aMaxCountIsNeverExceeded(MinecraftServer server) {
        var unlimited = countAcrossSeeds(server, SMALL_HOUSE, element -> element);
        assertTrue(unlimited.stream().anyMatch(count -> count > 1), "the test needs a house that appears more than once on its own, got " + unlimited);

        var limited = countAcrossSeeds(server, SMALL_HOUSE, element -> new ConditionalPoolElement(element, 0, 0, Optional.of(1)));
        assertTrue(limited.stream().allMatch(count -> count <= 1), "at most one, got " + limited);
    }

    @Test
    void aMinCountIsMetByBacktracking(MinecraftServer server) {
        var unlimited = countAcrossSeeds(server, TEMPLE, element -> element);
        assertTrue(unlimited.stream().anyMatch(count -> count >= 0 && count < 2), "the test needs a house that's often missing on its own, got " + unlimited);

        var required = countAcrossSeeds(server, TEMPLE, element -> new ConditionalPoolElement(element, 2, 2, Optional.empty()));
        var generated = required.stream().filter(count -> count != FAILED).toList();
        assertTrue(generated.stream().allMatch(count -> count == UNREACHED || count >= 2), "at least two wherever the houses are reached, got " + required);
        assertTrue(generated.size() >= SEEDS * 3 / 4, "most layouts can meet it, got " + required);
    }

    @Test
    void aSoftMinCountIsMetInMostLayoutsAndNeverRejectsTheSpot(MinecraftServer server) {
        // The library rarely fits, so without undoing earlier pieces it fits in fewer than half of the layouts.
        var counts = countAcrossSeeds(server, Identifier.withDefaultNamespace("village/plains/houses/plains_library_1"), element -> new ConditionalPoolElement(element, 0, 1, Optional.empty()));
        assertTrue(counts.stream().noneMatch(count -> count == FAILED), "every spot generates, got " + counts);
        assertTrue(counts.stream().anyMatch(count -> count == 0), "the test needs a layout that can't meet it, got " + counts);
        var reached = counts.stream().filter(count -> count != UNREACHED).toList();
        assertTrue(reached.stream().filter(count -> count >= 1).count() >= reached.size() * 3 / 4, "met in most layouts, got " + counts);
    }

    @Test
    void aFailedBacktrackLeavesNoJigsawEmpty(MinecraftServer server) {
        var templates = server.getStructureManager();
        // A street with a single jigsaw into the houses pool, and no streets attach to it, so the layout decides one house.
        var street = holder(server, STREETS).value().getShuffledTemplates(RandomSource.create(0)).stream()
                .filter(element -> element.getShuffledJigsawBlocks(templates, BlockPos.ZERO, Rotation.NONE, RandomSource.create(0)).stream().filter(jigsaw -> jigsaw.pool().equals(HOUSES)).count() == 1)
                .findFirst().orElseThrow();
        var house = holder(server, HOUSES).value().getShuffledTemplates(RandomSource.create(0)).stream()
                .filter(element -> element instanceof SinglePoolElement single && single.getTemplateLocation().equals(SMALL_HOUSE))
                .findFirst().orElseThrow();
        // Below -32, the copy never fits, so every backtrack for it fails.
        var below = new PlacementFilters.HeightRange(Optional.empty(), Optional.of(HeightAnchor.absolute(-32)), PlacementFilters.HeightRange.Check.BOX);
        var never = new ConditionalPoolElement(house, 0, 1, Optional.empty(), 0, Optional.empty(), List.of(below));
        var registered = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        var houses = Holder.direct(new StructureTemplatePool(registered.getOrThrow(pool("empty")), List.of(Pair.of(house, 1), Pair.of(never, 1))));
        Function<ResourceKey<StructureTemplatePool>, Optional<? extends Holder<StructureTemplatePool>>> lookup = key -> key.equals(HOUSES) ? Optional.of(houses) : key.equals(STREETS) ? registered.get(pool("empty")) : registered.get(key);
        // The test layouts stand on terrain at Y 0, where houses attached to a street that follows the terrain go.
        var position = BlockPos.ZERO;
        var box = street.getBoundingBox(templates, position, Rotation.NONE);
        for (int seed = 0; seed < SEEDS; seed++) {
            var context = context(server, seed);
            // Taller, like the expansion hack makes the streets that attach, so the house fits inside.
            var start = new PoolElementStructurePiece(templates, street, position, street.getGroundLevelDelta(), Rotation.NONE, new BoundingBox(box.minX(), box.minY() - 8, box.minZ(), box.maxX(), box.minY() + 32, box.maxZ()), LiquidSettings.IGNORE_WATERLOGGING);
            var free = JigsawAssembler.freeSpace(start.getBoundingBox(), 0, new JigsawStructure.MaxDistance(80), context.heightAccessor(), DimensionPadding.ZERO);
            var pieceContext = new PieceSource.Context(context, BlockPos.ZERO, Rotation.NONE, TerrainAdjustment.NONE, LiquidSettings.IGNORE_WATERLOGGING, Optional.empty(), Optional.empty());
            var pieces = new JigsawAssembler(pieceContext, lookup, 1, false, PoolAliasLookup.EMPTY, JigsawSource.DEFAULT_MAX_BACKTRACKS).assemble(start, street, free, List.of()).orElseThrow();
            assertTrue(pieces.stream().anyMatch(piece -> piece.getElement() instanceof SinglePoolElement single && single.getTemplateLocation().equals(SMALL_HOUSE)), "seed " + seed);
        }
    }

    @Test
    void anElementOnlyAttachesWithinItsDepths(MinecraftServer server) {
        var unlimited = countAcrossSeeds(server, SMALL_HOUSE, element -> element);
        assertTrue(unlimited.stream().anyMatch(count -> count > 0), "the test needs a house that appears on its own, got " + unlimited);
        // Every piece but the start is at least one jigsaw deep.
        var never = countAcrossSeeds(server, SMALL_HOUSE, element -> new ConditionalPoolElement(element, 0, 0, Optional.empty(), 0, Optional.of(0), List.of()));
        assertTrue(never.stream().allMatch(count -> count <= 0), "never at depth 0, got " + never);
    }

    @Test
    void anElementOnlyAttachesWherePiecesPassItsFilters(MinecraftServer server) {
        // The test layouts stand on terrain at Y 0, so no house is below -32.
        var below = new PlacementFilters.HeightRange(Optional.empty(), Optional.of(HeightAnchor.absolute(-32)), PlacementFilters.HeightRange.Check.BOX);
        var never = countAcrossSeeds(server, SMALL_HOUSE, element -> new ConditionalPoolElement(element, 0, 0, Optional.empty(), 0, Optional.empty(), List.of(below)));
        assertTrue(never.stream().allMatch(count -> count <= 0), "never below -32, got " + never);

        var above = new PlacementFilters.HeightRange(Optional.of(HeightAnchor.absolute(-32)), Optional.empty(), PlacementFilters.HeightRange.Check.BOX);
        var still = countAcrossSeeds(server, SMALL_HOUSE, element -> new ConditionalPoolElement(element, 0, 0, Optional.empty(), 0, Optional.empty(), List.of(above)));
        assertTrue(still.stream().anyMatch(count -> count > 0), "still where it passes, got " + still);
    }

    @Test
    void readsTheConditionsFromJsonAndRejectsInvalidOnes(MinecraftServer server) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var conditional = assertInstanceOf(ConditionalPoolElement.class, StructurePoolElement.CODEC.parse(ops, JsonParser.parseString("""
                {"element_type": "ametrin_structures:conditional", "min_depth": 2, "max_depth": 5,
                 "filters": [{"type": "ametrin_structures:height_range", "max": {"heightmap": "WORLD_SURFACE_WG"}, "check": "box"}],
                 "element": {"element_type": "minecraft:empty_pool_element"}}
                """)).getOrThrow());
        assertEquals(List.of(2, Optional.of(5)), List.of(conditional.minDepth(), conditional.maxDepth()));
        assertEquals(List.of(new PlacementFilters.HeightRange(Optional.empty(), Optional.of(HeightAnchor.surface(0)), PlacementFilters.HeightRange.Check.BOX)), conditional.filters());

        assertTrue(StructurePoolElement.CODEC.parse(ops, JsonParser.parseString("""
                {"element_type": "ametrin_structures:conditional", "min_depth": 5, "max_depth": 2, "element": {"element_type": "minecraft:empty_pool_element"}}
                """)).isError());

        var minOnly = assertInstanceOf(ConditionalPoolElement.class, StructurePoolElement.CODEC.parse(ops, JsonParser.parseString("""
                {"element_type": "ametrin_structures:conditional", "min_count": 2, "element": {"element_type": "minecraft:empty_pool_element"}}
                """)).getOrThrow());
        assertEquals(2, minOnly.softMinCount(), "the soft minimum defaults to the minimum");
        var all = assertInstanceOf(ConditionalPoolElement.class, StructurePoolElement.CODEC.parse(ops, JsonParser.parseString("""
                {"element_type": "ametrin_structures:conditional", "min_count": 1, "soft_min_count": 3, "max_count": 4, "element": {"element_type": "minecraft:empty_pool_element"}}
                """)).getOrThrow());
        assertEquals(List.of(1, 3, Optional.of(4)), List.of(all.minCount(), all.softMinCount(), all.maxCount()));
        assertTrue(StructurePoolElement.CODEC.parse(ops, JsonParser.parseString("""
                {"element_type": "ametrin_structures:conditional", "min_count": 2, "max_count": 1, "element": {"element_type": "minecraft:empty_pool_element"}}
                """)).isError(), "a max count below the min count");

        var element = StructurePoolElement.single(TEMPLE.toString()).apply(StructureTemplatePool.Projection.RIGID);
        assertThrows(IllegalArgumentException.class, () -> new ConditionalPoolElement(element, 2, 2, Optional.of(1)));
        assertThrows(IllegalArgumentException.class, () -> new ConditionalPoolElement(element, -1, 0, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new ConditionalPoolElement(element, 2, 1, Optional.empty()));
    }

    @Test
    void jigsawsThatCantLeadToAMissingElementWait(MinecraftServer server) {
        var lookup = housesWith(server, TEMPLE, element -> new ConditionalPoolElement(element, 0, 1, Optional.empty()));
        int checked = 0;
        for (int seed = 0; seed < SEEDS; seed++) {
            // Without backtracking, so the order is what growing produced.
            var names = assemble(server, lookup.lookup(), seed, 0).stream().flatMap(List::stream)
                    .map(piece -> piece.getElement() == lookup.placed() ? "temple" : piece.getElement().toString())
                    .toList();
            int firstVillager = indexOf(names, name -> name.contains("/villagers/"));
            if (firstVillager < 0) {
                continue;
            }
            checked++;
            // Villagers only come from houses, so they wait until the temple is in or the streets and houses are done.
            var after = names.subList(firstVillager, names.size());
            assertTrue(names.subList(0, firstVillager).contains("temple") || after.stream().noneMatch(name -> name.contains("/streets/") || name.contains("/houses/")), "seed " + seed + ": " + names);
        }
        assertTrue(checked > 0, "the test needs layouts with villagers");
    }

    @Test
    void anUnreachableMinCountDoesNotStopTheLayout(MinecraftServer server) {
        var pools = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        var unreachable = new ConditionalPoolElement(StructurePoolElement.single(TEMPLE.toString()).apply(StructureTemplatePool.Projection.RIGID), 1, 1, Optional.empty());
        var pool = new StructureTemplatePool(pools.getOrThrow(pool("empty")), List.of(Pair.of(unreachable, 1)));
        var lookup = overriding(server, pool("unused"), pool);
        assertTrue(assemble(server, lookup, 0, JigsawSource.DEFAULT_MAX_BACKTRACKS).isPresent());
    }

    @Test
    void theStartPieceStandsOnTheStartHeight(MinecraftServer server) {
        var structure = ModularStructure.builder(Identifier.fromNamespaceAndPath("test", "village"))
                .jigsaw(TOWN_CENTERS, jigsaw -> jigsaw.size(2).useExpansionHack(true))
                .verticalPlacement(StartHeight.at(HeightAnchor.absolute(70)))
                .build(bootstrap(server));
        var generated = assertInstanceOf(ExtendedStructure.Evaluation.Generated.class, structure.evaluateGenerationPoint(context(server, 0)));
        var pieces = generated.stub().getPiecesBuilder().build().pieces();
        var start = (PoolElementStructurePiece) pieces.getFirst();
        assertEquals(70, start.getBoundingBox().minY() + start.getGroundLevelDelta());
        assertTrue(pieces.size() > 1, "the layout grew from the start piece");
    }

    @Test
    void grownPiecesKeepClearOfTheOtherSourcesPieces(MinecraftServer server) {
        var village = new JigsawSource.Builder(holder(server, TOWN_CENTERS)).size(4).useExpansionHack(true).build();
        var temple = new PieceSources.SingleSource(TemplateEntry.of(TEMPLE));
        var overlapsAlone = false;
        for (int seed = 0; seed < SEEDS; seed++) {
            var templeBox = grow(server, temple, seed).getFirst().getBoundingBox();
            overlapsAlone |= grow(server, village, seed).stream().skip(1).anyMatch(piece -> piece.getBoundingBox().intersects(templeBox));
            // The temple and the start piece come first, then what grew.
            var together = grow(server, new PieceSources.CompoundSource(List.of(temple, village)), seed);
            assertTrue(together.stream().skip(2).noneMatch(piece -> piece.getBoundingBox().intersects(templeBox)), "seed " + seed);
        }
        assertTrue(overlapsAlone, "the test needs a village that grows into the temple on its own");
    }

    @Test
    void anOffsetMovesTheWholeLayoutAndTurnsWithTheStructure(MinecraftServer server) {
        var builder = new JigsawSource.Builder(holder(server, TOWN_CENTERS)).size(2).useExpansionHack(true);
        var offset = new BlockPos(20, 0, 8);
        var plain = grow(server, builder.build(), 0);
        var moved = grow(server, builder.offset(offset).build(), 0);
        var shift = moved.getFirst().getBoundingBox().getCenter().subtract(plain.getFirst().getBoundingBox().getCenter());
        assertTrue(Arrays.stream(Rotation.values()).anyMatch(rotation -> shift.equals(offset.rotate(rotation))), shift::toString);
        assertEquals(plain.size(), moved.size());
        for (int i = 0; i < plain.size(); i++) {
            assertEquals(plain.get(i).getBoundingBox().moved(shift.getX(), shift.getY(), shift.getZ()), moved.get(i).getBoundingBox());
        }

        var raised = (PoolElementStructurePiece) grow(server, builder.offset(0, 3, 0).build(), 0).getFirst();
        assertEquals(3, raised.getBoundingBox().minY() + raised.getGroundLevelDelta());
    }

    @Test
    void weightedAndCompoundSourcesTakeJigsawLayouts(MinecraftServer server) {
        var weighted = ModularStructure.builder(Identifier.fromNamespaceAndPath("test", "weighted"))
                .weighted(sources -> sources.jigsaw(1, TOWN_CENTERS, jigsaw -> jigsaw.size(2)))
                .build(bootstrap(server));
        var compound = ModularStructure.builder(Identifier.fromNamespaceAndPath("test", "compound"))
                .compound(sources -> sources
                        .single(template -> template.template(TEMPLE))
                        .jigsaw(TOWN_CENTERS, jigsaw -> jigsaw.size(2).offset(40, 0, 0)))
                .build(bootstrap(server));
        var drawn = assertInstanceOf(PieceSources.WeightedSource.class, weighted.pieces()).sources().unwrap().getFirst().value();
        assertEquals(Optional.of(TOWN_CENTERS), assertInstanceOf(JigsawSource.class, drawn).startPool().unwrapKey());
        var parts = assertInstanceOf(PieceSources.CompoundSource.class, compound.pieces()).sources();
        assertEquals(new BlockPos(40, 0, 0), assertInstanceOf(JigsawSource.class, parts.get(1)).offset());
    }

    @Test
    void theStructuresSettingsReachTheStartPieceAndAreSavedWithIt(MinecraftServer server) {
        var element = new ExtendedSinglePoolElement(Either.left(Identifier.withDefaultNamespace("igloo/top")), Holder.direct(new StructureProcessorList(List.of())),
                StructureTemplatePool.Projection.RIGID, Optional.empty(), true, Optional.empty());
        var pools = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        var start = Holder.direct(new StructureTemplatePool(pools.getOrThrow(pool("empty")), List.of(Pair.of(element, 1))));
        var structure = new ModularStructure(new Structure.StructureSettings(HolderSet.empty()), ExtendedStructureSettings.DEFAULT, new JigsawSource.Builder(start).size(0).build(),
                StartHeight.at(HeightAnchor.absolute(64)), LiquidSettings.IGNORE_WATERLOGGING, Optional.of(Foundation.repeating()), Optional.empty());
        var generated = assertInstanceOf(ExtendedStructure.Evaluation.Generated.class, structure.evaluateGenerationPoint(context(server, 0)));
        var placed = (PoolElementStructurePiece) generated.stub().getPiecesBuilder().build().pieces().getFirst();
        assertEquals(Optional.of(Foundation.repeating()), assertInstanceOf(ExtendedSinglePoolElement.class, placed.getElement()).foundation());

        // A vanilla element without processors gets the structure's and keeps them when saved.
        var mossify = mossify(server);
        var piece = startPiece(server, StructurePoolElement.single(TEMPLE.toString()).apply(StructureTemplatePool.Projection.RIGID), Optional.of(mossify));
        assertSame(mossify, assertInstanceOf(SinglePoolElement.class, piece.getElement()).processors);
        var context = new StructurePieceSerializationContext(server.getResourceManager(), server.registryAccess(), server.getStructureManager());
        var reloaded = new PoolElementStructurePiece(context, piece.createTag(context));
        assertEquals(mossify.unwrapKey(), assertInstanceOf(SinglePoolElement.class, reloaded.getElement()).processors.unwrapKey());
    }

    @Test
    void anElementsOwnSettingsReplaceTheStructuresUnlessItInlinesThem(MinecraftServer server) {
        var own = Foundation.of(Blocks.STONE.defaultBlockState(), 4);
        var withFoundation = new ExtendedSinglePoolElement(Either.left(TEMPLE), Holder.direct(new StructureProcessorList(List.of())),
                StructureTemplatePool.Projection.RIGID, Optional.empty(), true, Optional.of(own));
        assertSame(withFoundation, StructureElements.withStructureSettings(withFoundation, Optional.empty(), Optional.of(Foundation.repeating())));
        var vanilla = StructurePoolElement.single(TEMPLE.toString()).apply(StructureTemplatePool.Projection.RIGID);
        assertSame(vanilla, StructureElements.withStructureSettings(vanilla, Optional.empty(), Optional.of(Foundation.repeating())), "vanilla elements get no foundation");

        var mossify = mossify(server);
        var withProcessors = StructurePoolElement.single(TEMPLE.toString(), Holder.direct(new StructureProcessorList(List.of(new BlockIgnoreProcessor(List.of(Blocks.STONE))))))
                .apply(StructureTemplatePool.Projection.RIGID);
        assertSame(withProcessors, StructureElements.withStructureSettings(withProcessors, Optional.of(mossify), Optional.empty()));
        var inlining = StructurePoolElement.single(TEMPLE.toString(), Holder.direct(new StructureProcessorList(List.of(InlineFromStructureProcessor.INSTANCE))))
                .apply(StructureTemplatePool.Projection.RIGID);
        var copy = assertInstanceOf(SinglePoolElement.class, StructureElements.withStructureSettings(inlining, Optional.of(mossify), Optional.empty()));
        assertEquals(mossify.value().list(), copy.processors.value().list());

        // An element type of its own decides what it takes.
        var replacement = StructurePoolElement.single(TEMPLE.toString()).apply(StructureTemplatePool.Projection.RIGID);
        var deciding = new ExtendedSinglePoolElement(Either.left(TEMPLE), Holder.direct(new StructureProcessorList(List.of())),
                StructureTemplatePool.Projection.RIGID, Optional.empty(), true, Optional.empty()) {
            @Override
            public StructurePoolElement withStructureSettings(Optional<Holder<StructureProcessorList>> processors, Optional<Foundation> foundation) {
                return replacement;
            }
        };
        assertSame(replacement, StructureElements.withStructureSettings(deciding, Optional.of(mossify), Optional.empty()));
    }

    @Test
    void piecesHoldTheElementInsideAConditionalOne(MinecraftServer server) {
        var houses = housesWith(server, SMALL_HOUSE, element -> new ConditionalPoolElement(element, 0, 0, Optional.of(1)));
        var withHouse = false;
        for (int seed = 0; seed < SEEDS; seed++) {
            var pieces = assemble(server, houses.lookup(), seed, JigsawSource.DEFAULT_MAX_BACKTRACKS).orElseThrow();
            assertTrue(pieces.stream().noneMatch(piece -> piece.getElement() instanceof ConditionalPoolElement), "seed " + seed);
            withHouse |= pieces.stream().anyMatch(piece -> piece.getElement() == houses.placed());
        }
        assertTrue(withHouse, "the test needs a layout with the house");
    }

    private static PoolElementStructurePiece startPiece(MinecraftServer server, StructurePoolElement element, Optional<Holder<StructureProcessorList>> processors) {
        var pools = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        var start = Holder.direct(new StructureTemplatePool(pools.getOrThrow(pool("empty")), List.of(Pair.of(element, 1))));
        var source = new JigsawSource.Builder(start).size(0).build();
        var builder = new StructurePiecesBuilder();
        source.appendPieces(builder, new PieceSource.Context(context(server, 0), BlockPos.ZERO, Rotation.NONE, TerrainAdjustment.NONE, LiquidSettings.IGNORE_WATERLOGGING, Optional.empty(), processors));
        return (PoolElementStructurePiece) builder.build().pieces().getFirst();
    }

    private static Holder<StructureProcessorList> mossify(MinecraftServer server) {
        return server.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST).getOrThrow(ProcessorLists.MOSSIFY_10_PERCENT);
    }

    @Test
    void readsFromJsonAndKeepsTheLayoutInsideTheStructureRange(MinecraftServer server) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var source = assertInstanceOf(JigsawSource.class, PieceSource.CODEC.parse(ops, JsonParser.parseString("""
                {"type": "ametrin_structures:jigsaw", "start_pool": "minecraft:village/plains/town_centers", "size": 3}
                """)).getOrThrow());
        assertEquals(Optional.of(TOWN_CENTERS), source.startPool().unwrapKey());
        assertEquals(JigsawSource.DEFAULT_MAX_BACKTRACKS, source.maxBacktracks());
        assertTrue(PieceSource.CODEC.parse(ops, JsonParser.parseString("""
                {"type": "ametrin_structures:jigsaw", "start_pool": "minecraft:village/plains/town_centers", "max_distance_from_center": 117}
                """)).isError());
        assertTrue(PieceSource.CODEC.parse(ops, JsonParser.parseString("""
                {"type": "ametrin_structures:jigsaw", "start_pool": "minecraft:village/plains/town_centers", "max_distance_from_center": 100, "offset": [0, 0, -20]}
                """)).isError(), "the offset counts toward the range");
    }

    private static void assertGrowsLikeVanilla(MinecraftServer server, JigsawSource source, int y) {
        for (int seed = 0; seed < SEEDS; seed++) {
            var vanilla = JigsawPlacement.addPieces(context(server, seed), source.startPool(), source.startJigsawName(), source.size().sample(RandomSource.create()), new BlockPos(0, y, 0), source.useExpansionHack(), Optional.empty(), source.maxDistanceFromCenter(), PoolAliasLookup.EMPTY, DimensionPadding.ZERO, LiquidSettings.IGNORE_WATERLOGGING);
            var expected = vanilla.orElseThrow().getPiecesBuilder().build().pieces().stream().map(Placed::of).toList();

            var context = context(server, seed);
            var pieces = new StructurePiecesBuilder();
            var pieceContext = new PieceSource.Context(context, BlockPos.ZERO, Rotation.getRandom(context.random()), TerrainAdjustment.NONE, LiquidSettings.IGNORE_WATERLOGGING, Optional.empty(), Optional.empty());
            source.appendPieces(pieces, pieceContext);
            pieces.offsetPiecesVertically(y);
            assertTrue(pieceContext.place(pieces, new BlockPos(0, y, 0)));

            assertEquals(expected, pieces.build().pieces().stream().map(Placed::of).toList(), "seed " + seed);
        }
    }

    // The source's pieces at Y 0, grown.
    private static List<StructurePiece> grow(MinecraftServer server, PieceSource source, long seed) {
        var context = context(server, seed);
        var pieces = new StructurePiecesBuilder();
        var pieceContext = new PieceSource.Context(context, BlockPos.ZERO, Rotation.getRandom(context.random()), TerrainAdjustment.NONE, LiquidSettings.IGNORE_WATERLOGGING, Optional.empty(), Optional.empty());
        source.appendPieces(pieces, pieceContext);
        assertTrue(pieceContext.place(pieces, BlockPos.ZERO));
        return pieces.build().pieces();
    }

    // How often `template` appears in a village layout at each seed, with the houses pool's element for it replaced by `replace`.
    // Zombie villages have houses of their own, so they're UNREACHED.
    private static List<Integer> countAcrossSeeds(MinecraftServer server, Identifier template, Function<StructurePoolElement, StructurePoolElement> replace) {
        var houses = housesWith(server, template, replace);
        var counts = new ArrayList<Integer>();
        for (int seed = 0; seed < SEEDS; seed++) {
            counts.add(assemble(server, houses.lookup(), seed, JigsawSource.DEFAULT_MAX_BACKTRACKS)
                    .map(pieces -> pieces.stream().noneMatch(piece -> houses.elements().contains(piece.getElement()))
                            ? UNREACHED
                            : (int) pieces.stream().filter(piece -> piece.getElement() == houses.placed()).count())
                    .orElse(FAILED));
        }
        return counts;
    }

    /// @param placed   the element pieces hold for `template`, without a conditional element around it
    /// @param elements the elements pieces from the houses pool hold
    private record Houses(Function<ResourceKey<StructureTemplatePool>, Optional<? extends Holder<StructureTemplatePool>>> lookup, StructurePoolElement placed, List<StructurePoolElement> elements) {}

    // The pools with the houses pool's element for `template` replaced by `replace`.
    private static Houses housesWith(MinecraftServer server, Identifier template, Function<StructurePoolElement, StructurePoolElement> replace) {
        var houses = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).getOrThrow(HOUSES).value();
        var weights = new LinkedHashMap<StructurePoolElement, Integer>();
        houses.getShuffledTemplates(RandomSource.create(0)).forEach(element -> weights.merge(element, 1, Integer::sum));
        var original = weights.keySet().stream().filter(element -> element instanceof SinglePoolElement single && single.getTemplateLocation().equals(template)).findFirst().orElseThrow();
        var replacement = replace.apply(original);
        var elements = weights.entrySet().stream().map(entry -> Pair.of(entry.getKey() == original ? replacement : entry.getKey(), entry.getValue())).toList();
        var lookup = overriding(server, HOUSES, new StructureTemplatePool(houses.getFallback(), elements));
        return new Houses(lookup, original, List.copyOf(weights.keySet()));
    }

    private static Optional<List<PoolElementStructurePiece>> assemble(MinecraftServer server, Function<ResourceKey<StructureTemplatePool>, Optional<? extends Holder<StructureTemplatePool>>> pools, long seed, int maxBacktracks) {
        var context = context(server, seed);
        var templates = server.getStructureManager();
        var random = context.random();
        var rotation = Rotation.getRandom(random);
        var element = holder(server, TOWN_CENTERS).value().getRandomTemplate(random);
        var position = new BlockPos(0, 63, 0);
        var start = new PoolElementStructurePiece(templates, element, position, element.getGroundLevelDelta(), rotation, element.getBoundingBox(templates, position, rotation), LiquidSettings.IGNORE_WATERLOGGING);
        var free = JigsawAssembler.freeSpace(start.getBoundingBox(), 64, new JigsawStructure.MaxDistance(80), context.heightAccessor(), DimensionPadding.ZERO);
        var pieceContext = new PieceSource.Context(context, BlockPos.ZERO, rotation, TerrainAdjustment.NONE, LiquidSettings.IGNORE_WATERLOGGING, Optional.empty(), Optional.empty());
        var assembler = new JigsawAssembler(pieceContext, pools, 6, true, PoolAliasLookup.EMPTY, maxBacktracks);
        return assembler.assemble(start, element, free, List.of());
    }

    private static int indexOf(List<String> names, Predicate<String> matches) {
        return IntStream.range(0, names.size()).filter(i -> matches.test(names.get(i))).findFirst().orElse(-1);
    }

    private static Function<ResourceKey<StructureTemplatePool>, Optional<? extends Holder<StructureTemplatePool>>> overriding(MinecraftServer server, ResourceKey<StructureTemplatePool> key, StructureTemplatePool pool) {
        var pools = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        return requested -> requested.equals(key) ? Optional.of(Holder.direct(pool)) : pools.get(requested);
    }

    private record Placed(StructurePoolElement element, BlockPos position, Rotation rotation, BoundingBox box, int groundLevelDelta, List<JigsawJunction> junctions) {
        static Placed of(StructurePiece piece) {
            var pooled = (PoolElementStructurePiece) piece;
            return new Placed(pooled.getElement(), pooled.getPosition(), pooled.getRotation(), pooled.getBoundingBox(), pooled.getGroundLevelDelta(), List.copyOf(pooled.getJunctions()));
        }
    }

    private static ResourceKey<StructureTemplatePool> pool(String path) {
        return ResourceKey.create(Registries.TEMPLATE_POOL, Identifier.withDefaultNamespace(path));
    }

    private static Holder<StructureTemplatePool> holder(MinecraftServer server, ResourceKey<StructureTemplatePool> key) {
        return server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).getOrThrow(key);
    }

    private static Structure.GenerationContext context(MinecraftServer server, long seed) {
        var plains = server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        return new Structure.GenerationContext(
                server.registryAccess(),
                new DebugLevelSource(plains),
                new FixedBiomeSource(plains),
                RandomState.create(server.registryAccess(), NoiseGeneratorSettings.OVERWORLD, 0),
                server.getStructureManager(),
                seed,
                new ChunkPos(0, 0),
                LevelHeightAccessor.create(-64, 384),
                biome -> true);
    }

    private static BootstrapContext<Structure> bootstrap(MinecraftServer server) {
        return new BootstrapContext<>() {
            @Override
            public Holder.Reference<Structure> register(ResourceKey<Structure> key, Structure value, Lifecycle lifecycle) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <S> HolderGetter<S> lookup(ResourceKey<? extends Registry<? extends S>> key) {
                return server.registryAccess().lookupOrThrow(key);
            }
        };
    }
}
