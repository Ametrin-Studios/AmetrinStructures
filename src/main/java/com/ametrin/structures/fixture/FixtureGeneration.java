package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASBlocks;
import com.ametrin.structures.util.ASLog;
import com.ametrin.structures.util.PositionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

@ApiStatus.Internal
public final class FixtureGeneration {
    /// Client updates but no neighbor cascade: worldgen sets its own shapes
    private static final int REPLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private FixtureGeneration() {
    }

    /// @param piece null when a pool element placed the template
    public static void process(
            @Nullable StructurePiece piece,
            StructureTemplate template,
            BlockPos templatePosition,
            StructurePlaceSettings placeSettings,
            BoundingBox bounds,
            RandomSource random,
            WorldGenLevel level,
            ChunkGenerator generator) {
        var markers = template.filterBlocks(templatePosition, placeSettings, ASBlocks.FIXTURE.get());
        for (var marker : markers) {
            if (bounds.isInside(marker.pos())) {
                processOne(piece, marker.pos(), bounds, random, level, generator, false);
            }
        }
    }

    /// Runs the marker at `pos` as it would during world gen but without the probability roll.
    ///
    ///  The marker is consumed.
    public static void runNow(ServerLevel level, BlockPos pos) {
        processOne(null, pos, BoundingBox.infinite(), level.getRandom(), level, level.getChunkSource().getGenerator(), true);
    }

    private static void processOne(
            @Nullable StructurePiece piece,
            BlockPos markerPos,
            BoundingBox bounds,
            RandomSource random,
            WorldGenLevel level,
            ChunkGenerator generator,
            boolean force) {
        if (!(level.getBlockEntity(markerPos) instanceof FixtureBlockEntity marker)) {
            return;
        }
        var markerState = level.getBlockState(markerPos);
        level.setBlock(markerPos, marker.becomes(), REPLACE_FLAGS);

        // Once the marker is gone, so it neither stops the fall to the ground nor shows to conditions.
        var actionPos = actionPosition(markerPos, marker, level);
        var actionBlockPos = BlockPos.containing(actionPos);
        var conditionContext = new FixtureCondition.Context(level, actionBlockPos);
        Predicate<WeightedFixture> eligible = alternative -> alternative.conditionsPass(conditionContext);
        var drawn = marker.draw(eligible, random).flatMap(fixture -> FixturePresets.resolve(fixture, FixturePresets.lookup(level.registryAccess()), eligible, random));
        if (drawn.isEmpty()) {
            return;
        }
        var fixture = drawn.get().fixture();
        if (fixture instanceof Fixture.Unreadable unreadable) {
            ASLog.warn("fixture at {} doesn't decode: {}", markerPos, unreadable.error());
            return;
        }
        var chance = drawn.get().generationChance();
        if (!force && chance < 1.0F && random.nextFloat() >= chance) {
            return;
        }

        var before = level.getBlockState(actionBlockPos);
        try {
            fixture.apply(new FixtureContext(markerState, markerPos, actionPos, actionBlockPos, level, random, bounds, piece, generator));
        } catch (RuntimeException exception) {
            ASLog.error("fixture {} at {} failed: {}", fixture, markerPos, exception.toString());
            return;
        }
        var placed = level.getBlockState(actionBlockPos);
        if (placed != before) {
            updateNeighborShapes(level, actionBlockPos, placed, random);
        }

        if (marker.markPostProcessing()) {
            level.getChunk(actionBlockPos).markPosForPostprocessing(actionBlockPos);
        }
    }

    // Placing a template reshapes the blocks along its edge against what lies beyond, which can be a marker of the
    // next chunk or piece that hasn't run yet: half a double chest finds no chest there and turns single. Worldgen
    // doesn't update neighbors when a fixture places its block, so this does, as placing the template's blocks would.
    private static void updateNeighborShapes(WorldGenLevel level, BlockPos pos, BlockState placed, RandomSource random) {
        var neighborPos = new BlockPos.MutableBlockPos();
        for (var direction : Direction.values()) {
            neighborPos.setWithOffset(pos, direction);
            var neighbor = level.getBlockState(neighborPos);
            var updated = neighbor.updateShape(level, level, neighborPos, direction.getOpposite(), pos, placed, random);
            if (updated != neighbor) {
                level.setBlock(neighborPos, updated, REPLACE_FLAGS);
            }
        }
    }

    private static Vec3 actionPosition(BlockPos markerPos, FixtureBlockEntity marker, WorldGenLevel level) {
        var position = PositionHelper.bottomCenter(markerPos).add(marker.offset());

        if (marker.useGravity()) {
            // Worldgen heightmaps only exist while a chunk generates. a finished level keeps the final one.
            var heightmap = level instanceof WorldGenRegion ? Heightmap.Types.OCEAN_FLOOR_WG : Heightmap.Types.OCEAN_FLOOR;
            var surface = level.getHeight(heightmap, Mth.floor(position.x), Mth.floor(position.z));
            position = new Vec3(position.x, Math.min(position.y, surface), position.z);
        }
        return position;
    }
}
