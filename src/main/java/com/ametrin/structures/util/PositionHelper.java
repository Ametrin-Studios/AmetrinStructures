package com.ametrin.structures.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

public final class PositionHelper {
    private PositionHelper() {}

    public static Vec3 transform(Vec3 offset, Mirror mirror, Rotation rotation) {
        Vec3 mirrored = mirror(offset, mirror);
        return switch (rotation) {
            case NONE -> mirrored;
            case CLOCKWISE_90 -> new Vec3(-mirrored.z, mirrored.y, mirrored.x);
            case CLOCKWISE_180 -> new Vec3(-mirrored.x, mirrored.y, -mirrored.z);
            case COUNTERCLOCKWISE_90 -> new Vec3(mirrored.z, mirrored.y, -mirrored.x);
        };
    }

    public static Vec3 mirror(Vec3 offset, Mirror mirror) {
        return switch (mirror) {
            case NONE -> offset;
            // LEFT_RIGHT mirrors across the x/y plane, flipping north/south.
            case LEFT_RIGHT -> new Vec3(offset.x, offset.y, -offset.z);
            // FRONT_BACK mirrors across the y/z plane, flipping east/west.
            case FRONT_BACK -> new Vec3(-offset.x, offset.y, offset.z);
        };
    }

    public static Vec3 bottomCenter(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
    }
}
