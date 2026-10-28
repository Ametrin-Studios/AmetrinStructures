package com.ametrin.structures.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;

import java.util.Map;
import java.util.function.Consumer;

/// Game tests rather than unit tests, since laying out a structure needs a server level.
public final class LayoutGameTests {
    public static final Map<String, Consumer<GameTestHelper>> TESTS = Map.of("layout_describes_a_village", LayoutGameTests::describesAVillage);

    private LayoutGameTests() {}

    private static void describesAVillage(GameTestHelper helper) {
        var level = helper.getLevel();
        var village = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(BuiltinStructures.VILLAGE_PLAINS).value();
        var pieces = StructureLayouts.layOut(level, village, ChunkPos.containing(helper.absolutePos(BlockPos.ZERO)), 1).orElseThrow();
        var layout = PiecesCommand.describe(level.getStructureManager(), pieces);

        int centers = layout.pieces().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("minecraft:village/plains/town_centers/"))
                .mapToInt(Map.Entry::getValue)
                .sum();
        helper.assertValueEqual(centers, 1, "town centers");
        helper.assertValueEqual(layout.pieces().values().stream().mapToInt(Integer::intValue).sum(), pieces.pieces().size(), "pieces");
        helper.assertFalse(layout.openJigsaws().containsKey("minecraft:empty"), "jigsaws into the empty pool are never open");
        // Each piece but the start hangs off a jigsaw into a pool, so at least that many of those are attached.
        long intoPools = pieces.pieces().stream()
                .filter(piece -> piece instanceof PoolElementStructurePiece)
                .map(piece -> (PoolElementStructurePiece) piece)
                .flatMap(piece -> piece.getElement().getShuffledJigsawBlocks(level.getStructureManager(), piece.getPosition(), piece.getRotation(), RandomSource.create(0)).stream())
                .filter(jigsaw -> !jigsaw.pool().equals(Pools.EMPTY))
                .count();
        int open = layout.openJigsaws().values().stream().mapToInt(Integer::intValue).sum();
        helper.assertTrue(intoPools - open >= pieces.pieces().size() - 1, "attached jigsaws aren't open, got " + open + " open of " + intoPools + " for " + pieces.pieces().size() + " pieces");
        helper.succeed();
    }
}
