package com.ametrin.structures.structure.filter;

import com.ametrin.structures.registry.ASPlacementFilters;
import com.ametrin.structures.structure.modular.HeightAnchor;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Optional;
import java.util.function.Predicate;

public final class PlacementFilters {
    private PlacementFilters() {}

    /// Requires a height between `min` and `max` inclusive. Either can be left out.
    /// Each is an absolute Y or a [HeightAnchor], so it can be relative to the terrain under the footprint.
    ///
    /// @param check what's compared: the origin's Y, or the footprint's lowest block with `min` and its highest with `max`
    public record HeightRange(Optional<HeightAnchor> min, Optional<HeightAnchor> max, Check check) implements PlacementFilter {
        private static final Codec<HeightAnchor> Y_CODEC = Codec.either(Codec.INT, HeightAnchor.CODEC).xmap(
                either -> either.map(HeightAnchor::absolute, anchor -> anchor),
                anchor -> anchor instanceof HeightAnchor.Fixed(VerticalAnchor.Absolute(int y)) ? Either.left(y) : Either.right(anchor));

        public static final MapCodec<HeightRange> CODEC = RecordCodecBuilder.<HeightRange>mapCodec(instance -> instance.group(
                                Y_CODEC.optionalFieldOf("min").forGetter(HeightRange::min),
                                Y_CODEC.optionalFieldOf("max").forGetter(HeightRange::max),
                                Check.CODEC.optionalFieldOf("check", Check.ORIGIN).forGetter(HeightRange::check))
                        .apply(instance, HeightRange::new))
                .validate(HeightRange::validate);

        /// Between the absolute Ys `min` and `max`, checked at the origin.
        public HeightRange(int min, int max) {
            this(Optional.of(HeightAnchor.absolute(min)), Optional.of(HeightAnchor.absolute(max)), Check.ORIGIN);
        }

        public DataResult<HeightRange> validate() {
            if (min.isEmpty() && max.isEmpty()) {
                return DataResult.error(() -> "height range needs a min or a max");
            }
            if (min.orElse(null) instanceof HeightAnchor.Fixed(VerticalAnchor.Absolute(int low)) && max.orElse(null) instanceof HeightAnchor.Fixed(VerticalAnchor.Absolute(int high)) && low > high) {
                return DataResult.error(() -> "height range min " + low + " is above max " + high);
            }
            return DataResult.success(this);
        }

        @Override
        public boolean test(Context context) {
            var world = new WorldGenerationContext(context.generation().chunkGenerator(), context.generation().heightAccessor());
            HeightAnchor.Terrain terrain = (heightmap, mode) -> mode.measure(context.terrain(), heightmap, context.origin(), context.footprint());
            int bottom = check == Check.BOX ? context.footprint().minY() : context.origin().getY();
            int top = check == Check.BOX ? context.footprint().maxY() : context.origin().getY();
            return min.map(anchor -> bottom >= anchor.resolve(world, terrain)).orElse(true)
                    && max.map(anchor -> top <= anchor.resolve(world, terrain)).orElse(true);
        }

        @Override
        public PlacementFilterType type() {
            return ASPlacementFilters.HEIGHT_RANGE.get();
        }

        public enum Check implements StringRepresentable {
            ORIGIN("origin"),
            BOX("box");

            public static final Codec<Check> CODEC = StringRepresentable.fromEnum(Check::values);

            private final String name;

            Check(String name) {
                this.name = name;
            }

            @Override
            public String getSerializedName() {
                return name;
            }
        }
    }

