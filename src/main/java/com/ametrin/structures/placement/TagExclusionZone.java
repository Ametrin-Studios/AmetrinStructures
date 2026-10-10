package com.ametrin.structures.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/// Keeps a placement `chunkCount` chunks away from the structures in a tag.
public record TagExclusionZone(TagKey<Structure> structures, int chunkCount) {
    public static final Codec<TagExclusionZone> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    TagKey.codec(Registries.STRUCTURE).fieldOf("structures").forGetter(TagExclusionZone::structures),
                    Codec.intRange(1, 16).fieldOf("chunk_count").forGetter(TagExclusionZone::chunkCount))
            .apply(instance, TagExclusionZone::new));

    private static final ThreadLocal<Boolean> CHECKING = ThreadLocal.withInitial(() -> false);

    // Nested checks skip exclusion zones, so placements that exclude each other don't recurse forever.
    public boolean isForbidden(ChunkGeneratorStructureState state, int x, int z, StructurePlacement own) {
        if (CHECKING.get()) {
            return false;
        }
        CHECKING.set(true);
        try {
            return StructurePlacements.hasStructureChunkInRange(
                    state, set -> set.value().placement() != own, holder -> holder.is(structures), x, z, chunkCount);
        } finally {
            CHECKING.set(false);
        }
    }
}
