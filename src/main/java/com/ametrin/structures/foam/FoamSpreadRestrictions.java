package com.ametrin.structures.foam;

import com.ametrin.structures.registry.ASFoamSpreadRestrictions;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class FoamSpreadRestrictions {
    private FoamSpreadRestrictions() {}

    /// Permits while the candidate's center is within `maxDistance` of the source's center.
    public record MaxDistance(int maxDistance) implements FoamSpreadRestriction {
        public static final MapCodec<MaxDistance> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ExtraCodecs.NON_NEGATIVE_INT.fieldOf("max_distance").forGetter(MaxDistance::maxDistance))
                .apply(instance, MaxDistance::new));

        @Override
        public boolean permits(Context context) {
            return within(context.source(), context.candidate(), maxDistance);
        }

        @Override
        public Component description() {
            return Component.translatable("foam_spread_restriction.ametrin_structures.max_distance", maxDistance);
        }

        @Override
        public FoamSpreadRestrictionType type() {
            return ASFoamSpreadRestrictions.MAX_DISTANCE.get();
        }

        static boolean within(BlockPos source, BlockPos candidate, int distance) {
            Vec3 from = Vec3.atCenterOf(source);
            Vec3 to = Vec3.atCenterOf(candidate);
            return from.distanceToSqr(to) <= (double) distance * distance;
        }
    }

    /// Keeps foam under a roof: a candidate needs some block above it, even where the sky shows through glass.
    public record RequiresShelter() implements FoamSpreadRestriction {
        public static final RequiresShelter INSTANCE = new RequiresShelter();
        public static final MapCodec<RequiresShelter> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public boolean permits(Context context) {
            var candidate = context.candidate();
            // The world surface lies right above the column's highest non-air block.
            return candidate.getY() + 1 < context.level().getHeight(Heightmap.Types.WORLD_SURFACE, candidate.getX(), candidate.getZ());
        }

        @Override
        public FoamSpreadRestrictionType type() {
            return ASFoamSpreadRestrictions.REQUIRES_SHELTER.get();
        }
    }

    /// [MaxDistance] with the placing stack's count as the distance, so a builder controls the blob size by how many foam items they hold.
    public record StackCountDistance() implements FoamSpreadRestriction {
        public static final StackCountDistance INSTANCE = new StackCountDistance();
        public static final MapCodec<StackCountDistance> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public boolean permits(Context context) {
            return MaxDistance.within(context.source(), context.candidate(), context.stack().getCount());
        }

        @Override
        public FoamSpreadRestrictionType type() {
            return ASFoamSpreadRestrictions.STACK_COUNT_DISTANCE.get();
        }
    }
}
