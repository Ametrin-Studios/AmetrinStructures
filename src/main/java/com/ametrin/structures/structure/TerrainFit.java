package com.ametrin.structures.structure;

import com.ametrin.structures.util.ASNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;

/// How a piece fits the terrain, from its [TerrainBox].
///
/// @param box              in template coordinates, empty for the whole piece
/// @param groundLevelDelta how far above the bottom of the box the ground lies
@ApiStatus.Internal
public record TerrainFit(TerrainAdjustment adaptation, Optional<BoundingBox> box, int groundLevelDelta) {
    private static final String ADAPTATION_KEY = "terrain_adaptation";
    private static final String BOX_KEY = "terrain_box";
    private static final String GROUND_LEVEL_DELTA_KEY = "terrain_ground_level_delta";

    /// @param groundLevelDelta used for pieces saved without one
    public static TerrainFit read(CompoundTag tag, int groundLevelDelta) {
        return new TerrainFit(
                ASNbt.read(tag, ADAPTATION_KEY, TerrainAdjustment.CODEC).orElse(TerrainAdjustment.NONE),
                ASNbt.read(tag, BOX_KEY, BoundingBox.CODEC),
                ASNbt.getIntOr(tag, GROUND_LEVEL_DELTA_KEY, groundLevelDelta));
    }

    public void write(CompoundTag tag) {
        ASNbt.store(tag, ADAPTATION_KEY, TerrainAdjustment.CODEC, adaptation);
        box.ifPresent(value -> ASNbt.store(tag, BOX_KEY, BoundingBox.CODEC, value));
        tag.putInt(GROUND_LEVEL_DELTA_KEY, groundLevelDelta);
    }

    /// The box placed like the template, so it follows the rotation, the mirror and any later move, or `whole` without one.
    public BoundingBox place(StructurePlaceSettings settings, BlockPos position, BoundingBox whole) {
        return box.map(local -> BoundingBox.fromCorners(
                        StructureTemplate.calculateRelativePosition(settings, new BlockPos(local.minX(), local.minY(), local.minZ())).offset(position),
                        StructureTemplate.calculateRelativePosition(settings, new BlockPos(local.maxX(), local.maxY(), local.maxZ())).offset(position)))
                .orElse(whole);
    }
}