    /// Requires the block below the origin to be in `blocks`.
    public record GroundCheck(TagKey<Block> blocks) implements PlacementFilter {
        public static final MapCodec<GroundCheck> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        TagKey.codec(Registries.BLOCK).fieldOf("blocks").forGetter(GroundCheck::blocks))
                .apply(instance, GroundCheck::new));

        @Override
        public boolean test(Context context) {
            BlockPos below = context.origin().below();
            return context.terrain().column(below.getX(), below.getZ()).getBlock(below.getY()).is(blocks);
        }

        @Override
        public PlacementFilterType type() {
            return ASPlacementFilters.GROUND_CHECK.get();
        }
    }

    /// Requires the terrain height under the structure to vary by at most `maxVariance`, measured at
    /// the footprint's corners and center on the structure's heightmap, or the world surface when it has
    /// none. `margin` widens the measured area sideways, or narrows it toward the center when negative.
    public record Flatness(int maxVariance, int margin) implements PlacementFilter {
        public static final int MAX_VARIANCE = 320;
        public static final int MAX_MARGIN = 64;

        public static final MapCodec<Flatness> CODEC = RecordCodecBuilder.<Flatness>mapCodec(instance -> instance.group(
                                Codec.INT.fieldOf("max_variance").forGetter(Flatness::maxVariance),
                                Codec.INT.optionalFieldOf("margin", 0).forGetter(Flatness::margin))
                        .apply(instance, Flatness::new))
                .validate(Flatness::validate);

        public DataResult<Flatness> validate() {
            if (maxVariance < 0 || maxVariance > MAX_VARIANCE) {
                return DataResult.error(() -> "flatness max_variance " + maxVariance + " is outside 0 to " + MAX_VARIANCE);
            }
            if (Math.abs(margin) > MAX_MARGIN) {
                return DataResult.error(() -> "flatness margin " + margin + " is outside -" + MAX_MARGIN + " to " + MAX_MARGIN);
            }
            return DataResult.success(this);
        }

        @Override
        public boolean test(Context context) {
            var area = sampledArea(context.footprint());
            var center = context.footprint().getCenter();
            var centerHeight = context.terrain().surfaceHeight(center.getX(), center.getZ());
            var lowest = centerHeight;
            var highest = centerHeight;
            for (var x : new int[]{area.minX(), area.maxX()}) {
                for (var z : new int[]{area.minZ(), area.maxZ()}) {
                    var height = context.terrain().surfaceHeight(x, z);
                    lowest = Math.min(lowest, height);
                    highest = Math.max(highest, height);
                }
            }
            return highest - lowest <= maxVariance;
        }

        BoundingBox sampledArea(BoundingBox footprint) {
            var center = footprint.getCenter();
            return new BoundingBox(
                    Math.min(footprint.minX() - margin, center.getX()), footprint.minY(), Math.min(footprint.minZ() - margin, center.getZ()),
                    Math.max(footprint.maxX() + margin, center.getX()), footprint.maxY(), Math.max(footprint.maxZ() + margin, center.getZ()));
        }

        @Override
        public PlacementFilterType type() {
            return ASPlacementFilters.FLATNESS.get();
        }
    }

    /// Requires at least `minDepth` blocks of fluid above the structure, at the footprint's center.
    /// Under the sea floor, only the water above the floor counts.
    public record Submerged(int minDepth) implements PlacementFilter {
        public static final MapCodec<Submerged> CODEC = RecordCodecBuilder.<Submerged>mapCodec(instance -> instance.group(
                                Codec.INT.fieldOf("min_depth").forGetter(Submerged::minDepth))
                        .apply(instance, Submerged::new))
                .validate(Submerged::validate);

        public DataResult<Submerged> validate() {
            return minDepth < 0
                    ? DataResult.error(() -> "submerged min_depth " + minDepth + " is negative")
                    : DataResult.success(this);
        }

        @Override
        public boolean test(Context context) {
            var center = context.footprint().getCenter();
            var terrain = context.terrain();
            var waterSurface = terrain.columnHeight(Heightmap.Types.WORLD_SURFACE_WG, center.getX(), center.getZ());
            var seaFloor = terrain.columnHeight(Heightmap.Types.OCEAN_FLOOR_WG, center.getX(), center.getZ());
            return waterAbove(waterSurface, seaFloor, context.footprint().maxY()) >= minDepth;
        }

        static int waterAbove(int waterSurface, int seaFloor, int structureTop) {
            return Math.max(0, waterSurface - Math.max(seaFloor, structureTop + 1));
        }

        @Override
        public PlacementFilterType type() {
            return ASPlacementFilters.SUBMERGED.get();
        }
    }

    /// Requires the water under the structure to be `min` to `max` blocks deep at the footprint's corners and center. Dry land is depth 0.
    public record WaterDepth(int min, Optional<Integer> max) implements PlacementFilter {
        public static final MapCodec<WaterDepth> CODEC = RecordCodecBuilder.<WaterDepth>mapCodec(instance -> instance.group(
                                Codec.INT.optionalFieldOf("min", 0).forGetter(WaterDepth::min),
                                Codec.INT.optionalFieldOf("max").forGetter(WaterDepth::max))
                        .apply(instance, WaterDepth::new))
                .validate(WaterDepth::validate);

        public DataResult<WaterDepth> validate() {
            if (min < 0) {
                return DataResult.error(() -> "water depth min " + min + " is negative");
            }
            return max.filter(max -> max < min).isPresent()
                    ? DataResult.error(() -> "water depth min " + min + " is above max " + max.get())
                    : DataResult.success(this);
        }

        @Override
        public boolean test(Context context) {
            var footprint = context.footprint();
            var center = footprint.getCenter();
            if (!isWithin(depth(context, center.getX(), center.getZ()))) {
                return false;
            }
            for (int x : new int[]{footprint.minX(), footprint.maxX()}) {
                for (int z : new int[]{footprint.minZ(), footprint.maxZ()}) {
                    if (!isWithin(depth(context, x, z))) {
                        return false;
                    }
                }
            }
            return true;
        }

        private boolean isWithin(int depth) {
            return depth >= min && max.map(max -> depth <= max).orElse(true);
        }

        private static int depth(Context context, int x, int z) {
            // The world surface stops at the water's surface, the ocean floor at the ground below it.
            return context.terrain().columnHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - context.terrain().columnHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
        }

        @Override
        public PlacementFilterType type() {
            return ASPlacementFilters.WATER_DEPTH.get();
        }
    }

    /// Requires every biome at the bottom and top of the structure's box, widened by `margin`, to be in `biomes`, or in the structure's biomes when empty.
    public record WithinBiome(Optional<HolderSet<Biome>> biomes, int margin) implements PlacementFilter {
        public static final int MAX_MARGIN = 64;

        public static final MapCodec<WithinBiome> CODEC = RecordCodecBuilder.<WithinBiome>mapCodec(instance -> instance.group(
                                Biome.LIST_CODEC.optionalFieldOf("biomes").forGetter(WithinBiome::biomes),
                                Codec.INT.optionalFieldOf("margin", 0).forGetter(WithinBiome::margin))
                        .apply(instance, WithinBiome::new))
                .validate(WithinBiome::validate);

        public DataResult<WithinBiome> validate() {
            return margin < 0 || margin > MAX_MARGIN
                    ? DataResult.error(() -> "within biome margin " + margin + " is outside 0 to " + MAX_MARGIN)
                    : DataResult.success(this);
        }

        @Override
        public boolean test(Context context) {
            var allowedBiomePredicate = biomes.<Predicate<Holder<Biome>>>map(set -> set::contains).orElse(context.generation().validBiome());
            var box = context.footprint().inflatedBy(margin, 0, margin);
            var resolver = context.generation().biomeResolver();
            var minX = QuartPos.fromBlock(box.minX());
            var maxX = QuartPos.fromBlock(box.maxX());
            var minZ = QuartPos.fromBlock(box.minZ());
            var maxZ = QuartPos.fromBlock(box.maxZ());
            var bottom = QuartPos.fromBlock(box.minY());
            var top = QuartPos.fromBlock(box.maxY());

            // Check ring by ring from the edge inward. The start is already known to be in a valid biome, so a
            // mismatch is most likely near the edge, and a failing box exits early.
            for (int ring = 0; minX + ring <= maxX - ring && minZ + ring <= maxZ - ring; ring++) {
                int ringMinX = minX + ring, ringMaxX = maxX - ring, ringMinZ = minZ + ring, ringMaxZ = maxZ - ring;
                for (int x = ringMinX; x <= ringMaxX; x++) {
                    var edgeColumn = x == ringMinX || x == ringMaxX;
                    var step = edgeColumn ? 1 : Math.max(1, ringMaxZ - ringMinZ);
                    for (int z = ringMinZ; z <= ringMaxZ; z += step) {
                        if (!allowedBiomePredicate.test(resolver.getNoiseBiome(x, bottom, z))
                                || top != bottom && !allowedBiomePredicate.test(resolver.getNoiseBiome(x, top, z))) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        @Override
        public PlacementFilterType type() {
            return ASPlacementFilters.WITHIN_BIOME.get();
        }
    }
}
