package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASBlocks;
import com.ametrin.structures.util.ASLog;
import com.ametrin.structures.util.PositionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

@ApiStatus.Internal
public final class FixtureGeneration {
    /// Updates clients but not neighbors, since worldgen sets its own shapes.
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

    /// Runs the marker at `pos` like world generation would, but without the generation chance roll.
    /// The marker is removed.
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

        // After the marker is removed, so it doesn't stop the fall to the ground and conditions don't see it.
        var actionPos = actionPosition(markerPos, marker, level);
        var actionBlockPos = BlockPos.containing(actionPos);
        var drawn = draw(marker.fixtures(), new FixtureCondition.Context(level, actionBlockPos), random, force);
        if (drawn.isEmpty()) {
            return;
        }
        var fixture = drawn.get();
        if (fixture instanceof Fixture.Unreadable unreadable) {
            ASLog.warn("fixture at {} doesn't decode: {}", markerPos, unreadable.error());
            return;
        }

        try {
            fixture.apply(new FixtureContext(markerState, markerPos, actionPos, actionBlockPos, level, random, bounds, piece, generator));
        } catch (RuntimeException exception) {
            ASLog.error("fixture {} at {} failed: {}", fixture, markerPos, exception.toString());
            return;
        }

        if (marker.markPostProcessing()) {
            level.getChunk(actionBlockPos).markPosForPostprocessing(actionBlockPos);
        }
    }

    /// Draws one of `alternatives` whose conditions pass at `conditions`, resolves presets and rolls the generation chance unless `force`.
    /// Empty when nothing generates.
    public static Optional<Fixture> draw(List<WeightedFixture> alternatives, FixtureCondition.Context conditions, RandomSource random, boolean force) {
        Predicate<WeightedFixture> eligible = alternative -> alternative.conditionsPass(conditions);
        var presets = FixturePresets.lookup(conditions.level().registryAccess());
        return WeightedFixture.draw(alternatives, eligible, random)
                .flatMap(drawn -> FixturePresets.resolve(drawn, presets, eligible, random))
                .filter(drawn -> force || drawn.generationChance() >= 1.0F || random.nextFloat() < drawn.generationChance())
                .map(WeightedFixture::fixture);
    }

    private static Vec3 actionPosition(BlockPos markerPos, FixtureBlockEntity marker, WorldGenLevel level) {
        var position = PositionHelper.bottomCenter(markerPos).add(marker.offset());

        if (marker.useGravity()) {
            // Worldgen heightmaps only exist while a chunk generates. A finished level only has the final ones.
            var heightmap = level instanceof WorldGenRegion ? Heightmap.Types.OCEAN_FLOOR_WG : Heightmap.Types.OCEAN_FLOOR;
            var surface = level.getHeight(heightmap, Mth.floor(position.x), Mth.floor(position.z));
            position = new Vec3(position.x, Math.min(position.y, surface), position.z);
        }
        return position;
    }
}
