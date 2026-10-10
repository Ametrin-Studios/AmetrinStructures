package com.ametrin.structures.fixture;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class BlockStateMerging {
    private static final Set<Property<?>> REPLACED = Set.of(
            BlockStateProperties.WATERLOGGED, BlockStateProperties.AXIS, BlockStateProperties.HORIZONTAL_AXIS);
    private static final Set<Property<?>> FACINGS = Set.of(
            BlockStateProperties.FACING, BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.FACING_HOPPER);

    private BlockStateMerging() {}

    /// A block state field whose block gets merged: its text leaves out what merging replaces.
    public static FieldType<BlockState> mergedBlockState() {
        return mergedBlockState(List.of());
    }

    /// As [#mergedBlockState()], also leaving out `derived`, which the fixture sets itself.
    public static FieldType<BlockState> mergedBlockState(Collection<? extends Property<?>> derived) {
        return FieldType.blockState().formattedBy((state, _) -> serialize(state, derived));
    }

    /// `state` as `/setblock` writes it, without the properties merging replaces: waterlogging and
    /// the axes, and a facing at the block's default, which means "the way the marker faces".
    static String serialize(BlockState state) {
        return serialize(state, List.of());
    }

    /// As [#serialize(BlockState)], also leaving out `derived`.
    static String serialize(BlockState state, Collection<? extends Property<?>> derived) {
        var shown = state.getProperties().stream()
                .filter(property -> !REPLACED.contains(property) && !derived.contains(property)
                        && !(FACINGS.contains(property) && state.getValue(property).equals(state.getBlock().defaultBlockState().getValue(property))))
                .map(property -> property.getName() + "=" + valueName(state, property))
                .toList();
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return shown.isEmpty() ? id : id + "[" + String.join(",", shown) + "]";
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    public static BlockState merge(BlockState markerState, BlockState placed) {
        var front = FixtureBlock.front(markerState);
        var horizontal = FixtureBlock.horizontalFacing(markerState);
        var waterlogged = markerState.getValue(FixtureBlock.WATERLOGGED);

        var result = placed;
        result = mergeFacing(result, BlockStateProperties.FACING, front);
        result = mergeFacing(result, BlockStateProperties.HORIZONTAL_FACING, horizontal);
        result = mergeFacing(result, BlockStateProperties.FACING_HOPPER, front == Direction.DOWN ? front : horizontal);

        if (result.hasProperty(BlockStateProperties.AXIS)) {
            result = result.setValue(BlockStateProperties.AXIS, front.getAxis());
        }
        if (result.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            result = result.setValue(BlockStateProperties.HORIZONTAL_AXIS, horizontal.getAxis());
        }
        if (result.hasProperty(BlockStateProperties.WATERLOGGED)) {
            result = result.setValue(BlockStateProperties.WATERLOGGED, waterlogged);
        }
        return result;
    }

    private static BlockState mergeFacing(BlockState placed, EnumProperty<Direction> property, Direction target) {
        if (!placed.hasProperty(property)) {
            return placed;
        }
        var current = placed.getValue(property);
        var defaultFacing = placed.getBlock().defaultBlockState().getValue(property);

        // Vertical facings cannot be expressed as a rotation, so they are applied directly.
        if (!target.getAxis().isHorizontal() || !defaultFacing.getAxis().isHorizontal()) {
            return property.getPossibleValues().contains(target) ? placed.setValue(property, target) : placed;
        }
        var rotated = rotationBetween(defaultFacing, target).rotate(current);
        return property.getPossibleValues().contains(rotated) ? placed.setValue(property, rotated) : placed;
    }

    /// The rotation taking `from` to `to`. Both must be horizontal.
    public static Rotation rotationBetween(Direction from, Direction to) {
        for (var rotation : Rotation.values()) {
            if (rotation.rotate(from) == to) {
                return rotation;
            }
        }
        return Rotation.NONE;
    }
}
