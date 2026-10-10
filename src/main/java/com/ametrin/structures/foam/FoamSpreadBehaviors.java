package com.ametrin.structures.foam;

import com.ametrin.structures.registry.ASFoamSpreadBehaviors;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

public final class FoamSpreadBehaviors {
    private FoamSpreadBehaviors() {}

    /// The six face neighbors.
    public record Faces() implements FoamSpreadBehavior {
        public static final Faces INSTANCE = new Faces();
        public static final MapCodec<Faces> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public List<BlockPos> offsets(Context context) {
            return faces(context.source());
        }

        @Override
        public FoamSpreadBehaviorType type() {
            return ASFoamSpreadBehaviors.FACES.get();
        }
    }

    /// The twelve edge neighbors.
    public record Edges() implements FoamSpreadBehavior {
        public static final Edges INSTANCE = new Edges();
        public static final MapCodec<Edges> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public List<BlockPos> offsets(Context context) {
            return edges(context.source());
        }

        @Override
        public FoamSpreadBehaviorType type() {
            return ASFoamSpreadBehaviors.EDGES.get();
        }
    }

    /// [Faces] and [Edges] together.
    public record FacesAndEdges() implements FoamSpreadBehavior {
        public static final FacesAndEdges INSTANCE = new FacesAndEdges();
        public static final MapCodec<FacesAndEdges> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public List<BlockPos> offsets(Context context) {
            var positions = new ArrayList<BlockPos>(18);
            positions.addAll(faces(context.source()));
            positions.addAll(edges(context.source()));
            return positions;
        }

        @Override
        public FoamSpreadBehaviorType type() {
            return ASFoamSpreadBehaviors.FACES_AND_EDGES.get();
        }
    }

    /// The face neighbors except the two along the axis the builder faced, which fills a plane. Without an axis it's the same as [Faces].
    public record Planar() implements FoamSpreadBehavior {
        public static final Planar INSTANCE = new Planar();
        public static final MapCodec<Planar> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public List<BlockPos> offsets(Context context) {
            if (context.axis().isEmpty()) {
                return faces(context.source());
            }
            var axis = context.axis().get();
            var positions = new ArrayList<BlockPos>(4);
            for (var direction : Direction.values()) {
                if (direction.getAxis() != axis) {
                    positions.add(context.source().relative(direction));
                }
            }
            return positions;
        }

        @Override
        public FoamSpreadBehaviorType type() {
            return ASFoamSpreadBehaviors.PLANAR.get();
        }
    }

    private static List<BlockPos> faces(BlockPos source) {
        var positions = new ArrayList<BlockPos>(6);
        for (var direction : Direction.values()) {
            positions.add(source.relative(direction));
        }
        return positions;
    }

    private static List<BlockPos> edges(BlockPos source) {
        var positions = new ArrayList<BlockPos>(12);
        for (var vertical : List.of(Direction.UP, Direction.DOWN)) {
            for (var horizontal : Direction.Plane.HORIZONTAL) {
                positions.add(source.relative(vertical).relative(horizontal));
            }
        }
        for (var horizontal : Direction.Plane.HORIZONTAL) {
            positions.add(source.relative(horizontal).relative(horizontal.getClockWise()));
        }
        return positions;
    }
}
