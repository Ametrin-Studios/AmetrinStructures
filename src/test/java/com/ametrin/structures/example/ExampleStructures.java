package com.ametrin.structures.example;

import com.ametrin.structures.foam.RemoveFoamProcessor;
import com.ametrin.structures.processor.ReplaceBlockProcessor;
import com.ametrin.structures.structure.DeferredStructureHolder;
import com.ametrin.structures.structure.DeferredStructureRegister;
import com.ametrin.structures.structure.jigsaw.ExtendedJigsawStructure;
import com.ametrin.structures.structure.jigsaw.JigsawPools;
import com.ametrin.structures.structure.simple.HeightAnchor;
import com.ametrin.structures.structure.simple.HeightMode;
import com.ametrin.structures.structure.simple.InlineFromStructureProcessor;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.ProcessorLists;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.data.event.DatapackRegistryGatherer;
import net.neoforged.neoforge.data.event.GatherDataRegistryEntriesEvent;

import java.util.List;

/// How a mod registers structures with this library, end to end: simple structures, a jigsaw
/// structure with its pools, the mod bus call and the datagen wiring.
final class ExampleStructures {
    static final String MODID = "examplemod";

    /// One per mod. It owns the structure types, piece types, structures and structure sets.
    static final DeferredStructureRegister REGISTER = new DeferredStructureRegister(MODID);

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    // One template on dry, level ground: no Structure subclass, no type, no piece type.
    static final DeferredStructureHolder RUINED_TOWER = REGISTER.set("ruined_tower")
            .evenSpreadPlacement(18, 0.6F)
            .simple(tower -> tower
                    .single("ruined_tower")
                    .surface()
                    .verticalPlacementMode(HeightMode.MEAN)
                    // Sitting at the mean height leaves some corners above the ground; the foundation fills under them.
                    .foundation()
                    .filterFlatness(3)
                    .filterMaxWaterDepth(1)
                    .filterGroundCheck(BlockTags.DIRT)
                    .biomes(BiomeTags.IS_FOREST))
            .build();

    // A random pick of templates anywhere from just above bedrock to well below the surface.
    static final DeferredStructureHolder CRYPT = REGISTER.set("crypt")
            .evenSpreadPlacement(spread -> spread.minDistance(24).probability(0.5F).minChunksFromCenter(16))
            .simple(crypt -> crypt
                    // Every template is mossified; the large one adds cobwebs on top of that, where foam was.
                    .weighted(weighted -> weighted
                            .single("crypt/small", 3)
                            .single(template -> template.template("crypt/large").processors(List.of(
                                    InlineFromStructureProcessor.INSTANCE,
                                    new ReplaceBlockProcessor(
                                            ReplaceBlockProcessor.Condition.of(Blocks.AIR), 0.05F, Blocks.COBWEB.defaultBlockState()))), 1))
                    .processors(ProcessorLists.MOSSIFY_20_PERCENT)
                    .between(HeightAnchor.aboveBottom(8), HeightAnchor.surface(-24))
                    .step(GenerationStep.Decoration.UNDERGROUND_STRUCTURES)
                    .terrainAdaptation(TerrainAdjustment.ENCAPSULATE))
            .build();

    // Sunk in the sea, at least 20 chunks apart: the interior foam floods, stairs weather, and there must be water overhead.
    static final DeferredStructureHolder SUNKEN_SHRINE = REGISTER.set("sunken_shrine")
            .evenSpreadPlacement(20, 0.4F)
            .simple(shrine -> shrine
                    .single(template -> template.template("sunken_shrine").processors(List.of(
                            new RemoveFoamProcessor(Fluids.WATER.defaultFluidState().createLegacyBlock()),
                            new ReplaceBlockProcessor(
                                    ReplaceBlockProcessor.Condition.of(BlockTags.STAIRS),
                                    0.3F,
                                    Blocks.MOSSY_STONE_BRICK_STAIRS.defaultBlockState(),
                                    true))))
                    .oceanFloor()
                    .verticalPlacementMode(HeightMode.LOWEST)
                    .filterSubmerged(6)
                    .step(GenerationStep.Decoration.UNDERGROUND_STRUCTURES)
                    .biomes(BiomeTags.IS_DEEP_OCEAN))
            .build();

    // Two structures sharing vanilla's random spread. Each spot tries them by weight, and takes the
    // other when the first does not fit.
    static final DeferredStructureHolder GRAVES = REGISTER.set("graves")
            .horizontalPlacement(new RandomSpreadStructurePlacement(20, 8, RandomSpreadType.LINEAR, 482_193))
            .simple("small", grave -> grave
                    .surface()
                    .filterFlatness(2)
                    .single("graves/small")
                    .weight(3)
            )
            .simple("large", grave -> grave
                    .surface()
                    .filterFlatness(1)
                    .single("graves/large")
            )
            .build();

    // A jigsaw castle, and now and then a simple ruin in its place.
    static final DeferredStructureHolder CASTLE = REGISTER.set("castle")
            .scatteredGridPlacement(40, 0.5F)
            .structure((settings, context) -> ExtendedJigsawStructure.builder(
                                    settings, context.lookup(Registries.TEMPLATE_POOL).getOrThrow(CastlePools.START))
                            .size(8)
                            .onSurface()
                            .build(),
                    castle -> castle.biomes(BiomeTags.IS_TAIGA).terrainAdaptation(TerrainAdjustment.BEARD_THIN).weight(4))
            .simple("ruin", ruin -> ruin.single("castle/ruin").surface().biomes(BiomeTags.IS_TAIGA))
            .build();

    static final class CastlePools {
        static final ResourceKey<StructureTemplatePool> START = JigsawPools.key(MODID, "castle/start");

        static void bootstrap(BootstrapContext<StructureTemplatePool> context) {
            var pools = new JigsawPools(context, MODID, "castle/");

            pools.pool("start", pool -> pool
                    .element("keep"));

            pools.pool("walls", pool -> pool
                    .fallback("wall_ends")
                    .element("wall_straight")
                    .element("wall_corner")
                    .element("wall_ruined", element -> element.weight(2).processors(ProcessorLists.MOSSIFY_10_PERCENT)));

            // Foam stays and the air saved in this template is placed, not treated as void.
            pools.pool("wall_ends", pool -> pool
                    .element("wall_end", JigsawPools.Element::noFoamProcessing));

            pools.pool("moat", pool -> pool
                    .terrainMatching()
                    .elements(List.of("moat_straight", "moat_corner"), element -> element
                            .processors(List.of(RemoveFoamProcessor.WATER)))
                    .empty(1));
        }
    }

    /// Mod constructor: registers the structure and piece types the register declared, if any.
    static void construct(IEventBus modBus) {
        REGISTER.register(modBus);
    }

    /// Datagen, on the [GatherDataRegistryEntriesEvent]: writes the structures, structure sets and pools as JSON.
    static void gatherRegistryEntries(DatapackRegistryGatherer registries) {
        registries.add(Registries.TEMPLATE_POOL, CastlePools::bootstrap);
        REGISTER.bootstrap(registries);
    }
}
