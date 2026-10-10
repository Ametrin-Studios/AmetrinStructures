package com.ametrin.structures.example;

import com.ametrin.structures.foam.RemoveFoamProcessor;
import com.ametrin.structures.processor.ReplaceBlockProcessor;
import com.ametrin.structures.structure.StructureBootstrap;
import com.ametrin.structures.structure.StructureSetKeys;
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
import net.neoforged.neoforge.data.event.DatapackRegistryGatherer;
import net.neoforged.neoforge.data.event.GatherDataRegistryEntriesEvent;

import java.util.List;

/// A full example of declaring structures with this library: simple structures, a jigsaw structure
/// with its pools, and the datagen setup.
final class ExampleStructures {
    static final String MODID = "examplemod";

    /// One per mod. It declares the structures and structure sets.
    static final StructureBootstrap STRUCTURES = new StructureBootstrap(MODID);

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    // One template on dry, level ground. Needs no Structure subclass, structure type or piece type.
    static final StructureSetKeys RUINED_TOWER = STRUCTURES.registerSet("ruined_tower", set -> set
            .evenSpreadPlacement(18, 0.6F)
            .simple(tower -> tower
                    .single("ruined_tower")
                    .surface()
                    .verticalPlacementMode(HeightMode.MEAN)
                    // At the mean height some corners float above the ground. The foundation fills under them.
                    .foundation()
                    .filterFlatness(3)
                    .filterMaxWaterDepth(1)
                    .filterGroundCheck(BlockTags.DIRT)
                    .biomes(BiomeTags.IS_FOREST)));

    // One of several templates, at a random height between just above bedrock and well below the surface.
    static final StructureSetKeys CRYPT = STRUCTURES.registerSet("crypt", set -> set
            .evenSpreadPlacement(spread -> spread.minDistance(24).probability(0.5F).minChunksFromCenter(16))
            .simple(crypt -> crypt
                    // Every template gets mossy. The large one also gets cobwebs where its foam was.
                    .weighted(weighted -> weighted
                            .single("crypt/small", 3)
                            .single(template -> template.template("crypt/large").processors(List.of(
                                    InlineFromStructureProcessor.INSTANCE,
                                    new ReplaceBlockProcessor(
                                            ReplaceBlockProcessor.Condition.of(Blocks.AIR), 0.05F, Blocks.COBWEB.defaultBlockState()))), 1))
                    .processors(ProcessorLists.MOSSIFY_20_PERCENT)
                    .between(HeightAnchor.aboveBottom(8), HeightAnchor.surface(-24))
                    .step(GenerationStep.Decoration.UNDERGROUND_STRUCTURES)
                    .terrainAdaptation(TerrainAdjustment.ENCAPSULATE)));

    // Sunk in the sea, at least 20 chunks apart. The interior foam turns into water, the stairs get mossy, and it needs water above it.
    static final StructureSetKeys SUNKEN_SHRINE = STRUCTURES.registerSet("sunken_shrine", set -> set
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
                    .biomes(BiomeTags.IS_DEEP_OCEAN)));

    // Two structures sharing vanilla's random spread. Each spot tries them by weight, and uses the
    // other one if the first doesn't fit.
    static final StructureSetKeys GRAVES = STRUCTURES.registerSet("graves", set -> set
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
            ));

    // A jigsaw castle, sometimes replaced by a simple ruin.
    static final StructureSetKeys CASTLE = STRUCTURES.registerSet("castle", set -> set
            .scatteredGridPlacement(40, 0.5F)
            .structure((settings, context) -> ExtendedJigsawStructure.builder(
                                    settings, context.lookup(Registries.TEMPLATE_POOL).getOrThrow(CastlePools.START))
                            .size(8)
                            .onSurface()
                            .build(),
                    castle -> castle.biomes(BiomeTags.IS_TAIGA).terrainAdaptation(TerrainAdjustment.BEARD_THIN).weight(4))
            .simple("ruin", ruin -> ruin.single("castle/ruin").surface().biomes(BiomeTags.IS_TAIGA)));

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

            // Foam stays, and the air saved in this template is placed instead of treated as void.
            pools.pool("wall_ends", pool -> pool
                    .element("wall_end", JigsawPools.Element::noFoamProcessing));

            pools.pool("moat", pool -> pool
                    .terrainMatching()
                    .elements(List.of("moat_straight", "moat_corner"), element -> element
                            .processors(List.of(RemoveFoamProcessor.WATER)))
                    .empty(1));
        }
    }

    /// Datagen, on [GatherDataRegistryEntriesEvent]. Writes the structures, structure sets and pools as JSON.
    static void gatherRegistryEntries(DatapackRegistryGatherer registries) {
        registries.add(Registries.TEMPLATE_POOL, CastlePools::bootstrap);
        STRUCTURES.addTo(registries);
    }
}
