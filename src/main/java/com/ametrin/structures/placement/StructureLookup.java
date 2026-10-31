package com.ametrin.structures.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.function.Predicate;

/// Checks if a position or box is inside a structure: inside one of its pieces with `pieceGranularity`, otherwise inside its overall box. Safe to call from features during world generation.
public final class StructureLookup {
    private StructureLookup() {}

    /// Whether a piece of a structure in `tag` intersects `box`. `box` may only span chunks the level can access.
    public static boolean intersectsTaggedStructure(WorldGenLevel level, BoundingBox box, TagKey<Structure> tag) {
        var manager = structureManager(level);
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (int chunkX = SectionPos.blockToSectionCoord(box.minX()); chunkX <= SectionPos.blockToSectionCoord(box.maxX()); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(box.minZ()); chunkZ <= SectionPos.blockToSectionCoord(box.maxZ()); chunkZ++) {
                for (var start : manager.startsForStructure(chunkX, chunkZ, structure -> structures.wrapAsHolder(structure).is(tag))) {
                    if (start.getBoundingBox().intersects(box) && start.getPieces().stream().anyMatch(piece -> piece.getBoundingBox().intersects(box))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isInsideStructure(WorldGenLevel level, BlockPos pos, Structure structure, boolean pieceGranularity) {
        var manager = structureManager(level);
        return pieceGranularity
                ? manager.getStructureWithPieceAt(pos.getX(), pos.getY(), pos.getZ(), structure).isValid()
                : manager.getStructureAt(pos, structure).isValid();
    }

    public static boolean isInsideStructureType(WorldGenLevel level, BlockPos pos, StructureType<?> type, boolean pieceGranularity) {
        return isInside(level, pos, pieceGranularity, holder -> holder.value().type() == type);
    }

    public static boolean isInsideTaggedStructure(WorldGenLevel level, BlockPos pos, TagKey<Structure> tag, boolean pieceGranularity) {
        return isInside(level, pos, pieceGranularity, holder -> holder.is(tag));
    }

    private static boolean isInside(WorldGenLevel level, BlockPos pos, boolean pieceGranularity, Predicate<Holder<Structure>> matches) {
        var manager = structureManager(level);
        if (pieceGranularity) {
            return manager.getStructureWithPieceAt(pos, matches).isValid();
        }
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        return manager.getAllStructuresAt(pos).keySet().stream()
                .anyMatch(structure -> matches.test(structures.wrapAsHolder(structure))
                        && manager.getStructureAt(pos, structure).isValid());
    }

    private static StructureManager structureManager(WorldGenLevel level) {
        var manager = level.getLevel().structureManager();
        return level instanceof WorldGenRegion region ? manager.forWorldGenRegion(region) : manager;
    }
}
